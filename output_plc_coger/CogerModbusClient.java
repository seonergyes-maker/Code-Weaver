import java.io.*;
import java.net.*;
import java.nio.*;
import java.util.concurrent.*;

/**
 * Cliente Modbus TCP para PLC COGER
 * Comunicación con PLCs industriales
 */
public class CogerModbusClient {
    
    /** Listener para eventos del PLC */
    public interface PLCListener {
        void onCoilChanged(boolean value);
        void onWriteSuccess();
        void onError(String message);
        void onConnected();
        void onDisconnected();
    }
    
    private String host;
    private int port;
    private int unitId;
    
    private Socket socket;
    private DataInputStream input;
    private DataOutputStream output;
    
    private int transactionId = 0;
    private boolean connected = false;
    private boolean lastCoilState = false;
    
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollingTask;
    private PLCListener listener;
    
    /** Constructor */
    public CogerModbusClient() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    
    /** Configura el host */
    public void setHost(String host) {
        this.host = host;
    }
    
    /** Configura el puerto */
    public void setPort(int port) {
        this.port = port;
    }
    
    /** Configura el Unit ID */
    public void setUnitId(int unitId) {
        this.unitId = unitId;
    }
    
    /** Configura el listener */
    public void setListener(PLCListener listener) {
        this.listener = listener;
    }
    
    /** Verifica si está conectado */
    public boolean isConnected() {
        return connected && socket != null && socket.isConnected();
    }
    
    /** Obtiene el último estado del coil */
    public boolean getLastCoilState() {
        return lastCoilState;
    }
    
    /** Conecta al PLC */
    public boolean connect() {
        try {
            System.out.println("[MODBUS] Conectando a " + host + ":" + port);
            
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            socket.setSoTimeout(3000);
            
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            
            connected = true;
            System.out.println("[MODBUS] Conectado exitosamente");
            
            if (listener != null) {
                listener.onConnected();
            }
            
            return true;
            
        } catch (Exception e) {
            System.err.println("[MODBUS] Error conectando: " + e.getMessage());
            connected = false;
            if (listener != null) {
                listener.onError("Error conectando: " + e.getMessage());
            }
            return false;
        }
    }
    
    /** Desconecta del PLC */
    public void disconnect() {
        stopPolling();
        
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
        } catch (Exception e) {
            // Ignorar
        }
        
        connected = false;
        System.out.println("[MODBUS] Desconectado");
        
