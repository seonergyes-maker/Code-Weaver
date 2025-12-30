import com.mot.rfid.api3.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Conexión al lector RFID Zebra FX7500/FX9600 usando SDK oficial.
 * Reemplaza la conexión LLRP por el SDK nativo de Zebra.
 */
public class ZebraSDKConnection implements RfidEventsListener {
    
    private RFIDReader reader;
    private RFIDConfig config;
    private volatile boolean connected = false;
    private volatile boolean reading = false;
    
    private Consumer<TagData> tagCallback;
    private Consumer<String> statusCallback;
    private Consumer<String> errorCallback;
    
    private ReaderCapabilities capabilities;
    private int maxPowerIndex = 100;
    
    private final ConcurrentLinkedQueue<TagData> tagQueue = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService executor;
    
    public ZebraSDKConnection(RFIDConfig config) {
        this.config = config;
    }
    
    public void setTagCallback(Consumer<TagData> callback) {
        this.tagCallback = callback;
    }
    
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }
    
    public void setErrorCallback(Consumer<String> callback) {
        this.errorCallback = callback;
    }
    
    private void notifyStatus(String message) {
        System.out.println("[ZebraSDK] " + message);
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }
    
    private void notifyError(String message) {
        System.err.println("[ZebraSDK] ERROR: " + message);
        if (errorCallback != null) {
            errorCallback.accept(message);
        }
    }
    
    public boolean connect() {
        if (connected) {
            notifyStatus("Ya está conectado");
            return true;
        }
        
        try {
            String ip = config.getReaderIP();
            int port = config.getReaderPort();
            
            notifyStatus("Conectando a " + ip + ":" + port + "...");
            
            reader = new RFIDReader(ip, port, 0);
            reader.connect();
            
            connected = true;
            notifyStatus("Conectado!");
            
            capabilities = reader.ReaderCapabilities;
            notifyStatus("Modelo: " + capabilities.getModelName());
            notifyStatus("Firmware: " + capabilities.getFirwareVersion());
            notifyStatus("Antenas: " + capabilities.getNumAntennaSupported());
            
            int[] powerLevels = capabilities.getTransmitPowerLevelValues();
            if (powerLevels != null && powerLevels.length > 0) {
                maxPowerIndex = powerLevels.length - 1;
            }
            
            configureEvents();
            configureAntennas();
            
            return true;
            
        } catch (InvalidUsageException e) {
            notifyError("Error de uso: " + e.getInfo());
            connected = false;
            return false;
        } catch (OperationFailureException e) {
            notifyError("Error de operación: " + e.getVendorMessage());
            connected = false;
            return false;
        } catch (Exception e) {
            notifyError("Error de conexión: " + e.getMessage());
            connected = false;
            return false;
        }
    }
    
    private void configureEvents() throws InvalidUsageException, OperationFailureException {
        reader.Events.addEventsListener(this);
        reader.Events.setInventoryStartEvent(true);
        reader.Events.setInventoryStopEvent(true);
        reader.Events.setTagReadEvent(true);
        reader.Events.setAttachTagDataWithReadEvent(false);
        reader.Events.setReaderDisconnectEvent(true);
        notifyStatus("Eventos configurados");
    }
    
    private void configureAntennas() {
        try {
            for (int ant = 1; ant <= 4; ant++) {
                AntennaConfig antConfig = config.getAntennaConfig(ant);
                if (antConfig != null && antConfig.isEnabled()) {
                    int powerIndex = antConfig.getPowerIndex();
                    if (powerIndex > maxPowerIndex) {
                        powerIndex = maxPowerIndex;
                    }
                    
                    Antennas.AntennaRfConfig rfConfig = reader.Config.Antennas.getAntennaRfConfig(ant);
                    rfConfig.setTransmitPowerIndex(powerIndex);
                    rfConfig.setrfModeTableIndex(0);
                    rfConfig.setTari(0);
                    reader.Config.Antennas.setAntennaRfConfig(ant, rfConfig);
                    
                    notifyStatus("Antena " + ant + " configurada: potencia indice " + powerIndex);
                }
            }
            
            // Configurar Cable Loss para todas las antenas habilitadas
            configureCableLoss();
            
            Antennas.SingulationControl singulation = reader.Config.Antennas.getSingulationControl(1);
            
            SESSION session = SESSION.SESSION_S0;
            switch (config.getInventorySession()) {
                case 0: session = SESSION.SESSION_S0; break;
                case 1: session = SESSION.SESSION_S1; break;
                case 2: session = SESSION.SESSION_S2; break;
                case 3: session = SESSION.SESSION_S3; break;
            }
            singulation.setSession(session);
            singulation.setTagPopulation((short) config.getTagPopulation());
            singulation.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A);
            singulation.Action.setSLFlag(SL_FLAG.SL_ALL);
            reader.Config.Antennas.setSingulationControl(1, singulation);
            
            notifyStatus("Singulación configurada: Sesión S" + config.getInventorySession());
            
        } catch (Exception e) {
            notifyError("Error configurando antenas: " + e.getMessage());
        }
    }
    
    /**
     * Configura la compensacion de perdida de cable para cada antena.
     * NOTA: El SDK de Zebra FX7500 no expone metodos directos para cable loss.
     * La compensacion se aplica ajustando la potencia de transmision manualmente.
     * Este metodo solo registra los valores configurados para referencia.
     */
    private void configureCableLoss() {
        try {
            for (int ant = 1; ant <= 4; ant++) {
                AntennaConfig antConfig = config.getAntennaConfig(ant);
                if (antConfig != null && antConfig.isEnabled()) {
                    double cableLossDb = antConfig.getCableLoss();
                    if (cableLossDb > 0) {
                        notifyStatus("Antena " + ant + " cable loss configurado: " + cableLossDb + " dB (referencia)");
                    }
                }
            }
        } catch (Exception e) {
            notifyError("Error en cable loss: " + e.getMessage());
        }
    }
    
    /**
     * Lee la configuracion actual de antenas desde el lector FX7500.
     * Actualiza los valores en RFIDConfig con la configuracion real del lector.
     * 
     * @return true si se leyo correctamente
     */
    public boolean readAntennaConfigFromReader() {
        if (!connected || reader == null) {
            notifyError("No conectado al lector");
            return false;
        }
        
        try {
            notifyStatus("Leyendo configuracion de antenas del lector...");
            
            int[] powerLevels = capabilities.getTransmitPowerLevelValues();
            System.out.println("[ZebraSDK] Power levels disponibles: " + 
                (powerLevels != null ? powerLevels.length : 0) + " niveles");
            
            // Obtener numero de antenas soportadas
            try {
                int numAntennas = capabilities.getNumAntennaSupported();
                System.out.println("[ZebraSDK] Antenas soportadas por el lector: " + numAntennas);
            } catch (Exception e) {
                System.out.println("[ZebraSDK] No se pudo obtener numero de antenas: " + e.getMessage());
            }
            
            for (int ant = 1; ant <= 4; ant++) {
                AntennaConfig antConfig = config.getAntennaConfig(ant);
                if (antConfig == null) continue;
                
                try {
                    // Primero verificar si la antena esta fisicamente conectada
                    boolean isConnected = false;
                    try {
                        Antennas.AntennaStatus status = reader.Config.Antennas.getAntennaStatus((short) ant);
                        if (status != null) {
                            isConnected = true; // Si no lanza excepcion, esta conectada
                            System.out.println("[ZebraSDK] Antena " + ant + " estado obtenido OK");
                        }
                    } catch (Exception statusEx) {
                        // Si falla getAntennaStatus, intentar leer la config RF
                        // Si eso funciona, asumimos que esta conectada
                        System.out.println("[ZebraSDK] Antena " + ant + " getAntennaStatus: " + statusEx.getMessage());
                    }
                    
                    Antennas.AntennaRfConfig rfConfig = reader.Config.Antennas.getAntennaRfConfig(ant);
                    int powerIndex = rfConfig.getTransmitPowerIndex();
                    
                    // Si llegamos aqui, la antena responde - esta conectada
                    isConnected = true;
                    antConfig.setPhysicallyConnected(true);
                    
                    System.out.println("[ZebraSDK] Antena " + ant + " powerIndex: " + powerIndex + " (conectada)");
                    
                    // Convertir indice a dBm
                    // powerLevels contiene valores en centesimas de dBm (ej: 2510 = 25.10 dBm)
                    double powerDbm = 10.0; // Minimo por defecto
                    if (powerLevels != null && powerIndex >= 0 && powerIndex < powerLevels.length) {
                        powerDbm = powerLevels[powerIndex] / 100.0; // Centesimas de dBm a dBm
                    } else if (powerIndex >= 0) {
                        // Aproximacion: cada indice es 0.1 dBm desde 10 dBm
                        powerDbm = 10.0 + (powerIndex * 0.1);
                    }
                    System.out.println("[ZebraSDK] Antena " + ant + " potencia calculada: " + powerDbm + " dBm");
                    
                    antConfig.setTransmitPower(powerDbm);
                    antConfig.setEnabled(true);
                    notifyStatus("Antena " + ant + " potencia: " + String.format("%.1f", powerDbm) + " dBm (conectada)");
                    
                } catch (Exception e) {
                    // Error al leer config RF = antena no conectada fisicamente
                    antConfig.setPhysicallyConnected(false);
                    antConfig.setEnabled(false);
                    System.out.println("[ZebraSDK] Antena " + ant + " no conectada: " + e.getMessage());
                    notifyStatus("Antena " + ant + " no conectada");
                }
            }
            
            // NOTA: El SDK de Zebra no expone metodos para leer cable loss directamente
            // Los valores de cable loss se mantienen en la configuracion local
            
            notifyStatus("Configuracion de antenas leida del lector");
            return true;
            
        } catch (Exception e) {
            notifyError("Error leyendo configuracion: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Aplica la configuracion de antenas actual al lector FX7500.
     * Convierte los valores dBm a indices y los envia al lector.
     * 
     * @return true si se aplico correctamente
     */
    public boolean applyAntennaConfigToReader() {
        if (!connected || reader == null) {
            notifyError("No conectado al lector");
            return false;
        }
        
        try {
            notifyStatus("Aplicando configuracion de antenas al lector...");
            
            int[] powerLevels = capabilities.getTransmitPowerLevelValues();
            if (powerLevels == null || powerLevels.length == 0) {
                notifyError("No se pudieron obtener los niveles de potencia del lector");
                return false;
            }
            
            System.out.println("[ZebraSDK] Aplicando config - Power levels: " + powerLevels.length);
            
            int successCount = 0;
            
            for (int ant = 1; ant <= 4; ant++) {
                AntennaConfig antConfig = config.getAntennaConfig(ant);
                if (antConfig == null) continue;
                
                // Solo configurar antenas habilitadas y fisicamente conectadas
                if (!antConfig.isEnabled()) {
                    System.out.println("[ZebraSDK] Antena " + ant + " deshabilitada, omitiendo");
                    continue;
                }
                
                try {
                    double targetPowerDbm = antConfig.getTransmitPower();
                    int targetPowerCentDbm = (int)(targetPowerDbm * 100); // Convertir a centesimas
                    
                    // Buscar el indice mas cercano
                    int bestIndex = 0;
                    int minDiff = Integer.MAX_VALUE;
                    for (int i = 0; i < powerLevels.length; i++) {
                        int diff = Math.abs(powerLevels[i] - targetPowerCentDbm);
                        if (diff < minDiff) {
                            minDiff = diff;
                            bestIndex = i;
                        }
                    }
                    
                    double actualPowerDbm = powerLevels[bestIndex] / 100.0;
                    System.out.println("[ZebraSDK] Antena " + ant + " target: " + targetPowerDbm + 
                                     " dBm -> index: " + bestIndex + " (" + actualPowerDbm + " dBm)");
                    
                    // Obtener config actual y modificar solo la potencia
                    Antennas.AntennaRfConfig rfConfig = reader.Config.Antennas.getAntennaRfConfig(ant);
                    rfConfig.setTransmitPowerIndex(bestIndex);
                    
                    // Aplicar la configuracion
                    reader.Config.Antennas.setAntennaRfConfig(ant, rfConfig);
                    
                    notifyStatus("Antena " + ant + " configurada: " + String.format("%.1f", actualPowerDbm) + " dBm");
                    successCount++;
                    
                } catch (Exception e) {
                    System.out.println("[ZebraSDK] Error configurando antena " + ant + ": " + e.getMessage());
                    notifyStatus("Antena " + ant + " error: " + e.getMessage());
                }
            }
            
            if (successCount > 0) {
                notifyStatus("Configuracion aplicada a " + successCount + " antena(s)");
                return true;
            } else {
                notifyError("No se pudo configurar ninguna antena");
                return false;
            }
            
        } catch (Exception e) {
            notifyError("Error aplicando configuracion: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public void disconnect() {
        if (!connected) {
            return;
        }
        
        try {
            stopReading();
            
            if (reader != null) {
                reader.Events.removeEventsListener(this);
                reader.disconnect();
            }
            
            notifyStatus("Desconectado");
            
        } catch (Exception e) {
            notifyError("Error al desconectar: " + e.getMessage());
        } finally {
            connected = false;
            reader = null;
        }
    }
    
    public boolean startReading() {
        if (!connected) {
            notifyError("No está conectado");
            return false;
        }
        
        if (reading) {
            notifyStatus("Ya está leyendo");
            return true;
        }
        
        try {
            reader.Actions.Inventory.perform();
            reading = true;
            notifyStatus("Lectura iniciada");
            return true;
            
        } catch (Exception e) {
            notifyError("Error al iniciar lectura: " + e.getMessage());
            return false;
        }
    }
    
    public boolean stopReading() {
        if (!reading) {
            return true;
        }
        
        try {
            reader.Actions.Inventory.stop();
            reading = false;
            notifyStatus("Lectura detenida");
            return true;
            
        } catch (Exception e) {
            notifyError("Error al detener lectura: " + e.getMessage());
            return false;
        }
    }
    
    public boolean isConnected() {
        return connected && reader != null;
    }
    
    public boolean isReading() {
        return reading;
    }
    
    public RFIDReader getReader() {
        return reader;
    }
    
    public ReaderCapabilities getCapabilities() {
        return capabilities;
    }
    
    public int getMaxPowerIndex() {
        return maxPowerIndex;
    }
    
    public String getModelName() {
        if (capabilities != null) {
            return capabilities.getModelName();
        }
        return "Desconocido";
    }
    
    public String getFirmwareVersion() {
        if (capabilities != null) {
            return capabilities.getFirwareVersion();
        }
        return "Desconocida";
    }
    
    public int getNumAntennas() {
        if (capabilities != null) {
            return capabilities.getNumAntennaSupported();
        }
        return 4;
    }
    
    public void setAntennaPower(int antenna, int powerPercent) {
        if (!connected) return;
        
        try {
            int powerIndex = (powerPercent * maxPowerIndex) / 100;
            
            Antennas.AntennaRfConfig rfConfig = reader.Config.Antennas.getAntennaRfConfig(antenna);
            rfConfig.setTransmitPowerIndex(powerIndex);
            reader.Config.Antennas.setAntennaRfConfig(antenna, rfConfig);
            
            AntennaConfig antConfig = config.getAntennaConfig(antenna);
            if (antConfig != null) {
                antConfig.setTransmitPowerFromIndex(powerIndex);
            }
            notifyStatus("Antena " + antenna + " potencia: " + powerPercent + "%");
            
        } catch (Exception e) {
            notifyError("Error configurando antena " + antenna + ": " + e.getMessage());
        }
    }
    
    public boolean setGPO(int port, boolean state) {
        if (!connected || reader == null) {
            return false;
        }
        
        try {
            GPO_PORT_STATE gpoState = state ? GPO_PORT_STATE.TRUE : GPO_PORT_STATE.FALSE;
            reader.Config.GPO.setPortState(port, gpoState);
            return true;
        } catch (Exception e) {
            notifyError("Error GPO " + port + ": " + e.getMessage());
            return false;
        }
    }
    
    public boolean getGPI(int port) {
        if (!connected || reader == null) {
            return false;
        }
        
        try {
            GPI_PORT_STATE state = reader.Config.GPI.getPortState(port);
            return state == GPI_PORT_STATE.GPI_PORT_STATE_HIGH;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public void eventReadNotify(RfidReadEvents e) {
        try {
            com.mot.rfid.api3.TagData[] tags = reader.Actions.getReadTags(100);
            if (tags != null && tags.length > 0) {
                for (com.mot.rfid.api3.TagData tag : tags) {
                    String epc = tag.getTagID();
                    short rssi = tag.getPeakRSSI();
                    short antenna = tag.getAntennaID();
                    short count = tag.getTagSeenCount();
                    
                    TagData tagData = new TagData(epc, rssi, antenna, System.currentTimeMillis());
                    tagData.setReadCount(count);
                    
                    if (tagCallback != null) {
                        tagCallback.accept(tagData);
                    }
                }
            }
        } catch (Exception ex) {
            notifyError("Error procesando tags: " + ex.getMessage());
        }
    }
    
    @Override
    public void eventStatusNotify(RfidStatusEvents e) {
        STATUS_EVENT_TYPE eventType = e.StatusEventData.getStatusEventType();
        
        if (eventType == STATUS_EVENT_TYPE.INVENTORY_START_EVENT) {
            notifyStatus("Inventario INICIADO");
            reading = true;
        } else if (eventType == STATUS_EVENT_TYPE.INVENTORY_STOP_EVENT) {
            notifyStatus("Inventario DETENIDO");
            reading = false;
        } else if (eventType == STATUS_EVENT_TYPE.DISCONNECTION_EVENT) {
            notifyStatus("DESCONEXIÓN detectada");
            connected = false;
            reading = false;
        }
    }
}
