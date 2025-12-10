import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class SimpleLLRPReader {
    
    private static final int LLRP_PORT = 5084;
    private static volatile boolean running = true;
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java SimpleLLRPReader <IP_LECTOR>");
            System.out.println("Ejemplo: java SimpleLLRPReader 192.168.1.117");
            return;
        }
        
        String readerIP = args[0];
        System.out.println("=== LECTOR RFID LLRP SIMPLIFICADO ===");
        System.out.println("Conectando a: " + readerIP + ":" + LLRP_PORT);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running = false;
            System.out.println("\nCerrando...");
        }));
        
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(readerIP, LLRP_PORT), 5000);
            socket.setSoTimeout(1000);
            
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            
            System.out.println("Conectado!");
            
            Thread readerThread = new Thread(() -> readMessages(in));
            readerThread.setDaemon(true);
            readerThread.start();
            
            Thread.sleep(500);
            
            System.out.println("Configurando lector...");
            sendDeleteAllRoSpecs(out, 1);
            Thread.sleep(200);
            
            sendAddRoSpec(out, 2);
            Thread.sleep(200);
            
            sendEnableRoSpec(out, 3);
            Thread.sleep(200);
            
            sendStartRoSpec(out, 4);
            System.out.println("Lector iniciado - Esperando tags...\n");
            
            while (running) {
                Thread.sleep(100);
            }
            
            sendStopRoSpec(out, 5);
            Thread.sleep(200);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
    
    private static void readMessages(DataInputStream in) {
        byte[] header = new byte[10];
        
        while (running) {
            try {
                in.readFully(header);
                
                int typeAndVer = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
                int msgType = typeAndVer & 0x3FF;
                int length = ((header[2] & 0xFF) << 24) | ((header[3] & 0xFF) << 16) | 
                             ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
                
                int bodyLen = length - 10;
                byte[] body = new byte[bodyLen];
                if (bodyLen > 0) {
                    in.readFully(body);
                }
                
                processMessage(msgType, body);
                
            } catch (SocketTimeoutException e) {
                // Normal timeout
            } catch (Exception e) {
                if (running) {
                    System.err.println("Error lectura: " + e.getMessage());
                }
                break;
            }
        }
    }
    
    private static void processMessage(int msgType, byte[] body) {
        switch (msgType) {
            case 61: // RO_ACCESS_REPORT
                parseRoAccessReport(body);
                break;
            case 63: // READER_EVENT_NOTIFICATION
                break;
            case 62: // KEEPALIVE
                break;
            case 30: // ADD_ROSPEC_RESPONSE
            case 31: // DELETE_ROSPEC_RESPONSE
            case 32: // START_ROSPEC_RESPONSE
            case 33: // STOP_ROSPEC_RESPONSE
            case 34: // ENABLE_ROSPEC_RESPONSE
                checkResponse(msgType, body);
                break;
        }
    }
    
    private static void checkResponse(int msgType, byte[] body) {
        if (body.length >= 8) {
            int statusCode = ((body[4] & 0xFF) << 8) | (body[5] & 0xFF);
            if (statusCode != 0) {
                System.err.println("Error en respuesta tipo " + msgType + ": StatusCode=" + statusCode);
            }
        }
    }
    
    private static void parseRoAccessReport(byte[] data) {
        int offset = 0;
        
        while (offset + 4 < data.length) {
            int paramHeader = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            int paramLen = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
            int paramType = paramHeader & 0x3FF;
            
            if (paramLen < 4 || offset + paramLen > data.length) break;
            
            if (paramType == 240) { // TagReportData
                parseTagReportData(data, offset + 4, paramLen - 4);
            }
            
            offset += paramLen;
        }
    }
    
    private static void parseTagReportData(byte[] data, int start, int length) {
        String epc = null;
        int rssi = 0;
        int antenna = 0;
        
        int offset = start;
        int end = start + length;
        
        while (offset < end) {
            if (offset + 2 > end) break;
            
            int firstByte = data[offset] & 0xFF;
            
            if ((firstByte & 0x80) != 0) {
                // TV Parameter
                int tvType = firstByte & 0x7F;
                
                switch (tvType) {
                    case 9: // AntennaID
                        if (offset + 3 <= end) {
                            antenna = ((data[offset + 1] & 0xFF) << 8) | (data[offset + 2] & 0xFF);
                        }
                        offset += 3;
                        break;
                    case 6: // PeakRSSI
                        if (offset + 2 <= end) {
                            rssi = data[offset + 1];
                        }
                        offset += 2;
                        break;
                    default:
                        offset += getTVLength(tvType);
                        break;
                }
            } else {
                // TLV Parameter
                if (offset + 4 > end) break;
                
                int tlvType = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
                tlvType &= 0x3FF;
                int tlvLen = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
                
                if (tlvLen < 4 || offset + tlvLen > end) break;
                
                if (tlvType == 241) { // EPCData
                    int epcBitCount = ((data[offset + 4] & 0xFF) << 8) | (data[offset + 5] & 0xFF);
                    int epcByteCount = (epcBitCount + 7) / 8;
                    if (offset + 6 + epcByteCount <= end) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < epcByteCount; i++) {
                            sb.append(String.format("%02X", data[offset + 6 + i] & 0xFF));
                        }
                        epc = sb.toString();
                    }
                } else if (tlvType == 349) { // C1G2_EPC
                    int pcBits = ((data[offset + 4] & 0xFF) << 8) | (data[offset + 5] & 0xFF);
                    int epcLen = (pcBits >> 11) * 2;
                    if (epcLen > 0 && offset + 6 + epcLen <= end) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < epcLen; i++) {
                            sb.append(String.format("%02X", data[offset + 6 + i] & 0xFF));
                        }
                        epc = sb.toString();
                    }
                }
                
                offset += tlvLen;
            }
        }
        
        if (epc != null) {
            System.out.printf("[TAG] EPC: %s | RSSI: %d dBm | Antena: %d%n", epc, rssi, antenna);
        }
    }
    
    private static int getTVLength(int tvType) {
        switch (tvType) {
            case 1: return 5;  // ROSpecID
            case 2: return 3;  // SpecIndex
            case 3: return 3;  // InventoryParamSpecID
            case 9: return 3;  // AntennaID
            case 6: return 2;  // PeakRSSI
            case 7: return 9;  // FirstSeenUTC
            case 8: return 9;  // LastSeenUTC
            case 10: return 3; // ChannelIndex
            case 14: return 3; // TagSeenCount
            default: return 2;
        }
    }
    
    // === LLRP Messages ===
    
    private static void sendMessage(DataOutputStream out, int msgType, int msgId, byte[] body) throws IOException {
        int length = 10 + body.length;
        int header = (1 << 10) | (msgType & 0x3FF);
        
        out.writeShort(header);
        out.writeInt(length);
        out.writeInt(msgId);
        out.write(body);
        out.flush();
    }
    
    private static void writeParameter(DataOutputStream out, int type, byte[] data) throws IOException {
        int length = 4 + data.length;
        out.writeShort(type & 0x3FF);
        out.writeShort(length);
        out.write(data);
    }
    
    private static void sendDeleteAllRoSpecs(DataOutputStream out, int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(0); // ROSpecID = 0 (all)
        sendMessage(out, 21, msgId, baos.toByteArray());
    }
    
    private static void sendAddRoSpec(DataOutputStream out, int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // ROSpec (Type 177)
        ByteArrayOutputStream rospecBaos = new ByteArrayOutputStream();
        DataOutputStream rospecDos = new DataOutputStream(rospecBaos);
        rospecDos.writeInt(1);  // ROSpecID
        rospecDos.writeByte(0); // Priority
        rospecDos.writeByte(0); // CurrentState = Disabled
        
        // ROBoundarySpec (Type 178)
        ByteArrayOutputStream boundaryBaos = new ByteArrayOutputStream();
        DataOutputStream boundaryDos = new DataOutputStream(boundaryBaos);
        writeParameter(boundaryDos, 179, new byte[]{0}); // ROSpecStartTrigger = Null
        
        ByteArrayOutputStream stopBaos = new ByteArrayOutputStream();
        DataOutputStream stopDos = new DataOutputStream(stopBaos);
        stopDos.writeByte(0);
        stopDos.writeInt(0);
        writeParameter(boundaryDos, 182, stopBaos.toByteArray()); // ROSpecStopTrigger = Null
        
        writeParameter(rospecDos, 178, boundaryBaos.toByteArray());
        
        // AISpec (Type 183)
        ByteArrayOutputStream aispecBaos = new ByteArrayOutputStream();
        DataOutputStream aispecDos = new DataOutputStream(aispecBaos);
        aispecDos.writeShort(1); // AntennaCount
        aispecDos.writeShort(1); // AntennaID = 1
        
        // AISpecStopTrigger (Type 184) = Null
        ByteArrayOutputStream aiStopBaos = new ByteArrayOutputStream();
        DataOutputStream aiStopDos = new DataOutputStream(aiStopBaos);
        aiStopDos.writeByte(0);
        aiStopDos.writeInt(0);
        writeParameter(aispecDos, 184, aiStopBaos.toByteArray());
        
        // InventoryParameterSpec (Type 186)
        ByteArrayOutputStream invBaos = new ByteArrayOutputStream();
        DataOutputStream invDos = new DataOutputStream(invBaos);
        invDos.writeShort(1); // InventoryParameterSpecID
        invDos.writeByte(1);  // ProtocolID = EPCGlobalClass1Gen2
        
        // C1G2InventoryCommand (Type 330)
        ByteArrayOutputStream c1g2Baos = new ByteArrayOutputStream();
        DataOutputStream c1g2Dos = new DataOutputStream(c1g2Baos);
        c1g2Dos.writeByte(0); // TagInventoryStateAware = false
        
        // C1G2RFControl (Type 335)
        ByteArrayOutputStream rfBaos = new ByteArrayOutputStream();
        DataOutputStream rfDos = new DataOutputStream(rfBaos);
        rfDos.writeShort(1); // ModeIndex
        rfDos.writeShort(0); // Tari
        writeParameter(c1g2Dos, 335, rfBaos.toByteArray());
        
        // C1G2SingulationControl (Type 336) - Session S0
        ByteArrayOutputStream singBaos = new ByteArrayOutputStream();
        DataOutputStream singDos = new DataOutputStream(singBaos);
        singDos.writeByte(0);  // Session S0
        singDos.writeShort(0); // TagPopulation
        singDos.writeInt(0);   // TagTransitTime
        writeParameter(c1g2Dos, 336, singBaos.toByteArray());
        
        writeParameter(invDos, 330, c1g2Baos.toByteArray());
        writeParameter(aispecDos, 186, invBaos.toByteArray());
        writeParameter(rospecDos, 183, aispecBaos.toByteArray());
        
        // ROReportSpec (Type 237)
        ByteArrayOutputStream reportBaos = new ByteArrayOutputStream();
        DataOutputStream reportDos = new DataOutputStream(reportBaos);
        reportDos.writeByte(1);  // ROReportTrigger = Upon_N_Tags
        reportDos.writeShort(1); // N = 1
        
        // TagReportContentSelector (Type 238)
        ByteArrayOutputStream tagBaos = new ByteArrayOutputStream();
        DataOutputStream tagDos = new DataOutputStream(tagBaos);
        tagDos.writeShort(0b0001011111_100000); // Enable AntennaID, RSSI, etc
        
        // C1G2EPCMemorySelector (Type 348)
        ByteArrayOutputStream epcSelBaos = new ByteArrayOutputStream();
        DataOutputStream epcSelDos = new DataOutputStream(epcSelBaos);
        epcSelDos.writeByte(0b11100000); // Enable CRC, PC, EPC
        writeParameter(tagDos, 348, epcSelBaos.toByteArray());
        
        writeParameter(reportDos, 238, tagBaos.toByteArray());
        writeParameter(rospecDos, 237, reportBaos.toByteArray());
        
        writeParameter(dos, 177, rospecBaos.toByteArray());
        sendMessage(out, 20, msgId, baos.toByteArray());
    }
    
    private static void sendEnableRoSpec(DataOutputStream out, int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(1);
        sendMessage(out, 24, msgId, baos.toByteArray());
    }
    
    private static void sendStartRoSpec(DataOutputStream out, int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(1);
        sendMessage(out, 22, msgId, baos.toByteArray());
    }
    
    private static void sendStopRoSpec(DataOutputStream out, int msgId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(1);
        sendMessage(out, 23, msgId, baos.toByteArray());
    }
}
