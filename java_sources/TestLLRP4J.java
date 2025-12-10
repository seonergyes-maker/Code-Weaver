import net.enilink.llrp4j.LlrpContext;
import net.enilink.llrp4j.net.LlrpClient;
import net.enilink.llrp4j.net.LlrpEndpoint;
import net.enilink.llrp4j.types.LlrpMessage;
import net.enilink.llrp4j.types.BitList;

import org.llrp.modules.LlrpModule;
import org.llrp.messages.*;
import org.llrp.parameters.*;
import org.llrp.enumerations.*;
import org.llrp.interfaces.EPCParameter;
import org.llrp.interfaces.SpecParameter;
import org.llrp.interfaces.AirProtocolEPCMemorySelector;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prueba simple de conexión LLRP4J al lector FX7500.
 */
public class TestLLRP4J {
    
    private static AtomicInteger tagCount = new AtomicInteger(0);
    private static LlrpClient client;
    private static volatile boolean running = true;
    
    public static void main(String[] args) {
        String readerIP = args.length > 0 ? args[0] : "192.168.1.117";
        int testDuration = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        
        System.out.println("========================================");
        System.out.println("  PRUEBA LLRP4J - Zebra FX7500");
        System.out.println("========================================");
        System.out.println("IP del lector: " + readerIP);
        System.out.println("Duración: " + testDuration + " segundos");
        System.out.println();
        
        try {
            // Crear contexto LLRP4J
            System.out.println("[1] Creando contexto LLRP4J...");
            LlrpContext context = LlrpContext.create(new LlrpModule());
            System.out.println("    OK - Contexto creado");
            
            // Crear endpoint para recibir mensajes
            LlrpEndpoint endpoint = new LlrpEndpoint() {
                @Override
                public void messageReceived(LlrpMessage message) {
                    handleMessage(message);
                }
                
                @Override
                public void errorOccured(String msg, Throwable cause) {
                    System.err.println("[ERROR] " + msg);
                    if (cause != null) {
                        cause.printStackTrace();
                    }
                }
            };
            
            // Conectar al lector
            System.out.println("[2] Conectando a " + readerIP + ":5084...");
            client = LlrpClient.create(context, readerIP, 5084).endpoint(endpoint);
            Thread.sleep(1000);
            System.out.println("    OK - Conectado!");
            
            // Eliminar ROSpecs existentes
            System.out.println("[3] Eliminando ROSpecs existentes...");
            DELETE_ROSPEC deleteRospec = new DELETE_ROSPEC();
            deleteRospec.roSpecID(0);
            LlrpMessage deleteResp = client.transact(deleteRospec);
            System.out.println("    OK - ROSpecs eliminados");
            
            // Crear y agregar ROSpec
            System.out.println("[4] Creando ROSpec...");
            ADD_ROSPEC addRospec = createROSpec();
            LlrpMessage addResp = client.transact(addRospec);
            
            if (addResp instanceof ADD_ROSPEC_RESPONSE) {
                ADD_ROSPEC_RESPONSE resp = (ADD_ROSPEC_RESPONSE) addResp;
                StatusCode status = resp.llrpStatus().statusCode();
                if (status == StatusCode.M_Success) {
                    System.out.println("    OK - ROSpec creado");
                } else {
                    System.out.println("    ERROR: " + resp.llrpStatus().errorDescription());
                    return;
                }
            }
            
            // Habilitar ROSpec
            System.out.println("[5] Habilitando ROSpec...");
            ENABLE_ROSPEC enableRospec = new ENABLE_ROSPEC();
            enableRospec.roSpecID(1);
            client.transact(enableRospec);
            System.out.println("    OK - ROSpec habilitado");
            
            // Iniciar ROSpec
            System.out.println("[6] Iniciando lectura...");
            START_ROSPEC startRospec = new START_ROSPEC();
            startRospec.roSpecID(1);
            client.transact(startRospec);
            System.out.println("    OK - Lectura iniciada!");
            
            System.out.println();
            System.out.println("========================================");
            System.out.println("  ESPERANDO TAGS (" + testDuration + " segundos)...");
            System.out.println("========================================");
            System.out.println();
            
            // Esperar y monitorear
            long startTime = System.currentTimeMillis();
            int lastCount = 0;
            
            for (int i = 0; i < testDuration && running; i++) {
                Thread.sleep(1000);
                int currentCount = tagCount.get();
                int newTags = currentCount - lastCount;
                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                
                System.out.printf("[%3ds] Tags totales: %d (+%d en último segundo)\n", 
                    elapsed, currentCount, newTags);
                
                lastCount = currentCount;
            }
            
            // Detener lectura
            System.out.println();
            System.out.println("[7] Deteniendo lectura...");
            STOP_ROSPEC stopRospec = new STOP_ROSPEC();
            stopRospec.roSpecID(1);
            client.transact(stopRospec);
            
            DELETE_ROSPEC finalDelete = new DELETE_ROSPEC();
            finalDelete.roSpecID(1);
            client.transact(finalDelete);
            System.out.println("    OK - Lectura detenida");
            
            // Cerrar conexión
            System.out.println("[8] Cerrando conexión...");
            client.close();
            System.out.println("    OK - Desconectado");
            
            System.out.println();
            System.out.println("========================================");
            System.out.println("  RESULTADOS");
            System.out.println("========================================");
            System.out.println("Total de tags leídos: " + tagCount.get());
            System.out.println("Promedio: " + (tagCount.get() / testDuration) + " tags/segundo");
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void handleMessage(LlrpMessage message) {
        if (message instanceof RO_ACCESS_REPORT) {
            RO_ACCESS_REPORT report = (RO_ACCESS_REPORT) message;
            List<TagReportData> tags = report.tagReportData();
            
            if (tags != null && !tags.isEmpty()) {
                for (TagReportData tagReport : tags) {
                    tagCount.incrementAndGet();
                    
                    String epc = "???";
                    int antenna = 0;
                    int rssi = 0;
                    
                    // Obtener EPC
                    EPCParameter epcParam = tagReport.epcParameter();
                    if (epcParam != null) {
                        if (epcParam instanceof EPC_96) {
                            EPC_96 epc96 = (EPC_96) epcParam;
                            BigInteger epcValue = epc96.epc();
                            if (epcValue != null) {
                                String hexStr = epcValue.toString(16).toUpperCase();
                                while (hexStr.length() < 24) {
                                    hexStr = "0" + hexStr;
                                }
                                epc = hexStr;
                            }
                        } else if (epcParam instanceof EPCData) {
                            EPCData epcData = (EPCData) epcParam;
                            BitList bits = epcData.epc();
                            if (bits != null) {
                                epc = bits.toHexString().toUpperCase();
                            }
                        }
                    }
                    
                    // Obtener antena
                    AntennaID antennaID = tagReport.antennaID();
                    if (antennaID != null) {
                        antenna = antennaID.antennaID();
                    }
                    
                    // Obtener RSSI
                    PeakRSSI peakRSSI = tagReport.peakRSSI();
                    if (peakRSSI != null) {
                        rssi = peakRSSI.peakRSSI();
                    }
                    
                    System.out.println(">>> TAG: " + epc + " | Antena: " + antenna + " | RSSI: " + rssi + " dBm");
                }
            }
        } else if (message instanceof KEEPALIVE) {
            // Responder keepalive
            if (client != null) {
                try {
                    client.send(new KEEPALIVE_ACK());
                } catch (Exception e) {
                    // Ignorar errores de keepalive
                }
            }
        } else if (message instanceof READER_EVENT_NOTIFICATION) {
            System.out.println("[EVENTO] Notificación del lector recibida");
        }
    }
    
    private static ADD_ROSPEC createROSpec() {
        ADD_ROSPEC addRospec = new ADD_ROSPEC();
        
        ROSpec rospec = new ROSpec();
        rospec.roSpecID(1);
        rospec.priority(0);
        rospec.currentState(ROSpecState.Disabled);
        
        // ROBoundarySpec
        ROBoundarySpec boundarySpec = new ROBoundarySpec();
        
        ROSpecStartTrigger startTrigger = new ROSpecStartTrigger();
        startTrigger.roSpecStartTriggerType(ROSpecStartTriggerType.Null);
        boundarySpec.roSpecStartTrigger(startTrigger);
        
        ROSpecStopTrigger stopTrigger = new ROSpecStopTrigger();
        stopTrigger.roSpecStopTriggerType(ROSpecStopTriggerType.Null);
        stopTrigger.durationTriggerValue(0);
        boundarySpec.roSpecStopTrigger(stopTrigger);
        
        rospec.roBoundarySpec(boundarySpec);
        
        // AISpec
        AISpec aiSpec = new AISpec();
        aiSpec.antennaIDs(new int[]{0}); // 0 = todas las antenas
        
        AISpecStopTrigger aiStopTrigger = new AISpecStopTrigger();
        aiStopTrigger.aiSpecStopTriggerType(AISpecStopTriggerType.Null);
        aiStopTrigger.durationTrigger(0);
        aiSpec.aiSpecStopTrigger(aiStopTrigger);
        
        InventoryParameterSpec invSpec = new InventoryParameterSpec();
        invSpec.inventoryParameterSpecID(1);
        invSpec.protocolID(AirProtocols.EPCGlobalClass1Gen2);
        
        List<InventoryParameterSpec> invSpecs = new ArrayList<>();
        invSpecs.add(invSpec);
        aiSpec.inventoryParameterSpec(invSpecs);
        
        List<SpecParameter> specParams = new ArrayList<>();
        specParams.add(aiSpec);
        rospec.specParameter(specParams);
        
        // ROReportSpec
        ROReportSpec reportSpec = new ROReportSpec();
        reportSpec.roReportTrigger(ROReportTriggerType.Upon_N_Tags_Or_End_Of_AISpec_Or_End_Of_RFSurveySpec);
        reportSpec.n(1);
        
        TagReportContentSelector contentSelector = new TagReportContentSelector();
        contentSelector.enableROSpecID(true);
        contentSelector.enableSpecIndex(true);
        contentSelector.enableInventoryParameterSpecID(true);
        contentSelector.enableAntennaID(true);
        contentSelector.enableChannelIndex(true);
        contentSelector.enablePeakRSSI(true);
        contentSelector.enableFirstSeenTimestamp(true);
        contentSelector.enableLastSeenTimestamp(true);
        contentSelector.enableTagSeenCount(true);
        contentSelector.enableAccessSpecID(true);
        
        C1G2EPCMemorySelector epcSelector = new C1G2EPCMemorySelector();
        epcSelector.enableCRC(true);
        epcSelector.enablePCBits(true);
        
        List<AirProtocolEPCMemorySelector> epcSelectors = new ArrayList<>();
        epcSelectors.add(epcSelector);
        contentSelector.airProtocolEPCMemorySelector(epcSelectors);
        
        reportSpec.tagReportContentSelector(contentSelector);
        rospec.roReportSpec(reportSpec);
        
        addRospec.roSpec(rospec);
        
        return addRospec;
    }
}
