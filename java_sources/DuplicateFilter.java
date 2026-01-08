import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtro de tags duplicados.
 * Evita procesar el mismo tag EPC múltiples veces dentro de un período configurable.
 * 
 * @author RFID IDE
 * @version 1.0
 */
public class DuplicateFilter {
    
    /** Tiempo de expiración por defecto en milisegundos (5 segundos) */
    public static final long DEFAULT_EXPIRATION_MS = 5000;
    
    /** Mapa de EPCs vistos con su timestamp */
    private final Map<String, Long> seenTags = new ConcurrentHashMap<>();
    
    /** Tiempo de expiración en milisegundos */
    private long expirationMs;
    
    /** Indica si el filtro está habilitado */
    private boolean enabled;
    
    /** Modo sin expiración (estilo VZEBRA) - tags bloqueados hasta reset manual */
    private boolean noExpiration;
    
    /** Contador de duplicados filtrados */
    private long duplicatesFiltered;
    
    /** Contador de tags únicos procesados */
    private long uniqueTagsProcessed;
    
    /**
     * Constructor con valores por defecto.
     */
    public DuplicateFilter() {
        this.expirationMs = DEFAULT_EXPIRATION_MS;
        this.enabled = true;
        this.noExpiration = false;
        this.duplicatesFiltered = 0;
        this.uniqueTagsProcessed = 0;
    }
    
    /**
     * Constructor con tiempo de expiración personalizado.
     * 
     * @param expirationMs Tiempo en milisegundos antes de permitir re-escaneo
     */
    public DuplicateFilter(long expirationMs) {
        this();
        setExpirationMs(expirationMs);
    }
    
    /**
     * Verifica si un tag debe ser procesado o filtrado como duplicado.
     * 
     * @param epc Código EPC del tag
     * @return true si el tag debe ser procesado, false si es duplicado
     */
    public synchronized boolean shouldProcess(String epc) {
        if (!enabled || epc == null || epc.isEmpty()) {
            return true;
        }
        
        long now = System.currentTimeMillis();
        Long lastSeen = seenTags.get(epc);
        
        // Modo sin expiración (estilo VZEBRA)
        if (noExpiration) {
            if (lastSeen != null) {
                // Tag ya fue visto, es duplicado permanente
                duplicatesFiltered++;
                return false;
            }
            // Tag nuevo, agregarlo al set
            seenTags.put(epc, now);
            uniqueTagsProcessed++;
            return true;
        }
        
        // Modo con tiempo de expiración
        if (lastSeen != null && (now - lastSeen) < expirationMs) {
            duplicatesFiltered++;
            return false;
        }
        
        seenTags.put(epc, now);
        uniqueTagsProcessed++;
        return true;
    }
    
    /**
     * Limpia tags expirados del cache.
     * Llamar periódicamente para liberar memoria.
     */
    public synchronized void cleanExpired() {
        long now = System.currentTimeMillis();
        seenTags.entrySet().removeIf(entry -> 
            (now - entry.getValue()) >= expirationMs);
    }
    
    /**
     * Limpia todos los tags del cache.
     */
    public synchronized void clear() {
        seenTags.clear();
    }
    
    /**
     * Elimina un tag específico del cache.
     * Permite que el tag sea procesado nuevamente.
     * 
     * @param epc EPC del tag a eliminar
     */
    public synchronized void remove(String epc) {
        if (epc != null) {
            seenTags.remove(epc);
        }
    }
    
    /**
     * Resetea las estadísticas.
     */
    public synchronized void resetStats() {
        duplicatesFiltered = 0;
        uniqueTagsProcessed = 0;
    }
    
    // Getters y Setters
    
    public long getExpirationMs() {
        return expirationMs;
    }
    
    /**
     * Establece el tiempo de expiración.
     * 
     * @param expirationMs Tiempo en milisegundos (mínimo 100ms)
     */
    public void setExpirationMs(long expirationMs) {
        if (expirationMs < 100) {
            throw new IllegalArgumentException(
                "El tiempo de expiración debe ser al menos 100ms");
        }
        this.expirationMs = expirationMs;
    }
    
    /**
     * Establece el tiempo de expiración en segundos.
     * 
     * @param seconds Segundos antes de permitir re-escaneo
     */
    public void setExpirationSeconds(int seconds) {
        setExpirationMs(seconds * 1000L);
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public long getDuplicatesFiltered() {
        return duplicatesFiltered;
    }
    
    public long getUniqueTagsProcessed() {
        return uniqueTagsProcessed;
    }
    
    public int getCacheSize() {
        return seenTags.size();
    }
    
    /**
     * Verifica si está en modo sin expiración.
     */
    public boolean isNoExpiration() {
        return noExpiration;
    }
    
    /**
     * Establece el modo sin expiración (estilo VZEBRA).
     * Cuando está activo, los tags se bloquean permanentemente hasta llamar a clear().
     * 
     * @param noExpiration true para bloquear tags permanentemente
     */
    public void setNoExpiration(boolean noExpiration) {
        this.noExpiration = noExpiration;
    }
    
    @Override
    public String toString() {
        return String.format(
            "DuplicateFilter[enabled=%b, noExpiration=%b, expiration=%dms, cached=%d, filtered=%d, processed=%d]",
            enabled, noExpiration, expirationMs, seenTags.size(), duplicatesFiltered, uniqueTagsProcessed);
    }
}
