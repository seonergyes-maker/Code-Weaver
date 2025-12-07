import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Gestiona la conexión TCP con el lector RFID Zebra FX7500 usando el protocolo LLRP.
 * Proporciona comunicación bidireccional thread-safe con soporte para reconexión automática.
 * 
 * Características principales:
 * - Conexión TCP al puerto LLRP (5084)
 * - Envío y recepción de mensajes LLRP binarios
 * - Manejo de timeouts y errores de conexión
 * - Reconexión automática opcional
 * - Thread-safe para envío de mensajes
 * - Callback para mensajes asíncronos (RO_ACCESS_REPORT, KEEPALIVE)
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public class LLRPConnection implements AutoCloseable {
    
    /** Puerto LLRP estándar */
    public static final int DEFAULT_PORT = 5084;
    
    /** Timeout de conexión por defecto (ms) */
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    
    /** Timeout de lectura por defecto (ms) */
    public static final int DEFAULT_READ_TIMEOUT = 10000;
    
    /** Intervalo de reconexión por defecto (ms) */
    public static final int DEFAULT_RECONNECT_INTERVAL = 3000;
    
    /** Número máximo de reintentos de reconexión */
    public static final int MAX_RECONNECT_ATTEMPTS = 10;
    
    /** Dirección IP del lector */
    private final String readerIP;
    
    /** Puerto LLRP del lector */
    private final int readerPort;
    
    /** Socket de conexión */
    private Socket socket;
    
    /** Stream de entrada */
    private DataInputStream inputStream;
    
    /** Stream de salida */
    private DataOutputStream outputStream;
    
    /** Indica si la conexión está activa */
    private final AtomicBoolean connected = new AtomicBoolean(false);
    
    /** Indica si el hilo de recepción debe continuar */
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /** Indica si está en proceso de reconexión */
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    
    /** Habilitar reconexión automática */
    private boolean autoReconnect = true;
    
    /** Timeout de conexión en milisegundos */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    
    /** Timeout de lectura en milisegundos */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
    
    /** Intervalo entre intentos de reconexión en milisegundos */
    private int reconnectInterval = DEFAULT_RECONNECT_INTERVAL;
    
    /** Contador de intentos de reconexión */
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    
    /** Executor para el hilo de recepción */
    private ExecutorService receiverExecutor;
    
    /** Executor para reconexión */
    private ScheduledExecutorService reconnectExecutor;
    
    /** Lock para sincronizar operaciones de socket */
    private final Object socketLock = new Object();
    
    /** Lock para sincronizar envío de mensajes */
    private final Object sendLock = new Object();
    
    /** Cola de respuestas pendientes */
    private final ConcurrentHashMap<Integer, CompletableFuture<LLRPMessage>> pendingResponses = 
        new ConcurrentHashMap<>();
    
    /** Callback para mensajes asíncronos (reportes de tags, keepalive, etc.) */
    private Consumer<LLRPMessage> asyncMessageHandler;
    
    /** Callback para cambios de estado de conexión */
    private Consumer<ConnectionState> connectionStateHandler;
    
    /** Callback para errores */
    private Consumer<Exception> errorHandler;
    
    /**
     * Estados de conexión posibles.
     */
    public enum ConnectionState {
        /** Desconectado */
        DISCONNECTED,
        /** Conectando */
        CONNECTING,
        /** Conectado */
        CONNECTED,
        /** Reconectando */
        RECONNECTING,
        /** Error */
        ERROR
    }
    
    /**
     * Constructor con IP del lector.
     * 
     * @param readerIP Dirección IP del lector RFID
     */
    public LLRPConnection(String readerIP) {
        this(readerIP, DEFAULT_PORT);
    }
    
    /**
     * Constructor con IP y puerto.
     * 
     * @param readerIP Dirección IP del lector RFID
     * @param readerPort Puerto LLRP (normalmente 5084)
     */
    public LLRPConnection(String readerIP, int readerPort) {
        if (readerIP == null || readerIP.trim().isEmpty()) {
            throw new IllegalArgumentException("La dirección IP del lector no puede ser nula o vacía");
        }
        if (readerPort < 1 || readerPort > 65535) {
            throw new IllegalArgumentException("Puerto inválido: " + readerPort);
        }
        this.readerIP = readerIP.trim();
        this.readerPort = readerPort;
    }
    
    /**
     * Constructor desde configuración.
     * 
     * @param config Configuración RFID
     */
    public LLRPConnection(RFIDConfig config) {
        this(config.getReaderIP(), config.getReaderPort());
        this.autoReconnect = config.isAutoReconnect();
        this.connectTimeout = config.getConnectionTimeout();
        this.reconnectInterval = config.getReconnectInterval();
    }
    
    // ==================== Configuración ====================
    
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }
    
    public boolean isAutoReconnect() {
        return autoReconnect;
    }
    
    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = Math.max(1000, connectTimeout);
    }
    
    public int getConnectTimeout() {
        return connectTimeout;
    }
    
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = Math.max(1000, readTimeout);
    }
    
    public int getReadTimeout() {
        return readTimeout;
    }
    
    public void setReconnectInterval(int reconnectInterval) {
        this.reconnectInterval = Math.max(1000, reconnectInterval);
    }
    
    public int getReconnectInterval() {
        return reconnectInterval;
    }
    
    public void setAsyncMessageHandler(Consumer<LLRPMessage> handler) {
        this.asyncMessageHandler = handler;
    }
    
    public void setConnectionStateHandler(Consumer<ConnectionState> handler) {
        this.connectionStateHandler = handler;
    }
    
    public void setErrorHandler(Consumer<Exception> handler) {
        this.errorHandler = handler;
    }
    
    // ==================== Estado de conexión ====================
    
    /**
     * Verifica si hay conexión activa con el lector.
     * 
     * @return true si está conectado
     */
    public boolean isConnected() {
        synchronized (socketLock) {
            return connected.get() && socket != null && socket.isConnected() && !socket.isClosed();
        }
    }
    
    /**
     * Obtiene la dirección IP del lector.
     * 
     * @return Dirección IP
     */
    public String getReaderIP() {
        return readerIP;
    }
    
    /**
     * Obtiene el puerto del lector.
     * 
     * @return Puerto LLRP
     */
    public int getReaderPort() {
        return readerPort;
    }
    
    // ==================== Conexión ====================
    
    /**
     * Establece conexión con el lector RFID.
     * 
     * @return true si la conexión fue exitosa
     * @throws IOException si ocurre un error de conexión
     */
    public boolean connect() throws IOException {
        synchronized (socketLock) {
            if (isConnected()) {
                return true;
            }
            
            notifyConnectionState(ConnectionState.CONNECTING);
            
            try {
                socket = new Socket();
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.setSoTimeout(readTimeout);
                
                socket.connect(new InetSocketAddress(readerIP, readerPort), connectTimeout);
                
                inputStream = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                outputStream = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                
                connected.set(true);
                running.set(true);
                reconnectAttempts.set(0);
                
                startReceiver();
                
                notifyConnectionState(ConnectionState.CONNECTED);
                
                return true;
                
            } catch (IOException e) {
                closeInternal();
                notifyConnectionState(ConnectionState.ERROR);
                throw e;
            }
        }
    }
    
    /**
     * Desconecta del lector RFID.
     */
    public void disconnect() {
        autoReconnect = false;
        close();
    }
    
    /**
     * Cierra la conexión con el lector.
     */
    @Override
    public void close() {
        running.set(false);
        
        if (reconnectExecutor != null && !reconnectExecutor.isShutdown()) {
            reconnectExecutor.shutdownNow();
        }
        
        if (receiverExecutor != null && !receiverExecutor.isShutdown()) {
            receiverExecutor.shutdownNow();
        }
        
        synchronized (socketLock) {
            closeInternal();
        }
        
        pendingResponses.values().forEach(f -> 
            f.completeExceptionally(new IOException("Conexión cerrada")));
        pendingResponses.clear();
        
        notifyConnectionState(ConnectionState.DISCONNECTED);
    }
    
    /**
     * Cierra recursos internos sin notificaciones.
     */
    private void closeInternal() {
        connected.set(false);
        
        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException ignored) {}
        
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException ignored) {}
        
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {}
        
        inputStream = null;
        outputStream = null;
        socket = null;
    }
    
    // ==================== Envío y recepción ====================
    
    /**
     * Envía un mensaje LLRP al lector (thread-safe).
     * 
     * @param message Mensaje LLRP a enviar
     * @throws IOException si ocurre un error de envío
     */
    public void send(LLRPMessage message) throws IOException {
        if (!isConnected()) {
            throw new IOException("No hay conexión con el lector");
        }
        
        byte[] data = message.encode();
        
        synchronized (sendLock) {
            try {
                outputStream.write(data);
                outputStream.flush();
            } catch (IOException e) {
                handleConnectionError(e);
                throw e;
            }
        }
    }
    
    /**
     * Envía un mensaje y espera la respuesta.
     * 
     * @param message Mensaje LLRP a enviar
     * @param timeoutMs Tiempo máximo de espera en milisegundos
     * @return Mensaje de respuesta
     * @throws IOException si ocurre un error de comunicación
     * @throws TimeoutException si se agota el tiempo de espera
     */
    public LLRPMessage sendAndReceive(LLRPMessage message, long timeoutMs) 
            throws IOException, TimeoutException {
        
        CompletableFuture<LLRPMessage> future = new CompletableFuture<>();
        pendingResponses.put(message.getMessageId(), future);
        
        try {
            send(message);
            
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Operación interrumpida", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Error al recibir respuesta", cause);
        } finally {
            pendingResponses.remove(message.getMessageId());
        }
    }
    
    /**
     * Envía un mensaje y espera la respuesta con timeout por defecto.
     * 
     * @param message Mensaje LLRP a enviar
     * @return Mensaje de respuesta
     * @throws IOException si ocurre un error de comunicación
     * @throws TimeoutException si se agota el tiempo de espera
     */
    public LLRPMessage sendAndReceive(LLRPMessage message) throws IOException, TimeoutException {
        return sendAndReceive(message, readTimeout);
    }
    
    /**
     * Inicia el hilo receptor de mensajes.
     */
    private void startReceiver() {
        receiverExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LLRP-Receiver-" + readerIP);
            t.setDaemon(true);
            return t;
        });
        
        receiverExecutor.submit(this::receiveLoop);
    }
    
    /**
     * Loop principal de recepción de mensajes.
     */
    private void receiveLoop() {
        byte[] headerBuffer = new byte[LLRPMessage.HEADER_LENGTH];
        
        while (running.get() && isConnected()) {
            try {
                int bytesRead = 0;
                while (bytesRead < LLRPMessage.HEADER_LENGTH) {
                    int read = inputStream.read(headerBuffer, bytesRead, 
                                                LLRPMessage.HEADER_LENGTH - bytesRead);
                    if (read == -1) {
                        throw new IOException("Fin de stream - conexión cerrada por el lector");
                    }
                    bytesRead += read;
                }
                
                int messageLength = LLRPMessage.readMessageLength(headerBuffer);
                
                if (messageLength < LLRPMessage.HEADER_LENGTH || messageLength > 1024 * 1024) {
                    continue;
                }
                
                byte[] messageData = new byte[messageLength];
                System.arraycopy(headerBuffer, 0, messageData, 0, LLRPMessage.HEADER_LENGTH);
                
                int payloadLength = messageLength - LLRPMessage.HEADER_LENGTH;
                bytesRead = 0;
                while (bytesRead < payloadLength) {
                    int read = inputStream.read(messageData, LLRPMessage.HEADER_LENGTH + bytesRead,
                                                payloadLength - bytesRead);
                    if (read == -1) {
                        throw new IOException("Fin de stream - conexión cerrada por el lector");
                    }
                    bytesRead += read;
                }
                
                LLRPMessage message = LLRPMessage.decode(messageData);
                handleReceivedMessage(message);
                
            } catch (SocketTimeoutException e) {
                // Timeout normal, continuar
            } catch (SocketException e) {
                if (running.get()) {
                    handleConnectionError(e);
                }
                break;
            } catch (IOException e) {
                if (running.get()) {
                    handleConnectionError(e);
                }
                break;
            } catch (Exception e) {
                notifyError(e);
            }
        }
    }
    
    /**
     * Procesa un mensaje recibido.
     */
    private void handleReceivedMessage(LLRPMessage message) {
        if (message == null) return;
        
        if (message.getMessageType() == LLRPMessageType.KEEPALIVE) {
            try {
                send(LLRPMessage.createKeepaliveAck());
            } catch (IOException e) {
                notifyError(e);
            }
            return;
        }
        
        if (message.getMessageType() == LLRPMessageType.RO_ACCESS_REPORT ||
            message.getMessageType() == LLRPMessageType.READER_EVENT_NOTIFICATION) {
            
            if (asyncMessageHandler != null) {
                asyncMessageHandler.accept(message);
            }
            return;
        }
        
        CompletableFuture<LLRPMessage> pending = pendingResponses.remove(message.getMessageId());
        if (pending != null) {
            pending.complete(message);
        } else if (message.getMessageType().isResponse()) {
            for (Integer msgId : pendingResponses.keySet()) {
                CompletableFuture<LLRPMessage> future = pendingResponses.remove(msgId);
                if (future != null) {
                    future.complete(message);
                    break;
                }
            }
        }
    }
    
    // ==================== Reconexión automática ====================
    
    /**
     * Maneja errores de conexión e inicia reconexión si está habilitada.
     */
    private void handleConnectionError(Exception e) {
        synchronized (socketLock) {
            closeInternal();
        }
        
        notifyError(e);
        
        pendingResponses.values().forEach(f -> f.completeExceptionally(e));
        pendingResponses.clear();
        
        if (autoReconnect && running.get() && !reconnecting.get()) {
            scheduleReconnect();
        } else {
            notifyConnectionState(ConnectionState.DISCONNECTED);
        }
    }
    
    /**
     * Programa un intento de reconexión.
     */
    private void scheduleReconnect() {
        if (reconnecting.getAndSet(true)) {
            return;
        }
        
        notifyConnectionState(ConnectionState.RECONNECTING);
        
        if (reconnectExecutor == null || reconnectExecutor.isShutdown()) {
            reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "LLRP-Reconnect-" + readerIP);
                t.setDaemon(true);
                return t;
            });
        }
        
        reconnectExecutor.schedule(this::attemptReconnect, reconnectInterval, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Intenta reconectar con el lector.
     */
    private void attemptReconnect() {
        if (!running.get() || !autoReconnect) {
            reconnecting.set(false);
            return;
        }
        
        int attempt = reconnectAttempts.incrementAndGet();
        
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            reconnecting.set(false);
            notifyConnectionState(ConnectionState.ERROR);
            notifyError(new IOException("Máximo de intentos de reconexión alcanzado"));
            return;
        }
        
        try {
            connect();
            reconnecting.set(false);
        } catch (IOException e) {
            if (running.get() && autoReconnect) {
                int delay = Math.min(reconnectInterval * attempt, 30000);
                reconnectExecutor.schedule(this::attemptReconnect, delay, TimeUnit.MILLISECONDS);
            } else {
                reconnecting.set(false);
            }
        }
    }
    
    // ==================== Notificaciones ====================
    
    /**
     * Notifica cambio de estado de conexión.
     */
    private void notifyConnectionState(ConnectionState state) {
        if (connectionStateHandler != null) {
            try {
                connectionStateHandler.accept(state);
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * Notifica un error.
     */
    private void notifyError(Exception e) {
        if (errorHandler != null) {
            try {
                errorHandler.accept(e);
            } catch (Exception ignored) {}
        }
    }
    
    // ==================== Métodos de conveniencia ====================
    
    /**
     * Envía un mensaje GET_READER_CAPABILITIES y espera la respuesta.
     * 
     * @return Mensaje de respuesta
     * @throws IOException si ocurre un error
     * @throws TimeoutException si se agota el tiempo
     */
    public LLRPMessage getReaderCapabilities() throws IOException, TimeoutException {
        return sendAndReceive(LLRPMessage.createGetReaderCapabilities(0));
    }
    
    /**
     * Envía configuración de antenas al lector.
     * 
     * @param configs Configuraciones de antena
     * @return true si fue exitoso
     * @throws IOException si ocurre un error
     * @throws TimeoutException si se agota el tiempo
     */
    public boolean setReaderConfig(AntennaConfig[] configs) throws IOException, TimeoutException {
        LLRPMessage response = sendAndReceive(
            LLRPMessage.createSetReaderConfig(configs, false));
        return LLRPMessage.isSuccess(response);
    }
    
    /**
     * Agrega un ROSpec al lector.
     * 
     * @param roSpecId ID del ROSpec
     * @param antennaPorts Puertos de antena a usar
     * @return true si fue exitoso
     * @throws IOException si ocurre un error
     * @throws TimeoutException si se agota el tiempo
     */
    public boolean addROSpec(int roSpecId, int[] antennaPorts) throws IOException, TimeoutException {
        LLRPMessage response = sendAndReceive(
            LLRPMessage.createAddROSpec(roSpecId, antennaPorts, 1));
        return LLRPMessage.isSuccess(response);
    }
    
    /**
     * Habilita un ROSpec.
     * 
     * @param roSpecId ID del ROSpec
     * @return true si fue exitoso
     * @throws IOException si ocurre un error
     * @throws TimeoutException si se agota el tiempo
     */
    public boolean enableROSpec(int roSpecId) throws IOException, TimeoutException {
        LLRPMessage response = sendAndReceive(LLRPMessage.createEnableROSpec(roSpecId));
        return LLRPMessage.isSuccess(response);
    }
    
    /**
     * Inicia un ROSpec.
     * 
     * @param roSpecId ID del ROSpec
     * @return true si fue exitoso
     * @throws IOException si ocurre un error
     * @throws TimeoutException si se agota el tiempo
     */
    public boolean startROSpec(int roSpecId) throws IOException, TimeoutException {
        LLRPMessage response = sendAndReceive(LLRPMessage.createStartROSpec(roSpecId));
        return LLRPMessage.isSuccess(response);
    }
    
    /**
     * Detiene un ROSpec.
     * 
     * @param roSpecId ID del ROSpec
     * @return true si fue exitoso
     * @throws IOException si ocurre un error
     * @throws TimeoutException si se agota el tiempo
     */
    public boolean stopROSpec(int roSpecId) throws IOException, TimeoutException {
        LLRPMessage response = sendAndReceive(LLRPMessage.createStopROSpec(roSpecId));
        return LLRPMessage.isSuccess(response);
    }
    
    /**
     * Elimina un ROSpec.
     * 
     * @param roSpecId ID del ROSpec (0 para eliminar todos)
     * @return true si fue exitoso
     * @throws IOException si ocurre un error
     * @throws TimeoutException si se agota el tiempo
     */
    public boolean deleteROSpec(int roSpecId) throws IOException, TimeoutException {
        LLRPMessage response = sendAndReceive(LLRPMessage.createDeleteROSpec(roSpecId));
        return LLRPMessage.isSuccess(response);
    }
    
    /**
     * Inicia lectura continua de etiquetas.
     * 
     * @param config Configuración RFID
     * @return true si se inició exitosamente
     * @throws IOException si ocurre un error
     * @throws TimeoutException si se agota el tiempo
     */
    public boolean startReading(RFIDConfig config) throws IOException, TimeoutException {
        int roSpecId = config.getRoSpecId();
        int[] antennaPorts = config.getEnabledAntennaPorts();
        
        deleteROSpec(0);
        
        if (!setReaderConfig(config.getAntennaConfigs())) {
            return false;
        }
        
        if (!addROSpec(roSpecId, antennaPorts)) {
            return false;
        }
        
        if (!enableROSpec(roSpecId)) {
            return false;
        }
        
        return startROSpec(roSpecId);
    }
    
    /**
     * Detiene la lectura de etiquetas.
     * 
     * @param roSpecId ID del ROSpec
     * @return true si se detuvo exitosamente
     * @throws IOException si ocurre un error
     * @throws TimeoutException si se agota el tiempo
     */
    public boolean stopReading(int roSpecId) throws IOException, TimeoutException {
        stopROSpec(roSpecId);
        return deleteROSpec(roSpecId);
    }
    
    @Override
    public String toString() {
        return String.format("LLRPConnection[%s:%d, conectado=%b]", 
            readerIP, readerPort, isConnected());
    }
}
