import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

/**
 * Cliente HTTP para envío asíncrono de datos de etiquetas RFID a una API externa.
 * Implementa cola de envío, reintentos con backoff exponencial y envío por lotes.
 * 
 * Características principales:
 * - Endpoint URL y API key configurables
 * - Envío asíncrono basado en BlockingQueue
 * - Reintentos con backoff exponencial (máximo 3 intentos)
 * - Soporte para envío por lotes (batch)
 * - Formato JSON para payload de etiquetas
 * - Manejo de timeouts y errores de conexión
 * - Estadísticas: sentCount, failedCount, lastSendTime
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public class APIClient implements AutoCloseable {
    
    /** Timeout de conexión por defecto en milisegundos */
    public static final int DEFAULT_CONNECT_TIMEOUT = 5000;
    
    /** Timeout de lectura por defecto en milisegundos */
    public static final int DEFAULT_READ_TIMEOUT = 10000;
    
    /** Número máximo de reintentos */
    public static final int MAX_RETRIES = 3;
    
    /** Retardo base para backoff exponencial en milisegundos */
    public static final long BASE_BACKOFF_MS = 1000;
    
    /** Tamaño máximo del lote de envío */
    public static final int DEFAULT_BATCH_SIZE = 50;
    
    /** Intervalo de envío por lotes en milisegundos */
    public static final long DEFAULT_BATCH_INTERVAL = 1000;
    
    /** URL del endpoint de la API */
    private String endpointUrl;
    
    /** Clave de API para autenticación */
    private String apiKey;
    
    /** Timeout de conexión en milisegundos */
    private int connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    
    /** Timeout de lectura en milisegundos */
    private int readTimeout = DEFAULT_READ_TIMEOUT;
    
    /** Tamaño del lote de envío */
    private int batchSize = DEFAULT_BATCH_SIZE;
    
    /** Intervalo de envío por lotes en milisegundos */
    private long batchInterval = DEFAULT_BATCH_INTERVAL;
    
    /** Cola de etiquetas pendientes de envío */
    private final BlockingQueue<TagData> sendQueue;
    
    /** Executor para el hilo de envío */
    private ExecutorService senderExecutor;
    
    /** Scheduler para envío periódico por lotes */
    private ScheduledExecutorService batchScheduler;
    
    /** Indica si el cliente está activo */
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /** Contador de envíos exitosos */
    private final AtomicLong sentCount = new AtomicLong(0);
    
    /** Contador de envíos fallidos */
    private final AtomicLong failedCount = new AtomicLong(0);
    
    /** Marca de tiempo del último envío exitoso */
    private volatile long lastSendTime = 0;
    
    /** Marca de tiempo del último error */
    private volatile long lastErrorTime = 0;
    
    /** Último mensaje de error */
    private volatile String lastError = null;
    
    /** Headers adicionales para las peticiones */
    private final Map<String, String> customHeaders = new ConcurrentHashMap<>();
    
    /** Callback para eventos de envío */
    private Consumer<SendResult> sendResultHandler;
    
    /** Callback para errores */
    private Consumer<Exception> errorHandler;
    
    /**
     * Resultado de un envío a la API.
     */
    public static class SendResult {
        /** Indica si el envío fue exitoso */
        public final boolean success;
        /** Número de etiquetas enviadas */
        public final int tagCount;
        /** Código de respuesta HTTP */
        public final int responseCode;
        /** Mensaje de respuesta */
        public final String message;
        /** Tiempo de respuesta en milisegundos */
        public final long responseTimeMs;
        
        public SendResult(boolean success, int tagCount, int responseCode, 
                         String message, long responseTimeMs) {
            this.success = success;
            this.tagCount = tagCount;
            this.responseCode = responseCode;
            this.message = message;
            this.responseTimeMs = responseTimeMs;
        }
    }
    
    /**
     * Constructor por defecto.
     */
    public APIClient() {
        this.sendQueue = new LinkedBlockingQueue<>(10000);
    }
    
    /**
     * Constructor con endpoint y API key.
     * 
     * @param endpointUrl URL del endpoint de la API
     * @param apiKey Clave de API para autenticación
     */
    public APIClient(String endpointUrl, String apiKey) {
        this();
        setEndpointUrl(endpointUrl);
        setApiKey(apiKey);
    }
    
    /**
     * Constructor desde configuración.
     * 
     * @param config Configuración RFID
     */
    public APIClient(RFIDConfig config) {
        this(config.getApiEndpoint(), config.getApiKey());
    }
    
    // ==================== Configuración ====================
    
    /**
     * Establece la URL del endpoint.
     * 
     * @param endpointUrl URL del endpoint
     * @throws IllegalArgumentException si la URL es inválida
     */
    public void setEndpointUrl(String endpointUrl) {
        if (endpointUrl != null && !endpointUrl.trim().isEmpty()) {
            try {
                new URL(endpointUrl);
                this.endpointUrl = endpointUrl.trim();
            } catch (MalformedURLException e) {
                throw new IllegalArgumentException("URL inválida: " + endpointUrl, e);
            }
        } else {
            this.endpointUrl = null;
        }
    }
    
    public String getEndpointUrl() {
        return endpointUrl;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getApiKey() {
        return apiKey;
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
    
    public void setBatchSize(int batchSize) {
        this.batchSize = Math.max(1, Math.min(1000, batchSize));
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchInterval(long batchInterval) {
        this.batchInterval = Math.max(100, batchInterval);
    }
    
    public long getBatchInterval() {
        return batchInterval;
    }
    
    /**
     * Agrega un header personalizado a las peticiones.
     * 
     * @param name Nombre del header
     * @param value Valor del header
     */
    public void addCustomHeader(String name, String value) {
        if (name != null && value != null) {
            customHeaders.put(name, value);
        }
    }
    
    /**
     * Elimina un header personalizado.
     * 
     * @param name Nombre del header a eliminar
     */
    public void removeCustomHeader(String name) {
        customHeaders.remove(name);
    }
    
    public void setSendResultHandler(Consumer<SendResult> handler) {
        this.sendResultHandler = handler;
    }
    
    public void setErrorHandler(Consumer<Exception> handler) {
        this.errorHandler = handler;
    }
    
    // ==================== Estadísticas ====================
    
    public long getSentCount() {
        return sentCount.get();
    }
    
    public long getFailedCount() {
        return failedCount.get();
    }
    
    public long getLastSendTime() {
        return lastSendTime;
    }
    
    public long getLastErrorTime() {
        return lastErrorTime;
    }
    
    public String getLastError() {
        return lastError;
    }
    
    public int getQueueSize() {
        return sendQueue.size();
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Verifica si el cliente está configurado correctamente.
     * 
     * @return true si el endpoint está configurado
     */
    public boolean isConfigured() {
        return endpointUrl != null && !endpointUrl.isEmpty();
    }
    
    /**
     * Reinicia las estadísticas.
     */
    public void resetStatistics() {
        sentCount.set(0);
        failedCount.set(0);
        lastSendTime = 0;
        lastErrorTime = 0;
        lastError = null;
    }
    
    // ==================== Control del cliente ====================
    
    /**
     * Inicia el cliente de envío asíncrono.
     * 
     * @throws IllegalStateException si no está configurado
     */
    public synchronized void start() {
        if (running.get()) {
            return;
        }
        
        if (!isConfigured()) {
            throw new IllegalStateException("El cliente no está configurado. Establezca el endpoint URL.");
        }
        
        running.set(true);
        
        senderExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "APIClient-Sender");
            t.setDaemon(true);
            return t;
        });
        
        batchScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "APIClient-BatchScheduler");
            t.setDaemon(true);
            return t;
        });
        
        senderExecutor.submit(this::senderLoop);
        
        System.out.println("[APIClient] Cliente iniciado. Endpoint: " + endpointUrl);
    }
    
    /**
     * Detiene el cliente de envío.
     */
    public synchronized void stop() {
        if (!running.get()) {
            return;
        }
        
        running.set(false);
        
        if (batchScheduler != null) {
            batchScheduler.shutdown();
            try {
                batchScheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            batchScheduler = null;
        }
        
        if (senderExecutor != null) {
            senderExecutor.shutdown();
            try {
                senderExecutor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            senderExecutor = null;
        }
        
        System.out.println("[APIClient] Cliente detenido.");
    }
    
    @Override
    public void close() {
        stop();
    }
    
    // ==================== Envío de datos ====================
    
    /**
     * Encola una etiqueta para envío asíncrono.
     * 
     * @param tag Datos de la etiqueta a enviar
     * @return true si se encoló correctamente
     */
    public boolean enqueue(TagData tag) {
        if (tag == null || !running.get()) {
            return false;
        }
        return sendQueue.offer(tag);
    }
    
    /**
     * Encola múltiples etiquetas para envío.
     * 
     * @param tags Colección de etiquetas a enviar
     * @return Número de etiquetas encoladas
     */
    public int enqueueAll(Collection<TagData> tags) {
        if (tags == null || !running.get()) {
            return 0;
        }
        int count = 0;
        for (TagData tag : tags) {
            if (sendQueue.offer(tag)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Envía una etiqueta de forma síncrona.
     * 
     * @param tag Datos de la etiqueta
     * @return Resultado del envío
     */
    public SendResult sendSync(TagData tag) {
        List<TagData> batch = Collections.singletonList(tag);
        return sendBatch(batch);
    }
    
    /**
     * Envía un lote de etiquetas de forma síncrona.
     * 
     * @param tags Lista de etiquetas a enviar
     * @return Resultado del envío
     */
    public SendResult sendBatch(List<TagData> tags) {
        if (tags == null || tags.isEmpty()) {
            return new SendResult(false, 0, 0, "No hay etiquetas para enviar", 0);
        }
        
        if (!isConfigured()) {
            return new SendResult(false, 0, 0, "Cliente no configurado", 0);
        }
        
        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                int responseCode = doSend(tags);
                long responseTime = System.currentTimeMillis() - startTime;
                
                if (responseCode >= 200 && responseCode < 300) {
                    sentCount.addAndGet(tags.size());
                    lastSendTime = System.currentTimeMillis();
                    
                    SendResult result = new SendResult(true, tags.size(), responseCode, 
                        "Enviado correctamente", responseTime);
                    notifySendResult(result);
                    return result;
                } else {
                    throw new IOException("Código de respuesta HTTP: " + responseCode);
                }
                
            } catch (Exception e) {
                lastException = e;
                
                if (attempt < MAX_RETRIES) {
                    long backoffTime = calculateBackoff(attempt);
                    System.out.println("[APIClient] Intento " + attempt + " fallido. " +
                        "Reintentando en " + backoffTime + "ms. Error: " + e.getMessage());
                    
                    try {
                        Thread.sleep(backoffTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        failedCount.addAndGet(tags.size());
        lastErrorTime = System.currentTimeMillis();
        lastError = lastException != null ? lastException.getMessage() : "Error desconocido";
        
        if (errorHandler != null && lastException != null) {
            errorHandler.accept(lastException);
        }
        
        long responseTime = System.currentTimeMillis() - startTime;
        SendResult result = new SendResult(false, tags.size(), 0, 
            "Fallo después de " + MAX_RETRIES + " intentos: " + lastError, responseTime);
        notifySendResult(result);
        return result;
    }
    
    /**
     * Prueba la conexión con la API.
     * 
     * @return true si la conexión es exitosa
     */
    public boolean testConnection() {
        if (!isConfigured()) {
            return false;
        }
        
        try {
            URL url = new URL(endpointUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            
            int responseCode = conn.getResponseCode();
            conn.disconnect();
            
            return responseCode >= 200 && responseCode < 500;
        } catch (Exception e) {
            lastError = e.getMessage();
            return false;
        }
    }
    
    // ==================== Métodos privados ====================
    
    /**
     * Bucle principal del hilo de envío.
     */
    private void senderLoop() {
        List<TagData> batch = new ArrayList<>(batchSize);
        
        while (running.get()) {
            try {
                TagData tag = sendQueue.poll(batchInterval, TimeUnit.MILLISECONDS);
                
                if (tag != null) {
                    batch.add(tag);
                    sendQueue.drainTo(batch, batchSize - 1);
                }
                
                if (!batch.isEmpty()) {
                    sendBatch(batch);
                    batch.clear();
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[APIClient] Error en bucle de envío: " + e.getMessage());
                if (errorHandler != null) {
                    errorHandler.accept(e);
                }
            }
        }
        
        if (!batch.isEmpty()) {
            sendBatch(batch);
        }
    }
    
    /**
     * Realiza el envío HTTP de las etiquetas.
     * 
     * @param tags Lista de etiquetas a enviar
     * @return Código de respuesta HTTP
     * @throws IOException si hay error de conexión
     */
    private int doSend(List<TagData> tags) throws IOException {
        URL url = new URL(endpointUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
            }
            
            String jsonPayload = buildJsonPayload(tags);
            
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            return conn.getResponseCode();
            
        } finally {
            conn.disconnect();
        }
    }
    
    /**
     * Construye el payload JSON para las etiquetas.
     * 
     * @param tags Lista de etiquetas
     * @return JSON string
     */
    private String buildJsonPayload(List<TagData> tags) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"tags\":[");
        
        for (int i = 0; i < tags.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(tagToJson(tags.get(i)));
        }
        
        sb.append("],\"timestamp\":\"");
        sb.append(Instant.now().toString());
        sb.append("\",\"count\":");
        sb.append(tags.size());
        sb.append("}");
        
        return sb.toString();
    }
    
    /**
     * Convierte una etiqueta a formato JSON.
     * 
     * @param tag Datos de la etiqueta
     * @return JSON string de la etiqueta
     */
    private String tagToJson(TagData tag) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"epc\":\"").append(escapeJson(tag.getEpc())).append("\"");
        sb.append(",\"rssi\":").append(tag.getRssi());
        sb.append(",\"antenna\":").append(tag.getAntennaPort());
        sb.append(",\"timestamp\":").append(tag.getTimestamp());
        sb.append(",\"readCount\":").append(tag.getReadCount());
        
        if (tag.getTid() != null) {
            sb.append(",\"tid\":\"").append(escapeJson(tag.getTid())).append("\"");
        }
        if (tag.getUserData() != null) {
            sb.append(",\"userData\":\"").append(escapeJson(tag.getUserData())).append("\"");
        }
        if (tag.getPhaseAngle() != null) {
            sb.append(",\"phaseAngle\":").append(tag.getPhaseAngle());
        }
        if (tag.getFrequency() > 0) {
            sb.append(",\"frequency\":").append(tag.getFrequency());
        }
        
        sb.append(",\"firstSeen\":").append(tag.getFirstSeen());
        sb.append(",\"lastSeen\":").append(tag.getLastSeen());
        sb.append(",\"peakRssi\":").append(tag.getPeakRssi());
        sb.append("}");
        
        return sb.toString();
    }
    
    /**
     * Escapa caracteres especiales para JSON.
     * 
     * @param value Valor a escapar
     * @return Valor escapado
     */
    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    /**
     * Calcula el tiempo de espera con backoff exponencial.
     * 
     * @param attempt Número de intento (1-based)
     * @return Tiempo de espera en milisegundos
     */
    private long calculateBackoff(int attempt) {
        long backoff = BASE_BACKOFF_MS * (long) Math.pow(2, attempt - 1);
        long jitter = (long) (backoff * 0.1 * Math.random());
        return Math.min(backoff + jitter, 30000);
    }
    
    /**
     * Notifica el resultado de un envío.
     * 
     * @param result Resultado del envío
     */
    private void notifySendResult(SendResult result) {
        if (sendResultHandler != null) {
            try {
                sendResultHandler.accept(result);
            } catch (Exception e) {
                System.err.println("[APIClient] Error en handler de resultado: " + e.getMessage());
            }
        }
    }
    
    @Override
    public String toString() {
        return String.format("APIClient[endpoint=%s, running=%s, sent=%d, failed=%d, queue=%d]",
            endpointUrl, running.get(), sentCount.get(), failedCount.get(), sendQueue.size());
    }
}
