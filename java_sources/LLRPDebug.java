import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.concurrent.*;

/**
 * Debug de conexión LLRP a bajo nivel.
 * Lee bytes crudos del socket para ver exactamente qué envía el lector.
 */
public class LLRPDebug {
    
    private static final int LLRP_PORT = 5084;
    private static volatile boolean running = true;
    
    public static void main(String[] args) {
        String readerIP = args.length > 0 ? args[0] : "192.168.1.117";
        int testDuration = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        
        System.out.println("========================================");
        System.out.println("  DEBUG LLRP - Análisis de tráfico");
        System.out.println("========================================");
        System.out.println("IP: " + readerIP);
        System.out.println("Puerto: " + LLRP_PORT);
        System.out.println("Duración: " + testDuration + " segundos");
        System.out.println();
        
        try (Socket socket = new Socket()) {
            // Conectar con timeout
            System.out.println("[1] Conectando...");
            socket.connect(new InetSocketAddress(readerIP, LLRP_PORT), 5000);
            socket.setSoTimeout(1000); // 1 segundo timeout para lecturas
            System.out.println("    OK - Conectado a " + socket.getRemoteSocketAddress());
            
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            
            // Thread para leer mensajes entrantes
            ExecutorService executor = Executors.newSingleThreadExecutor();
            final int[] messageCount = {0};
            final int[] roAccessReportCount = {0};
            
            Future<?> readerTask = executor.submit(() -> {
                System.out.println("[READER] Thread de lectura iniciado");
                byte[] header = new byte[10];
                
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        // Leer header LLRP (10 bytes)
                        int bytesRead = 0;
                        while (bytesRead < 10) {
                            int read = in.read(header, bytesRead, 10 - bytesRead);
                            if (read == -1) {
                                System.out.println("[READER] Conexión cerrada por el lector");
                                return;
                            }
                            bytesRead += read;
                        }
                        
                        // Parsear header
                        // Bytes 0-1: Reserved (3 bits) + Version (3 bits) + Message Type (10 bits)
                        // Bytes 2-5: Message Length (32 bits)
                        // Bytes 6-9: Message ID (32 bits)
                        
                        int typeWord = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
                        int version = (typeWord >> 10) & 0x07;
                        int messageType = typeWord & 0x03FF;
                        
                        long messageLength = ((long)(header[2] & 0xFF) << 24) |
                                            ((long)(header[3] & 0xFF) << 16) |
                                            ((long)(header[4] & 0xFF) << 8) |
                                            ((long)(header[5] & 0xFF));
                        
                        long messageId = ((long)(header[6] & 0xFF) << 24) |
                                        ((long)(header[7] & 0xFF) << 16) |
                                        ((long)(header[8] & 0xFF) << 8) |
                                        ((long)(header[9] & 0xFF));
                        
                        // Leer cuerpo del mensaje
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
                        
                        messageCount[0]++;
                        String msgName = getMessageTypeName(messageType);
                        
                        // Mostrar mensaje recibido
                        if (messageType == 61) { // RO_ACCESS_REPORT
                            roAccessReportCount[0]++;
                            System.out.println("[>>> RO_ACCESS_REPORT] ID:" + messageId);
                            // Parsear TagReportData parameters
                            parseTagReportData(body);
                        } else if (messageType == 100) { // ERROR_MESSAGE
                            System.out.println("[!!! ERROR_MESSAGE] ID:" + messageId + " Len:" + messageLength);
                            // Parsear LLRPStatus del error
                            if (bodyLength >= 6) {
                                // LLRPStatus parameter: Type (2 bytes) + Length (2 bytes) + StatusCode (2 bytes) + ErrorDescription
                                int paramType = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
                                int paramLen = ((body[2] & 0xFF) << 8) | (body[3] & 0xFF);
                                int statusCode = ((body[4] & 0xFF) << 8) | (body[5] & 0xFF);
                                
                                String statusName = getStatusCodeName(statusCode);
                                System.out.println("    StatusCode: " + statusCode + " (" + statusName + ")");
                                
                                // Leer ErrorDescription si existe
                                if (paramLen > 6 && bodyLength > 6) {
                                    // ErrorDescriptionByteCount (2 bytes)
                                    int descLen = ((body[6] & 0xFF) << 8) | (body[7] & 0xFF);
                                    if (descLen > 0 && bodyLength >= 8 + descLen) {
                                        String errorDesc = new String(body, 8, descLen, "UTF-8");
                                        System.out.println("    ErrorDescription: " + errorDesc);
                                    }
                                }
                            }
                            // Mostrar bytes crudos
                            StringBuilder hex = new StringBuilder();
                            for (int i = 0; i < Math.min(100, bodyLength); i++) {
                                hex.append(String.format("%02X ", body[i]));
                            }
                            System.out.println("    Raw: " + hex.toString());
                        } else if (messageType == 62) { // KEEPALIVE
                            // Responder KEEPALIVE_ACK silenciosamente
                            sendKeepaliveAck(out, messageId);
                        } else if (messageType == 63) { // READER_EVENT_NOTIFICATION
                            System.out.println("[EVENTO] READER_EVENT_NOTIFICATION ID:" + messageId);
                        } else if (messageType == 13) { // SET_READER_CONFIG_RESPONSE
                            System.out.print("[MSG] SET_READER_CONFIG_RESPONSE ID:" + messageId);
                            if (bodyLength >= 8) {
                                int statusCode = ((body[4] & 0xFF) << 8) | (body[5] & 0xFF);
                                if (statusCode == 0) {
                                    System.out.println(" -> OK");
                                } else {
                                    System.out.println(" -> ERROR: " + statusCode + " (" + getStatusCodeName(statusCode) + ")");
                                }
                            } else {
                                System.out.println();
                            }
                        } else if (messageType >= 30 && messageType <= 34) {
                            // *_RESPONSE messages - parsear LLRPStatus
                            System.out.print("[MSG] " + msgName + " (Type:" + messageType + ") ID:" + messageId);
                            if (bodyLength >= 8) {
                                // LLRPStatus: Type (2) + Len (2) + StatusCode (2) + ErrorDescByteCount (2)
                                int statusCode = ((body[4] & 0xFF) << 8) | (body[5] & 0xFF);
                                String statusName = getStatusCodeName(statusCode);
                                if (statusCode == 0) {
                                    System.out.println(" -> OK");
                                } else {
                                    System.out.println(" -> ERROR: " + statusCode + " (" + statusName + ")");
                                    // Mostrar descripción si existe
                                    int descLen = ((body[6] & 0xFF) << 8) | (body[7] & 0xFF);
                                    if (descLen > 0 && bodyLength >= 8 + descLen) {
                                        String desc = new String(body, 8, descLen, "UTF-8");
                                        System.out.println("    Descripción: " + desc);
                                    }
                                }
                            } else {
                                System.out.println();
                            }
                        } else {
                            System.out.println("[MSG] " + msgName + " (Type:" + messageType + ") ID:" + messageId + " Len:" + messageLength);
                            // Mostrar primeros bytes para debug
                            if (bodyLength > 0) {
                                StringBuilder hex = new StringBuilder();
                                for (int i = 0; i < Math.min(40, bodyLength); i++) {
                                    hex.append(String.format("%02X ", body[i]));
                                }
                                System.out.println("    Raw: " + hex.toString());
                            }
                        }
                        
                    } catch (SocketTimeoutException e) {
                        // Timeout normal, continuar
                    } catch (IOException e) {
                        if (running) {
                            System.err.println("[READER] Error: " + e.getMessage());
                        }
                        break;
                    }
                }
                System.out.println("[READER] Thread de lectura terminado");
            });
            
