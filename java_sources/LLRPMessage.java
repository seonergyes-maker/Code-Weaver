import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase para codificación y decodificación de mensajes LLRP (Low Level Reader Protocol).
 * Implementa el formato binario según la especificación EPCglobal LLRP 1.0.1.
 * 
 * Formato del encabezado LLRP:
 * - 3 bits reservados + 3 bits versión + 10 bits tipo mensaje (2 bytes)
 * - 32 bits longitud total del mensaje (4 bytes)
 * - 32 bits ID del mensaje (4 bytes)
 * Total encabezado: 10 bytes
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public class LLRPMessage {
    
    /** Versión del protocolo LLRP (1) */
    public static final int LLRP_VERSION = 1;
    
    /** Longitud del encabezado LLRP en bytes */
    public static final int HEADER_LENGTH = 10;
    
    /** Vendor ID de Zebra/Motorola/Symbol */
    public static final int ZEBRA_VENDOR_ID = 161;
    
    // ==================== Tipos de parámetros LLRP ====================
    
    /** Parámetro ROSpec */
    public static final int PARAM_ROSPEC = 177;
    
    /** Parámetro ROBoundarySpec */
    public static final int PARAM_RO_BOUNDARY_SPEC = 178;
    
    /** Parámetro ROSpecStartTrigger */
    public static final int PARAM_ROSPEC_START_TRIGGER = 179;
    
    /** Parámetro ROSpecStopTrigger */
    public static final int PARAM_ROSPEC_STOP_TRIGGER = 182;
    
    /** Parámetro AISpec */
    public static final int PARAM_AISPEC = 183;
    
    /** Parámetro AISpecStopTrigger */
    public static final int PARAM_AISPEC_STOP_TRIGGER = 184;
    
    /** Parámetro InventoryParameterSpec */
    public static final int PARAM_INVENTORY_PARAMETER_SPEC = 186;
    
    /** Parámetro AntennaConfiguration */
    public static final int PARAM_ANTENNA_CONFIGURATION = 222;
    
    /** Parámetro RFTransmitter */
    public static final int PARAM_RF_TRANSMITTER = 224;
    
    /** Parámetro C1G2InventoryCommand */
    public static final int PARAM_C1G2_INVENTORY_COMMAND = 330;
    
    /** Parámetro C1G2RFControl */
    public static final int PARAM_C1G2_RF_CONTROL = 335;
    
    /** Parámetro C1G2SingulationControl */
    public static final int PARAM_C1G2_SINGULATION_CONTROL = 336;
    
    /** Parámetro ROReportSpec */
    public static final int PARAM_RO_REPORT_SPEC = 237;
    
    /** Parámetro TagReportContentSelector */
    public static final int PARAM_TAG_REPORT_CONTENT_SELECTOR = 238;
    
    /** Parámetro TagReportData */
    public static final int PARAM_TAG_REPORT_DATA = 240;
    
    /** Parámetro EPCData */
    public static final int PARAM_EPC_DATA = 241;
    
    /** Parámetro EPC-96 */
    public static final int PARAM_EPC_96 = 13;
    
    /** Parámetro AntennaID (TV parameter) */
    public static final int PARAM_ANTENNA_ID = 1;
    
    /** Parámetro PeakRSSI (TV parameter) */
    public static final int PARAM_PEAK_RSSI = 6;
    
    /** Parámetro FirstSeenTimestamp UTC */
    public static final int PARAM_FIRST_SEEN_TIMESTAMP_UTC = 2;
    
    /** Parámetro LastSeenTimestamp UTC */
    public static final int PARAM_LAST_SEEN_TIMESTAMP_UTC = 3;
    
    /** Parámetro TagSeenCount */
    public static final int PARAM_TAG_SEEN_COUNT = 4;
    
    /** Parámetro ChannelIndex */
    public static final int PARAM_CHANNEL_INDEX = 7;
    
    /** Parámetro LLRPStatus */
    public static final int PARAM_LLRP_STATUS = 287;
    
    /** Parámetro StatusCode (TV) */
    public static final int PARAM_STATUS_CODE = 14;
    
    /** Parámetro Custom (para extensiones de vendedor) */
    public static final int PARAM_CUSTOM = 1023;
    
    /** MotoDefaultSpec parameter subtype for Zebra FX7500 */
    public static final int MOTO_DEFAULT_SPEC_SUBTYPE = 102;
    
    // ==================== Campos del mensaje ====================
    
    /** Tipo de mensaje LLRP */
    private LLRPMessageType messageType;
    
    /** ID único del mensaje */
    private int messageId;
    
    /** Payload binario del mensaje */
    private byte[] payload;
    
    /** Contador estático para generar IDs únicos */
    private static int messageIdCounter = 1;
    
    /**
     * Constructor por defecto.
     */
    public LLRPMessage() {
        this.messageId = generateMessageId();
        this.payload = new byte[0];
    }
    
    /**
     * Constructor con tipo de mensaje.
     * 
     * @param messageType Tipo de mensaje LLRP
     */
    public LLRPMessage(LLRPMessageType messageType) {
        this();
        this.messageType = messageType;
    }
    
    /**
     * Constructor con tipo de mensaje y payload.
     * 
     * @param messageType Tipo de mensaje LLRP
     * @param payload Datos del mensaje
     */
    public LLRPMessage(LLRPMessageType messageType, byte[] payload) {
        this(messageType);
        this.payload = payload != null ? payload : new byte[0];
    }
    
    /**
     * Genera un ID de mensaje único (thread-safe).
     * 
     * @return ID de mensaje
     */
    public static synchronized int generateMessageId() {
        return messageIdCounter++;
    }
    
    // ==================== Getters y Setters ====================
    
    public LLRPMessageType getMessageType() {
        return messageType;
    }
    
    public void setMessageType(LLRPMessageType messageType) {
        this.messageType = messageType;
    }
    
    public int getMessageId() {
        return messageId;
    }
    
    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }
    
    public byte[] getPayload() {
        return payload;
    }
    
    public void setPayload(byte[] payload) {
        this.payload = payload != null ? payload : new byte[0];
    }
    
    // ==================== Codificación de mensajes ====================
    
    /**
     * Codifica el mensaje completo a formato binario LLRP.
     * 
     * @return Array de bytes con el mensaje codificado
     */
    public byte[] encode() {
        int totalLength = HEADER_LENGTH + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        int typeField = ((LLRP_VERSION & 0x07) << 10) | (messageType.getTypeId() & 0x03FF);
        buffer.putShort((short) typeField);
        
        buffer.putInt(totalLength);
        
        buffer.putInt(messageId);
        
        if (payload.length > 0) {
            buffer.put(payload);
        }
        
        return buffer.array();
    }
    
    /**
     * Decodifica un mensaje LLRP desde bytes.
     * 
     * @param data Datos binarios del mensaje
     * @return Mensaje LLRP decodificado
     * @throws IllegalArgumentException si los datos son inválidos
     */
    public static LLRPMessage decode(byte[] data) {
        if (data == null || data.length < HEADER_LENGTH) {
            throw new IllegalArgumentException(
                "Datos insuficientes para mensaje LLRP. Mínimo: " + HEADER_LENGTH + " bytes");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        short typeField = buffer.getShort();
        int version = (typeField >> 10) & 0x07;
        int typeId = typeField & 0x03FF;
        
        int length = buffer.getInt();
        
        int msgId = buffer.getInt();
        
        LLRPMessage message = new LLRPMessage();
        message.messageType = LLRPMessageType.fromTypeId(typeId);
        message.messageId = msgId;
        
        int payloadLength = length - HEADER_LENGTH;
        if (payloadLength > 0 && data.length >= length) {
            message.payload = new byte[payloadLength];
            buffer.get(message.payload);
        } else {
            message.payload = new byte[0];
        }
        
        return message;
    }
    
    /**
     * Lee la longitud de un mensaje desde los primeros bytes del encabezado.
     * 
     * @param headerBytes Primeros bytes del encabezado (mínimo 6 bytes)
     * @return Longitud total del mensaje
     */
    public static int readMessageLength(byte[] headerBytes) {
        if (headerBytes == null || headerBytes.length < 6) {
            return -1;
        }
        ByteBuffer buffer = ByteBuffer.wrap(headerBytes, 2, 4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        return buffer.getInt();
    }
    
    // ==================== Creación de mensajes específicos ====================
    
    /**
     * Crea un mensaje GET_READER_CAPABILITIES.
     * 
     * @param requestedData Tipo de datos solicitados (0=All, 1=GeneralDeviceCapabilities, etc.)
     * @return Mensaje LLRP codificado
     */
    public static LLRPMessage createGetReaderCapabilities(int requestedData) {
        ByteBuffer payload = ByteBuffer.allocate(1);
        payload.put((byte) requestedData);
        return new LLRPMessage(LLRPMessageType.GET_READER_CAPABILITIES, payload.array());
    }
    
    /**
     * Crea un mensaje SET_READER_CONFIG para configurar antenas.
     * 
     * @param antennaConfigs Configuraciones de antenas
     * @param resetToFactoryDefaults Si se debe resetear a valores de fábrica
     * @return Mensaje LLRP
     */
    public static LLRPMessage createSetReaderConfig(AntennaConfig[] antennaConfigs, 
                                                     boolean resetToFactoryDefaults) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            dos.writeByte(resetToFactoryDefaults ? 1 : 0);
            
            for (AntennaConfig config : antennaConfigs) {
                if (config.isEnabled()) {
                    writeAntennaConfiguration(dos, config);
                }
            }
            
            dos.flush();
            return new LLRPMessage(LLRPMessageType.SET_READER_CONFIG, baos.toByteArray());
            
        } catch (IOException e) {
            throw new RuntimeException("Error al crear SET_READER_CONFIG", e);
        }
    }
    
    /**
     * Crea un mensaje ADD_ROSPEC para agregar una especificación de operación.
     * 
     * @param roSpecId ID del ROSpec
     * @param antennaPorts Puertos de antena a usar
     * @param inventoryParameterSpecId ID del parámetro de inventario
     * @return Mensaje LLRP
     */
    public static LLRPMessage createAddROSpec(int roSpecId, int[] antennaPorts, 
                                               int inventoryParameterSpecId) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            writeROSpec(dos, roSpecId, antennaPorts, inventoryParameterSpecId);
            
            dos.flush();
            return new LLRPMessage(LLRPMessageType.ADD_ROSPEC, baos.toByteArray());
            
        } catch (IOException e) {
            throw new RuntimeException("Error al crear ADD_ROSPEC", e);
        }
    }
    
    /**
     * Crea un mensaje ENABLE_ROSPEC.
     * 
     * @param roSpecId ID del ROSpec a habilitar
     * @return Mensaje LLRP
     */
    public static LLRPMessage createEnableROSpec(int roSpecId) {
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.order(ByteOrder.BIG_ENDIAN);
        payload.putInt(roSpecId);
        return new LLRPMessage(LLRPMessageType.ENABLE_ROSPEC, payload.array());
    }
    
    /**
     * Crea un mensaje START_ROSPEC.
     * 
     * @param roSpecId ID del ROSpec a iniciar
     * @return Mensaje LLRP
     */
    public static LLRPMessage createStartROSpec(int roSpecId) {
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.order(ByteOrder.BIG_ENDIAN);
        payload.putInt(roSpecId);
        return new LLRPMessage(LLRPMessageType.START_ROSPEC, payload.array());
    }
    
    /**
     * Crea un mensaje STOP_ROSPEC.
     * 
     * @param roSpecId ID del ROSpec a detener
     * @return Mensaje LLRP
     */
    public static LLRPMessage createStopROSpec(int roSpecId) {
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.order(ByteOrder.BIG_ENDIAN);
        payload.putInt(roSpecId);
        return new LLRPMessage(LLRPMessageType.STOP_ROSPEC, payload.array());
    }
    
    /**
     * Crea un mensaje DELETE_ROSPEC.
     * 
     * @param roSpecId ID del ROSpec a eliminar (0 para eliminar todos)
     * @return Mensaje LLRP
     */
    public static LLRPMessage createDeleteROSpec(int roSpecId) {
        ByteBuffer payload = ByteBuffer.allocate(4);
        payload.order(ByteOrder.BIG_ENDIAN);
        payload.putInt(roSpecId);
        return new LLRPMessage(LLRPMessageType.DELETE_ROSPEC, payload.array());
    }
    
    /**
     * Crea un mensaje KEEPALIVE_ACK.
     * 
     * @return Mensaje LLRP
     */
    public static LLRPMessage createKeepaliveAck() {
        return new LLRPMessage(LLRPMessageType.KEEPALIVE_ACK, new byte[0]);
    }
    
    /**
     * Crea un mensaje CLOSE_CONNECTION.
     * 
     * @return Mensaje LLRP
     */
    public static LLRPMessage createCloseConnection() {
        return new LLRPMessage(LLRPMessageType.CLOSE_CONNECTION, new byte[0]);
    }
    
    // ==================== Escritura de parámetros TLV ====================
    
    /**
     * Escribe un parámetro TLV (Type-Length-Value).
     * 
     * @param dos Stream de salida
     * @param type Tipo de parámetro
     * @param value Valor del parámetro
     */
    private static void writeTLVParameter(DataOutputStream dos, int type, byte[] value) 
            throws IOException {
        int length = 4 + value.length;
        dos.writeShort((type & 0x03FF) | 0x0400);
        dos.writeShort(length);
        dos.write(value);
    }
    
    /**
     * Escribe un parámetro TV (Type-Value) de 1 byte.
     * 
     * @param dos Stream de salida
     * @param type Tipo de parámetro (7 bits)
     * @param value Valor (1 byte)
     */
    private static void writeTVParameter1(DataOutputStream dos, int type, int value) 
            throws IOException {
        dos.writeByte(0x80 | (type & 0x7F));
        dos.writeByte(value);
    }
    
    /**
     * Escribe un parámetro TV de 2 bytes.
     */
    private static void writeTVParameter2(DataOutputStream dos, int type, int value) 
            throws IOException {
        dos.writeByte(0x80 | (type & 0x7F));
        dos.writeShort(value);
    }
    
    /**
     * Escribe la configuración de una antena como parámetro LLRP.
     */
    private static void writeAntennaConfiguration(DataOutputStream dos, AntennaConfig config) 
            throws IOException {
        ByteArrayOutputStream paramBaos = new ByteArrayOutputStream();
        DataOutputStream paramDos = new DataOutputStream(paramBaos);
        
        paramDos.writeShort(config.getAntennaPort());
        
        ByteArrayOutputStream rfTxBaos = new ByteArrayOutputStream();
        DataOutputStream rfTxDos = new DataOutputStream(rfTxBaos);
        rfTxDos.writeShort(config.getPowerIndex());
        rfTxDos.writeShort(0);
        rfTxDos.flush();
        
        byte[] rfTxData = rfTxBaos.toByteArray();
        paramDos.writeShort((PARAM_RF_TRANSMITTER & 0x03FF) | 0x0400);
        paramDos.writeShort(4 + rfTxData.length);
        paramDos.write(rfTxData);
        
        paramDos.flush();
        
        byte[] paramData = paramBaos.toByteArray();
        dos.writeShort((PARAM_ANTENNA_CONFIGURATION & 0x03FF) | 0x0400);
        dos.writeShort(4 + paramData.length);
        dos.write(paramData);
    }
    
    /**
     * Escribe un ROSpec completo simplificado para FX7500.
     */
    private static void writeROSpec(DataOutputStream dos, int roSpecId, 
                                     int[] antennaPorts, int inventoryParamSpecId) 
            throws IOException {
        ByteArrayOutputStream roSpecBaos = new ByteArrayOutputStream();
        DataOutputStream roSpecDos = new DataOutputStream(roSpecBaos);
        
        // ROSpecID (4 bytes)
        roSpecDos.writeInt(roSpecId);
        // Priority (1 byte)
        roSpecDos.writeByte(0);
        // CurrentState (1 byte) - 0 = Disabled
        roSpecDos.writeByte(0);
        
        // ROBoundarySpec
        writeROBoundarySpecSimple(roSpecDos);
        
        // AISpec (Antenna Inventory Spec)
        writeAISpecSimple(roSpecDos, antennaPorts, inventoryParamSpecId);
        
        // ROReportSpec
        writeROReportSpecSimple(roSpecDos);
        
        roSpecDos.flush();
        byte[] roSpecData = roSpecBaos.toByteArray();
        
        // TLV header for ROSpec (type 177)
        dos.writeShort((PARAM_ROSPEC & 0x03FF) | 0x0400);
        dos.writeShort(4 + roSpecData.length);
        dos.write(roSpecData);
    }
    
    /**
     * Escribe ROBoundarySpec simplificado.
     * StartTrigger = Immediate (1) para que inicie al habilitar
     * StopTrigger = Null (0) para que corra indefinidamente
     */
    private static void writeROBoundarySpecSimple(DataOutputStream dos) throws IOException {
        ByteArrayOutputStream boundaryBaos = new ByteArrayOutputStream();
        DataOutputStream boundaryDos = new DataOutputStream(boundaryBaos);
        
        // ROSpecStartTrigger (type 179) - Immediate trigger (start when enabled)
        boundaryDos.writeShort((PARAM_ROSPEC_START_TRIGGER & 0x03FF) | 0x0400);
        boundaryDos.writeShort(5); // length = 4 header + 1 byte trigger type
        boundaryDos.writeByte(1);  // ROSpecStartTriggerType = 1 (Immediate) <-- CAMBIADO DE 0 A 1
        
        // ROSpecStopTrigger (type 182) - Null trigger (run forever)
        boundaryDos.writeShort((PARAM_ROSPEC_STOP_TRIGGER & 0x03FF) | 0x0400);
        boundaryDos.writeShort(9); // length = 4 header + 1 byte type + 4 bytes duration
        boundaryDos.writeByte(0);  // ROSpecStopTriggerType = 0 (Null)
        boundaryDos.writeInt(0);   // DurationTriggerValue = 0
        
        boundaryDos.flush();
        byte[] boundaryData = boundaryBaos.toByteArray();
        
        // ROBoundarySpec (type 178)
        dos.writeShort((PARAM_RO_BOUNDARY_SPEC & 0x03FF) | 0x0400);
        dos.writeShort(4 + boundaryData.length);
        dos.write(boundaryData);
    }
    
    /**
     * Escribe AISpec simplificado.
     */
    private static void writeAISpecSimple(DataOutputStream dos, int[] antennaPorts, 
                                           int inventoryParamSpecId) throws IOException {
        ByteArrayOutputStream aiSpecBaos = new ByteArrayOutputStream();
        DataOutputStream aiSpecDos = new DataOutputStream(aiSpecBaos);
        
        // AntennaIDs (count + list of antenna IDs)
        aiSpecDos.writeShort(antennaPorts.length);
        for (int port : antennaPorts) {
            aiSpecDos.writeShort(port);
        }
        
        // AISpecStopTrigger (type 184) - Null trigger
        aiSpecDos.writeShort((PARAM_AISPEC_STOP_TRIGGER & 0x03FF) | 0x0400);
        aiSpecDos.writeShort(9); // length = 4 header + 1 type + 4 duration
        aiSpecDos.writeByte(0);  // AISpecStopTriggerType = 0 (Null)
        aiSpecDos.writeInt(0);   // DurationTrigger = 0
        
        // InventoryParameterSpec (type 186)
        writeInventoryParameterSpecSimple(aiSpecDos, inventoryParamSpecId);
        
        aiSpecDos.flush();
        byte[] aiSpecData = aiSpecBaos.toByteArray();
        
        // AISpec (type 183)
        dos.writeShort((PARAM_AISPEC & 0x03FF) | 0x0400);
        dos.writeShort(4 + aiSpecData.length);
        dos.write(aiSpecData);
    }
    
    /**
     * Escribe InventoryParameterSpec simplificado.
     */
    private static void writeInventoryParameterSpecSimple(DataOutputStream dos, int specId) 
            throws IOException {
        ByteArrayOutputStream invBaos = new ByteArrayOutputStream();
        DataOutputStream invDos = new DataOutputStream(invBaos);
        
        // InventoryParameterSpecID (2 bytes)
        invDos.writeShort(specId);
        // ProtocolID (1 byte) - 1 = EPCGlobalClass1Gen2
        invDos.writeByte(1);
        
        invDos.flush();
        byte[] invData = invBaos.toByteArray();
        
        // InventoryParameterSpec (type 186)
        dos.writeShort((PARAM_INVENTORY_PARAMETER_SPEC & 0x03FF) | 0x0400);
        dos.writeShort(4 + invData.length);
        dos.write(invData);
    }
    
    /**
     * Escribe ROReportSpec simplificado.
     */
    private static void writeROReportSpecSimple(DataOutputStream dos) throws IOException {
        ByteArrayOutputStream reportBaos = new ByteArrayOutputStream();
        DataOutputStream reportDos = new DataOutputStream(reportBaos);
        
        // ROReportTrigger (1 byte) - 1 = Upon_N_Tags_Or_End_Of_AISpec
        reportDos.writeByte(1);
        // N (2 bytes) - Report every 1 tag
        reportDos.writeShort(1);
        
        // TagReportContentSelector (type 238)
        ByteArrayOutputStream selectorBaos = new ByteArrayOutputStream();
        DataOutputStream selectorDos = new DataOutputStream(selectorBaos);
        
        // EnableROSpecID, EnableSpecIndex, EnableInventoryParameterSpecID, 
        // EnableAntennaID, EnableChannelIndex, EnablePeakRSSI, 
        // EnableFirstSeenTimestamp, EnableLastSeenTimestamp, 
        // EnableTagSeenCount, EnableAccessSpecID
        // All enabled = 0x03FF (10 flags in 2 bytes)
        selectorDos.writeShort(0x03FF);
        
        // C1G2EPCMemorySelector (type 348)
        selectorDos.writeShort((348 & 0x03FF) | 0x0400);
        selectorDos.writeShort(5); // 4 header + 1 byte
        selectorDos.writeByte(0xC0); // EnableCRC=1, EnablePCBits=1
        
        selectorDos.flush();
        byte[] selectorData = selectorBaos.toByteArray();
        
        reportDos.writeShort((PARAM_TAG_REPORT_CONTENT_SELECTOR & 0x03FF) | 0x0400);
        reportDos.writeShort(4 + selectorData.length);
        reportDos.write(selectorData);
        
        reportDos.flush();
        byte[] reportData = reportBaos.toByteArray();
        
        // ROReportSpec (type 237)
        dos.writeShort((PARAM_RO_REPORT_SPEC & 0x03FF) | 0x0400);
        dos.writeShort(4 + reportData.length);
        dos.write(reportData);
    }
    
    /**
     * Escribe ROBoundarySpec (triggers de inicio y fin).
     */
    private static void writeROBoundarySpec(DataOutputStream dos) throws IOException {
        ByteArrayOutputStream boundaryBaos = new ByteArrayOutputStream();
        DataOutputStream boundaryDos = new DataOutputStream(boundaryBaos);
        
        ByteArrayOutputStream startTriggerBaos = new ByteArrayOutputStream();
        DataOutputStream startTriggerDos = new DataOutputStream(startTriggerBaos);
        startTriggerDos.writeByte(0);
        startTriggerDos.flush();
        byte[] startTriggerData = startTriggerBaos.toByteArray();
        
        boundaryDos.writeShort((PARAM_ROSPEC_START_TRIGGER & 0x03FF) | 0x0400);
        boundaryDos.writeShort(4 + startTriggerData.length);
        boundaryDos.write(startTriggerData);
        
        ByteArrayOutputStream stopTriggerBaos = new ByteArrayOutputStream();
        DataOutputStream stopTriggerDos = new DataOutputStream(stopTriggerBaos);
        stopTriggerDos.writeByte(0);
        stopTriggerDos.writeInt(0);
        stopTriggerDos.flush();
        byte[] stopTriggerData = stopTriggerBaos.toByteArray();
        
        boundaryDos.writeShort((PARAM_ROSPEC_STOP_TRIGGER & 0x03FF) | 0x0400);
        boundaryDos.writeShort(4 + stopTriggerData.length);
        boundaryDos.write(stopTriggerData);
        
        boundaryDos.flush();
        byte[] boundaryData = boundaryBaos.toByteArray();
        
        dos.writeShort((PARAM_RO_BOUNDARY_SPEC & 0x03FF) | 0x0400);
        dos.writeShort(4 + boundaryData.length);
        dos.write(boundaryData);
    }
    
    /**
     * Escribe AISpec (Antenna Inventory Spec).
     */
    private static void writeAISpec(DataOutputStream dos, int[] antennaPorts, 
                                     int inventoryParamSpecId) throws IOException {
        ByteArrayOutputStream aiSpecBaos = new ByteArrayOutputStream();
        DataOutputStream aiSpecDos = new DataOutputStream(aiSpecBaos);
        
        aiSpecDos.writeShort(antennaPorts.length);
        for (int port : antennaPorts) {
            aiSpecDos.writeShort(port);
        }
        
        ByteArrayOutputStream stopTriggerBaos = new ByteArrayOutputStream();
        DataOutputStream stopTriggerDos = new DataOutputStream(stopTriggerBaos);
        stopTriggerDos.writeByte(0);
        stopTriggerDos.writeInt(0);
        stopTriggerDos.flush();
        byte[] stopTriggerData = stopTriggerBaos.toByteArray();
        
        aiSpecDos.writeShort((PARAM_AISPEC_STOP_TRIGGER & 0x03FF) | 0x0400);
        aiSpecDos.writeShort(4 + stopTriggerData.length);
        aiSpecDos.write(stopTriggerData);
        
        writeInventoryParameterSpec(aiSpecDos, inventoryParamSpecId, antennaPorts);
        
        aiSpecDos.flush();
        byte[] aiSpecData = aiSpecBaos.toByteArray();
        
        dos.writeShort((PARAM_AISPEC & 0x03FF) | 0x0400);
        dos.writeShort(4 + aiSpecData.length);
        dos.write(aiSpecData);
    }
    
    /**
     * Escribe InventoryParameterSpec.
     */
    private static void writeInventoryParameterSpec(DataOutputStream dos, 
                                                     int specId, int[] antennaPorts) 
            throws IOException {
        ByteArrayOutputStream invBaos = new ByteArrayOutputStream();
        DataOutputStream invDos = new DataOutputStream(invBaos);
        
        invDos.writeShort(specId);
        invDos.writeByte(1);
        
        for (int port : antennaPorts) {
            writeC1G2InventoryCommand(invDos, port);
        }
        
        invDos.flush();
        byte[] invData = invBaos.toByteArray();
        
        dos.writeShort((PARAM_INVENTORY_PARAMETER_SPEC & 0x03FF) | 0x0400);
        dos.writeShort(4 + invData.length);
        dos.write(invData);
    }
    
    /**
     * Escribe C1G2InventoryCommand.
     */
    private static void writeC1G2InventoryCommand(DataOutputStream dos, int antennaId) 
            throws IOException {
        ByteArrayOutputStream cmdBaos = new ByteArrayOutputStream();
        DataOutputStream cmdDos = new DataOutputStream(cmdBaos);
        
        cmdDos.writeByte(0);
        
        ByteArrayOutputStream rfCtrlBaos = new ByteArrayOutputStream();
        DataOutputStream rfCtrlDos = new DataOutputStream(rfCtrlBaos);
        rfCtrlDos.writeShort(1);
        rfCtrlDos.writeShort(0);
        rfCtrlDos.flush();
        byte[] rfCtrlData = rfCtrlBaos.toByteArray();
        
        cmdDos.writeShort((PARAM_C1G2_RF_CONTROL & 0x03FF) | 0x0400);
        cmdDos.writeShort(4 + rfCtrlData.length);
        cmdDos.write(rfCtrlData);
        
        ByteArrayOutputStream singBaos = new ByteArrayOutputStream();
        DataOutputStream singDos = new DataOutputStream(singBaos);
        singDos.writeByte(0);
        singDos.writeShort(0);
        singDos.writeShort(0);
        singDos.flush();
        byte[] singData = singBaos.toByteArray();
        
        cmdDos.writeShort((PARAM_C1G2_SINGULATION_CONTROL & 0x03FF) | 0x0400);
        cmdDos.writeShort(4 + singData.length);
        cmdDos.write(singData);
        
        cmdDos.flush();
        byte[] cmdData = cmdBaos.toByteArray();
        
        dos.writeShort((PARAM_C1G2_INVENTORY_COMMAND & 0x03FF) | 0x0400);
        dos.writeShort(4 + cmdData.length);
        dos.write(cmdData);
    }
    
    /**
     * Escribe ROReportSpec.
     */
    private static void writeROReportSpec(DataOutputStream dos) throws IOException {
        ByteArrayOutputStream reportBaos = new ByteArrayOutputStream();
        DataOutputStream reportDos = new DataOutputStream(reportBaos);
        
        reportDos.writeByte(2);
        reportDos.writeShort(1);
        
        writeTagReportContentSelector(reportDos);
        
        reportDos.flush();
        byte[] reportData = reportBaos.toByteArray();
        
        dos.writeShort((PARAM_RO_REPORT_SPEC & 0x03FF) | 0x0400);
        dos.writeShort(4 + reportData.length);
        dos.write(reportData);
    }
    
    /**
     * Escribe TagReportContentSelector.
     */
    private static void writeTagReportContentSelector(DataOutputStream dos) throws IOException {
        ByteArrayOutputStream selectorBaos = new ByteArrayOutputStream();
        DataOutputStream selectorDos = new DataOutputStream(selectorBaos);
        
        int enableFlags = 0;
        enableFlags |= (1 << 15);
        enableFlags |= (1 << 14);
        enableFlags |= (1 << 13);
        enableFlags |= (1 << 12);
        enableFlags |= (1 << 11);
        enableFlags |= (1 << 10);
        
        selectorDos.writeShort(enableFlags);
        
        selectorDos.flush();
        byte[] selectorData = selectorBaos.toByteArray();
        
        dos.writeShort((PARAM_TAG_REPORT_CONTENT_SELECTOR & 0x03FF) | 0x0400);
        dos.writeShort(4 + selectorData.length);
        dos.write(selectorData);
    }
    
    /**
     * Escribe MotoDefaultSpec - parámetro personalizado de Zebra/Motorola.
     * REQUERIDO por el FX7500 para funcionamiento correcto del ROSpec.
     * 
     * Formato Custom Parameter:
     * - Type: 1023 (Custom)
     * - VendorID: 161 (Zebra/Motorola)
     * - Subtype: 102 (MotoDefaultSpec)
     * - UseDefaultSpecForAutoMode: 1 byte (1 = true)
     */
    private static void writeMotoDefaultSpec(DataOutputStream dos) throws IOException {
        ByteArrayOutputStream customBaos = new ByteArrayOutputStream();
        DataOutputStream customDos = new DataOutputStream(customBaos);
        
        customDos.writeInt(ZEBRA_VENDOR_ID);
        
        customDos.writeInt(MOTO_DEFAULT_SPEC_SUBTYPE);
        
        customDos.writeByte(1);
        
        customDos.flush();
        byte[] customData = customBaos.toByteArray();
        
        dos.writeShort((PARAM_CUSTOM & 0x03FF) | 0x0400);
        dos.writeShort(4 + customData.length);
        dos.write(customData);
    }
    
    // ==================== Parsing de RO_ACCESS_REPORT ====================
    
    /**
     * Extrae datos de etiquetas de un mensaje RO_ACCESS_REPORT.
     * 
     * @param message Mensaje RO_ACCESS_REPORT recibido
     * @return Lista de etiquetas leídas
     */
    public static List<TagData> parseROAccessReport(LLRPMessage message) {
        List<TagData> tags = new ArrayList<>();
        
        if (message.messageType != LLRPMessageType.RO_ACCESS_REPORT) {
            return tags;
        }
        
        byte[] data = message.payload;
        if (data == null || data.length < 4) {
            return tags;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        while (buffer.remaining() >= 4) {
            int typeAndFlags = buffer.getShort() & 0xFFFF;
            
            boolean isTLV = (typeAndFlags & 0x0400) != 0;
            int paramType = typeAndFlags & 0x03FF;
            
            if (isTLV) {
                int paramLength = buffer.getShort() & 0xFFFF;
                int dataLength = paramLength - 4;
                
                if (dataLength > 0 && buffer.remaining() >= dataLength) {
                    if (paramType == PARAM_TAG_REPORT_DATA) {
                        TagData tag = parseTagReportData(buffer, dataLength);
                        if (tag != null) {
                            tags.add(tag);
                        }
                    } else {
                        buffer.position(buffer.position() + dataLength);
                    }
                }
            } else {
                int tvType = (typeAndFlags >> 8) & 0x7F;
                if (tvType > 0) {
                    int tvDataLength = getTVParameterLength(tvType);
                    if (buffer.remaining() >= tvDataLength) {
                        buffer.position(buffer.position() + tvDataLength);
                    }
                }
            }
        }
        
        return tags;
    }
    
    /**
     * Parsea un TagReportData individual.
     */
    private static TagData parseTagReportData(ByteBuffer buffer, int length) {
        int endPosition = buffer.position() + length;
        TagData tag = new TagData();
        
        try {
            while (buffer.position() < endPosition && buffer.remaining() >= 2) {
                int pos = buffer.position();
                int firstByte = buffer.get() & 0xFF;
                
                if ((firstByte & 0x80) != 0) {
                    int tvType = firstByte & 0x7F;
                    
                    switch (tvType) {
                        case PARAM_ANTENNA_ID:
                            if (buffer.remaining() >= 2) {
                                tag.setAntennaPort(buffer.getShort() & 0xFFFF);
                            }
                            break;
                        case PARAM_PEAK_RSSI:
                            if (buffer.remaining() >= 1) {
                                int rssiRaw = buffer.get();
                                tag.setRssi(rssiRaw - 128);
                            }
                            break;
                        case PARAM_FIRST_SEEN_TIMESTAMP_UTC:
                            if (buffer.remaining() >= 8) {
                                long microseconds = buffer.getLong();
                                tag.setTimestamp(microseconds / 1000);
                            }
                            break;
                        case PARAM_TAG_SEEN_COUNT:
                            if (buffer.remaining() >= 2) {
                                tag.setReadCount(buffer.getShort() & 0xFFFF);
                            }
                            break;
                        case PARAM_CHANNEL_INDEX:
                            if (buffer.remaining() >= 2) {
                                tag.setFrequency(buffer.getShort() & 0xFFFF);
                            }
                            break;
                        default:
                            int skipLen = getTVParameterLength(tvType);
                            if (buffer.remaining() >= skipLen) {
                                buffer.position(buffer.position() + skipLen);
                            }
                    }
                } else {
                    buffer.position(pos);
                    int typeAndFlags = buffer.getShort() & 0xFFFF;
                    int paramType = typeAndFlags & 0x03FF;
                    int paramLength = buffer.getShort() & 0xFFFF;
                    int dataLength = paramLength - 4;
                    
                    if (paramType == PARAM_EPC_96 || paramType == PARAM_EPC_DATA) {
                        if (dataLength > 0 && buffer.remaining() >= dataLength) {
                            byte[] epcBytes = new byte[dataLength];
                            buffer.get(epcBytes);
                            tag.setEpc(bytesToHex(epcBytes));
                        }
                    } else if (dataLength > 0 && buffer.remaining() >= dataLength) {
                        buffer.position(buffer.position() + dataLength);
                    }
                }
            }
            
            if (tag.getEpc() != null && !tag.getEpc().isEmpty()) {
                return tag;
            }
            
        } catch (Exception e) {
            buffer.position(endPosition);
        }
        
        return null;
    }
    
    /**
     * Obtiene la longitud de datos de un parámetro TV.
     */
    private static int getTVParameterLength(int tvType) {
        switch (tvType) {
            case PARAM_ANTENNA_ID:
                return 2;
            case PARAM_PEAK_RSSI:
                return 1;
            case PARAM_FIRST_SEEN_TIMESTAMP_UTC:
            case PARAM_LAST_SEEN_TIMESTAMP_UTC:
                return 8;
            case PARAM_TAG_SEEN_COUNT:
                return 2;
            case PARAM_CHANNEL_INDEX:
                return 2;
            default:
                return 0;
        }
    }
    
    // ==================== Parsing de respuestas de estado ====================
    
    /**
     * Extrae el código de estado de un mensaje de respuesta.
     * 
     * @param message Mensaje de respuesta LLRP
     * @return Código de estado (0 = éxito) o -1 si no se puede determinar
     */
    public static int getStatusCode(LLRPMessage message) {
        byte[] data = message.payload;
        if (data == null || data.length < 8) {
            return -1;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        while (buffer.remaining() >= 4) {
            int typeAndFlags = buffer.getShort() & 0xFFFF;
            boolean isTLV = (typeAndFlags & 0x0400) != 0;
            int paramType = typeAndFlags & 0x03FF;
            
            if (isTLV) {
                int paramLength = buffer.getShort() & 0xFFFF;
                int dataLength = paramLength - 4;
                
                if (paramType == PARAM_LLRP_STATUS && dataLength >= 2) {
                    return buffer.getShort() & 0xFFFF;
                } else if (dataLength > 0 && buffer.remaining() >= dataLength) {
                    buffer.position(buffer.position() + dataLength);
                }
            } else {
                break;
            }
        }
        
        return -1;
    }
    
    /**
     * Verifica si el mensaje de respuesta indica éxito.
     * 
     * @param message Mensaje de respuesta
     * @return true si el estado es éxito (código 0)
     */
    public static boolean isSuccess(LLRPMessage message) {
        return getStatusCode(message) == 0;
    }
    
    // ==================== Utilidades ====================
    
    /**
     * Convierte bytes a String hexadecimal.
     * 
     * @param bytes Array de bytes
     * @return String hexadecimal
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
    
    /**
     * Convierte String hexadecimal a bytes.
     * 
     * @param hex String hexadecimal
     * @return Array de bytes
     */
    public static byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("[^0-9A-Fa-f]", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
    
    @Override
    public String toString() {
        return String.format("LLRPMessage[tipo=%s, id=%d, payloadLen=%d]",
            messageType, messageId, payload != null ? payload.length : 0);
    }
}
