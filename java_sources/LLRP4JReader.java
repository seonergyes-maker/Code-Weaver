package com.rfid.zebra;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Lector RFID usando implementación directa del protocolo LLRP.
 * Compatible con Zebra FX7500 y otros lectores LLRP.
 */
public class LLRP4JReader implements AutoCloseable {
    
    private final String readerIP;
    private final int port;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean connected = false;
    private volatile boolean reading = false;
    private ExecutorService executor;
    
    private Consumer<String[]> tagCallback; // [epc, antenna, rssi, timestamp]
    private Consumer<String> statusCallback;
    private Consumer<Exception> errorCallback;
    
    // Contadores de estadísticas
    private long totalTagsRead = 0;
    private long sessionTagsRead = 0;
    
    public LLRP4JReader(String readerIP) {
        this(readerIP, 5084);
    }
    
    public LLRP4JReader(String readerIP, int port) {
        this.readerIP = readerIP;
        this.port = port;
    }
    
    public void setTagCallback(Consumer<String[]> callback) {
        this.tagCallback = callback;
    }
    
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }
    
    public void setErrorCallback(Consumer<Exception> callback) {
        this.errorCallback = callback;
    }
    
    private void log(String message) {
        System.out.println("[LLRP] " + message);
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }
    
    private void logError(String message, Exception e) {
        System.err.println("[LLRP] ERROR: " + message);
        if (e != null) {
            e.printStackTrace();
        }
        if (errorCallback != null && e != null) {
            errorCallback.accept(e);
        }
    }
    
    /**
     * Conecta al lector RFID.
     */
    public boolean connect() {
        try {
            log("Conectando a " + readerIP + ":" + port + "...");
            
            socket = new Socket();
            socket.connect(new InetSocketAddress(readerIP, port), 5000);
            socket.setSoTimeout(1000);
            
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            
            connected = true;
            log("Conectado exitosamente a " + readerIP);
            
            // Esperar READER_EVENT_NOTIFICATION inicial
            Thread.sleep(500);
            drainInitialMessages();
            
            return true;
            
        } catch (Exception e) {
            logError("Error al conectar: " + e.getMessage(), e);
            return false;
        }
    }
    
    private void drainInitialMessages() {
        try {
            while (true) {
                try {
                    byte[] header = new byte[10];
                    int read = in.read(header, 0, 10);
                    if (read < 10) break;
                    
                    int typeWord = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
                    int messageType = typeWord & 0x03FF;
                    long messageLength = ((long)(header[2] & 0xFF) << 24) |
                                        ((long)(header[3] & 0xFF) << 16) |
                                        ((long)(header[4] & 0xFF) << 8) |
                                        ((long)(header[5] & 0xFF));
                    
                    int bodyLength = (int)(messageLength - 10);
                    if (bodyLength > 0) {
                        byte[] body = new byte[bodyLength];
                        in.readFully(body);
                    }
                    
                    log("Mensaje inicial recibido: Type " + messageType);
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (Exception e) {
            // Ignorar
        }
    }
    
    /**
     * Desconecta del lector.
     */
    public void disconnect() {
        try {
            if (reading) {
                stopReading();
            }
            
            connected = false;
            
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            if (executor != null) {
                executor.shutdownNow();
            }
            
            log("Desconectado");
            
        } catch (Exception e) {
            logError("Error al desconectar: " + e.getMessage(), e);
        }
    }
    
    /**
     * Inicia la lectura continua de tags.
     */
    public boolean startReading() {
        if (!connected) {
            logError("No conectado al lector", null);
            return false;
        }
        
        if (reading) {
            log("Ya está leyendo");
            return true;
        }
        
        try {
            // Configurar ROSpec
            log("Configurando ROSpec...");
            
            // DELETE_ROSPEC(0) - eliminar todas las ROSpecs existentes
            sendDeleteRoSpec(0, 1);
            LLRPResponse resp = waitForResponse(31, 2000);
            if (resp == null || resp.statusCode != 0) {
                log("Advertencia: DELETE_ROSPEC falló, continuando...");
            }
            
            // ADD_ROSPEC
            sendAddRoSpec(2);
            resp = waitForResponse(30, 2000);
            if (resp == null || resp.statusCode != 0) {
                logError("ADD_ROSPEC falló" + (resp != null ? ": " + resp.statusCode : ""), null);
                return false;
            }
            log("ROSpec agregado correctamente");
            
            // ENABLE_ROSPEC
            sendEnableRoSpec(3);
            resp = waitForResponse(34, 2000);
            if (resp == null || resp.statusCode != 0) {
                logError("ENABLE_ROSPEC falló", null);
                return false;
            }
            log("ROSpec habilitado");
            
            // START_ROSPEC
            sendStartRoSpec(4);
            resp = waitForResponse(32, 2000);
            if (resp == null || resp.statusCode != 0) {
                logError("START_ROSPEC falló", null);
                return false;
            }
            log("ROSpec iniciado - Leyendo tags...");
            
            reading = true;
            sessionTagsRead = 0;
            
            // Iniciar thread de lectura
            executor = Executors.newSingleThreadExecutor();
            executor.submit(this::readLoop);
            
            return true;
            
        } catch (Exception e) {
            logError("Error al iniciar lectura: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Detiene la lectura de tags.
     */
    public boolean stopReading() {
        if (!reading) {
            return true;
        }
        
        reading = false;
        
        try {
            // STOP_ROSPEC
            sendStopRoSpec(5);
            waitForResponse(33, 1000);
            
            // DELETE_ROSPEC
            sendDeleteRoSpec(1, 6);
            waitForResponse(31, 1000);
            
            log("Lectura detenida. Tags leídos en sesión: " + sessionTagsRead);
            
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            
            return true;
            
        } catch (Exception e) {
            logError("Error al detener lectura: " + e.getMessage(), e);
            return false;
        }
    }
    
    private void readLoop() {
        byte[] header = new byte[10];
        
        while (reading && connected) {
            try {
                // Leer header
                int bytesRead = 0;
                while (bytesRead < 10 && reading) {
                    try {
                        int read = in.read(header, bytesRead, 10 - bytesRead);
                        if (read == -1) {
                            log("Conexión cerrada por el lector");
                            reading = false;
                            return;
                        }
                        bytesRead += read;
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                }
                
                if (!reading) break;
                
                // Parsear header
                int typeWord = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
                int messageType = typeWord & 0x03FF;
                long messageLength = ((long)(header[2] & 0xFF) << 24) |
                                    ((long)(header[3] & 0xFF) << 16) |
                                    ((long)(header[4] & 0xFF) << 8) |
                                    ((long)(header[5] & 0xFF));
                
                // Leer body
                int bodyLength = (int)(messageLength - 10);
                byte[] body = new byte[bodyLength];
                if (bodyLength > 0) {
                    int bodyRead = 0;
                    while (bodyRead < bodyLength) {
                        int read = in.read(body, bodyRead, bodyLength - bodyRead);
                        if (read == -1) break;
                        bodyRead += read;
                    }
                }
                
                // Procesar mensaje
                if (messageType == 61) { // RO_ACCESS_REPORT
                    processRoAccessReport(body);
                } else if (messageType == 62) { // KEEPALIVE
                    sendKeepaliveAck();
                }
                
            } catch (SocketTimeoutException e) {
                // Normal, continuar
            } catch (Exception e) {
                if (reading) {
                    logError("Error en lectura: " + e.getMessage(), e);
                }
            }
        }
    }
    
    private void processRoAccessReport(byte[] body) {
        try {
            int offset = 0;
            
            while (offset < body.length - 4) {
                // Leer parámetro TLV
                int paramType = ((body[offset] & 0x3F) << 8) | (body[offset + 1] & 0xFF);
                int paramLen = ((body[offset + 2] & 0xFF) << 8) | (body[offset + 3] & 0xFF);
                
                if (paramType == 240) { // TagReportData
                    parseTagReportData(body, offset + 4, paramLen - 4);
                }
                
                offset += paramLen;
                if (paramLen <= 0) break;
            }
        } catch (Exception e) {
            logError("Error procesando RO_ACCESS_REPORT: " + e.getMessage(), e);
        }
    }
    
    private void parseTagReportData(byte[] data, int start, int length) {
        try {
            String epc = null;
            int antenna = 0;
            int rssi = 0;
            long timestamp = System.currentTimeMillis();
            
            int offset = start;
            int end = start + length;
            
            while (offset < end - 2) {
                // Verificar si es TV (bit 7 = 1) o TLV (bit 7 = 0)
                int firstByte = data[offset] & 0xFF;
                
                if ((firstByte & 0x80) != 0) {
                    // TV Parameter (1-byte type)
                    int tvType = firstByte & 0x7F;
                    
                    if (tvType == 1) { // AntennaID
                        antenna = ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
                        offset += 3;
                    } else if (tvType == 6) { // PeakRSSI
                        rssi = data[offset + 1]; // signed byte
                        offset += 2;
                    } else if (tvType == 14) { // C1G2PC + EPC (EPCData)
                        // EPC-96 inline format
                        int epcBitCount = ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
                        int epcByteCount = (epcBitCount + 7) / 8;
                        if (epcByteCount > 0 && offset + 3 + epcByteCount <= end) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < epcByteCount; i++) {
                                sb.append(String.format("%02X", data[offset + 3 + i]));
                            }
                            epc = sb.toString();
                        }
                        offset += 3 + epcByteCount;
                    } else {
                        // Skip unknown TV parameter - estimate 2-4 bytes
                        offset += 2;
                    }
                } else {
                    // TLV Parameter
                    int tlvType = ((data[offset] & 0x3F) << 8) | (data[offset + 1] & 0xFF);
                    int tlvLen = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
                    
                    if (tlvType == 241) { // EPC-96
                        // EPC dentro del TLV
                        if (tlvLen >= 16) { // 4 header + 12 EPC
                            StringBuilder sb = new StringBuilder();
                            for (int i = 4; i < Math.min(16, tlvLen); i++) {
                                sb.append(String.format("%02X", data[offset + i]));
                            }
                            if (sb.length() > 0) {
                                epc = sb.toString();
                            }
                        }
                    }
                    
                    offset += tlvLen;
                    if (tlvLen <= 0) break;
                }
            }
            
            // Si encontramos EPC, notificar
            if (epc != null && !epc.isEmpty() && tagCallback != null) {
                totalTagsRead++;
                sessionTagsRead++;
                
                String[] tagData = new String[] {
                    epc,
                    String.valueOf(antenna),
                    String.valueOf(rssi),
                    String.valueOf(timestamp)
                };
                
                tagCallback.accept(tagData);
            }
            
        } catch (Exception e) {
            // Ignorar errores de parsing individuales
        }
    }
    
    private LLRPResponse waitForResponse(int expectedType, int timeoutMs) {
        long startTime = System.currentTimeMillis();
        byte[] header = new byte[10];
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                int bytesRead = 0;
                while (bytesRead < 10) {
                    try {
                        int read = in.read(header, bytesRead, 10 - bytesRead);
                        if (read == -1) return null;
                        bytesRead += read;
                    } catch (SocketTimeoutException e) {
                        if (System.currentTimeMillis() - startTime >= timeoutMs) {
                            return null;
                        }
                        continue;
                    }
                }
                
                int typeWord = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
                int messageType = typeWord & 0x03FF;
                long messageLength = ((long)(header[2] & 0xFF) << 24) |
                                    ((long)(header[3] & 0xFF) << 16) |
                                    ((long)(header[4] & 0xFF) << 8) |
                                    ((long)(header[5] & 0xFF));
                
                int bodyLength = (int)(messageLength - 10);
                byte[] body = new byte[bodyLength];
                if (bodyLength > 0) {
                    in.readFully(body);
                }
                
                if (messageType == expectedType) {
                    LLRPResponse resp = new LLRPResponse();
                    resp.type = messageType;
                    if (bodyLength >= 8) {
                        // LLRPStatus: Type(2) + Len(2) + StatusCode(2) + ErrorDescByteCount(2)
                        resp.statusCode = ((body[4] & 0xFF) << 8) | (body[5] & 0xFF);
                    }
                    return resp;
                }
                
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    
    private static class LLRPResponse {
        int type;
        int statusCode;
    }
    
    // ==================== Métodos de envío LLRP ====================
    
    private void sendMessage(int type, int msgId, byte[] body) throws IOException {
        int length = 10 + body.length;
        // Header: Reserved (3 bits) + Version (3 bits) + Type (10 bits)
        // Version 1 = LLRP 1.0.1
        int typeWord = (1 << 10) | (type & 0x3FF);
        
        out.writeShort(typeWord);
        out.writeInt(length);
        out.writeInt(msgId);
        out.write(body);
        out.flush();
    }
    
    private void writeParameter(DataOutputStream dos, int type, byte[] data) throws IOException {
        int length = 4 + data.length;
        dos.writeShort(type & 0x3FF);
        dos.writeShort(length);
        dos.write(data);
    }
    
    private void sendDeleteRoSpec(int roSpecId, int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(roSpecId);
        sendMessage(21, msgId, baos.toByteArray());
    }
    
    private void sendAddRoSpec(int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // ROSpec Parameter (Type 177)
        ByteArrayOutputStream rospecBaos = new ByteArrayOutputStream();
        DataOutputStream rospecDos = new DataOutputStream(rospecBaos);
        
        rospecDos.writeInt(1);  // ROSpecID
        rospecDos.writeByte(0); // Priority
        rospecDos.writeByte(0); // CurrentState (Disabled)
        
        // ROBoundarySpec (Type 178)
        ByteArrayOutputStream boundaryBaos = new ByteArrayOutputStream();
        DataOutputStream boundaryDos = new DataOutputStream(boundaryBaos);
        
        // ROSpecStartTrigger (Type 179) - Null trigger
        writeParameter(boundaryDos, 179, new byte[]{0});
        
        // ROSpecStopTrigger (Type 182) - Null trigger
        ByteArrayOutputStream stopBaos = new ByteArrayOutputStream();
        DataOutputStream stopDos = new DataOutputStream(stopBaos);
        stopDos.writeByte(0); // TriggerType = Null
        stopDos.writeInt(0);  // DurationTriggerValue
        writeParameter(boundaryDos, 182, stopBaos.toByteArray());
        
        writeParameter(rospecDos, 178, boundaryBaos.toByteArray());
        
        // AISpec (Type 183)
        ByteArrayOutputStream aispecBaos = new ByteArrayOutputStream();
        DataOutputStream aispecDos = new DataOutputStream(aispecBaos);
        
        aispecDos.writeShort(1); // AntennaIDs count
        aispecDos.writeShort(1); // AntennaID = 1
        
        // AISpecStopTrigger (Type 184) - Null trigger
        ByteArrayOutputStream aiStopBaos = new ByteArrayOutputStream();
        DataOutputStream aiStopDos = new DataOutputStream(aiStopBaos);
        aiStopDos.writeByte(0); // TriggerType = Null
        aiStopDos.writeInt(0);  // DurationTrigger
        writeParameter(aispecDos, 184, aiStopBaos.toByteArray());
        
        // InventoryParameterSpec (Type 186)
        ByteArrayOutputStream invBaos = new ByteArrayOutputStream();
        DataOutputStream invDos = new DataOutputStream(invBaos);
        invDos.writeShort(1); // InventoryParameterSpecID
        invDos.writeByte(1);  // ProtocolID = EPCGlobalClass1Gen2
        writeParameter(aispecDos, 186, invBaos.toByteArray());
        
        writeParameter(rospecDos, 183, aispecBaos.toByteArray());
        
        // ROReportSpec (Type 237)
        ByteArrayOutputStream reportBaos = new ByteArrayOutputStream();
        DataOutputStream reportDos = new DataOutputStream(reportBaos);
        reportDos.writeByte(1);  // ROReportTrigger = Upon_N_Tags_Or_End_Of_ROSpec
        reportDos.writeShort(1); // N = 1 (report immediately)
        
        // TagReportContentSelector (Type 238)
        ByteArrayOutputStream tagContentBaos = new ByteArrayOutputStream();
        DataOutputStream tagContentDos = new DataOutputStream(tagContentBaos);
        // EnableMask bits for: ROSpecID, SpecIndex, InvParamSpecID, AntennaID, PeakRSSI, FirstSeenTimestamp, LastSeenTimestamp, TagSeenCount
        short enableMask = (short)0b1111011110;
        tagContentDos.writeShort(enableMask);
        writeParameter(reportDos, 238, tagContentBaos.toByteArray());
        
        writeParameter(rospecDos, 237, reportBaos.toByteArray());
        
        writeParameter(dos, 177, rospecBaos.toByteArray());
        
        sendMessage(20, msgId, baos.toByteArray());
    }
    
    private void sendEnableRoSpec(int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(1); // ROSpecID
        sendMessage(24, msgId, baos.toByteArray());
    }
    
    private void sendStartRoSpec(int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(1); // ROSpecID
        sendMessage(22, msgId, baos.toByteArray());
    }
    
    private void sendStopRoSpec(int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(1); // ROSpecID
        sendMessage(23, msgId, baos.toByteArray());
    }
    
    private void sendKeepaliveAck() throws IOException {
        sendMessage(72, 0, new byte[0]);
    }
    
    // ==================== Métodos públicos adicionales ====================
    
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }
    
    public boolean isReading() {
        return reading;
    }
    
    public long getTotalTagsRead() {
        return totalTagsRead;
    }
    
    public long getSessionTagsRead() {
        return sessionTagsRead;
    }
    
    public String getReaderIP() {
        return readerIP;
    }
    
    @Override
    public void close() {
        disconnect();
    }
}
