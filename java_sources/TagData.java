import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Modelo de datos para una etiqueta RFID leída por el lector Zebra FX7500.
 * Contiene toda la información asociada a una lectura de etiqueta incluyendo
 * EPC, TID, RSSI, puerto de antena y metadatos adicionales.
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public class TagData implements Serializable, Comparable<TagData> {
    
    private static final long serialVersionUID = 1L;
    
    /** EPC (Electronic Product Code) de la etiqueta en formato hexadecimal */
    private String epc;
    
    /** TID (Tag Identifier) de la etiqueta en formato hexadecimal (opcional) */
    private String tid;
    
    /** Datos de usuario de la etiqueta en formato hexadecimal (opcional) */
    private String userData;
    
    /** RSSI (Received Signal Strength Indicator) en dBm */
    private double rssi;
    
    /** Número de puerto de antena que realizó la lectura (1-4) */
    private int antennaPort;
    
    /** Marca de tiempo de la lectura en milisegundos desde epoch */
    private long timestamp;
    
    /** Número de veces que se ha leído esta etiqueta */
    private int readCount;
    
    /** Ángulo de fase de la señal en grados (opcional) */
    private Double phaseAngle;
    
    /** Frecuencia de operación durante la lectura en kHz */
    private int frequency;
    
    /** RSSI más alto observado para esta etiqueta */
    private double peakRssi;
    
    /** Marca de tiempo de la primera lectura */
    private long firstSeen;
    
    /** Marca de tiempo de la última lectura */
    private long lastSeen;
    
    /** Estado del envío a la API: "", "Enviando", "OK", "Error" */
    private String apiStatus = "";
    
    /** Estado del envío al PLC: "", "Enviando", "OK", "Error" */
    private String plcStatus = "";
    
    /** Formateador de fecha para visualización */
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                        .withZone(ZoneId.systemDefault());
    
    /**
     * Constructor por defecto.
     */
    public TagData() {
        this.timestamp = System.currentTimeMillis();
        this.firstSeen = this.timestamp;
        this.lastSeen = this.timestamp;
        this.readCount = 1;
        this.peakRssi = Double.NEGATIVE_INFINITY;
    }
    
    /**
     * Constructor con EPC básico.
     * 
     * @param epc EPC de la etiqueta en formato hexadecimal
     */
    public TagData(String epc) {
        this();
        setEpc(epc);
    }
    
    /**
     * Constructor completo con todos los parámetros principales.
     * 
     * @param epc EPC de la etiqueta en formato hexadecimal
     * @param rssi RSSI de la lectura en dBm
     * @param antennaPort Puerto de antena (1-4)
     * @param timestamp Marca de tiempo de la lectura
     */
    public TagData(String epc, double rssi, int antennaPort, long timestamp) {
        setEpc(epc);
        setRssi(rssi);
        setAntennaPort(antennaPort);
        this.timestamp = timestamp;
        this.firstSeen = timestamp;
        this.lastSeen = timestamp;
        this.readCount = 1;
        this.peakRssi = rssi;
    }
    
    /**
     * Obtiene el EPC de la etiqueta.
     * 
     * @return EPC en formato hexadecimal
     */
    public String getEpc() {
        return epc;
    }
    
    /**
     * Establece el EPC de la etiqueta.
     * 
     * @param epc EPC en formato hexadecimal
     * @throws IllegalArgumentException si el EPC es nulo o vacío
     */
    public void setEpc(String epc) {
        if (epc == null || epc.trim().isEmpty()) {
            throw new IllegalArgumentException("El EPC no puede ser nulo o vacío");
        }
        this.epc = epc.toUpperCase().replaceAll("[^0-9A-F]", "");
    }
    
    /**
     * Obtiene el TID de la etiqueta.
     * 
     * @return TID en formato hexadecimal o null si no está disponible
     */
    public String getTid() {
        return tid;
    }
    
    /**
     * Establece el TID de la etiqueta.
     * 
     * @param tid TID en formato hexadecimal
     */
    public void setTid(String tid) {
        if (tid != null && !tid.trim().isEmpty()) {
            this.tid = tid.toUpperCase().replaceAll("[^0-9A-F]", "");
        } else {
            this.tid = null;
        }
    }
    
    /**
     * Obtiene los datos de usuario de la etiqueta.
     * 
     * @return Datos de usuario en formato hexadecimal o null
     */
    public String getUserData() {
        return userData;
    }
    
    /**
     * Establece los datos de usuario de la etiqueta.
     * 
     * @param userData Datos de usuario en formato hexadecimal
     */
    public void setUserData(String userData) {
        if (userData != null && !userData.trim().isEmpty()) {
            this.userData = userData.toUpperCase().replaceAll("[^0-9A-F]", "");
        } else {
            this.userData = null;
        }
    }
    
    /**
     * Obtiene el RSSI de la lectura.
     * 
     * @return RSSI en dBm
     */
    public double getRssi() {
        return rssi;
    }
    
    /**
     * Establece el RSSI de la lectura.
     * 
     * @param rssi RSSI en dBm (típicamente entre -80 y -20 dBm)
     */
    public void setRssi(double rssi) {
        this.rssi = rssi;
        if (rssi > this.peakRssi) {
            this.peakRssi = rssi;
        }
    }
    
    /**
     * Obtiene el puerto de antena que realizó la lectura.
     * 
     * @return Número de puerto (1-4)
     */
    public int getAntennaPort() {
        return antennaPort;
    }
    
    /**
     * Establece el puerto de antena.
     * 
     * @param antennaPort Número de puerto (1-4)
     * @throws IllegalArgumentException si el puerto está fuera de rango
     */
    public void setAntennaPort(int antennaPort) {
        if (antennaPort < 1 || antennaPort > 4) {
            throw new IllegalArgumentException(
                "El puerto de antena debe estar entre 1 y 4: " + antennaPort);
        }
        this.antennaPort = antennaPort;
    }
    
    /**
     * Obtiene la marca de tiempo de la lectura.
     * 
     * @return Timestamp en milisegundos desde epoch
     */
    public long getTimestamp() {
        return timestamp;
    }
    
    /**
     * Establece la marca de tiempo de la lectura.
     * 
     * @param timestamp Timestamp en milisegundos desde epoch
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
        if (timestamp < this.firstSeen) {
            this.firstSeen = timestamp;
        }
        if (timestamp > this.lastSeen) {
            this.lastSeen = timestamp;
        }
    }
    
    /**
     * Obtiene el número de lecturas de esta etiqueta.
     * 
     * @return Contador de lecturas
     */
    public int getReadCount() {
        return readCount;
    }
    
    /**
     * Establece el contador de lecturas.
     * 
     * @param readCount Número de lecturas
     */
    public void setReadCount(int readCount) {
        this.readCount = Math.max(1, readCount);
    }
    
    /**
     * Incrementa el contador de lecturas.
     */
    public void incrementReadCount() {
        this.readCount++;
    }
    
    /**
     * Obtiene el ángulo de fase de la señal.
     * 
     * @return Ángulo de fase en grados o null si no está disponible
     */
    public Double getPhaseAngle() {
        return phaseAngle;
    }
    
    /**
     * Establece el ángulo de fase de la señal.
     * 
     * @param phaseAngle Ángulo de fase en grados (0-360)
     */
    public void setPhaseAngle(Double phaseAngle) {
        if (phaseAngle != null) {
            this.phaseAngle = ((phaseAngle % 360.0) + 360.0) % 360.0;
        } else {
            this.phaseAngle = null;
        }
    }
    
    /**
     * Obtiene la frecuencia de operación durante la lectura.
     * 
     * @return Frecuencia en kHz
     */
    public int getFrequency() {
        return frequency;
    }
    
    /**
     * Establece la frecuencia de operación.
     * 
     * @param frequency Frecuencia en kHz
     */
    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }
    
    /**
     * Obtiene el RSSI más alto observado.
     * 
     * @return Peak RSSI en dBm
     */
    public double getPeakRssi() {
        return peakRssi;
    }
    
    /**
     * Obtiene la marca de tiempo de la primera lectura.
     * 
     * @return Timestamp de primera lectura
     */
    public long getFirstSeen() {
        return firstSeen;
    }
    
    /**
     * Obtiene la marca de tiempo de la última lectura.
     * 
     * @return Timestamp de última lectura
     */
    public long getLastSeen() {
        return lastSeen;
    }
    
    public String getApiStatus() {
        return apiStatus;
    }
    
    public void setApiStatus(String apiStatus) {
        this.apiStatus = apiStatus;
    }
    
    public String getPlcStatus() {
        return plcStatus;
    }
    
    public void setPlcStatus(String plcStatus) {
        this.plcStatus = plcStatus;
    }
    
    /**
     * Actualiza esta etiqueta con datos de una nueva lectura.
     * 
     * @param newReading Nueva lectura de la misma etiqueta
     */
    public void merge(TagData newReading) {
        if (newReading == null || !this.epc.equals(newReading.epc)) {
            return;
        }
        
        this.readCount += newReading.readCount;
        
        if (newReading.rssi > this.peakRssi) {
            this.peakRssi = newReading.rssi;
        }
        
        this.rssi = newReading.rssi;
        this.antennaPort = newReading.antennaPort;
        this.timestamp = newReading.timestamp;
        
        if (newReading.firstSeen < this.firstSeen) {
            this.firstSeen = newReading.firstSeen;
        }
        if (newReading.lastSeen > this.lastSeen) {
            this.lastSeen = newReading.lastSeen;
        }
        
        if (newReading.tid != null) {
            this.tid = newReading.tid;
        }
        if (newReading.userData != null) {
            this.userData = newReading.userData;
        }
        if (newReading.phaseAngle != null) {
            this.phaseAngle = newReading.phaseAngle;
        }
        if (newReading.frequency > 0) {
            this.frequency = newReading.frequency;
        }
    }
    
    /**
     * Obtiene la marca de tiempo formateada como String.
     * 
     * @return Timestamp formateado
     */
    public String getFormattedTimestamp() {
        return DATE_FORMATTER.format(Instant.ofEpochMilli(timestamp));
    }
    
    /**
     * Obtiene el EPC formateado con separadores cada 4 caracteres.
     * 
     * @return EPC formateado (ej: "3034-2A00-1234-5678")
     */
    public String getFormattedEpc() {
        if (epc == null || epc.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < epc.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append("-");
            }
            sb.append(epc.charAt(i));
        }
        return sb.toString();
    }
    
    /**
     * Calcula el tiempo desde la última lectura en milisegundos.
     * 
     * @return Tiempo transcurrido desde la última lectura
     */
    public long getTimeSinceLastSeen() {
        return System.currentTimeMillis() - lastSeen;
    }
    
    /**
     * Verifica si la etiqueta está "fresca" (vista recientemente).
     * 
     * @param freshnessMs Umbral de frescura en milisegundos
     * @return true si la etiqueta fue vista dentro del umbral
     */
    public boolean isFresh(long freshnessMs) {
        return getTimeSinceLastSeen() < freshnessMs;
    }
    
    /**
     * Crea una copia de esta etiqueta.
     * 
     * @return Nueva instancia con los mismos valores
     */
    public TagData copy() {
        TagData copy = new TagData();
        copy.epc = this.epc;
        copy.tid = this.tid;
        copy.userData = this.userData;
        copy.rssi = this.rssi;
        copy.antennaPort = this.antennaPort;
        copy.timestamp = this.timestamp;
        copy.readCount = this.readCount;
        copy.phaseAngle = this.phaseAngle;
        copy.frequency = this.frequency;
        copy.peakRssi = this.peakRssi;
        copy.firstSeen = this.firstSeen;
        copy.lastSeen = this.lastSeen;
        return copy;
    }
    
    @Override
    public int compareTo(TagData other) {
        if (other == null) return 1;
        return this.epc.compareTo(other.epc);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TagData[EPC=").append(getFormattedEpc());
        if (tid != null) {
            sb.append(", TID=").append(tid);
        }
        sb.append(", RSSI=").append(String.format("%.1f", rssi)).append("dBm");
        sb.append(", antena=").append(antennaPort);
        sb.append(", lecturas=").append(readCount);
        sb.append(", tiempo=").append(getFormattedTimestamp());
        if (phaseAngle != null) {
            sb.append(", fase=").append(String.format("%.1f°", phaseAngle));
        }
        sb.append("]");
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TagData other = (TagData) obj;
        return epc != null && epc.equals(other.epc);
    }
    
    @Override
    public int hashCode() {
        return epc != null ? epc.hashCode() : 0;
    }
}
