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
                if (config.isAntennaEnabled(ant)) {
                    int power = config.getAntennaPower(ant);
                    int powerIndex = (power * maxPowerIndex) / 100;
                    
                    Antennas.AntennaRfConfig rfConfig = reader.Config.Antennas.getAntennaRfConfig(ant);
                    rfConfig.setTransmitPowerIndex(powerIndex);
                    rfConfig.setrfModeTableIndex(0);
                    rfConfig.setTari(0);
                    reader.Config.Antennas.setAntennaRfConfig(ant, rfConfig);
                    
                    notifyStatus("Antena " + ant + " configurada: potencia " + power + "%");
                }
            }
            
            Antennas.SingulationControl singulation = reader.Config.Antennas.getSingulationControl(1);
            
            SESSION session = SESSION.SESSION_S0;
            switch (config.getSession()) {
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
            
            notifyStatus("Singulación configurada: Sesión S" + config.getSession());
            
        } catch (Exception e) {
            notifyError("Error configurando antenas: " + e.getMessage());
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
            
            config.setAntennaPower(antenna, powerPercent);
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
            reader.Config.setGPOState(port, state);
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
            return reader.Config.getGPIState(port);
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
                    
                    TagData tagData = new TagData(epc);
                    tagData.setRssi(rssi);
                    tagData.setAntennaPort(antenna);
                    tagData.setReadCount(count);
                    tagData.setLastSeen(System.currentTimeMillis());
                    
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
