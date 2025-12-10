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
 * Prueba de conexión LLRP4J al lector FX7500.
 * Lectura de tags en tiempo real con reportes inmediatos.
 */
public class TestLLRP4J {
    
    private static AtomicInteger tagCount = new AtomicInteger(0);
    private static AtomicInteger messageCount = new AtomicInteger(0);
    private static LlrpClient client;
    private static volatile boolean running = true;
    
    public static void main(String[] args) {
        String readerIP = args.length > 0 ? args[0] : "192.168.1.117";
        int testDuration = args.length > 1 ? Integer.parseInt(args[1]) : 30;
        
        System.out.println("========================================");
        System.out.println("  PRUEBA LLRP4J - Zebra FX7500");
        System.out.println("  Lectura en tiempo real");
        System.out.println("========================================");
        System.out.println("IP del lector: " + readerIP);
        System.out.println("Duración: " + testDuration + " segundos");
        System.out.println();
        
        try {
            // Crear contexto LLRP4J
            System.out.println("[1] Creando contexto LLRP4J...");
            LlrpContext context = LlrpContext.create(new LlrpModule());
            System.out.println("    OK");
            
            // IMPORTANTE: Crear endpoint ANTES de conectar
            System.out.println("[2] Registrando listener de mensajes...");
            LlrpEndpoint endpoint = new LlrpEndpoint() {
                @Override
                public void messageReceived(LlrpMessage message) {
                    messageCount.incrementAndGet();
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
            System.out.println("    OK");
            
            // Conectar al lector CON el endpoint ya registrado
            System.out.println("[3] Conectando a " + readerIP + ":5084...");
            client = LlrpClient.create(context, readerIP, 5084).endpoint(endpoint);
            Thread.sleep(2000);
            System.out.println("    OK - Conectado");
            
            // Eliminar TODOS los ROSpecs existentes
            System.out.println("[4] Limpiando ROSpecs existentes...");
            DELETE_ROSPEC deleteRospec = new DELETE_ROSPEC();
            deleteRospec.roSpecID(0); // 0 = eliminar todos
            LlrpMessage delResp = client.transact(deleteRospec);
            if (delResp instanceof DELETE_ROSPEC_RESPONSE) {
                DELETE_ROSPEC_RESPONSE resp = (DELETE_ROSPEC_RESPONSE) delResp;
                System.out.println("    Respuesta: " + resp.llrpStatus().statusCode());
            }
            
            // Crear ROSpec optimizado para tiempo real
            System.out.println("[5] Creando ROSpec para lectura en tiempo real...");
            ADD_ROSPEC addRospec = createRealTimeROSpec();
            LlrpMessage addResp = client.transact(addRospec);
            
            if (addResp instanceof ADD_ROSPEC_RESPONSE) {
                ADD_ROSPEC_RESPONSE resp = (ADD_ROSPEC_RESPONSE) addResp;
                StatusCode status = resp.llrpStatus().statusCode();
                System.out.println("    Respuesta: " + status);
                if (status != StatusCode.M_Success) {
                    System.err.println("    ERROR: " + resp.llrpStatus().errorDescription());
                    client.close();
                    return;
                }
            }
            
            // Habilitar ROSpec
            System.out.println("[6] Habilitando ROSpec...");
            ENABLE_ROSPEC enableRospec = new ENABLE_ROSPEC();
            enableRospec.roSpecID(1);
            LlrpMessage enableResp = client.transact(enableRospec);
            if (enableResp instanceof ENABLE_ROSPEC_RESPONSE) {
                ENABLE_ROSPEC_RESPONSE resp = (ENABLE_ROSPEC_RESPONSE) enableResp;
                System.out.println("    Respuesta: " + resp.llrpStatus().statusCode());
            }
            
            // Iniciar ROSpec
            System.out.println("[7] Iniciando lectura...");
            START_ROSPEC startRospec = new START_ROSPEC();
            startRospec.roSpecID(1);
            LlrpMessage startResp = client.transact(startRospec);
            if (startResp instanceof START_ROSPEC_RESPONSE) {
                START_ROSPEC_RESPONSE resp = (START_ROSPEC_RESPONSE) startResp;
                StatusCode status = resp.llrpStatus().statusCode();
                System.out.println("    Respuesta: " + status);
                if (status != StatusCode.M_Success) {
                    System.err.println("    ERROR: " + resp.llrpStatus().errorDescription());
                }
            }
            
            System.out.println();
            System.out.println("========================================");
            System.out.println("  LEYENDO TAGS EN TIEMPO REAL");
            System.out.println("  (" + testDuration + " segundos)");
            System.out.println("========================================");
            System.out.println();
            
            // Monitorear con polling activo de GET_REPORT
            long startTime = System.currentTimeMillis();
            int lastCount = 0;
            
            for (int i = 0; i < testDuration && running; i++) {
                // Cada segundo, solicitar reporte
                Thread.sleep(500);
                
                try {
                    GET_REPORT getReport = new GET_REPORT();
                    LlrpMessage reportResp = client.transact(getReport, 1500);
                    
                    if (reportResp instanceof RO_ACCESS_REPORT) {
                        processReport((RO_ACCESS_REPORT) reportResp);
                    }
                } catch (Exception e) {
                    // Timeout - no hay tags pendientes
                }
                
                Thread.sleep(500);
                
                int currentCount = tagCount.get();
                if (currentCount > lastCount || i % 5 == 0) {
                    long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                    System.out.printf("[%3ds] Total tags: %d | Mensajes: %d\n", 
                        elapsed, currentCount, messageCount.get());
                }
                lastCount = currentCount;
            }
            
            // Detener lectura
            System.out.println();
            System.out.println("[8] Deteniendo lectura...");
            
            STOP_ROSPEC stopRospec = new STOP_ROSPEC();
            stopRospec.roSpecID(1);
            client.transact(stopRospec);
            
            DELETE_ROSPEC finalDelete = new DELETE_ROSPEC();
            finalDelete.roSpecID(1);
            client.transact(finalDelete);
            
            System.out.println("    OK");
            
            // Cerrar conexión
            System.out.println("[9] Cerrando conexión...");
            client.close();
            System.out.println("    OK");
            
            System.out.println();
            System.out.println("========================================");
            System.out.println("  RESULTADOS");
            System.out.println("========================================");
            System.out.println("Total de tags leídos: " + tagCount.get());
            System.out.println("Total de mensajes: " + messageCount.get());
            double tagsPerSec = testDuration > 0 ? (double) tagCount.get() / testDuration : 0;
            System.out.printf("Promedio: %.1f tags/segundo\n", tagsPerSec);
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void handleMessage(LlrpMessage message) {
        String msgType = message.getClass().getSimpleName();
        
        if (message instanceof RO_ACCESS_REPORT) {
            System.out.println("[ASYNC] RO_ACCESS_REPORT recibido!");
            processReport((RO_ACCESS_REPORT) message);
        } else if (message instanceof KEEPALIVE) {
            // Responder keepalive
            if (client != null) {
                try {
                    client.send(new KEEPALIVE_ACK());
                } catch (Exception e) {
                    // Ignorar
                }
            }
        } else if (message instanceof READER_EVENT_NOTIFICATION) {
            System.out.println("[EVENTO] Notificación del lector");
        } else {
            System.out.println("[MSG] " + msgType);
        }
    }
    
    private static void processReport(RO_ACCESS_REPORT report) {
        List<TagReportData> tags = report.tagReportData();
        
        if (tags == null || tags.isEmpty()) {
            return;
        }
        
        for (TagReportData tagReport : tags) {
            tagCount.incrementAndGet();
            
            String epc = "???";
            int antenna = 0;
            int rssi = 0;
            int seenCount = 0;
            
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
            
            // Obtener conteo de lecturas
            TagSeenCount tsc = tagReport.tagSeenCount();
            if (tsc != null) {
                seenCount = tsc.tagCount();
            }
            
            System.out.println(">>> TAG: " + epc + " | Ant:" + antenna + " | RSSI:" + rssi + " | Reads:" + seenCount);
        }
    }
    
    /**
     * Crea un ROSpec optimizado para lectura en tiempo real.
     * Solo usa antena 1 (la conectada físicamente).
     */
    private static ADD_ROSPEC createRealTimeROSpec() {
        ADD_ROSPEC addRospec = new ADD_ROSPEC();
        
        ROSpec rospec = new ROSpec();
        rospec.roSpecID(1);
        rospec.priority(0);
        rospec.currentState(ROSpecState.Disabled);
        
        // ROBoundarySpec - Trigger Null (se controla con START/STOP)
        ROBoundarySpec boundarySpec = new ROBoundarySpec();
        
        ROSpecStartTrigger startTrigger = new ROSpecStartTrigger();
        startTrigger.roSpecStartTriggerType(ROSpecStartTriggerType.Null);
        boundarySpec.roSpecStartTrigger(startTrigger);
        
        ROSpecStopTrigger stopTrigger = new ROSpecStopTrigger();
        stopTrigger.roSpecStopTriggerType(ROSpecStopTriggerType.Null);
        stopTrigger.durationTriggerValue(0);
        boundarySpec.roSpecStopTrigger(stopTrigger);
        
        rospec.roBoundarySpec(boundarySpec);
        
        // AISpec - SOLO ANTENA 1 (la que está conectada físicamente)
        AISpec aiSpec = new AISpec();
        aiSpec.antennaIDs(new int[]{1}); // SOLO antena 1
        
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
        
        // ROReportSpec - Reporte inmediato por cada tag (N=1)
        ROReportSpec reportSpec = new ROReportSpec();
        reportSpec.roReportTrigger(ROReportTriggerType.Upon_N_Tags_Or_End_Of_ROSpec);
        reportSpec.n(1); // Reportar cada tag inmediatamente
        
        TagReportContentSelector contentSelector = new TagReportContentSelector();
        contentSelector.enableROSpecID(true);
        contentSelector.enableSpecIndex(true);
        contentSelector.enableInventoryParameterSpecID(true);
        contentSelector.enableAntennaID(true);
        contentSelector.enableChannelIndex(false);
        contentSelector.enablePeakRSSI(true);
        contentSelector.enableFirstSeenTimestamp(true);
        contentSelector.enableLastSeenTimestamp(true);
        contentSelector.enableTagSeenCount(true);
        contentSelector.enableAccessSpecID(false);
        
        C1G2EPCMemorySelector epcSelector = new C1G2EPCMemorySelector();
        epcSelector.enableCRC(false);
        epcSelector.enablePCBits(false);
        
        List<AirProtocolEPCMemorySelector> epcSelectors = new ArrayList<>();
        epcSelectors.add(epcSelector);
        contentSelector.airProtocolEPCMemorySelector(epcSelectors);
        
        reportSpec.tagReportContentSelector(contentSelector);
        rospec.roReportSpec(reportSpec);
        
        addRospec.roSpec(rospec);
        
        return addRospec;
    }
}
