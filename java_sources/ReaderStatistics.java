import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Seguimiento de estadísticas en tiempo real para el lector RFID Zebra FX7500.
 * Proporciona métricas detalladas sobre lecturas de etiquetas, RSSI por antena,
 * estado de conexión y rendimiento de la API.
 * 
 * Características principales:
 * - tagsRead, uniqueTags, tagsPerSecond
 * - avgRssi, minRssi, maxRssi por antena
 * - connectionTime, uptime, reconnectCount
 * - apiSuccessCount, apiFailureCount
 * - Métodos de reset y snapshot
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public class ReaderStatistics implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** Número máximo de puertos de antena */
    public static final int MAX_ANTENNAS = 4;
    
    // ==================== Estadísticas de etiquetas ====================
    
    /** Total de etiquetas leídas (incluyendo repeticiones) */
    private final AtomicLong tagsRead = new AtomicLong(0);
    
    /** Etiquetas únicas leídas */
    private final AtomicLong uniqueTags = new AtomicLong(0);
    
    /** Etiquetas leídas en el último segundo */
    private volatile long tagsLastSecond = 0;
    
    /** Etiquetas por segundo (promedio móvil) */
    private volatile double tagsPerSecond = 0.0;
    
    /** Timestamp de inicio de conteo para TPS */
    private volatile long tpsWindowStart = 0;
    
    /** Conteo de tags en ventana actual para TPS */
    private final AtomicLong tpsWindowCount = new AtomicLong(0);
    
    // ==================== Estadísticas de RSSI por antena ====================
    
    /** Suma de RSSI por antena (para calcular promedio) */
    private final double[] rssiSum = new double[MAX_ANTENNAS];
    
    /** Conteo de lecturas por antena (para calcular promedio) */
    private final long[] rssiCount = new long[MAX_ANTENNAS];
    
    /** RSSI mínimo por antena */
    private final double[] minRssi = new double[MAX_ANTENNAS];
    
    /** RSSI máximo por antena */
    private final double[] maxRssi = new double[MAX_ANTENNAS];
    
    /** RSSI promedio calculado por antena */
    private final double[] avgRssi = new double[MAX_ANTENNAS];
    
    /** Lecturas por antena */
    private final AtomicLong[] readsPerAntenna = new AtomicLong[MAX_ANTENNAS];
    
    // ==================== Estadísticas de conexión ====================
    
    /** Timestamp de conexión inicial */
    private volatile long connectionTime = 0;
    
    /** Timestamp de última conexión exitosa */
    private volatile long lastConnectedTime = 0;
    
    /** Timestamp de última desconexión */
    private volatile long lastDisconnectedTime = 0;
    
    /** Número de reconexiones */
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    
    /** Indica si está conectado actualmente */
    private volatile boolean connected = false;
    
    /** Tiempo total conectado en milisegundos */
    private volatile long totalConnectedTime = 0;
    
    // ==================== Estadísticas de API ====================
    
    /** Envíos exitosos a la API */
    private final AtomicLong apiSuccessCount = new AtomicLong(0);
    
    /** Envíos fallidos a la API */
    private final AtomicLong apiFailureCount = new AtomicLong(0);
    
    /** Total de etiquetas enviadas a la API */
    private final AtomicLong apiTagsSent = new AtomicLong(0);
    
    /** Tiempo de respuesta promedio de la API en ms */
    private volatile double apiAvgResponseTime = 0.0;
    
    /** Suma de tiempos de respuesta (para calcular promedio) */
    private volatile long apiResponseTimeSum = 0;
    
    /** Conteo de respuestas API (para calcular promedio) */
    private volatile long apiResponseCount = 0;
    
    // ==================== Estadísticas de errores ====================
    
    /** Número de errores totales */
    private final AtomicLong errorCount = new AtomicLong(0);
    
    /** Timestamp del último error */
    private volatile long lastErrorTime = 0;
    
    /** Último mensaje de error */
    private volatile String lastErrorMessage = null;
    
    // ==================== Timestamps ====================
    
    /** Timestamp de inicio de estadísticas */
    private volatile long startTime;
    
    /** Timestamp de última actualización */
    private volatile long lastUpdateTime;
    
    /** Lock para sincronización de estadísticas de RSSI */
    private final Object rssiLock = new Object();
    
    /**
     * Constructor por defecto. Inicializa todas las estadísticas.
     */
    public ReaderStatistics() {
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = this.startTime;
        this.tpsWindowStart = this.startTime;
        
        for (int i = 0; i < MAX_ANTENNAS; i++) {
            readsPerAntenna[i] = new AtomicLong(0);
            minRssi[i] = Double.MAX_VALUE;
            maxRssi[i] = Double.MIN_VALUE;
            rssiSum[i] = 0;
            rssiCount[i] = 0;
            avgRssi[i] = 0;
        }
    }
    
    // ==================== Registro de eventos ====================
    
    /**
     * Registra una lectura de etiqueta.
     * 
     * @param tag Datos de la etiqueta leída
     */
    public void recordTagRead(TagData tag) {
        if (tag == null) return;
        
        tagsRead.incrementAndGet();
        tpsWindowCount.incrementAndGet();
        
        int antenna = tag.getAntennaPort();
        if (antenna >= 1 && antenna <= MAX_ANTENNAS) {
            int idx = antenna - 1;
            readsPerAntenna[idx].incrementAndGet();
            
            synchronized (rssiLock) {
                double rssi = tag.getRssi();
                rssiSum[idx] += rssi;
                rssiCount[idx]++;
                
                if (rssi < minRssi[idx]) {
                    minRssi[idx] = rssi;
                }
                if (rssi > maxRssi[idx]) {
                    maxRssi[idx] = rssi;
                }
                
                avgRssi[idx] = rssiSum[idx] / rssiCount[idx];
            }
        }
        
        updateTagsPerSecond();
        lastUpdateTime = System.currentTimeMillis();
    }
    
    /**
     * Registra una nueva etiqueta única.
     */
    public void recordUniqueTag() {
        uniqueTags.incrementAndGet();
    }
    
    /**
     * Registra múltiples lecturas de etiquetas.
     * 
     * @param count Número de etiquetas leídas
     */
    public void recordTagReads(int count) {
        tagsRead.addAndGet(count);
        tpsWindowCount.addAndGet(count);
        updateTagsPerSecond();
    }
    
    /**
     * Registra un envío exitoso a la API.
     * 
     * @param tagCount Número de etiquetas enviadas
     * @param responseTimeMs Tiempo de respuesta en milisegundos
     */
    public void recordApiSuccess(int tagCount, long responseTimeMs) {
        apiSuccessCount.incrementAndGet();
        apiTagsSent.addAndGet(tagCount);
        
        synchronized (this) {
            apiResponseTimeSum += responseTimeMs;
            apiResponseCount++;
            apiAvgResponseTime = (double) apiResponseTimeSum / apiResponseCount;
        }
    }
    
    /**
     * Registra un envío fallido a la API.
     * 
     * @param tagCount Número de etiquetas que fallaron
     */
    public void recordApiFailure(int tagCount) {
        apiFailureCount.incrementAndGet();
    }
    
    /**
     * Registra una conexión exitosa.
     */
    public void recordConnection() {
        long now = System.currentTimeMillis();
        
        if (connectionTime == 0) {
            connectionTime = now;
        }
        
        if (!connected && lastConnectedTime > 0) {
            reconnectCount.incrementAndGet();
        }
        
        lastConnectedTime = now;
        connected = true;
    }
    
    /**
     * Registra una desconexión.
     */
    public void recordDisconnection() {
        long now = System.currentTimeMillis();
        
        if (connected && lastConnectedTime > 0) {
            totalConnectedTime += (now - lastConnectedTime);
        }
        
        lastDisconnectedTime = now;
        connected = false;
    }
    
    /**
     * Registra una reconexión.
     */
    public void recordReconnect() {
        reconnectCount.incrementAndGet();
    }
    
    /**
     * Registra un error.
     * 
     * @param message Mensaje de error
     */
    public void recordError(String message) {
        errorCount.incrementAndGet();
        lastErrorTime = System.currentTimeMillis();
        lastErrorMessage = message;
    }
    
    // ==================== Cálculos ====================
    
    /**
     * Actualiza el cálculo de etiquetas por segundo.
     */
    private void updateTagsPerSecond() {
        long now = System.currentTimeMillis();
        long elapsed = now - tpsWindowStart;
        
        if (elapsed >= 1000) {
            long count = tpsWindowCount.getAndSet(0);
            tagsLastSecond = count;
            tagsPerSecond = count * 1000.0 / elapsed;
            tpsWindowStart = now;
        }
    }
    
    /**
     * Calcula el uptime en milisegundos.
     * 
     * @return Tiempo de actividad en ms
     */
    public long getUptime() {
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * Calcula el uptime formateado como string.
     * 
     * @return Uptime en formato "Xd Xh Xm Xs"
     */
    public String getUptimeFormatted() {
        Duration duration = Duration.ofMillis(getUptime());
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0 || days > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        
        return sb.toString();
    }
    
    /**
     * Calcula el tiempo total conectado incluyendo la sesión actual.
     * 
     * @return Tiempo conectado en milisegundos
     */
    public long getTotalConnectedTime() {
        if (connected && lastConnectedTime > 0) {
            return totalConnectedTime + (System.currentTimeMillis() - lastConnectedTime);
        }
        return totalConnectedTime;
    }
    
    /**
     * Calcula la tasa de éxito de la API en porcentaje.
     * 
     * @return Porcentaje de éxito (0-100)
     */
    public double getApiSuccessRate() {
        long total = apiSuccessCount.get() + apiFailureCount.get();
        if (total == 0) return 100.0;
        return (apiSuccessCount.get() * 100.0) / total;
    }
    
    // ==================== Getters ====================
    
    public long getTagsRead() {
        return tagsRead.get();
    }
    
    public long getUniqueTags() {
        return uniqueTags.get();
    }
    
    public double getTagsPerSecond() {
        updateTagsPerSecond();
        return tagsPerSecond;
    }
    
    public long getTagsLastSecond() {
        return tagsLastSecond;
    }
    
    public double getAvgRssi(int antenna) {
        if (antenna < 1 || antenna > MAX_ANTENNAS) return 0;
        synchronized (rssiLock) {
            return avgRssi[antenna - 1];
        }
    }
    
    public double getMinRssi(int antenna) {
        if (antenna < 1 || antenna > MAX_ANTENNAS) return 0;
        synchronized (rssiLock) {
            double val = minRssi[antenna - 1];
            return val == Double.MAX_VALUE ? 0 : val;
        }
    }
    
    public double getMaxRssi(int antenna) {
        if (antenna < 1 || antenna > MAX_ANTENNAS) return 0;
        synchronized (rssiLock) {
            double val = maxRssi[antenna - 1];
            return val == Double.MIN_VALUE ? 0 : val;
        }
    }
    
    public long getReadsForAntenna(int antenna) {
        if (antenna < 1 || antenna > MAX_ANTENNAS) return 0;
        return readsPerAntenna[antenna - 1].get();
    }
    
    public long getConnectionTime() {
        return connectionTime;
    }
    
    public long getLastConnectedTime() {
        return lastConnectedTime;
    }
    
    public long getLastDisconnectedTime() {
        return lastDisconnectedTime;
    }
    
    public int getReconnectCount() {
        return reconnectCount.get();
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public long getApiSuccessCount() {
        return apiSuccessCount.get();
    }
    
    public long getApiFailureCount() {
        return apiFailureCount.get();
    }
    
    public long getApiTagsSent() {
        return apiTagsSent.get();
    }
    
    public double getApiAvgResponseTime() {
        return apiAvgResponseTime;
    }
    
    public long getErrorCount() {
        return errorCount.get();
    }
    
    public long getLastErrorTime() {
        return lastErrorTime;
    }
    
    public String getLastErrorMessage() {
        return lastErrorMessage;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    // ==================== Reset y Snapshot ====================
    
    /**
     * Reinicia todas las estadísticas.
     */
    public void reset() {
        tagsRead.set(0);
        uniqueTags.set(0);
        tagsLastSecond = 0;
        tagsPerSecond = 0.0;
        tpsWindowCount.set(0);
        
        synchronized (rssiLock) {
            for (int i = 0; i < MAX_ANTENNAS; i++) {
                readsPerAntenna[i].set(0);
                rssiSum[i] = 0;
                rssiCount[i] = 0;
                minRssi[i] = Double.MAX_VALUE;
                maxRssi[i] = Double.MIN_VALUE;
                avgRssi[i] = 0;
            }
        }
        
        reconnectCount.set(0);
        totalConnectedTime = 0;
        
        apiSuccessCount.set(0);
        apiFailureCount.set(0);
        apiTagsSent.set(0);
        apiResponseTimeSum = 0;
        apiResponseCount = 0;
        apiAvgResponseTime = 0;
        
        errorCount.set(0);
        lastErrorTime = 0;
        lastErrorMessage = null;
        
        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;
        tpsWindowStart = startTime;
        
        System.out.println("[Statistics] Estadísticas reiniciadas");
    }
    
    /**
     * Reinicia solo las estadísticas de etiquetas.
     */
    public void resetTagStatistics() {
        tagsRead.set(0);
        uniqueTags.set(0);
        tagsLastSecond = 0;
        tagsPerSecond = 0.0;
        tpsWindowCount.set(0);
        tpsWindowStart = System.currentTimeMillis();
        
        synchronized (rssiLock) {
            for (int i = 0; i < MAX_ANTENNAS; i++) {
                readsPerAntenna[i].set(0);
                rssiSum[i] = 0;
                rssiCount[i] = 0;
                minRssi[i] = Double.MAX_VALUE;
                maxRssi[i] = Double.MIN_VALUE;
                avgRssi[i] = 0;
            }
        }
    }
    
    /**
     * Reinicia solo las estadísticas de API.
     */
    public void resetApiStatistics() {
        apiSuccessCount.set(0);
        apiFailureCount.set(0);
        apiTagsSent.set(0);
        apiResponseTimeSum = 0;
        apiResponseCount = 0;
        apiAvgResponseTime = 0;
    }
    
    /**
     * Crea una instantánea (snapshot) de las estadísticas actuales.
     * 
     * @return Copia de las estadísticas actuales
     */
    public StatisticsSnapshot snapshot() {
        return new StatisticsSnapshot(this);
    }
    
    /**
     * Clase interna para instantánea de estadísticas.
     */
    public static class StatisticsSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public final long timestamp;
        public final long tagsRead;
        public final long uniqueTags;
        public final double tagsPerSecond;
        public final double[] avgRssi;
        public final double[] minRssi;
        public final double[] maxRssi;
        public final long[] readsPerAntenna;
        public final long uptime;
        public final long totalConnectedTime;
        public final int reconnectCount;
        public final boolean connected;
        public final long apiSuccessCount;
        public final long apiFailureCount;
        public final double apiSuccessRate;
        public final double apiAvgResponseTime;
        public final long errorCount;
        
        /**
         * Crea un snapshot desde las estadísticas actuales.
         * 
         * @param stats Estadísticas a capturar
         */
        public StatisticsSnapshot(ReaderStatistics stats) {
            this.timestamp = System.currentTimeMillis();
            this.tagsRead = stats.getTagsRead();
            this.uniqueTags = stats.getUniqueTags();
            this.tagsPerSecond = stats.getTagsPerSecond();
            this.avgRssi = new double[MAX_ANTENNAS];
            this.minRssi = new double[MAX_ANTENNAS];
            this.maxRssi = new double[MAX_ANTENNAS];
            this.readsPerAntenna = new long[MAX_ANTENNAS];
            
            for (int i = 1; i <= MAX_ANTENNAS; i++) {
                this.avgRssi[i - 1] = stats.getAvgRssi(i);
                this.minRssi[i - 1] = stats.getMinRssi(i);
                this.maxRssi[i - 1] = stats.getMaxRssi(i);
                this.readsPerAntenna[i - 1] = stats.getReadsForAntenna(i);
            }
            
            this.uptime = stats.getUptime();
            this.totalConnectedTime = stats.getTotalConnectedTime();
            this.reconnectCount = stats.getReconnectCount();
            this.connected = stats.isConnected();
            this.apiSuccessCount = stats.getApiSuccessCount();
            this.apiFailureCount = stats.getApiFailureCount();
            this.apiSuccessRate = stats.getApiSuccessRate();
            this.apiAvgResponseTime = stats.getApiAvgResponseTime();
            this.errorCount = stats.getErrorCount();
        }
        
        @Override
        public String toString() {
            return String.format(
                "Snapshot[tags=%d, unique=%d, tps=%.1f, connected=%s, uptime=%dms]",
                tagsRead, uniqueTags, tagsPerSecond, connected, uptime);
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ReaderStatistics {\n");
        sb.append("  Tags: read=").append(tagsRead.get());
        sb.append(", unique=").append(uniqueTags.get());
        sb.append(", tps=").append(String.format("%.1f", tagsPerSecond)).append("\n");
        
        sb.append("  RSSI por antena:\n");
        for (int i = 1; i <= MAX_ANTENNAS; i++) {
            sb.append("    Antena ").append(i);
            sb.append(": avg=").append(String.format("%.1f", getAvgRssi(i)));
            sb.append(", min=").append(String.format("%.1f", getMinRssi(i)));
            sb.append(", max=").append(String.format("%.1f", getMaxRssi(i)));
            sb.append(", reads=").append(getReadsForAntenna(i)).append("\n");
        }
        
        sb.append("  Conexión: connected=").append(connected);
        sb.append(", uptime=").append(getUptimeFormatted());
        sb.append(", reconnects=").append(reconnectCount.get()).append("\n");
        
        sb.append("  API: success=").append(apiSuccessCount.get());
        sb.append(", failure=").append(apiFailureCount.get());
        sb.append(", rate=").append(String.format("%.1f%%", getApiSuccessRate()));
        sb.append(", avgTime=").append(String.format("%.0fms", apiAvgResponseTime)).append("\n");
        
        sb.append("  Errors: ").append(errorCount.get());
        if (lastErrorMessage != null) {
            sb.append(", last=\"").append(lastErrorMessage).append("\"");
        }
        sb.append("\n}");
        
        return sb.toString();
    }
}