            // Esperar un momento para recibir READER_EVENT_NOTIFICATION inicial
            Thread.sleep(1000);
            
            // Enviar DELETE_ROSPEC(0) para limpiar
            System.out.println("[2] Enviando DELETE_ROSPEC(0)...");
            sendDeleteRoSpec(out, 0, 1);
            Thread.sleep(500);
            
            // Enviar ADD_ROSPEC
            System.out.println("[3] Enviando ADD_ROSPEC...");
            sendAddRoSpec(out, 2);
            Thread.sleep(500);
            
            // Enviar ENABLE_ROSPEC
            System.out.println("[4] Enviando ENABLE_ROSPEC...");
            sendEnableRoSpec(out, 3);
            Thread.sleep(500);
            
            // Enviar START_ROSPEC
            System.out.println("[5] Enviando START_ROSPEC...");
            sendStartRoSpec(out, 4);
            Thread.sleep(500);
            
            System.out.println();
            System.out.println("========================================");
            System.out.println("  MONITOREANDO TRÁFICO (" + testDuration + "s)");
            System.out.println("========================================");
            System.out.println();
            
            // Monitorear por la duración especificada
            long startTime = System.currentTimeMillis();
            int lastMsgCount = 0;
            int lastReportCount = 0;
            
            for (int i = 0; i < testDuration && running; i++) {
                Thread.sleep(1000);
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                int newMsgs = messageCount[0] - lastMsgCount;
                int newReports = roAccessReportCount[0] - lastReportCount;
                
                System.out.printf("[%3ds] Mensajes: %d (+%d) | RO_ACCESS_REPORT: %d (+%d)\n",
                    elapsed, messageCount[0], newMsgs, roAccessReportCount[0], newReports);
                
                // Cada 5 segundos, enviar GET_REPORT
                if (i > 0 && i % 5 == 0) {
                    System.out.println("    [Enviando GET_REPORT...]");
                    sendGetReport(out, 100 + i);
                }
                
                lastMsgCount = messageCount[0];
                lastReportCount = roAccessReportCount[0];
            }
            
