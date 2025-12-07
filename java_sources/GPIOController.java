import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Controlador de GPIO (General Purpose Input/Output) para el lector RFID Zebra FX7500.
 * Gestiona 4 puertos GPO (salidas para relés, luces, alarmas) y 4 puertos GPI (entradas
 * para sensores, triggers) usando comandos LLRP SET_READER_CONFIG y GET_READER_CONFIG.
 * 
 * Características principales:
 * - 4 puertos GPO para control de relés, luces, alarmas
 * - 4 puertos GPI para lectura de sensores, triggers
 * - LLRP SET_READER_CONFIG para establecer estado GPO
 * - LLRP GET_READER_CONFIG para leer estado GPI
 * - Monitoreo basado en eventos para GPI
 * - Métodos de conveniencia: activateRelay, deactivateRelay, getInputState
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public class GPIOController implements AutoCloseable {
    
    /** Número de puertos GPO disponibles */
    public static final int GPO_PORT_COUNT = 4;
    
    /** Número de puertos GPI disponibles */
    public static final int GPI_PORT_COUNT = 4;
    
    /** Tipo de parámetro GPOWriteData */
    private static final int PARAM_GPO_WRITE_DATA = 225;
    
    /** Tipo de parámetro GPIPortCurrentState */
    private static final int PARAM_GPI_PORT_CURRENT_STATE = 226;
    
    /** Tipo de parámetro EventsAndReports */
    private static final int PARAM_EVENTS_AND_REPORTS = 223;
    
    /** Tipo de parámetro ReaderEventNotificationSpec */
    private static final int PARAM_READER_EVENT_NOTIFICATION_SPEC = 244;
    
    /** Tipo de parámetro EventNotificationState */
    private static final int PARAM_EVENT_NOTIFICATION_STATE = 245;
    
    /** Conexión LLRP con el lector */
    private final LLRPConnection connection;
    
    /** Estado actual de los puertos GPO */
    private final boolean[] gpoState = new boolean[GPO_PORT_COUNT];
    
    /** Estado actual de los puertos GPI */
    private final boolean[] gpiState = new boolean[GPI_PORT_COUNT];
    
    /** Indica si el monitoreo de GPI está activo */
    private final AtomicBoolean gpiMonitoringActive = new AtomicBoolean(false);
    
    /** Executor para polling de GPI */
    private ScheduledExecutorService gpiMonitorExecutor;
    
    /** Intervalo de polling GPI en milisegundos */
    private long gpiPollingInterval = 500;
    
    /** Callback para cambios en estado GPI */
    private BiConsumer<Integer, Boolean> gpiChangeHandler;
    
    /** Callback para cambios en estado GPO */
    private BiConsumer<Integer, Boolean> gpoChangeHandler;
    
    /** Callback para errores */
    private java.util.function.Consumer<Exception> errorHandler;
    
    /** Timeout para operaciones en milisegundos */
    private int operationTimeout = 5000;
    
    /**
     * Constructor con conexión LLRP.
     * 
     * @param connection Conexión LLRP activa con el lector
     */
    public GPIOController(LLRPConnection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("La conexión LLRP no puede ser nula");
        }
        this.connection = connection;
    }
    
    // ==================== Configuración ====================
    
    /**
     * Establece el intervalo de polling para GPI.
     * 
     * @param interval Intervalo en milisegundos (mínimo 100ms)
     */
    public void setGpiPollingInterval(long interval) {
        this.gpiPollingInterval = Math.max(100, interval);
    }
    
    public long getGpiPollingInterval() {
        return gpiPollingInterval;
    }
    
    public void setOperationTimeout(int timeout) {
        this.operationTimeout = Math.max(1000, timeout);
    }
    
    public int getOperationTimeout() {
        return operationTimeout;
    }
    
    /**
     * Establece el handler para cambios de estado GPI.
     * 
     * @param handler Callback que recibe (puerto, estado)
     */
    public void setGpiChangeHandler(BiConsumer<Integer, Boolean> handler) {
        this.gpiChangeHandler = handler;
    }
    
    /**
     * Establece el handler para cambios de estado GPO.
     * 
     * @param handler Callback que recibe (puerto, estado)
     */
    public void setGpoChangeHandler(BiConsumer<Integer, Boolean> handler) {
        this.gpoChangeHandler = handler;
    }
    
    /**
     * Establece el handler para errores.
     * 
     * @param handler Callback para excepciones
     */
    public void setErrorHandler(java.util.function.Consumer<Exception> handler) {
        this.errorHandler = handler;
    }
    
    // ==================== Control GPO ====================
    
    /**
     * Establece el estado de un puerto GPO.
     * 
     * @param port Número de puerto GPO (1-4)
     * @param state true para activar, false para desactivar
     * @return true si la operación fue exitosa
     * @throws IllegalArgumentException si el puerto está fuera de rango
     */
    public boolean setGpoState(int port, boolean state) {
        validateGpoPort(port);
        
        try {
            LLRPMessage request = buildSetGpoMessage(port, state);
            LLRPMessage response = connection.sendAndReceive(request, operationTimeout);
            
            if (response != null && isSuccessResponse(response)) {
                gpoState[port - 1] = state;
                notifyGpoChange(port, state);
                System.out.println("[GPIO] GPO " + port + " establecido a " + (state ? "ALTO" : "BAJO"));
                return true;
            } else {
                System.err.println("[GPIO] Error al establecer GPO " + port);
                return false;
            }
            
        } catch (Exception e) {
            handleError(e);
            return false;
        }
    }
    
    /**
     * Activa un relé/salida (establece GPO en ALTO).
     * 
     * @param port Número de puerto GPO (1-4)
     * @return true si la operación fue exitosa
     */
    public boolean activateRelay(int port) {
        return setGpoState(port, true);
    }
    
    /**
     * Desactiva un relé/salida (establece GPO en BAJO).
     * 
     * @param port Número de puerto GPO (1-4)
     * @return true si la operación fue exitosa
     */
    public boolean deactivateRelay(int port) {
        return setGpoState(port, false);
    }
    
    /**
     * Alterna el estado de un puerto GPO.
     * 
     * @param port Número de puerto GPO (1-4)
     * @return true si la operación fue exitosa
     */
    public boolean toggleGpo(int port) {
        validateGpoPort(port);
        return setGpoState(port, !gpoState[port - 1]);
    }
    
    /**
     * Activa un puerto GPO por un tiempo determinado.
     * 
     * @param port Número de puerto GPO (1-4)
     * @param durationMs Duración en milisegundos
     * @return CompletableFuture que completa cuando termina el pulso
     */
    public CompletableFuture<Boolean> pulseGpo(int port, long durationMs) {
        validateGpoPort(port);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (activateRelay(port)) {
                    Thread.sleep(Math.max(50, durationMs));
                    return deactivateRelay(port);
                }
                return false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deactivateRelay(port);
                return false;
            }
        });
    }
    
    /**
     * Establece todos los puertos GPO al mismo tiempo.
     * 
     * @param states Array de 4 estados (true/false)
     * @return true si todas las operaciones fueron exitosas
     */
    public boolean setAllGpo(boolean[] states) {
        if (states == null || states.length != GPO_PORT_COUNT) {
            throw new IllegalArgumentException("Se requieren exactamente " + GPO_PORT_COUNT + " estados");
        }
        
        boolean success = true;
        for (int i = 0; i < GPO_PORT_COUNT; i++) {
            if (!setGpoState(i + 1, states[i])) {
                success = false;
            }
        }
        return success;
    }
    
    /**
     * Desactiva todos los puertos GPO.
     * 
     * @return true si la operación fue exitosa
     */
    public boolean deactivateAllGpo() {
        boolean success = true;
        for (int i = 1; i <= GPO_PORT_COUNT; i++) {
            if (!deactivateRelay(i)) {
                success = false;
            }
        }
        return success;
    }
    
    /**
     * Obtiene el estado actual de un puerto GPO (del caché local).
     * 
     * @param port Número de puerto GPO (1-4)
     * @return Estado actual del puerto
     */
    public boolean getGpoState(int port) {
        validateGpoPort(port);
        return gpoState[port - 1];
    }
    
    /**
     * Obtiene los estados de todos los puertos GPO.
     * 
     * @return Array con los estados de los 4 puertos GPO
     */
    public boolean[] getAllGpoStates() {
        return gpoState.clone();
    }
    
    // ==================== Control GPI ====================
    
    /**
     * Lee el estado actual de un puerto GPI desde el lector.
     * 
     * @param port Número de puerto GPI (1-4)
     * @return Estado del puerto (true = ALTO, false = BAJO)
     * @throws IllegalArgumentException si el puerto está fuera de rango
     */
    public boolean getInputState(int port) {
        validateGpiPort(port);
        
        try {
            LLRPMessage request = buildGetGpiMessage(port);
            LLRPMessage response = connection.sendAndReceive(request, operationTimeout);
            
            if (response != null) {
                boolean state = parseGpiState(response, port);
                gpiState[port - 1] = state;
                return state;
            }
            
        } catch (Exception e) {
            handleError(e);
        }
        
        return gpiState[port - 1];
    }
    
    /**
     * Lee los estados de todos los puertos GPI.
     * 
     * @return Array con los estados de los 4 puertos GPI
     */
    public boolean[] getAllInputStates() {
        for (int i = 1; i <= GPI_PORT_COUNT; i++) {
            try {
                getInputState(i);
            } catch (Exception e) {
                // Mantener estado anterior
            }
        }
        return gpiState.clone();
    }
    
    /**
     * Obtiene el estado del caché local de un puerto GPI.
     * 
     * @param port Número de puerto GPI (1-4)
     * @return Estado cacheado del puerto
     */
    public boolean getCachedInputState(int port) {
        validateGpiPort(port);
        return gpiState[port - 1];
    }
    
    // ==================== Monitoreo de GPI ====================
    
    /**
     * Habilita los eventos de notificación para GPI en el lector.
     * DEBE llamarse antes de iniciar el monitoreo de GPI para que el lector
     * reporte cambios de estado en los puertos GPI.
     * 
     * Envía SET_READER_CONFIG con EventsAndReports conteniendo
     * ReaderEventNotificationSpec con EventNotificationState para GPI habilitado.
     * 
     * @return true si la configuración fue exitosa
     */
    public boolean enableGPIEvents() {
        try {
            LLRPMessage request = buildEnableGPIEventsMessage();
            LLRPMessage response = connection.sendAndReceive(request, operationTimeout);
            
            if (response != null && isSuccessResponse(response)) {
                System.out.println("[GPIO] Eventos GPI habilitados correctamente");
                return true;
            } else {
                System.err.println("[GPIO] Error al habilitar eventos GPI");
                return false;
            }
            
        } catch (Exception e) {
            handleError(e);
            return false;
        }
    }
    
    /**
     * Construye mensaje SET_READER_CONFIG para habilitar eventos GPI.
     * 
     * @return Mensaje LLRP codificado
     */
    private LLRPMessage buildEnableGPIEventsMessage() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        dos.writeByte(0);
        
        ByteArrayOutputStream eventsBaos = new ByteArrayOutputStream();
        DataOutputStream eventsDos = new DataOutputStream(eventsBaos);
        
        ByteArrayOutputStream notifBaos = new ByteArrayOutputStream();
        DataOutputStream notifDos = new DataOutputStream(notifBaos);
        
        int gpiEventType = 5;
        notifDos.writeShort(gpiEventType);
        notifDos.writeByte(1);
        
        notifDos.flush();
        byte[] notifData = notifBaos.toByteArray();
        
        int eventNotifStateType = (1 << 15) | PARAM_EVENT_NOTIFICATION_STATE;
        eventsDos.writeShort(eventNotifStateType);
        eventsDos.writeShort(4 + notifData.length);
        eventsDos.write(notifData);
        
        eventsDos.flush();
        byte[] eventsInnerData = eventsBaos.toByteArray();
        
        ByteArrayOutputStream readerEventBaos = new ByteArrayOutputStream();
        DataOutputStream readerEventDos = new DataOutputStream(readerEventBaos);
        readerEventDos.write(eventsInnerData);
        readerEventDos.flush();
        byte[] readerEventData = readerEventBaos.toByteArray();
        
        int readerEventNotifType = (1 << 15) | PARAM_READER_EVENT_NOTIFICATION_SPEC;
        dos.writeShort(readerEventNotifType);
        dos.writeShort(4 + readerEventData.length);
        dos.write(readerEventData);
        
        dos.flush();
        return new LLRPMessage(LLRPMessageType.SET_READER_CONFIG, baos.toByteArray());
    }
    
    /**
     * Inicia el monitoreo automático de puertos GPI.
     * Detecta cambios y notifica a través del handler configurado.
     */
    public synchronized void startGpiMonitoring() {
        if (gpiMonitoringActive.get()) {
            return;
        }
        
        gpiMonitoringActive.set(true);
        
        gpiMonitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GPIOController-GpiMonitor");
            t.setDaemon(true);
            return t;
        });
        
        gpiMonitorExecutor.scheduleWithFixedDelay(
            this::pollGpiStates,
            0,
            gpiPollingInterval,
            TimeUnit.MILLISECONDS
        );
        
        System.out.println("[GPIO] Monitoreo de GPI iniciado (intervalo: " + gpiPollingInterval + "ms)");
    }
    
    /**
     * Detiene el monitoreo automático de puertos GPI.
     */
    public synchronized void stopGpiMonitoring() {
        if (!gpiMonitoringActive.get()) {
            return;
        }
        
        gpiMonitoringActive.set(false);
        
        if (gpiMonitorExecutor != null) {
            gpiMonitorExecutor.shutdown();
            try {
                gpiMonitorExecutor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            gpiMonitorExecutor = null;
        }
        
        System.out.println("[GPIO] Monitoreo de GPI detenido");
    }
    
    /**
     * Verifica si el monitoreo de GPI está activo.
     * 
     * @return true si el monitoreo está activo
     */
    public boolean isGpiMonitoringActive() {
        return gpiMonitoringActive.get();
    }
    
    /**
     * Realiza un poll de todos los estados GPI y detecta cambios.
     */
    private void pollGpiStates() {
        if (!connection.isConnected()) {
            return;
        }
        
        for (int port = 1; port <= GPI_PORT_COUNT; port++) {
            try {
                boolean oldState = gpiState[port - 1];
                boolean newState = getInputState(port);
                
                if (oldState != newState) {
                    notifyGpiChange(port, newState);
                }
                
            } catch (Exception e) {
                // Error silencioso durante polling
            }
        }
    }
    
    // ==================== Construcción de mensajes LLRP ====================
    
    /**
     * Construye mensaje SET_READER_CONFIG para establecer GPO.
     * 
     * @param port Puerto GPO (1-4)
     * @param state Estado a establecer
     * @return Mensaje LLRP codificado
     */
    private LLRPMessage buildSetGpoMessage(int port, boolean state) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Flags de SET_READER_CONFIG
        dos.writeByte(0); // ResetToFactoryDefault = false
        
        // GPOWriteData parameter
        int paramTypeField = (1 << 15) | PARAM_GPO_WRITE_DATA;
        dos.writeShort(paramTypeField);
        dos.writeShort(8); // Length
        dos.writeShort(port); // GPO port number
        dos.writeBoolean(state); // GPO data
        dos.writeByte(0); // Padding
        
        dos.flush();
        return new LLRPMessage(LLRPMessageType.SET_READER_CONFIG, baos.toByteArray());
    }
    
    /**
     * Construye mensaje GET_READER_CONFIG para leer GPI.
     * 
     * @param port Puerto GPI (1-4) o 0 para todos
     * @return Mensaje LLRP codificado
     */
    private LLRPMessage buildGetGpiMessage(int port) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Antenna ID (0 = all)
        dos.writeShort(0);
        
        // RequestedData: GPIPortCurrentState = 4
        dos.writeByte(4);
        
        // GPI Port Num (0 = all)
        dos.writeShort(port);
        
        // GPO Port Num (0 = all)
        dos.writeShort(0);
        
        dos.flush();
        return new LLRPMessage(LLRPMessageType.GET_READER_CONFIG, baos.toByteArray());
    }
    
    /**
     * Parsea el estado GPI de la respuesta GET_READER_CONFIG_RESPONSE.
     * 
     * @param response Mensaje de respuesta
     * @param port Puerto GPI a buscar
     * @return Estado del puerto
     */
    private boolean parseGpiState(LLRPMessage response, int port) {
        byte[] payload = response.getPayload();
        if (payload == null || payload.length < 10) {
            return false;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // Buscar parámetro GPIPortCurrentState en el payload
        while (buffer.remaining() >= 4) {
            int typeField = buffer.getShort() & 0xFFFF;
            int paramType = typeField & 0x3FF;
            boolean isTV = (typeField & 0x8000) != 0;
            
            if (paramType == PARAM_GPI_PORT_CURRENT_STATE) {
                if (isTV) {
                    int gpiPort = buffer.getShort() & 0xFFFF;
                    boolean state = buffer.get() != 0;
                    
                    if (gpiPort == port) {
                        return state;
                    }
                } else {
                    int length = buffer.getShort() & 0xFFFF;
                    if (length >= 7) {
                        int gpiPort = buffer.getShort() & 0xFFFF;
                        boolean state = buffer.get() != 0;
                        
                        if (gpiPort == port) {
                            return state;
                        }
                    }
                    // Saltar el resto del parámetro
                    buffer.position(buffer.position() + length - 7);
                }
            } else {
                // Saltar parámetro desconocido
                if (!isTV) {
                    int length = buffer.getShort() & 0xFFFF;
                    buffer.position(buffer.position() + length - 4);
                }
            }
        }
        
        return false;
    }
    
    /**
     * Verifica si la respuesta indica éxito.
     * 
     * @param response Mensaje de respuesta
     * @return true si fue exitoso
     */
    private boolean isSuccessResponse(LLRPMessage response) {
        if (response == null) {
            return false;
        }
        
        byte[] payload = response.getPayload();
        if (payload == null || payload.length < 6) {
            return response.getMessageType() == LLRPMessageType.SET_READER_CONFIG_RESPONSE;
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        buffer.order(ByteOrder.BIG_ENDIAN);
        
        // Buscar LLRPStatus
        while (buffer.remaining() >= 4) {
            int typeField = buffer.getShort() & 0xFFFF;
            int paramType = typeField & 0x3FF;
            
            if (paramType == 287) { // PARAM_LLRP_STATUS
                int length = buffer.getShort() & 0xFFFF;
                if (buffer.remaining() >= 2) {
                    int statusCode = buffer.getShort() & 0xFFFF;
                    return statusCode == 0; // M_Success
                }
            }
            break;
        }
        
        return true;
    }
    
    // ==================== Validación ====================
    
    /**
     * Valida que el puerto GPO esté en rango.
     * 
     * @param port Número de puerto a validar
     * @throws IllegalArgumentException si está fuera de rango
     */
    private void validateGpoPort(int port) {
        if (port < 1 || port > GPO_PORT_COUNT) {
            throw new IllegalArgumentException(
                "Puerto GPO inválido: " + port + ". Debe estar entre 1 y " + GPO_PORT_COUNT);
        }
    }
    
    /**
     * Valida que el puerto GPI esté en rango.
     * 
     * @param port Número de puerto a validar
     * @throws IllegalArgumentException si está fuera de rango
     */
    private void validateGpiPort(int port) {
        if (port < 1 || port > GPI_PORT_COUNT) {
            throw new IllegalArgumentException(
                "Puerto GPI inválido: " + port + ". Debe estar entre 1 y " + GPI_PORT_COUNT);
        }
    }
    
    // ==================== Notificaciones ====================
    
    /**
     * Notifica un cambio en estado GPI.
     * 
     * @param port Puerto que cambió
     * @param state Nuevo estado
     */
    private void notifyGpiChange(int port, boolean state) {
        System.out.println("[GPIO] GPI " + port + " cambió a " + (state ? "ALTO" : "BAJO"));
        
        if (gpiChangeHandler != null) {
            try {
                gpiChangeHandler.accept(port, state);
            } catch (Exception e) {
                System.err.println("[GPIO] Error en handler GPI: " + e.getMessage());
            }
        }
    }
    
    /**
     * Notifica un cambio en estado GPO.
     * 
     * @param port Puerto que cambió
     * @param state Nuevo estado
     */
    private void notifyGpoChange(int port, boolean state) {
        if (gpoChangeHandler != null) {
            try {
                gpoChangeHandler.accept(port, state);
            } catch (Exception e) {
                System.err.println("[GPIO] Error en handler GPO: " + e.getMessage());
            }
        }
    }
    
    /**
     * Maneja un error.
     * 
     * @param e Excepción a manejar
     */
    private void handleError(Exception e) {
        System.err.println("[GPIO] Error: " + e.getMessage());
        if (errorHandler != null) {
            try {
                errorHandler.accept(e);
            } catch (Exception ex) {
                // Ignorar
            }
        }
    }
    
    // ==================== Cierre ====================
    
    @Override
    public void close() {
        stopGpiMonitoring();
        deactivateAllGpo();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("GPIOController[");
        sb.append("GPO=[");
        for (int i = 0; i < GPO_PORT_COUNT; i++) {
            if (i > 0) sb.append(",");
            sb.append(gpoState[i] ? "1" : "0");
        }
        sb.append("], GPI=[");
        for (int i = 0; i < GPI_PORT_COUNT; i++) {
            if (i > 0) sb.append(",");
            sb.append(gpiState[i] ? "1" : "0");
        }
        sb.append("], monitoring=").append(gpiMonitoringActive.get());
        sb.append("]");
        return sb.toString();
    }
}
