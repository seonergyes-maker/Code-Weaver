import java.io.*;
import java.nio.file.*;
import java.util.Arrays;

/**
 * Configuración principal del sistema RFID para el lector Zebra FX7500.
 * Gestiona la configuración de conexión, antenas, modo de operación y
 * proporciona persistencia JSON de la configuración.
 * 
 * @author Sistema RFID
 * @version 1.0
 */
public class RFIDConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /** Puerto LLRP estándar */
    public static final int DEFAULT_LLRP_PORT = 5084;
    
    /** Número máximo de puertos de antena */
    public static final int MAX_ANTENNA_PORTS = 4;
    
    /** Timeout de caché de etiquetas por defecto en milisegundos */
    public static final long DEFAULT_TAG_CACHE_TIMEOUT = 5000;
    
    /**
     * Modo de operación del lector RFID.
     */
    public enum OperationMode {
        /** Lectura continua de etiquetas */
        CONTINUOUS,
        /** Lectura bajo demanda (trigger) */
        ON_DEMAND,
        /** Modo de inventario periódico */
        PERIODIC
    }
    
    /** Dirección IP del lector RFID */
    private String readerIP;
    
    /** Puerto de conexión LLRP */
    private int readerPort;
    
    /** Configuración de antenas (índice 0-3 para puertos 1-4) */
    private AntennaConfig[] antennaConfigs;
    
    /** Modo de operación actual */
    private OperationMode operationMode;
    
    /** Timeout de caché de etiquetas en milisegundos */
    private long tagCacheTimeout;
    
    /** Endpoint de API para envío de datos */
    private String apiEndpoint;
    
    /** Clave de API para autenticación */
    private String apiKey;
    
    /** Identificador de trabajo para la API (campo configurable) */
    private String apiTrabajo;
    
    /** Configuración de GPO (General Purpose Output) - 4 puertos */
    private boolean[] gpoSettings;
    
    /** Intervalo de keep-alive en segundos */
    private int keepAliveInterval;
    
    /** Timeout de conexión en milisegundos */
    private int connectionTimeout;
    
    /** Habilitar reconexión automática */
    private boolean autoReconnect;
    
    /** Intervalo de reintentos de conexión en milisegundos */
    private int reconnectInterval;
    
    /** ID del ROSpec para operaciones */
    private int roSpecId;
    
    /** Región regulatoria */
    private String regulatoryRegion;
    
    /** Habilitar filtro de duplicados */
    private boolean duplicateFilterEnabled;
    
    /** Tiempo de expiración del filtro de duplicados en segundos */
    private int duplicateFilterExpiration;
    
    // ==================== Configuración Avanzada Zebra ====================
    
    /** Umbral RSSI mínimo en dBm (-80 a 0). Tags con RSSI menor se ignoran. */
    private int rssiThreshold;
    
    /** Habilitar filtrado por RSSI */
    private boolean rssiFilterEnabled;
    
    /** Sesión de inventario C1G2 (0-3). Sesión 2 es común para múltiples lectores. */
    private int inventorySession;
    
    /** Target de inventario (0=A, 1=B, 2=AB alternado) */
    private int inventoryTarget;
    
    /** Estimación de población de tags (afecta algoritmo Q) */
    private int tagPopulation;
    
    /** Índice de modo RF para Dense Reader Mode (0-50, depende del firmware) */
    private int rfModeIndex;
    
    /** Habilitar Dense Reader Mode (reduce interferencia entre lectores) */
    private boolean denseReaderMode;
    
    /** Puerto GPI que dispara inicio de lectura (0 = deshabilitado) */
    private int gpiTriggerPort;
    
    /** Habilitar trigger por GPI */
    private boolean gpiTriggerEnabled;
    
    /** Estado del GPI que activa lectura (true=HIGH, false=LOW) */
    private boolean gpiTriggerState;
    
    /** Tiempo mínimo entre reportes de tags en ms */
    private int reportIntervalMs;
    
    /** Incluir timestamp en reportes */
    private boolean reportTimestamp;
    
    /** Incluir datos de antena en reportes */
    private boolean reportAntennaId;
    
    /** Incluir phase angle en reportes (para localización) */
    private boolean reportPhaseAngle;
    
    /** Incluir frecuencia de canal en reportes */
    private boolean reportChannelIndex;
    
    /** Auto-conexión al iniciar la aplicación */
    private boolean autoConnectEnabled;
    
    /** Mostrar EPC en formato hexadecimal (true) o decimal (false) */
    private boolean displayHexMode;
    
    /** Última conexión exitosa (para auto-conexión) */
    private String lastConnectedIP;
    
    /**
     * Constructor por defecto con valores predeterminados.
     */
    public RFIDConfig() {
        this.readerIP = "192.168.1.100";
        this.readerPort = DEFAULT_LLRP_PORT;
        this.operationMode = OperationMode.CONTINUOUS;
        this.tagCacheTimeout = DEFAULT_TAG_CACHE_TIMEOUT;
        this.keepAliveInterval = 10;
        this.connectionTimeout = 5000;
        this.autoReconnect = true;
        this.reconnectInterval = 3000;
        this.roSpecId = 1;
        this.regulatoryRegion = "FCC";
        
        this.antennaConfigs = new AntennaConfig[MAX_ANTENNA_PORTS];
        for (int i = 0; i < MAX_ANTENNA_PORTS; i++) {
            this.antennaConfigs[i] = new AntennaConfig(i + 1);
            // Por defecto solo habilitar antenas 1 y 2 (las más comunes)
            // Las antenas no conectadas físicamente causan que el ROSpec falle
            if (i >= 2) {
                this.antennaConfigs[i].setEnabled(false);
            }
        }
        
        this.gpoSettings = new boolean[4];
        Arrays.fill(this.gpoSettings, false);
        
        this.duplicateFilterEnabled = true;
        this.duplicateFilterExpiration = 5;
        
        // Configuración avanzada Zebra - valores por defecto
        this.rssiThreshold = -70;           // -70 dBm es un buen umbral por defecto
        this.rssiFilterEnabled = false;     // Deshabilitado por defecto
        this.inventorySession = 2;          // Sesión 2 recomendada para múltiples lectores
        this.inventoryTarget = 0;           // Target A
        this.tagPopulation = 32;            // Estimación conservadora
        this.rfModeIndex = 0;               // Modo estándar
        this.denseReaderMode = false;       // DRM deshabilitado por defecto
        this.gpiTriggerPort = 0;            // Sin trigger
        this.gpiTriggerEnabled = false;
        this.gpiTriggerState = true;        // Trigger en HIGH
        this.reportIntervalMs = 0;          // Sin throttling
        this.reportTimestamp = true;
        this.reportAntennaId = true;
        this.reportPhaseAngle = false;
        this.reportChannelIndex = false;
        this.autoConnectEnabled = false;    // Deshabilitado por defecto
        this.displayHexMode = true;          // Hexadecimal por defecto
        this.lastConnectedIP = null;
    }
    
    /**
     * Constructor con IP del lector.
     * 
     * @param readerIP Dirección IP del lector
     */
    public RFIDConfig(String readerIP) {
        this();
        setReaderIP(readerIP);
    }
    
    /**
     * Constructor con IP y puerto del lector.
     * 
     * @param readerIP Dirección IP del lector
     * @param readerPort Puerto LLRP
     */
    public RFIDConfig(String readerIP, int readerPort) {
        this(readerIP);
        setReaderPort(readerPort);
    }
    
    // ==================== Getters y Setters ====================
    
    public String getReaderIP() {
        return readerIP;
    }
    
    public void setReaderIP(String readerIP) {
        if (readerIP == null || readerIP.trim().isEmpty()) {
            throw new IllegalArgumentException("La dirección IP no puede ser nula o vacía");
        }
        this.readerIP = readerIP.trim();
    }
    
    public int getReaderPort() {
        return readerPort;
    }
    
    public void setReaderPort(int readerPort) {
        if (readerPort < 1 || readerPort > 65535) {
            throw new IllegalArgumentException("Puerto inválido: " + readerPort);
        }
        this.readerPort = readerPort;
    }
    
    public AntennaConfig[] getAntennaConfigs() {
        return antennaConfigs;
    }
    
    public AntennaConfig getAntennaConfig(int port) {
        if (port < 1 || port > MAX_ANTENNA_PORTS) {
            throw new IllegalArgumentException("Puerto de antena inválido: " + port);
        }
        return antennaConfigs[port - 1];
    }
    
    public void setAntennaConfig(int port, AntennaConfig config) {
        if (port < 1 || port > MAX_ANTENNA_PORTS) {
            throw new IllegalArgumentException("Puerto de antena inválido: " + port);
        }
        if (config == null) {
            throw new IllegalArgumentException("La configuración de antena no puede ser nula");
        }
        config.setAntennaPort(port);
        antennaConfigs[port - 1] = config;
    }
    
    public OperationMode getOperationMode() {
        return operationMode;
    }
    
    public void setOperationMode(OperationMode operationMode) {
        this.operationMode = operationMode != null ? operationMode : OperationMode.CONTINUOUS;
    }
    
    public long getTagCacheTimeout() {
        return tagCacheTimeout;
    }
    
    public void setTagCacheTimeout(long tagCacheTimeout) {
        this.tagCacheTimeout = Math.max(100, tagCacheTimeout);
    }
    
    public String getApiEndpoint() {
        return apiEndpoint;
    }
    
    public void setApiEndpoint(String apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
    }
    
    public String getApiKey() {
        return apiKey;
    }
    
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    public String getApiTrabajo() {
        return apiTrabajo;
    }
    
    public void setApiTrabajo(String apiTrabajo) {
        this.apiTrabajo = apiTrabajo;
    }
    
    public boolean[] getGpoSettings() {
        return gpoSettings;
    }
    
    public boolean getGpo(int port) {
        if (port < 1 || port > 4) {
            throw new IllegalArgumentException("Puerto GPO inválido: " + port);
        }
        return gpoSettings[port - 1];
    }
    
    public void setGpo(int port, boolean state) {
        if (port < 1 || port > 4) {
            throw new IllegalArgumentException("Puerto GPO inválido: " + port);
        }
        gpoSettings[port - 1] = state;
    }
    
    public int getKeepAliveInterval() {
        return keepAliveInterval;
    }
    
    public void setKeepAliveInterval(int keepAliveInterval) {
        this.keepAliveInterval = Math.max(1, keepAliveInterval);
    }
    
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = Math.max(1000, connectionTimeout);
    }
    
    public boolean isAutoReconnect() {
        return autoReconnect;
    }
    
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }
    
    public int getReconnectInterval() {
        return reconnectInterval;
    }
    
    public void setReconnectInterval(int reconnectInterval) {
        this.reconnectInterval = Math.max(1000, reconnectInterval);
    }
    
    public int getRoSpecId() {
        return roSpecId;
    }
    
    public void setRoSpecId(int roSpecId) {
        this.roSpecId = Math.max(1, roSpecId);
    }
    
    public String getRegulatoryRegion() {
        return regulatoryRegion;
    }
    
    public void setRegulatoryRegion(String regulatoryRegion) {
        this.regulatoryRegion = regulatoryRegion;
    }
    
    public boolean isDuplicateFilterEnabled() {
        return duplicateFilterEnabled;
    }
    
    public void setDuplicateFilterEnabled(boolean enabled) {
        this.duplicateFilterEnabled = enabled;
    }
    
    public int getDuplicateFilterExpiration() {
        return duplicateFilterExpiration;
    }
    
    public void setDuplicateFilterExpiration(int seconds) {
        if (seconds < 1) seconds = 1;
        this.duplicateFilterExpiration = seconds;
    }
    
    // ==================== Getters/Setters Configuración Avanzada ====================
    
    public int getRssiThreshold() {
        return rssiThreshold;
    }
    
    public void setRssiThreshold(int rssiThreshold) {
        if (rssiThreshold < -80) rssiThreshold = -80;
        if (rssiThreshold > 0) rssiThreshold = 0;
        this.rssiThreshold = rssiThreshold;
    }
    
    public boolean isRssiFilterEnabled() {
        return rssiFilterEnabled;
    }
    
    public void setRssiFilterEnabled(boolean enabled) {
        this.rssiFilterEnabled = enabled;
    }
    
    public int getInventorySession() {
        return inventorySession;
    }
    
    public void setInventorySession(int session) {
        if (session < 0) session = 0;
        if (session > 3) session = 3;
        this.inventorySession = session;
    }
    
    public int getInventoryTarget() {
        return inventoryTarget;
    }
    
    public void setInventoryTarget(int target) {
        if (target < 0) target = 0;
        if (target > 2) target = 2;
        this.inventoryTarget = target;
    }
    
    public int getTagPopulation() {
        return tagPopulation;
    }
    
    public void setTagPopulation(int population) {
        if (population < 1) population = 1;
        if (population > 1000) population = 1000;
        this.tagPopulation = population;
    }
    
    public int getRfModeIndex() {
        return rfModeIndex;
    }
    
    public void setRfModeIndex(int index) {
        if (index < 0) index = 0;
        if (index > 50) index = 50;
        this.rfModeIndex = index;
    }
    
    public boolean isDenseReaderMode() {
        return denseReaderMode;
    }
    
    public void setDenseReaderMode(boolean enabled) {
        this.denseReaderMode = enabled;
    }
    
    public int getGpiTriggerPort() {
        return gpiTriggerPort;
    }
    
    public void setGpiTriggerPort(int port) {
        if (port < 0) port = 0;
        if (port > 4) port = 4;
        this.gpiTriggerPort = port;
    }
    
    public boolean isGpiTriggerEnabled() {
        return gpiTriggerEnabled;
    }
    
    public void setGpiTriggerEnabled(boolean enabled) {
        this.gpiTriggerEnabled = enabled;
    }
    
    public boolean isGpiTriggerState() {
        return gpiTriggerState;
    }
    
    public void setGpiTriggerState(boolean state) {
        this.gpiTriggerState = state;
    }
    
    public int getReportIntervalMs() {
        return reportIntervalMs;
    }
    
    public void setReportIntervalMs(int intervalMs) {
        if (intervalMs < 0) intervalMs = 0;
        this.reportIntervalMs = intervalMs;
    }
    
    public boolean isReportTimestamp() {
        return reportTimestamp;
    }
    
    public void setReportTimestamp(boolean report) {
        this.reportTimestamp = report;
    }
    
    public boolean isReportAntennaId() {
        return reportAntennaId;
    }
    
    public void setReportAntennaId(boolean report) {
        this.reportAntennaId = report;
    }
    
    public boolean isReportPhaseAngle() {
        return reportPhaseAngle;
    }
    
    public void setReportPhaseAngle(boolean report) {
        this.reportPhaseAngle = report;
    }
    
    public boolean isReportChannelIndex() {
        return reportChannelIndex;
    }
    
    public void setReportChannelIndex(boolean report) {
        this.reportChannelIndex = report;
    }
    
    public boolean isAutoConnectEnabled() {
        return autoConnectEnabled;
    }
    
    public void setAutoConnectEnabled(boolean enabled) {
        this.autoConnectEnabled = enabled;
    }
    
    public boolean isDisplayHexMode() {
        return displayHexMode;
    }
    
    public void setDisplayHexMode(boolean hexMode) {
        this.displayHexMode = hexMode;
    }
    
    public String getLastConnectedIP() {
        return lastConnectedIP;
    }
    
    public void setLastConnectedIP(String ip) {
        this.lastConnectedIP = ip;
    }
    
    // ==================== Métodos de utilidad ====================
    
    /**
     * Obtiene el número de antenas habilitadas.
     * 
     * @return Cantidad de antenas habilitadas
     */
    public int getEnabledAntennaCount() {
        int count = 0;
        for (AntennaConfig config : antennaConfigs) {
            if (config.isEnabled()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Obtiene los puertos de antenas habilitadas.
     * 
     * @return Array con los números de puerto de antenas habilitadas
     */
    public int[] getEnabledAntennaPorts() {
        int count = getEnabledAntennaCount();
        int[] ports = new int[count];
        int idx = 0;
        for (AntennaConfig config : antennaConfigs) {
            if (config.isEnabled()) {
                ports[idx++] = config.getAntennaPort();
            }
        }
        return ports;
    }
    
    /**
     * Valida la configuración completa.
     * 
     * @return true si la configuración es válida
     */
    public boolean isValid() {
        if (readerIP == null || readerIP.isEmpty()) {
            return false;
        }
        if (readerPort < 1 || readerPort > 65535) {
            return false;
        }
        if (getEnabledAntennaCount() == 0) {
            return false;
        }
        
        boolean regulatoryValid = true;
        for (AntennaConfig config : antennaConfigs) {
            if (config.isEnabled()) {
                if ("FCC".equals(regulatoryRegion) && !config.validateFCC()) {
                    regulatoryValid = false;
                    break;
                }
                if ("ETSI".equals(regulatoryRegion) && !config.validateETSI()) {
                    regulatoryValid = false;
                    break;
                }
            }
        }
        
        return regulatoryValid;
    }
    
    /**
     * Crea una copia de esta configuración.
     * 
     * @return Nueva instancia con los mismos valores
     */
    public RFIDConfig copy() {
        RFIDConfig copy = new RFIDConfig();
        copy.readerIP = this.readerIP;
        copy.readerPort = this.readerPort;
        copy.operationMode = this.operationMode;
        copy.tagCacheTimeout = this.tagCacheTimeout;
        copy.apiEndpoint = this.apiEndpoint;
        copy.apiKey = this.apiKey;
        copy.apiTrabajo = this.apiTrabajo;
        copy.keepAliveInterval = this.keepAliveInterval;
        copy.connectionTimeout = this.connectionTimeout;
        copy.autoReconnect = this.autoReconnect;
        copy.reconnectInterval = this.reconnectInterval;
        copy.roSpecId = this.roSpecId;
        copy.regulatoryRegion = this.regulatoryRegion;
        
        for (int i = 0; i < MAX_ANTENNA_PORTS; i++) {
            copy.antennaConfigs[i] = this.antennaConfigs[i].copy();
        }
        
        copy.gpoSettings = Arrays.copyOf(this.gpoSettings, this.gpoSettings.length);
        
        return copy;
    }
    
    // ==================== Persistencia JSON ====================
    
    /**
     * Serializa la configuración a formato JSON.
     * 
     * @return String con la configuración en formato JSON
     */
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"readerIP\": \"").append(escapeJson(readerIP)).append("\",\n");
        sb.append("  \"readerPort\": ").append(readerPort).append(",\n");
        sb.append("  \"operationMode\": \"").append(operationMode.name()).append("\",\n");
        sb.append("  \"tagCacheTimeout\": ").append(tagCacheTimeout).append(",\n");
        sb.append("  \"keepAliveInterval\": ").append(keepAliveInterval).append(",\n");
        sb.append("  \"connectionTimeout\": ").append(connectionTimeout).append(",\n");
        sb.append("  \"autoReconnect\": ").append(autoReconnect).append(",\n");
        sb.append("  \"reconnectInterval\": ").append(reconnectInterval).append(",\n");
        sb.append("  \"roSpecId\": ").append(roSpecId).append(",\n");
        sb.append("  \"regulatoryRegion\": \"").append(escapeJson(regulatoryRegion)).append("\",\n");
        
        if (apiEndpoint != null) {
            sb.append("  \"apiEndpoint\": \"").append(escapeJson(apiEndpoint)).append("\",\n");
        }
        if (apiKey != null) {
            sb.append("  \"apiKey\": \"").append(escapeJson(apiKey)).append("\",\n");
        }
        if (apiTrabajo != null) {
            sb.append("  \"apiTrabajo\": \"").append(escapeJson(apiTrabajo)).append("\",\n");
        }
        
        sb.append("  \"antennaConfigs\": [\n");
        for (int i = 0; i < antennaConfigs.length; i++) {
            AntennaConfig ac = antennaConfigs[i];
            sb.append("    {\n");
            sb.append("      \"antennaPort\": ").append(ac.getAntennaPort()).append(",\n");
            sb.append("      \"transmitPower\": ").append(ac.getTransmitPower()).append(",\n");
            sb.append("      \"receiveSensitivity\": ").append(ac.getReceiveSensitivity()).append(",\n");
            sb.append("      \"enabled\": ").append(ac.isEnabled()).append(",\n");
            sb.append("      \"cableLoss\": ").append(ac.getCableLoss()).append(",\n");
            sb.append("      \"antennaGain\": ").append(ac.getAntennaGain()).append("\n");
            sb.append("    }");
            if (i < antennaConfigs.length - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        
        sb.append("  \"gpoSettings\": [");
        for (int i = 0; i < gpoSettings.length; i++) {
            sb.append(gpoSettings[i]);
            if (i < gpoSettings.length - 1) sb.append(", ");
        }
        sb.append("],\n");
        
        // Configuración avanzada
        sb.append("  \"duplicateFilterEnabled\": ").append(duplicateFilterEnabled).append(",\n");
        sb.append("  \"duplicateFilterExpiration\": ").append(duplicateFilterExpiration).append(",\n");
        sb.append("  \"rssiThreshold\": ").append(rssiThreshold).append(",\n");
        sb.append("  \"rssiFilterEnabled\": ").append(rssiFilterEnabled).append(",\n");
        sb.append("  \"inventorySession\": ").append(inventorySession).append(",\n");
        sb.append("  \"inventoryTarget\": ").append(inventoryTarget).append(",\n");
        sb.append("  \"tagPopulation\": ").append(tagPopulation).append(",\n");
        sb.append("  \"rfModeIndex\": ").append(rfModeIndex).append(",\n");
        sb.append("  \"denseReaderMode\": ").append(denseReaderMode).append(",\n");
        sb.append("  \"gpiTriggerPort\": ").append(gpiTriggerPort).append(",\n");
        sb.append("  \"gpiTriggerEnabled\": ").append(gpiTriggerEnabled).append(",\n");
        sb.append("  \"gpiTriggerState\": ").append(gpiTriggerState).append(",\n");
        sb.append("  \"reportIntervalMs\": ").append(reportIntervalMs).append(",\n");
        sb.append("  \"reportTimestamp\": ").append(reportTimestamp).append(",\n");
        sb.append("  \"reportAntennaId\": ").append(reportAntennaId).append(",\n");
        sb.append("  \"reportPhaseAngle\": ").append(reportPhaseAngle).append(",\n");
        sb.append("  \"reportChannelIndex\": ").append(reportChannelIndex).append(",\n");
        sb.append("  \"autoConnectEnabled\": ").append(autoConnectEnabled).append(",\n");
        sb.append("  \"displayHexMode\": ").append(displayHexMode).append("\n");
        
        sb.append("}");
        return sb.toString();
    }
    
    /**
     * Carga la configuración desde un String JSON.
     * 
     * @param json String con la configuración en formato JSON
     * @return Nueva instancia de RFIDConfig con los valores cargados
     */
    public static RFIDConfig fromJson(String json) {
        RFIDConfig config = new RFIDConfig();
        
        if (json == null || json.trim().isEmpty()) {
            return config;
        }
        
        config.readerIP = extractStringValue(json, "readerIP", config.readerIP);
        config.readerPort = extractIntValue(json, "readerPort", config.readerPort);
        config.tagCacheTimeout = extractLongValue(json, "tagCacheTimeout", config.tagCacheTimeout);
        config.keepAliveInterval = extractIntValue(json, "keepAliveInterval", config.keepAliveInterval);
        config.connectionTimeout = extractIntValue(json, "connectionTimeout", config.connectionTimeout);
        config.autoReconnect = extractBooleanValue(json, "autoReconnect", config.autoReconnect);
        config.reconnectInterval = extractIntValue(json, "reconnectInterval", config.reconnectInterval);
        config.roSpecId = extractIntValue(json, "roSpecId", config.roSpecId);
        config.regulatoryRegion = extractStringValue(json, "regulatoryRegion", config.regulatoryRegion);
        config.apiEndpoint = extractStringValue(json, "apiEndpoint", null);
        config.apiKey = extractStringValue(json, "apiKey", null);
        config.apiTrabajo = extractStringValue(json, "apiTrabajo", null);
        
        String modeStr = extractStringValue(json, "operationMode", "CONTINUOUS");
        try {
            config.operationMode = OperationMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            config.operationMode = OperationMode.CONTINUOUS;
        }
        
        int antennaStart = json.indexOf("\"antennaConfigs\"");
        if (antennaStart >= 0) {
            int arrayStart = json.indexOf("[", antennaStart);
            int arrayEnd = json.indexOf("]", arrayStart);
            if (arrayStart >= 0 && arrayEnd > arrayStart) {
                String antennaArrayStr = json.substring(arrayStart + 1, arrayEnd);
                String[] antennaObjs = antennaArrayStr.split("\\}\\s*,\\s*\\{");
                
                for (int i = 0; i < Math.min(antennaObjs.length, MAX_ANTENNA_PORTS); i++) {
                    String obj = antennaObjs[i];
                    AntennaConfig ac = config.antennaConfigs[i];
                    ac.setTransmitPower(extractDoubleValue(obj, "transmitPower", ac.getTransmitPower()));
                    ac.setReceiveSensitivity(extractIntValue(obj, "receiveSensitivity", ac.getReceiveSensitivity()));
                    ac.setEnabled(extractBooleanValue(obj, "enabled", ac.isEnabled()));
                    ac.setCableLoss(extractDoubleValue(obj, "cableLoss", ac.getCableLoss()));
                    ac.setAntennaGain(extractDoubleValue(obj, "antennaGain", ac.getAntennaGain()));
                }
            }
        }
        
        int gpoStart = json.indexOf("\"gpoSettings\"");
        if (gpoStart >= 0) {
            int arrayStart = json.indexOf("[", gpoStart);
            int arrayEnd = json.indexOf("]", arrayStart);
            if (arrayStart >= 0 && arrayEnd > arrayStart) {
                String gpoArrayStr = json.substring(arrayStart + 1, arrayEnd);
                String[] values = gpoArrayStr.split(",");
                for (int i = 0; i < Math.min(values.length, 4); i++) {
                    config.gpoSettings[i] = values[i].trim().equalsIgnoreCase("true");
                }
            }
        }
        
        // Configuración avanzada
        config.duplicateFilterEnabled = extractBooleanValue(json, "duplicateFilterEnabled", config.duplicateFilterEnabled);
        config.duplicateFilterExpiration = extractIntValue(json, "duplicateFilterExpiration", config.duplicateFilterExpiration);
        config.rssiThreshold = extractIntValue(json, "rssiThreshold", config.rssiThreshold);
        config.rssiFilterEnabled = extractBooleanValue(json, "rssiFilterEnabled", config.rssiFilterEnabled);
        config.inventorySession = extractIntValue(json, "inventorySession", config.inventorySession);
        config.inventoryTarget = extractIntValue(json, "inventoryTarget", config.inventoryTarget);
        config.tagPopulation = extractIntValue(json, "tagPopulation", config.tagPopulation);
        config.rfModeIndex = extractIntValue(json, "rfModeIndex", config.rfModeIndex);
        config.denseReaderMode = extractBooleanValue(json, "denseReaderMode", config.denseReaderMode);
        config.gpiTriggerPort = extractIntValue(json, "gpiTriggerPort", config.gpiTriggerPort);
        config.gpiTriggerEnabled = extractBooleanValue(json, "gpiTriggerEnabled", config.gpiTriggerEnabled);
        config.gpiTriggerState = extractBooleanValue(json, "gpiTriggerState", config.gpiTriggerState);
        config.reportIntervalMs = extractIntValue(json, "reportIntervalMs", config.reportIntervalMs);
        config.reportTimestamp = extractBooleanValue(json, "reportTimestamp", config.reportTimestamp);
        config.reportAntennaId = extractBooleanValue(json, "reportAntennaId", config.reportAntennaId);
        config.reportPhaseAngle = extractBooleanValue(json, "reportPhaseAngle", config.reportPhaseAngle);
        config.reportChannelIndex = extractBooleanValue(json, "reportChannelIndex", config.reportChannelIndex);
        config.autoConnectEnabled = extractBooleanValue(json, "autoConnectEnabled", config.autoConnectEnabled);
        config.displayHexMode = extractBooleanValue(json, "displayHexMode", config.displayHexMode);
        
        return config;
    }
    
    /**
     * Guarda la configuración en un archivo.
     * 
     * @param filePath Ruta del archivo
     * @throws IOException Si ocurre un error de escritura
     */
    public void saveToFile(String filePath) throws IOException {
        Files.writeString(Path.of(filePath), toJson());
    }
    
    /**
     * Carga la configuración desde un archivo.
     * 
     * @param filePath Ruta del archivo
     * @return Configuración cargada
     * @throws IOException Si ocurre un error de lectura
     */
    public static RFIDConfig loadFromFile(String filePath) throws IOException {
        String json = Files.readString(Path.of(filePath));
        return fromJson(json);
    }
    
    // ==================== Métodos auxiliares JSON ====================
    
    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
    
    private static String extractStringValue(String json, String key, String defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return defaultValue;
    }
    
    private static int extractIntValue(String json, String key, int defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private static long extractLongValue(String json, String key, long defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private static double extractDoubleValue(String json, String key, double defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+\\.?\\d*)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    private static boolean extractBooleanValue(String json, String key, boolean defaultValue) {
        String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern, 
            java.util.regex.Pattern.CASE_INSENSITIVE).matcher(json);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return defaultValue;
    }
    
    @Override
    public String toString() {
        return String.format(
            "RFIDConfig[ip=%s, puerto=%d, modo=%s, antenas=%d habilitadas]",
            readerIP, readerPort, operationMode, getEnabledAntennaCount());
    }
}