            running = false;
            
            // Detener ROSpec
            System.out.println();
            System.out.println("[6] Deteniendo ROSpec...");
            sendStopRoSpec(out, 5);
            Thread.sleep(300);
            
            sendDeleteRoSpec(out, 1, 6);
            Thread.sleep(300);
            
            // Cerrar
            System.out.println("[7] Cerrando conexión...");
            executor.shutdownNow();
            
            System.out.println();
            System.out.println("========================================");
            System.out.println("  RESULTADOS");
            System.out.println("========================================");
            System.out.println("Total mensajes recibidos: " + messageCount[0]);
            System.out.println("Total RO_ACCESS_REPORT: " + roAccessReportCount[0]);
            
            if (roAccessReportCount[0] == 0) {
                System.out.println();
                System.out.println("DIAGNÓSTICO: No se recibieron RO_ACCESS_REPORT");
                System.out.println("Posibles causas:");
                System.out.println("  1. El ROSpec no se inició correctamente");
                System.out.println("  2. No hay tags en el rango de la antena");
                System.out.println("  3. Problema de configuración del lector");
                System.out.println("  4. El lector no está enviando reportes por esta conexión");
            }
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String getMessageTypeName(int type) {
        switch (type) {
            case 1: return "GET_READER_CAPABILITIES";
            case 2: return "GET_READER_CONFIG";
            case 3: return "SET_READER_CONFIG";
            case 11: return "GET_READER_CAPABILITIES_RESPONSE";
            case 12: return "GET_READER_CONFIG_RESPONSE";
            case 13: return "SET_READER_CONFIG_RESPONSE";
            case 20: return "ADD_ROSPEC";
            case 21: return "DELETE_ROSPEC";
            case 22: return "START_ROSPEC";
            case 23: return "STOP_ROSPEC";
            case 24: return "ENABLE_ROSPEC";
            case 30: return "ADD_ROSPEC_RESPONSE";
            case 31: return "DELETE_ROSPEC_RESPONSE";
            case 32: return "START_ROSPEC_RESPONSE";
            case 33: return "STOP_ROSPEC_RESPONSE";
            case 34: return "ENABLE_ROSPEC_RESPONSE";
            case 60: return "GET_REPORT";
            case 61: return "RO_ACCESS_REPORT";
            case 62: return "KEEPALIVE";
            case 63: return "READER_EVENT_NOTIFICATION";
            case 72: return "KEEPALIVE_ACK";
            case 100: return "ERROR_MESSAGE";
            default: return "UNKNOWN(" + type + ")";
        }
    }
    
    private static String getStatusCodeName(int code) {
        switch (code) {
            case 0: return "M_Success";
            case 100: return "M_ParameterError";
            case 101: return "M_FieldError";
            case 102: return "M_UnexpectedParameter";
            case 103: return "M_MissingParameter";
            case 104: return "M_DuplicateParameter";
            case 105: return "M_OverflowParameter";
            case 106: return "M_OverflowField";
            case 107: return "M_UnknownParameter";
            case 108: return "M_UnknownField";
            case 109: return "M_UnsupportedMessage";
            case 110: return "M_UnsupportedVersion";
            case 111: return "M_UnsupportedParameter";
            case 200: return "A_Invalid";
            case 201: return "A_OutOfRange";
            default: return "Unknown(" + code + ")";
        }
    }
    
