import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

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
    
    /** URL del endpoint de la API (base URL sin parámetros) */
    private String endpointUrl;
    
    /** Clave de API para autenticación (token) */
    private String apiKey;
    
    /** Indica si es modo apilado */
    private boolean apilado = false;
    
    /** IP del lector RFID para enviar a la API */
    private String readerIP;
    
    /** Identificador de trabajo para la API */
    private String apiTrabajo;
    
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
    private FailedTagStorage failedTagStorage;
    private LogManager logger = LogManager.getInstance();
    
    /** Modo de visualización del EPC: true = hex, false = decimal */
    private volatile boolean displayHexMode = true;
    
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
     * Respuesta de la API de inicio de lectura.
     * Contiene la autorización para iniciar la lectura y parámetros de configuración.
     */
    public static class InicioResponse {
        /** Indica si se puede iniciar la lectura */
        public final boolean resultado;
        /** Mensaje de la API (error o información) */
        public final String mensaje;
        /** Fecha actual del servidor para validación de tiempo (epoch seconds) */
        public final long fecha;
        /** Fecha de inicio de sesión para reset de duplicados (epoch seconds) */
        public final long fechaInicio;
        /** Milisegundos de pausa entre lecturas */
        public final int milisegundosParada;
        
        public InicioResponse(boolean resultado, String mensaje, long fecha, long fechaInicio, int milisegundosParada) {
            this.resultado = resultado;
            this.mensaje = mensaje;
            this.fecha = fecha;
            this.fechaInicio = fechaInicio;
            this.milisegundosParada = milisegundosParada;
        }
        
        public static InicioResponse error(String mensaje) {
            return new InicioResponse(false, mensaje, 0, 0, 100);
        }
        
        @Override
        public String toString() {
            return String.format("InicioResponse[resultado=%s, mensaje=%s, fecha=%d, fechaInicio=%d, parada=%dms]",
                resultado, mensaje, fecha, fechaInicio, milisegundosParada);
        }
    }

    /**
     * Respuesta de la API de consulta de modelo de producto.
     * Contiene los datos del producto asociado al tag RFID.
     */
    public static class ModeloResponse {
        /** Indica si el tag es válido */
        public final boolean valido;
        /** Código del producto */
        public final String codigo;
        /** Descripción del producto */
        public final String descripcion;
        /** Tipo de embalaje (valor numérico) */
        public final int tipoEmbalaje;
        /** Descripción del tipo de embalaje */
        public final String descTipoEmbalaje;
        /** Ancho en cm */
        public final int ancho;
        /** Largo en cm */
        public final int largo;
        /** Unidad de medida */
        public final String unidadMedida;
        /** Variante del producto */
        public final String variante;
        /** Mensaje de error (si aplica) */
        public final String mensajeError;
        
        public ModeloResponse(boolean valido, String codigo, String descripcion, 
                              int tipoEmbalaje, String descTipoEmbalaje,
                              int ancho, int largo, String unidadMedida, String variante) {
            this.valido = valido;
            this.codigo = codigo;
            this.descripcion = descripcion;
            this.tipoEmbalaje = tipoEmbalaje;
            this.descTipoEmbalaje = descTipoEmbalaje;
            this.ancho = ancho;
            this.largo = largo;
            this.unidadMedida = unidadMedida;
            this.variante = variante;
            this.mensajeError = null;
        }
        
        private ModeloResponse(String error) {
            this.valido = false;
            this.codigo = "";
            this.descripcion = "";
            this.tipoEmbalaje = 0;
            this.descTipoEmbalaje = "";
            this.ancho = 0;
            this.largo = 0;
            this.unidadMedida = "";
            this.variante = "";
            this.mensajeError = error;
        }
        
        public static ModeloResponse error(String mensaje) {
            return new ModeloResponse(mensaje);
        }
        
        @Override
        public String toString() {
            if (!valido) return "ModeloResponse[error=" + mensajeError + "]";
            return "ModeloResponse[codigo=" + codigo + ", tipo=" + tipoEmbalaje + 
                   ", ancho=" + ancho + ", largo=" + largo + "]";
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
    
    public void setReaderIP(String readerIP) {
        this.readerIP = readerIP;
    }
    
    public String getReaderIP() {
        return readerIP;
    }
    
    public void setApiTrabajo(String apiTrabajo) {
        this.apiTrabajo = apiTrabajo;
    }
    
    public String getApiTrabajo() {
        return apiTrabajo;
    }
    
    public boolean isApilado() {
        return apilado;
    }
    
    public void setApilado(boolean apilado) {
        this.apilado = apilado;
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
    
    public void setFailedTagStorage(FailedTagStorage storage) {
        this.failedTagStorage = storage;
    }
    
    /**
     * Establece el modo de visualización del EPC (hexadecimal o decimal).
     * 
     * @param hexMode true para hexadecimal, false para decimal
     */
    public void setDisplayHexMode(boolean hexMode) {
        this.displayHexMode = hexMode;
    }
    
    /**
     * Obtiene el modo de visualización del EPC.
     * 
     * @return true si está en modo hexadecimal, false si decimal
     */
    public boolean isDisplayHexMode() {
        return displayHexMode;
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
    
    /**
     * Limpia la cola de envío pendiente.
     * @return Número de tags eliminados de la cola
     */
    public int clearQueue() {
        int size = sendQueue.size();
        sendQueue.clear();
        System.out.println("[APIClient] Cola limpiada: " + size + " tags descartados");
        return size;
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

    /**
     * Consulta a la API si se puede iniciar la lectura RFID.
     * Llama al endpoint /rfid_lecturas/inicia con la IP del lector.
     * 
     * @return InicioResponse con el resultado de la consulta
     */
    public InicioResponse consultarAPIInicio() {
        System.out.println("[APIClient] Consultando API inicio...");
        
        if (endpointUrl == null || endpointUrl.isEmpty()) {
            System.out.println("[APIClient] Error: URL no configurada");
            return InicioResponse.error("URL de API no configurada");
        }
        
        // Construir URL de inicia: reemplazar /alta por /inicia en la URL base
        String urlInicia = endpointUrl;
        
        // Si contiene /rfid_lecturas/alta, reemplazar por /inicia
        if (urlInicia.contains("/rfid_lecturas/alta")) {
            urlInicia = urlInicia.substring(0, urlInicia.indexOf("/rfid_lecturas/alta")) + "/rfid_lecturas/inicia";
        } else {
            // Asumir URL base, agregar /rfid_lecturas/inicia
            if (!urlInicia.endsWith("/")) urlInicia += "/";
            urlInicia += "rfid_lecturas/inicia";
        }
        
        System.out.println("[APIClient] URL inicia: " + urlInicia);
        
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlInicia);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty("Accept", "application/json");
            // Enviar api-key e ip como headers HTTP
            if (apiKey != null && !apiKey.isEmpty()) {
                connection.setRequestProperty("api-key", apiKey);
            }
            if (readerIP != null && !readerIP.isEmpty()) {
                connection.setRequestProperty("ip", readerIP);
            }
            
            System.out.println("[APIClient] Headers: api-key=" + (apiKey != null ? apiKey.substring(0, Math.min(8, apiKey.length())) + "..." : "null") + ", ip=" + readerIP);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // Parsear JSON manualmente (sin dependencias externas)
                String jsonStr = response.toString();
                boolean resultado = parseJsonBoolean(jsonStr, "resultado");
                String mensaje = parseJsonString(jsonStr, "mensaje");
                long fecha = parseJsonLong(jsonStr, "fecha");
                long fechaInicio = parseJsonLong(jsonStr, "fecha_inicio");
                int milisParada = parseJsonInt(jsonStr, "milisegundos_parada", 100);
                
                System.out.println("[APIClient] Respuesta de inicio: resultado=" + resultado + 
                                 ", mensaje=" + mensaje + ", fecha=" + fecha + ", fecha_inicio=" + fechaInicio);
                
                return new InicioResponse(resultado, mensaje, fecha, fechaInicio, milisParada);
                
            } else {
                String errorMsg = "Error HTTP " + responseCode;
                System.err.println("[APIClient] " + errorMsg);
                return InicioResponse.error(errorMsg);
            }
            
        } catch (Exception e) {
            String errorMsg = "Error al consultar API de inicio: " + e.getMessage();
            System.err.println("[APIClient] " + errorMsg);
            return InicioResponse.error(errorMsg);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    /**
     * Parsea un valor booleano de un JSON simple.
     */
    
    /**
     * Parsea un valor String de un JSON simple.
     */
    
    /**
     * Parsea un valor long de un JSON simple.
     */
    
    /**
     * Parsea un valor int de un JSON simple con valor por defecto.
     */

    /**
     * Parsea un valor booleano de un JSON simple.
     */
    private boolean parseJsonBoolean(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Boolean.parseBoolean(m.group(1));
            }
        } catch (Exception e) {
            System.err.println("[APIClient] Error parseando boolean: " + e.getMessage());
        }
        return false;
    }
    
    /**
     * Parsea un valor String de un JSON simple.
     */
    private String parseJsonString(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            System.err.println("[APIClient] Error parseando string: " + e.getMessage());
        }
        return "";
    }
    
    /**
     * Parsea un valor long de un JSON simple.
     */
    private long parseJsonLong(String json, String key) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Long.parseLong(m.group(1));
            }
        } catch (Exception e) {
            System.err.println("[APIClient] Error parseando long: " + e.getMessage());
        }
        return 0;
    }
    
    /**
     * Parsea un valor int de un JSON simple con valor por defecto.
     */
    private int parseJsonInt(String json, String key, int defaultValue) {
        try {
            String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
            java.util.regex.Matcher m = p.matcher(json);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception e) {
            System.err.println("[APIClient] Error parseando int: " + e.getMessage());
        }
        return defaultValue;
    }




    /**
     * Envía un tag individual a la API usando el formato de VZEBRA.
     * URL: {endpoint}/rfid_lecturas/alta/{token}/{ip}/{trabajo}/{tag}
     * 
     * @param tag EPC del tag leído
     * @param antena Número de antena
     * @param rssi Nivel de señal
     * @return true si el envío fue exitoso (HTTP 200)
     */
    public boolean enviarTagAPI(String tag, int antena, int rssi) {
        if (endpointUrl == null || endpointUrl.isEmpty()) {
            System.err.println("[APIClient] URL no configurada");
            return false;
        }
        
        try {
            // Construir URL estilo VZEBRA: {URL}/rfid_lecturas/alta/{token}/{ip}/{trabajo}/{tag}
            String baseUrl = endpointUrl.endsWith("/") ? endpointUrl : endpointUrl + "/";
            String apiladoParam = apilado ? "S" : "N";
            String urlAlta = baseUrl + "rfid_lecturas/alta/" + 
                           apiKey + "/" + 
                           readerIP + "/" + 
                           apiTrabajo + "/" + 
                           tag + "/" +
                           apiladoParam;
            
            System.out.println("[APIClient] Enviando tag: " + urlAlta);
            logger.debug("APIClient", "Enviando tag: " + tag);
            
            URL url = new URL(urlAlta);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 200) {
                logger.logTag(tag, antena, rssi, true);
                return true;
            } else {
                logger.warn("APIClient", "Error enviando tag. HTTP " + responseCode + " - " + tag);
                lastError = "HTTP " + responseCode;
                // Almacenar lectura fallida para reintento
                if (failedTagStorage != null) {
                    failedTagStorage.almacenarLecturaFallida(readerIP, antena, rssi, tag);
                }
                return false;
            }
            
        } catch (Exception e) {
            logger.error("APIClient", "Error enviando tag: " + tag, e);
            lastError = e.getMessage();
            // Almacenar lectura fallida para reintento
            if (failedTagStorage != null) {
                failedTagStorage.almacenarLecturaFallida(readerIP, antena, rssi, tag);
            }
            if (errorHandler != null) {
                errorHandler.accept(e);
            }
            return false;
        }
    }
    
    /**
     * Envía un tag a la API de forma asíncrona (no bloquea el hilo principal).
     */
    public void enviarTagAPIAsync(String tag, int antena, int rssi) {
        new Thread(() -> {
            enviarTagAPI(tag, antena, rssi);
        }).start();
    }

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
     * Usa formato URL con parámetros en path: {baseUrl}/{token}/{ip}/{trabajo}/{tag}
     * 
     * @param tags Lista de etiquetas a enviar
     * @return Código de respuesta HTTP del último envío
     * @throws IOException si hay error de conexión
     */
    private int doSend(List<TagData> tags) throws IOException {
        int lastResponseCode = 0;
        
        for (TagData tag : tags) {
            lastResponseCode = doSendSingleTag(tag);
        }
        
        return lastResponseCode;
    }
    
    /**
     * Envía una etiqueta individual usando formato URL con path parameters.
     * URL: {baseUrl}/{token}/{ip}/{trabajo}/{tag}
     * 
     * @param tag Datos de la etiqueta
     * @return Código de respuesta HTTP
     * @throws IOException si hay error de conexión
     */
    private int doSendSingleTag(TagData tag) throws IOException {
        // EPC se envía tal cual (el filtro HEX/DECIMAL ya se aplicó antes)
        String epc = tag.getEpc();
        System.out.println("[APIClient] Enviando tag: " + epc);
        
        String token = (apiKey != null) ? URLEncoder.encode(apiKey, StandardCharsets.UTF_8.toString()) : "";
        String ip = (readerIP != null) ? URLEncoder.encode(readerIP, StandardCharsets.UTF_8.toString()) : "";
        String trabajo = (apiTrabajo != null) ? URLEncoder.encode(apiTrabajo, StandardCharsets.UTF_8.toString()) : "";
        String tagEpc = URLEncoder.encode(epc, StandardCharsets.UTF_8.toString());
        
        String fullUrl = endpointUrl;
        if (!fullUrl.endsWith("/")) {
            fullUrl += "/";
        }
        fullUrl += token + "/" + ip + "/" + trabajo + "/" + tagEpc;
        
        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        try {
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Accept", "application/json");
            
            for (Map.Entry<String, String> header : customHeaders.entrySet()) {
                conn.setRequestProperty(header.getKey(), header.getValue());
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
        String epc = displayHexMode ? tag.getEpc() : convertEpcToDecimal(tag.getEpc());
        sb.append("{\"epc\":\"").append(escapeJson(epc)).append("\"");
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
     * Convierte un EPC hexadecimal a formato decimal.
     * 
     * @param hexEpc EPC en formato hexadecimal
     * @return EPC en formato decimal (número grande)
     */
    private String convertEpcToDecimal(String hexEpc) {
        if (hexEpc == null || hexEpc.isEmpty()) {
            return "";
        }
        try {
            java.math.BigInteger bigInt = new java.math.BigInteger(hexEpc, 16);
            return bigInt.toString();
        } catch (NumberFormatException e) {
            return hexEpc;
        }
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

    
    /**
     * Obtiene datos del producto desde la API para un EPC específico.
     * Usado por la integración PLC para obtener dimensiones del producto.
     * 
     * @param epc Código EPC del tag
     * @return JSON string con datos del producto o null si hay error
     */
    public String getProductData(String epc) {
        if (endpointUrl == null || endpointUrl.isEmpty()) {
            System.err.println("[API] URL del endpoint no configurada");
            return null;
        }
        
        try {
            // Construir URL para obtener datos de producto
            String productUrl = endpointUrl;
            if (!productUrl.contains("?")) {
                productUrl += "?";
            } else {
                productUrl += "&";
            }
            productUrl += "epc=" + java.net.URLEncoder.encode(epc, "UTF-8") + "&action=getProduct";
            
            URL url = new URL(productUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(DEFAULT_CONNECT_TIMEOUT);
            conn.setReadTimeout(DEFAULT_READ_TIMEOUT);
            conn.setRequestProperty("Accept", "application/json");
            
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("X-API-Key", apiKey);
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                System.err.println("[API] Error obteniendo datos de producto: HTTP " + responseCode);
                return null;
            }
            
        } catch (Exception ex) {
            System.err.println("[API] Excepcion obteniendo datos de producto: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Consulta la API de modelo para obtener datos del producto asociado a un tag RFID.
     * POST a {endpoint}/rfid_lecturas/modelo con headers api-tag y api-token.
     * 
     * @param tag EPC del tag RFID a consultar
     * @return ModeloResponse con los datos del producto
     */
    public ModeloResponse consultarModelo(String tag) {
        System.out.println("[APIClient] Consultando modelo para tag: " + tag);
        
        if (endpointUrl == null || endpointUrl.isEmpty()) {
            System.out.println("[APIClient] Error: URL no configurada");
            return ModeloResponse.error("URL de API no configurada");
        }
        
        if (apiKey == null || apiKey.isEmpty()) {
            System.out.println("[APIClient] Error: Token no configurado");
            return ModeloResponse.error("Token de API no configurado");
        }
        
        // Construir URL de modelo
        String urlModelo = endpointUrl;
        
        // Ajustar URL base para /rfid_lecturas/modelo
        if (urlModelo.contains("/rfid_lecturas/alta")) {
            urlModelo = urlModelo.substring(0, urlModelo.indexOf("/rfid_lecturas/alta")) + "/rfid_lecturas/modelo";
        } else if (urlModelo.contains("/rfid_lecturas/")) {
            int idx = urlModelo.indexOf("/rfid_lecturas/");
            String base = urlModelo.substring(0, idx);
            urlModelo = base + "/rfid_lecturas/modelo";
        } else {
            urlModelo = urlModelo + "/rfid_lecturas/modelo";
        }
        
        System.out.println("[APIClient] URL modelo: " + urlModelo);
        
        HttpURLConnection conn = null;
        InputStream inputStream = null;
        InputStream errorStream = null;
        OutputStream outputStream = null;
        
        try {
            URL url = new URL(urlModelo);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            
            // Headers requeridos
            conn.setRequestProperty("api-tag", tag);
            conn.setRequestProperty("api-token", apiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            // Body vacio
            outputStream = conn.getOutputStream();
            outputStream.write("".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            
            int responseCode = conn.getResponseCode();
            System.out.println("[APIClient] Respuesta modelo: " + responseCode);
            
            if (responseCode == 200) {
                inputStream = conn.getInputStream();
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                String jsonResponse = response.toString();
                System.out.println("[APIClient] Respuesta JSON: " + jsonResponse);
                
                return parseModeloResponse(jsonResponse);
                
            } else {
                StringBuilder error = new StringBuilder();
                errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(errorStream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            error.append(line);
                        }
                    }
                }
                return ModeloResponse.error("HTTP " + responseCode + ": " + error.toString());
            }
            
        } catch (java.net.SocketTimeoutException e) {
            System.out.println("[APIClient] Timeout consultando modelo");
            return ModeloResponse.error("Timeout de conexion");
        } catch (Exception e) {
            System.out.println("[APIClient] Error consultando modelo: " + e.getMessage());
            return ModeloResponse.error(e.getMessage());
        } finally {
            // Cerrar todos los streams
            try { if (outputStream != null) outputStream.close(); } catch (Exception ignored) {}
            try { if (inputStream != null) inputStream.close(); } catch (Exception ignored) {}
            try { if (errorStream != null) errorStream.close(); } catch (Exception ignored) {}
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
    
    /**
     * DTO interno para deserializar respuesta JSON del endpoint /modelo.
     */
    private static class ModeloDto {
        public String valido;
        public String codigo;
        public String descripcion;
        @SerializedName("tipo_embalaje")
        public int tipoEmbalaje;
        @SerializedName("desc_tipo_embalaje")
        public String descTipoEmbalaje;
        public int ancho;
        public int largo;
        @SerializedName("unidad_medida")
        public String unidadMedida;
        public String variante;
    }
    
    /** Instancia de Gson para parseo JSON */
    private static final Gson gson = new Gson();
    
    /**
     * Parsea la respuesta JSON del endpoint de modelo usando Gson.
     */
    private ModeloResponse parseModeloResponse(String json) {
        try {
            if (json == null || json.trim().isEmpty()) {
                return ModeloResponse.error("Respuesta JSON vacia");
            }
            
            ModeloDto dto = gson.fromJson(json, ModeloDto.class);
            
            if (dto == null) {
                return ModeloResponse.error("No se pudo parsear JSON");
            }
            
            boolean valido = "TRUE".equalsIgnoreCase(dto.valido);
            
            System.out.println("[APIClient] Modelo parseado con Gson: valido=" + valido + 
                             ", codigo=" + dto.codigo + ", tipo=" + dto.tipoEmbalaje);
            
            return new ModeloResponse(
                valido,
                dto.codigo != null ? dto.codigo : "",
                dto.descripcion != null ? dto.descripcion : "",
                dto.tipoEmbalaje,
                dto.descTipoEmbalaje != null ? dto.descTipoEmbalaje : "",
                dto.ancho,
                dto.largo,
                dto.unidadMedida != null ? dto.unidadMedida : "",
                dto.variante != null ? dto.variante : ""
            );
            
        } catch (JsonSyntaxException e) {
            System.out.println("[APIClient] Error de sintaxis JSON: " + e.getMessage());
            return ModeloResponse.error("Error de sintaxis JSON: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[APIClient] Error parseando modelo: " + e.getMessage());
            return ModeloResponse.error("Error parseando JSON: " + e.getMessage());
        }
    }

}