import java.io.*;
import java.net.*;

/**
 * Cliente Modbus TCP para comunicación con PLC.
 * Implementa lectura de coils (enabler) y escritura de registros holding.
 * Compatible con PLCs Siemens, Allen-Bradley, etc.
 * 
 * Flujo: PLC → App → RFID → API → App → PLC
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public class ModbusClient {
    
    /** IP del PLC */
    private String plcIP;
    
    /** Puerto Modbus (por defecto 502) */
    private int plcPort;
    
    /** Socket de conexión */
    private Socket socket;
    
    /** Stream de salida */
    private DataOutputStream outputStream;
    
    /** Stream de entrada */
    private DataInputStream inputStream;
    
    /** Transaction ID para mensajes Modbus */
    private int transactionId = 0;
    
    /** Timeout de conexión en ms */
    private int connectionTimeout = 5000;
    
    /** Timeout de lectura en ms */
    private int readTimeout = 3000;
    
    // Configuración del enabler (coil que indica si debe leer RFID)
    private int refEnable = 0;
    private int unitIdEnable = 1;
    
    // Configuración de registros de escritura
    private int refTipoEmbalaje = 0;
    private int unitIdTipoEmbalaje = 1;
    
    private int refAncho = 1;
    private int unitIdAncho = 1;
    
    private int refLargo = 2;
    private int unitIdLargo = 1;
    
    private int refActiva = 3;
    private int unitIdActiva = 1;
    
    /**
     * Constructor con IP y puerto.
     */
    public ModbusClient(String ip, int port) {
        this.plcIP = ip;
        this.plcPort = port;
    }
    
    /**
     * Constructor por defecto.
     */
    public ModbusClient() {
        this.plcIP = "localhost";
        this.plcPort = 502;
    }
    
    // ==================== GETTERS Y SETTERS ====================
    
    public String getPlcIP() { return plcIP; }
    public void setPlcIP(String plcIP) { this.plcIP = plcIP; }
    
    public int getPlcPort() { return plcPort; }
    public void setPlcPort(int plcPort) { this.plcPort = plcPort; }
    
    public int getRefEnable() { return refEnable; }
    public void setRefEnable(int refEnable) { this.refEnable = refEnable; }
    
    public int getUnitIdEnable() { return unitIdEnable; }
    public void setUnitIdEnable(int unitIdEnable) { this.unitIdEnable = unitIdEnable; }
    
    public int getRefTipoEmbalaje() { return refTipoEmbalaje; }
    public void setRefTipoEmbalaje(int ref) { this.refTipoEmbalaje = ref; }
    
    public int getUnitIdTipoEmbalaje() { return unitIdTipoEmbalaje; }
    public void setUnitIdTipoEmbalaje(int unitId) { this.unitIdTipoEmbalaje = unitId; }
    
    public int getRefAncho() { return refAncho; }
    public void setRefAncho(int ref) { this.refAncho = ref; }
    
    public int getUnitIdAncho() { return unitIdAncho; }
    public void setUnitIdAncho(int unitId) { this.unitIdAncho = unitId; }
    
    public int getRefLargo() { return refLargo; }
    public void setRefLargo(int ref) { this.refLargo = ref; }
    
    public int getUnitIdLargo() { return unitIdLargo; }
    public void setUnitIdLargo(int unitId) { this.unitIdLargo = unitId; }
    
    public int getRefActiva() { return refActiva; }
    public void setRefActiva(int ref) { this.refActiva = ref; }
    
    public int getUnitIdActiva() { return unitIdActiva; }
    public void setUnitIdActiva(int unitId) { this.unitIdActiva = unitId; }
    
    public int getConnectionTimeout() { return connectionTimeout; }
    public void setConnectionTimeout(int timeout) { this.connectionTimeout = timeout; }
    
    public int getReadTimeout() { return readTimeout; }
    public void setReadTimeout(int timeout) { this.readTimeout = timeout; }
    
    // ==================== CONEXIÓN ====================
    
    /**
     * Conecta al PLC via Modbus TCP.
     */
    public boolean connect() {
        try {
            if (isConnected()) {
                return true;
            }
            
            socket = new Socket();
            socket.connect(new InetSocketAddress(plcIP, plcPort), connectionTimeout);
            socket.setSoTimeout(readTimeout);
            
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
            
            System.out.println("[ModbusClient] Conectado a PLC: " + plcIP + ":" + plcPort);
            return true;
            
        } catch (Exception e) {
            System.err.println("[ModbusClient] Error conectando a PLC: " + e.getMessage());
            disconnect();
            return false;
        }
    }
    
    /**
     * Desconecta del PLC.
     */
    public void disconnect() {
        try {
            if (inputStream != null) inputStream.close();
            if (outputStream != null) outputStream.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
            // Ignorar errores al cerrar
        }
        socket = null;
        inputStream = null;
        outputStream = null;
    }
    
    /**
     * Verifica si está conectado.
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }
    
    // ==================== OPERACIONES MODBUS ====================
    
    /**
     * Lee el coil enabler del PLC.
     * Función Modbus 01 (Read Coils).
     * 
     * @return true si el PLC indica que debe leer RFID, false en caso contrario
     */
    public boolean checkEnabler(int refCoil, int unitId) {
        try {
            if (!isConnected()) {
                System.err.println("[ModbusClient] No conectado, no se puede verificar enabler");
                return false;
            }
            
            // Construir mensaje Modbus: Read Coils (FC 01)
            byte[] request = buildReadCoilsRequest(unitId, refCoil, 1);
            
            outputStream.write(request);
            outputStream.flush();
            
            // Leer respuesta
            byte[] response = new byte[256];
            int bytesRead = inputStream.read(response);
            
            if (bytesRead < 9) {
                System.err.println("[ModbusClient] Respuesta incompleta del PLC");
                return false;
            }
            
            // El byte 9 contiene los coils (bit 0 = primer coil)
            boolean enablerValue = (response[9] & 0x01) == 1;
            
            return enablerValue;
            
        } catch (Exception e) {
            System.err.println("[ModbusClient] Error leyendo enabler: " + e.getMessage());
            return false;
        }
        // Nota: No desconectar aqui - mantener conexion abierta para escritura
    }
    
    /**
     * Escribe datos del producto al PLC.
     * Función Modbus 06 (Write Single Register).
     * 
     * @param tipoEmbalaje Tipo de embalaje
     * @param ancho Ancho del producto
     * @param largo Largo del producto
     * @return true si la escritura fue exitosa
     */
    public boolean writeProductData(int tipoEmbalaje, int ancho, int largo) {
        try {
            if (!connect()) {
                return false;
            }
            
            // Escribir tipo de embalaje
            if (!writeSingleRegister(unitIdTipoEmbalaje, refTipoEmbalaje, tipoEmbalaje)) {
                return false;
            }
            
            // Escribir ancho
            if (!writeSingleRegister(unitIdAncho, refAncho, ancho)) {
                return false;
            }
            
            // Escribir largo
            if (!writeSingleRegister(unitIdLargo, refLargo, largo)) {
                return false;
            }
            
            // Escribir señal de activación (1 = datos listos)
            if (!writeSingleRegister(unitIdActiva, refActiva, 1)) {
                return false;
            }
            
            System.out.println("[ModbusClient] Datos escritos al PLC: tipo=" + tipoEmbalaje + 
                             ", ancho=" + ancho + ", largo=" + largo);
            return true;
            
        } catch (Exception e) {
            System.err.println("[ModbusClient] Error escribiendo al PLC: " + e.getMessage());
            return false;
        }
        // Nota: No desconectar aqui - mantener conexion para siguiente escritura
    }
    
    /**
     * Escribe un registro individual.
     * Función Modbus 06 (Write Single Register).
     */
    private boolean writeSingleRegister(int unitId, int register, int value) {
        try {
            byte[] request = buildWriteRegisterRequest(unitId, register, value);
            
            outputStream.write(request);
            outputStream.flush();
            
            // Leer respuesta
            byte[] response = new byte[256];
            int bytesRead = inputStream.read(response);
            
            if (bytesRead < 8) {
                return false;
            }
            
            // Verificar que no hay error (función code sin bit 7)
            if ((response[7] & 0x80) != 0) {
                System.err.println("[ModbusClient] Error Modbus al escribir registro");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("[ModbusClient] Error en writeSingleRegister: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Construye mensaje Modbus para leer coils (FC 01).
     */
    private byte[] buildReadCoilsRequest(int unitId, int startAddress, int quantity) {
        transactionId++;
        
        byte[] request = new byte[12];
        
        // Transaction ID (2 bytes)
        request[0] = (byte) ((transactionId >> 8) & 0xFF);
        request[1] = (byte) (transactionId & 0xFF);
        
        // Protocol ID (2 bytes) - siempre 0 para Modbus TCP
        request[2] = 0x00;
        request[3] = 0x00;
        
        // Length (2 bytes) - bytes restantes
        request[4] = 0x00;
        request[5] = 0x06;
        
        // Unit ID (1 byte)
        request[6] = (byte) unitId;
        
        // Function Code (1 byte) - 01 = Read Coils
        request[7] = 0x01;
        
        // Start Address (2 bytes)
        request[8] = (byte) ((startAddress >> 8) & 0xFF);
        request[9] = (byte) (startAddress & 0xFF);
        
        // Quantity (2 bytes)
        request[10] = (byte) ((quantity >> 8) & 0xFF);
        request[11] = (byte) (quantity & 0xFF);
        
        return request;
    }
    
    /**
     * Construye un mensaje Modbus para escribir un registro holding (FC 06).
     * 
     * @param register Dirección del registro
     * @param unitId Unit ID del esclavo
     * @param value Valor a escribir
     * @return Mensaje Modbus TCP
     */
    private byte[] buildWriteSingleRegisterRequest(int register, int unitId, int value) {
        transactionId++;
        
        byte[] request = new byte[12];
        
        // Transaction ID (2 bytes)
        request[0] = (byte) ((transactionId >> 8) & 0xFF);
        request[1] = (byte) (transactionId & 0xFF);
        
        // Protocol ID (2 bytes)
        request[2] = 0x00;
        request[3] = 0x00;
        
        // Length (2 bytes)
        request[4] = 0x00;
        request[5] = 0x06;
        
        // Unit ID (1 byte)
        request[6] = (byte) unitId;
        
        // Function Code (1 byte) - 06 = Write Single Register
        request[7] = 0x06;
        
        // Register Address (2 bytes)
        request[8] = (byte) ((register >> 8) & 0xFF);
        request[9] = (byte) (register & 0xFF);
        
        // Value (2 bytes)
        request[10] = (byte) ((value >> 8) & 0xFF);
        request[11] = (byte) (value & 0xFF);
        
        return request;
    }
    
    /**
     * Construye mensaje Modbus para escribir registro (FC 06).
     */
    private byte[] buildWriteRegisterRequest(int unitId, int register, int value) {
        transactionId++;
        
        byte[] request = new byte[12];
        
        // Transaction ID (2 bytes)
        request[0] = (byte) ((transactionId >> 8) & 0xFF);
        request[1] = (byte) (transactionId & 0xFF);
        
        // Protocol ID (2 bytes)
        request[2] = 0x00;
        request[3] = 0x00;
        
        // Length (2 bytes)
        request[4] = 0x00;
        request[5] = 0x06;
        
        // Unit ID (1 byte)
        request[6] = (byte) unitId;
        
        // Function Code (1 byte) - 06 = Write Single Register
        request[7] = 0x06;
        
        // Register Address (2 bytes)
        request[8] = (byte) ((register >> 8) & 0xFF);
        request[9] = (byte) (register & 0xFF);
        
        // Value (2 bytes)
        request[10] = (byte) ((value >> 8) & 0xFF);
        request[11] = (byte) (value & 0xFF);
        
        return request;
    }
    
    /**
     * Escribe un valor en un registro holding del PLC.
     * 
     * @param register Dirección del registro (0-65535)
     * @param unitId Unit ID del esclavo Modbus
     * @param value Valor a escribir (0-65535)
     * @return true si la escritura fue exitosa
     */
    public boolean writeRegister(int register, int unitId, int value) {
        if (!isConnected()) {
            System.err.println("[ModbusClient] No conectado, no se puede escribir registro");
            return false;
        }
        
        try {
            byte[] request = buildWriteSingleRegisterRequest(register, unitId, value);
            outputStream.write(request);
            outputStream.flush();
            
            // Leer respuesta (12 bytes para write single register)
            byte[] response = new byte[12];
            int bytesRead = inputStream.read(response, 0, 12);
            
            if (bytesRead >= 8) {
                // Verificar function code (byte 7)
                int functionCode = response[7] & 0xFF;
                if (functionCode == 0x06) {
                    System.out.println("[ModbusClient] Registro " + register + " escrito con valor " + value);
                    return true;
                } else if (functionCode == 0x86) {
                    // Error Modbus
                    int errorCode = response[8] & 0xFF;
                    System.err.println("[ModbusClient] Error Modbus al escribir: codigo " + errorCode);
                    return false;
                }
            }
            
            System.err.println("[ModbusClient] Respuesta invalida al escribir registro");
            return false;
            
        } catch (Exception e) {
            System.err.println("[ModbusClient] Error escribiendo registro: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Test de conexión al PLC.
     */
    public boolean testConnection() {
        try {
            if (connect()) {
                System.out.println("[ModbusClient] Test de conexión exitoso");
                disconnect();
                return true;
            }
            return false;
        } catch (Exception e) {
            System.err.println("[ModbusClient] Test fallido: " + e.getMessage());
            return false;
        }
    }
    
    @Override
    public String toString() {
        return "ModbusClient[" + plcIP + ":" + plcPort + ", connected=" + isConnected() + "]";
    }
}