    private static void parseTagReportData(byte[] data) {
        if (data == null || data.length == 0) {
            System.out.println("    (sin datos)");
            return;
        }
        
        int offset = 0;
        int tagCount = 0;
        
        while (offset + 4 <= data.length) {
            // Leer TLV header: Type (2 bytes) + Length (2 bytes)
            int paramType = ((data[offset] & 0x3F) << 8) | (data[offset + 1] & 0xFF);
            int paramLen = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            
            if (paramLen < 4 || offset + paramLen > data.length) {
                break;
            }
            
            // TagReportData = Type 240
            if (paramType == 240) {
                tagCount++;
                parseOneTag(data, offset + 4, paramLen - 4, tagCount);
            }
            
            offset += paramLen;
        }
        
        if (tagCount == 0) {
            System.out.println("    (reporte vacío - sin tags)");
            // Mostrar bytes crudos para debug
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(60, data.length); i++) {
                hex.append(String.format("%02X ", data[i]));
            }
            System.out.println("    Raw: " + hex.toString() + (data.length > 60 ? "..." : ""));
        }
    }
    
    private static void parseOneTag(byte[] data, int start, int len, int tagNum) {
        String epc = "";
        int rssi = 0;
        int antennaId = 0;
        
        int offset = start;
        int end = start + len;
        
        while (offset + 2 <= end) {
            int b0 = data[offset] & 0xFF;
            
            // Verificar si es TV parameter (bit 7 set) o TLV
            if ((b0 & 0x80) != 0) {
                // TV Parameter (1-byte type, variable length)
                int tvType = b0 & 0x7F;
                
                if (tvType == 1) { // AntennaID (TV)
                    if (offset + 3 <= end) {
                        antennaId = ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
                        offset += 3;
                    } else {
                        offset++;
                    }
                } else if (tvType == 6) { // PeakRSSI (TV)
                    if (offset + 2 <= end) {
                        rssi = data[offset + 1]; // signed byte
                        offset += 2;
                    } else {
                        offset++;
                    }
                } else {
                    // Otro TV, saltar según tipo conocido
                    offset += getTVLength(tvType);
                    if (offset > end) offset = end;
                }
            } else {
                // TLV Parameter
                if (offset + 4 > end) break;
                
                int paramType = ((b0 & 0x3F) << 8) | (data[offset + 1] & 0xFF);
                int paramLen = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
                
                if (paramLen < 4 || offset + paramLen > end) {
                    offset += 4;
                    continue;
                }
                
                // EPCData = Type 241, EPC-96 = Type 13
                if (paramType == 241) {
                    // EPCData: NumBits (2 bytes) + EPC bytes
                    if (paramLen > 6) {
                        int numBits = ((data[offset + 4] & 0xFF) << 8) | (data[offset + 5] & 0xFF);
                        int numBytes = (numBits + 7) / 8;
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < numBytes && offset + 6 + i < end; i++) {
                            sb.append(String.format("%02X", data[offset + 6 + i] & 0xFF));
                        }
                        epc = sb.toString();
                    }
                } else if (paramType == 13) {
                    // EPC-96: 96 bits = 12 bytes
                    StringBuilder sb = new StringBuilder();
                    for (int i = 4; i < paramLen && offset + i < end; i++) {
                        sb.append(String.format("%02X", data[offset + i] & 0xFF));
                    }
                    epc = sb.toString();
                }
                
                offset += paramLen;
            }
        }
        
        System.out.printf("    TAG #%d: EPC=%s RSSI=%ddBm Ant=%d%n", 
            tagNum, epc.isEmpty() ? "(no EPC)" : epc, rssi, antennaId);
    }
    
    private static int getTVLength(int tvType) {
        // Longitudes conocidas de TV parameters
        switch (tvType) {
            case 1: return 3;  // AntennaID
            case 2: return 3;  // ChannelIndex  
            case 3: return 9;  // FirstSeenTimestampUTC
            case 4: return 9;  // FirstSeenTimestampUptime
            case 5: return 9;  // LastSeenTimestampUTC
            case 6: return 2;  // PeakRSSI
            case 7: return 3;  // TagSeenCount
            case 8: return 3;  // ROSpecID (short)
            case 9: return 3;  // SpecIndex
            case 10: return 3; // InventoryParameterSpecID
            case 14: return 5; // ROSpecID (int)
            default: return 2; // Mínimo
        }
    }
    
    private static void sendKeepaliveAck(DataOutputStream out, long messageId) throws IOException {
        // KEEPALIVE_ACK: Type 72
        byte[] msg = new byte[10];
        int typeWord = (1 << 10) | 72; // Version 1 (LLRP 1.0.1), Type 72
        msg[0] = (byte)((typeWord >> 8) & 0xFF);
        msg[1] = (byte)(typeWord & 0xFF);
        // Length = 10
        msg[2] = 0; msg[3] = 0; msg[4] = 0; msg[5] = 10;
        // Message ID
        msg[6] = (byte)((messageId >> 24) & 0xFF);
        msg[7] = (byte)((messageId >> 16) & 0xFF);
        msg[8] = (byte)((messageId >> 8) & 0xFF);
        msg[9] = (byte)(messageId & 0xFF);
        out.write(msg);
        out.flush();
    }
    
    private static void sendSetReaderConfig(DataOutputStream out, int msgId) throws IOException {
        // SET_READER_CONFIG: Type 3
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // ResetToFactoryDefault = false (1 byte, but only 1 bit used)
        dos.writeByte(0);
        
        // ROReportSpec (Type 237) - Configuración global de reportes
        ByteArrayOutputStream reportBaos = new ByteArrayOutputStream();
        DataOutputStream reportDos = new DataOutputStream(reportBaos);
        reportDos.writeByte(2); // ROReportTrigger = Upon_N_Tags_Or_End_Of_ROSpec
        reportDos.writeShort(1); // N = 1 (reportar cada tag)
        
        // TagReportContentSelector (Type 238)
        ByteArrayOutputStream tagContentBaos = new ByteArrayOutputStream();
        DataOutputStream tagContentDos = new DataOutputStream(tagContentBaos);
        // EnableROSpecID, EnableSpecIndex, EnableInvParamSpecID, EnableAntennaID, 
        // EnableChannelIndex, EnablePeakRSSI, EnableFirstSeen, EnableLastSeen, 
        // EnableTagSeenCount, EnableAccessSpecID, C1G2EPCMemorySelector, Reserved
        short enableMask = (short)0b1111011110_000000; // EnableAntennaID, EnablePeakRSSI, etc
        tagContentDos.writeShort(enableMask);
        writeParameter(reportDos, 238, tagContentBaos.toByteArray());
        
        writeParameter(dos, 237, reportBaos.toByteArray());
        
        // EventsAndReports (Type 244) - No retener eventos
        ByteArrayOutputStream eventsBaos = new ByteArrayOutputStream();
        DataOutputStream eventsDos = new DataOutputStream(eventsBaos);
        eventsDos.writeByte(0); // HoldEventsAndReportsUponReconnect = false
        writeParameter(dos, 244, eventsBaos.toByteArray());
        
        byte[] body = baos.toByteArray();
        sendMessage(out, 3, msgId, body);
    }
    
    private static void sendDeleteRoSpec(DataOutputStream out, int roSpecId, int msgId) throws IOException {
        // DELETE_ROSPEC: Type 21
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // ROSpecID (4 bytes)
        dos.writeInt(roSpecId);
        
        byte[] body = baos.toByteArray();
        sendMessage(out, 21, msgId, body);
    }
    
    private static void sendAddRoSpec(DataOutputStream out, int msgId) throws IOException {
        // ADD_ROSPEC: Type 20
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // ROSpec Parameter (Type 177)
        ByteArrayOutputStream rospecBaos = new ByteArrayOutputStream();
        DataOutputStream rospecDos = new DataOutputStream(rospecBaos);
        
        // ROSpecID
        rospecDos.writeInt(1);
        // Priority
        rospecDos.writeByte(0);
        // CurrentState (Disabled = 0)
        rospecDos.writeByte(0);
        
        // ROBoundarySpec (Type 178)
        ByteArrayOutputStream boundaryBaos = new ByteArrayOutputStream();
        DataOutputStream boundaryDos = new DataOutputStream(boundaryBaos);
        
        // ROSpecStartTrigger (Type 179)
        // TriggerType = Null (0), Length = 5
        writeParameter(boundaryDos, 179, new byte[]{0});
        
        // ROSpecStopTrigger (Type 182)
        // TriggerType = Null (0), DurationTriggerValue = 0
        ByteArrayOutputStream stopBaos = new ByteArrayOutputStream();
        DataOutputStream stopDos = new DataOutputStream(stopBaos);
        stopDos.writeByte(0); // TriggerType
        stopDos.writeInt(0);  // DurationTriggerValue
        writeParameter(boundaryDos, 182, stopBaos.toByteArray());
        
        writeParameter(rospecDos, 178, boundaryBaos.toByteArray());
        
        // AISpec (Type 183)
        ByteArrayOutputStream aispecBaos = new ByteArrayOutputStream();
        DataOutputStream aispecDos = new DataOutputStream(aispecBaos);
        
        // AntennaIDs count
        aispecDos.writeShort(1);
        // AntennaID = 1
        aispecDos.writeShort(1);
        
        // AISpecStopTrigger (Type 184)
        // TriggerType: 0=Null, 1=Duration, 2=GPI, 3=TagObservation
        ByteArrayOutputStream aiStopBaos = new ByteArrayOutputStream();
        DataOutputStream aiStopDos = new DataOutputStream(aiStopBaos);
        aiStopDos.writeByte(0); // TriggerType = Null (continuo)
        aiStopDos.writeInt(0);  // No duration
        writeParameter(aispecDos, 184, aiStopBaos.toByteArray());
        
        // InventoryParameterSpec (Type 186)
        ByteArrayOutputStream invBaos = new ByteArrayOutputStream();
        DataOutputStream invDos = new DataOutputStream(invBaos);
        invDos.writeShort(1);  // InventoryParameterSpecID
        invDos.writeByte(1);   // ProtocolID = EPCGlobalClass1Gen2
        writeParameter(aispecDos, 186, invBaos.toByteArray());
        
        writeParameter(rospecDos, 183, aispecBaos.toByteArray());
        
        // ROReportSpec (Type 237)
        ByteArrayOutputStream reportBaos = new ByteArrayOutputStream();
        DataOutputStream reportDos = new DataOutputStream(reportBaos);
        reportDos.writeByte(1); // ROReportTrigger = Upon_N_Tags_Or_End_Of_ROSpec
        reportDos.writeShort(1); // N = 1
        
        // TagReportContentSelector (Type 238)
        ByteArrayOutputStream tagContentBaos = new ByteArrayOutputStream();
        DataOutputStream tagContentDos = new DataOutputStream(tagContentBaos);
        // EnableMask: ROSpecID, SpecIndex, InvParamSpecID, AntennaID, ChannelIndex, PeakRSSI, FirstSeen, LastSeen, TagSeenCount, AccessSpecID
        // Bits: ROSpecID=1, SpecIndex=1, InvParamSpecID=1, AntennaID=1, ChannelIndex=0, PeakRSSI=1, FirstSeen=1, LastSeen=1, TagSeenCount=1, AccessSpecID=0
        // = 1111011110 = 0x1EF (pero en formato LLRP es diferente)
        short enableMask = (short)0b1111011110;
        tagContentDos.writeShort(enableMask);
        writeParameter(reportDos, 238, tagContentBaos.toByteArray());
        
        writeParameter(rospecDos, 237, reportBaos.toByteArray());
        
        writeParameter(dos, 177, rospecBaos.toByteArray());
        
        byte[] body = baos.toByteArray();
        sendMessage(out, 20, msgId, body);
    }
    
    private static void sendEnableRoSpec(DataOutputStream out, int msgId) throws IOException {
        // ENABLE_ROSPEC: Type 24
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(1); // ROSpecID
        sendMessage(out, 24, msgId, baos.toByteArray());
    }
    
    private static void sendStartRoSpec(DataOutputStream out, int msgId) throws IOException {
        // START_ROSPEC: Type 22
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(1); // ROSpecID
        sendMessage(out, 22, msgId, baos.toByteArray());
    }
    
    private static void sendStopRoSpec(DataOutputStream out, int msgId) throws IOException {
        // STOP_ROSPEC: Type 23
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(1); // ROSpecID
        sendMessage(out, 23, msgId, baos.toByteArray());
    }
    
    private static void sendGetReport(DataOutputStream out, int msgId) throws IOException {
        // GET_REPORT: Type 60
        sendMessage(out, 60, msgId, new byte[0]);
    }
    
    private static void writeParameter(DataOutputStream out, int type, byte[] data) throws IOException {
        // TLV Parameter header: Reserved (6 bits, must be 0) + Type (10 bits) + Length (16 bits)
        int length = 4 + data.length;
        out.writeShort(type & 0x3FF); // Bits 15-10 = 0, Bits 9-0 = type
        out.writeShort(length);
        out.write(data);
    }
    
    private static void sendMessage(DataOutputStream out, int type, int msgId, byte[] body) throws IOException {
        int length = 10 + body.length;
        // Header: Reserved (3 bits) + Version (3 bits) + Type (10 bits)
        // Version 1 = LLRP 1.0.1 (bits 12-10 = 001)
        int typeWord = (1 << 10) | (type & 0x3FF); // Version 1 + Type
        
        out.writeShort(typeWord);
        out.writeInt(length);
        out.writeInt(msgId);
        out.write(body);
        out.flush();
    }
}
