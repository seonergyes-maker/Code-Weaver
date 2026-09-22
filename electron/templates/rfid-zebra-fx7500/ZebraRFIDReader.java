import com.mot.rfid.api3.*;

/**
 * Lector RFID usando SDK oficial de Zebra (Symbol/Motorola)
 * Para FX7500/FX9600
 */
public class ZebraRFIDReader implements RfidEventsListener {
    
    private RFIDReader reader;
    private volatile boolean running = true;
    
    public static void main(String[] args) {
        String ip = "192.168.1.117";
        if (args.length > 0) {
            ip = args[0];
        }
        
        System.out.println("===========================================");
        System.out.println("  Zebra RFID Reader - SDK Oficial");
        System.out.println("===========================================");
        System.out.println("IP: " + ip);
        System.out.println();
        
        ZebraRFIDReader app = new ZebraRFIDReader();
        app.run(ip);
    }
    
    public void run(String ip) {
        try {
            reader = new RFIDReader(ip, 5084, 0);
            
            System.out.println("[1] Conectando al lector...");
            reader.connect();
            System.out.println("    Conectado!");
            
            System.out.println("[2] Información del lector:");
            ReaderCapabilities caps = reader.ReaderCapabilities;
            System.out.println("    Modelo: " + caps.getModelName());
            System.out.println("    Firmware: " + caps.getFirwareVersion());
            System.out.println("    Antenas: " + caps.getNumAntennaSupported());
            
            System.out.println("[3] Configurando eventos...");
            reader.Events.addEventsListener(this);
            reader.Events.setInventoryStartEvent(true);
            reader.Events.setInventoryStopEvent(true);
            reader.Events.setTagReadEvent(true);
            reader.Events.setAttachTagDataWithReadEvent(false);
            
            System.out.println("[4] Configurando antena...");
            int maxPower = caps.getTransmitPowerLevelValues().length - 1;
            System.out.println("    Potencia máxima: índice " + maxPower);
            
            Antennas.AntennaRfConfig rfConfig = reader.Config.Antennas.getAntennaRfConfig(1);
            rfConfig.setTransmitPowerIndex(maxPower);
            rfConfig.setrfModeTableIndex(0);
            rfConfig.setTari(0);
            reader.Config.Antennas.setAntennaRfConfig(1, rfConfig);
            
            Antennas.SingulationControl singulation = reader.Config.Antennas.getSingulationControl(1);
            singulation.setSession(SESSION.SESSION_S0);
            singulation.setTagPopulation((short)30);
            singulation.Action.setInventoryState(INVENTORY_STATE.INVENTORY_STATE_A);
            singulation.Action.setSLFlag(SL_FLAG.SL_ALL);
            reader.Config.Antennas.setSingulationControl(1, singulation);
            
            System.out.println("[5] Iniciando inventario...");
            System.out.println();
            System.out.println("===========================================");
            System.out.println("  LEYENDO TAGS (Ctrl+C para detener)");
            System.out.println("===========================================");
            System.out.println();
            
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                running = false;
                stop();
            }));
            
            reader.Actions.Inventory.perform();
            
            while (running) {
                Thread.sleep(500);
            }
            
        } catch (InvalidUsageException e) {
            System.err.println("Error de uso: " + e.getInfo());
            e.printStackTrace();
        } catch (OperationFailureException e) {
            System.err.println("Error de operación: " + e.getResults().toString());
            System.err.println("Vendor: " + e.getVendorMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void stop() {
        try {
            if (reader != null && reader.isConnected()) {
                System.out.println("\nDeteniendo...");
                reader.Actions.Inventory.stop();
                reader.disconnect();
                System.out.println("Desconectado.");
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                    
                    System.out.printf("[TAG] EPC: %s | RSSI: %d dBm | Antena: %d | Lecturas: %d%n",
                        epc, rssi, antenna, count);
                }
            }
        } catch (Exception ex) {
            System.err.println("Error leyendo tags: " + ex.getMessage());
        }
    }
    
    @Override
    public void eventStatusNotify(RfidStatusEvents e) {
        STATUS_EVENT_TYPE eventType = e.StatusEventData.getStatusEventType();
        
        if (eventType == STATUS_EVENT_TYPE.INVENTORY_START_EVENT) {
            System.out.println("[EVENTO] Inventario INICIADO");
        } else if (eventType == STATUS_EVENT_TYPE.INVENTORY_STOP_EVENT) {
            System.out.println("[EVENTO] Inventario DETENIDO");
        }
    }
}