        if (listener != null) {
            listener.onDisconnected();
        }
    }
    
    /** Inicia el polling del coil enabler */
    public void startPolling(int coilAddress, int intervalMs) {
        if (pollingTask != null) {
            pollingTask.cancel(false);
        }
        
        System.out.println("[MODBUS] Iniciando polling coil " + coilAddress + " cada " + intervalMs + "ms");
        
        pollingTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!isConnected()) {
                    connect();
                }
                
                if (isConnected()) {
                    boolean coilValue = readCoil(coilAddress);
                    
                    // Detectar cambio de estado
                    if (coilValue != lastCoilState) {
                        System.out.println("[MODBUS] Coil " + coilAddress + " cambió: " + 
                            lastCoilState + " -> " + coilValue);
                        lastCoilState = coilValue;
                        
                        if (listener != null) {
                            listener.onCoilChanged(coilValue);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[MODBUS] Error en polling: " + e.getMessage());
                connected = false;
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);
    }
    
    /** Detiene el polling */
    public void stopPolling() {
        if (pollingTask != null) {
            pollingTask.cancel(false);
            pollingTask = null;
            System.out.println("[MODBUS] Polling detenido");
        }
    }
    
    /** Lee un coil (función 01) */
    public boolean readCoil(int address) throws Exception {
        // MBAP Header + PDU
        ByteBuffer request = ByteBuffer.allocate(12);
        request.order(ByteOrder.BIG_ENDIAN);
        
        int txId = transactionId++;
        
        // MBAP Header
        request.putShort((short) txId);        // Transaction ID
        request.putShort((short) 0);           // Protocol ID (0 = Modbus)
        request.putShort((short) 6);           // Length
        request.put((byte) unitId);            // Unit ID
        
        // PDU
        request.put((byte) 0x01);              // Function code: Read Coils
        request.putShort((short) address);     // Starting address
        request.putShort((short) 1);           // Quantity
        
        output.write(request.array());
        output.flush();
        
        // Leer respuesta
        byte[] response = new byte[10];
        input.readFully(response);
        
        // Verificar respuesta
        if (response[7] == (byte) 0x81) {
            throw new Exception("Modbus exception: " + (response[8] & 0xFF));
        }
        
        // El valor del coil está en el byte 9
        return (response[9] & 0x01) != 0;
    }
    
    /** Escribe un holding register (función 06) */
    public void writeHoldingRegister(int address, int value) throws Exception {
        ByteBuffer request = ByteBuffer.allocate(12);
        request.order(ByteOrder.BIG_ENDIAN);
        
        int txId = transactionId++;
        
        // MBAP Header
        request.putShort((short) txId);        // Transaction ID
        request.putShort((short) 0);           // Protocol ID
        request.putShort((short) 6);           // Length
        request.put((byte) unitId);            // Unit ID
        
        // PDU
        request.put((byte) 0x06);              // Function code: Write Single Register
        request.putShort((short) address);     // Register address
        request.putShort((short) value);       // Value
        
        output.write(request.array());
        output.flush();
        
        // Leer respuesta
        byte[] response = new byte[12];
        input.readFully(response);
        
        // Verificar respuesta
        if (response[7] == (byte) 0x86) {
            throw new Exception("Modbus exception: " + (response[8] & 0xFF));
        }
        
        System.out.println("[MODBUS] Registro " + address + " = " + value + " escrito");
    }
    
    /** Escribe múltiples holding registers (función 16) */
    public void writeMultipleRegisters(int startAddress, int[] values) throws Exception {
        int byteCount = values.length * 2;
        
        ByteBuffer request = ByteBuffer.allocate(13 + byteCount);
        request.order(ByteOrder.BIG_ENDIAN);
        
        int txId = transactionId++;
        
        // MBAP Header
        request.putShort((short) txId);                  // Transaction ID
        request.putShort((short) 0);                     // Protocol ID
        request.putShort((short) (7 + byteCount));       // Length
        request.put((byte) unitId);                      // Unit ID
        
        // PDU
        request.put((byte) 0x10);                        // Function code: Write Multiple Registers
        request.putShort((short) startAddress);          // Starting address
        request.putShort((short) values.length);         // Quantity
        request.put((byte) byteCount);                   // Byte count
        
        for (int value : values) {
            request.putShort((short) value);
        }
        
        output.write(request.array());
        output.flush();
        
        // Leer respuesta
        byte[] response = new byte[12];
        input.readFully(response);
        
        // Verificar respuesta
        if (response[7] == (byte) 0x90) {
            throw new Exception("Modbus exception: " + (response[8] & 0xFF));
        }
        
        System.out.println("[MODBUS] Escritos " + values.length + " registros desde " + startAddress);
    }
    
    /** Escribe los datos del producto en los holding registers */
    public void writeProductData(int hrTipo, int hrAncho, int hrLargo, int hrPila, int hrControl,
                                  int tipoEmbalaje, int ancho, int largo, int pila) {
        try {
            System.out.println("[MODBUS] Escribiendo datos del producto:");
            System.out.println("   HR " + hrTipo + " (tipo) = " + tipoEmbalaje);
            System.out.println("   HR " + hrAncho + " (ancho) = " + ancho);
            System.out.println("   HR " + hrLargo + " (largo) = " + largo);
            System.out.println("   HR " + hrPila + " (pila) = " + pila);
            System.out.println("   HR " + hrControl + " (control) = 1");
            
            writeHoldingRegister(hrTipo, tipoEmbalaje);
            writeHoldingRegister(hrAncho, ancho);
            writeHoldingRegister(hrLargo, largo);
            writeHoldingRegister(hrPila, pila);
            writeHoldingRegister(hrControl, 1);  // Activar control
            
            if (listener != null) {
                listener.onWriteSuccess();
            }
            
        } catch (Exception e) {
            System.err.println("[MODBUS] Error escribiendo datos: " + e.getMessage());
            if (listener != null) {
                listener.onError("Error escribiendo: " + e.getMessage());
            }
        }
    }
    
    /** Resetea el registro de control */
    public void resetControl(int hrControl) {
        try {
            writeHoldingRegister(hrControl, 0);
            System.out.println("[MODBUS] Control reseteado a 0");
        } catch (Exception e) {
            System.err.println("[MODBUS] Error reseteando control: " + e.getMessage());
        }
    }
    
    /** Cierra el cliente */
    public void shutdown() {
        disconnect();
        scheduler.shutdown();
        try {
            scheduler.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}
