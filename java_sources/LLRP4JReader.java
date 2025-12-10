package com.rfid.zebra;

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
import java.util.function.Consumer;

/**
 * Lector RFID usando la librería LLRP4J.
 * Proporciona una implementación robusta del protocolo LLRP.
 */
public class LLRP4JReader implements AutoCloseable {
    
    private final String readerIP;
    private final int port;
    private LlrpContext context;
    private LlrpClient client;
    private volatile boolean connected = false;
    private volatile boolean reading = false;
    
    private Consumer<String[]> tagCallback; // [epc, antenna, rssi, timestamp]
    private Consumer<String> statusCallback;
    private Consumer<Exception> errorCallback;
    
    public LLRP4JReader(String readerIP) {
        this(readerIP, 5084);
    }
    
    public LLRP4JReader(String readerIP, int port) {
        this.readerIP = readerIP;
        this.port = port;
    }
    
    public void setTagCallback(Consumer<String[]> callback) {
        this.tagCallback = callback;
    }
    
    public void setStatusCallback(Consumer<String> callback) {
        this.statusCallback = callback;
    }
    
    public void setErrorCallback(Consumer<Exception> callback) {
        this.errorCallback = callback;
    }
    
    private void log(String message) {
        System.out.println("[LLRP4J] " + message);
        if (statusCallback != null) {
            statusCallback.accept(message);
        }
    }
    
    private void logError(String message, Exception e) {
        System.err.println("[LLRP4J] ERROR: " + message);
        if (e != null) {
            e.printStackTrace();
        }
        if (errorCallback != null && e != null) {
            errorCallback.accept(e);
        }
    }
    
    /**
     * Conecta al lector RFID.
     */
    public boolean connect() {
        try {
            log("Conectando a " + readerIP + ":" + port + "...");
            
            context = LlrpContext.create(new LlrpModule());
            
            LlrpEndpoint endpoint = new LlrpEndpoint() {
                @Override
                public void messageReceived(LlrpMessage message) {
                    handleMessage(message);
                }
                
                @Override
                public void errorOccured(String msg, Throwable cause) {
                    logError(msg, cause != null ? new Exception(cause) : null);
                }
            };
            
            client = LlrpClient.create(context, readerIP, port).endpoint(endpoint);
            
            // Esperar conexión
            Thread.sleep(1000);
            
            connected = true;
            log("Conectado exitosamente a " + readerIP);
            
            return true;
            
        } catch (Exception e) {
            logError("Error al conectar: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Desconecta del lector.
     */
    public void disconnect() {
        try {
            if (reading) {
                stopReading();
            }
            
            if (client != null) {
                client.close();
                client = null;
            }
            
            connected = false;
            log("Desconectado del lector");
            
        } catch (Exception e) {
            logError("Error al desconectar: " + e.getMessage(), e);
        }
    }
    
    /**
     * Inicia la lectura de tags.
     */
    public boolean startReading() {
        if (!connected || client == null) {
            logError("No hay conexión activa", null);
            return false;
        }
        
        try {
            log("Configurando lectura...");
            
            // 1. Eliminar ROSpecs existentes
            DELETE_ROSPEC deleteRospec = new DELETE_ROSPEC();
            deleteRospec.roSpecID(0); // 0 = eliminar todos
            
            LlrpMessage deleteResponse = client.transact(deleteRospec);
            log("ROSpecs anteriores eliminados");
            
            // 2. Crear y agregar ROSpec
            ADD_ROSPEC addRospec = createROSpec();
            LlrpMessage addResponse = client.transact(addRospec);
            
            if (addResponse instanceof ADD_ROSPEC_RESPONSE) {
                ADD_ROSPEC_RESPONSE resp = (ADD_ROSPEC_RESPONSE) addResponse;
                if (resp.llrpStatus().statusCode() != StatusCode.M_Success) {
                    logError("Error al agregar ROSpec: " + resp.llrpStatus().errorDescription(), null);
                    return false;
                }
            }
            log("ROSpec agregado");
            
            // 3. Habilitar ROSpec
            ENABLE_ROSPEC enableRospec = new ENABLE_ROSPEC();
            enableRospec.roSpecID(1);
            LlrpMessage enableResponse = client.transact(enableRospec);
            log("ROSpec habilitado");
            
            // 4. Iniciar ROSpec
            START_ROSPEC startRospec = new START_ROSPEC();
            startRospec.roSpecID(1);
            LlrpMessage startResponse = client.transact(startRospec);
            log("ROSpec iniciado - Lectura activa!");
            
            reading = true;
            return true;
            
        } catch (Exception e) {
            logError("Error al iniciar lectura: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Detiene la lectura de tags.
     */
    public boolean stopReading() {
        if (!connected || client == null) {
            return false;
        }
        
        try {
            // Detener ROSpec
            STOP_ROSPEC stopRospec = new STOP_ROSPEC();
            stopRospec.roSpecID(1);
            client.transact(stopRospec);
            
            // Eliminar ROSpec
            DELETE_ROSPEC deleteRospec = new DELETE_ROSPEC();
            deleteRospec.roSpecID(1);
            client.transact(deleteRospec);
            
            reading = false;
            log("Lectura detenida");
            
            return true;
            
        } catch (Exception e) {
            logError("Error al detener lectura: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Crea un ROSpec para lectura continua.
     */
    private ADD_ROSPEC createROSpec() {
        ADD_ROSPEC addRospec = new ADD_ROSPEC();
        
        ROSpec rospec = new ROSpec();
        rospec.roSpecID(1);
        rospec.priority(0);
        rospec.currentState(ROSpecState.Disabled);
        
        // ROBoundarySpec
        ROBoundarySpec boundarySpec = new ROBoundarySpec();
        
        // Start trigger - Null (inicia con START_ROSPEC)
        ROSpecStartTrigger startTrigger = new ROSpecStartTrigger();
        startTrigger.roSpecStartTriggerType(ROSpecStartTriggerType.Null);
        boundarySpec.roSpecStartTrigger(startTrigger);
        
        // Stop trigger - Null (corre indefinidamente)
        ROSpecStopTrigger stopTrigger = new ROSpecStopTrigger();
        stopTrigger.roSpecStopTriggerType(ROSpecStopTriggerType.Null);
        stopTrigger.durationTriggerValue(0);
        boundarySpec.roSpecStopTrigger(stopTrigger);
        
        rospec.roBoundarySpec(boundarySpec);
        
        // AISpec
        AISpec aiSpec = new AISpec();
        
        // Usar todas las antenas (ID = 0)
        aiSpec.antennaIDs(new int[]{0});
        
        // AISpec stop trigger
        AISpecStopTrigger aiStopTrigger = new AISpecStopTrigger();
        aiStopTrigger.aiSpecStopTriggerType(AISpecStopTriggerType.Null);
        aiStopTrigger.durationTrigger(0);
        aiSpec.aiSpecStopTrigger(aiStopTrigger);
        
        // InventoryParameterSpec
        InventoryParameterSpec invSpec = new InventoryParameterSpec();
        invSpec.inventoryParameterSpecID(1);
        invSpec.protocolID(AirProtocols.EPCGlobalClass1Gen2);
        
        List<InventoryParameterSpec> invSpecs = new ArrayList<>();
        invSpecs.add(invSpec);
        aiSpec.inventoryParameterSpec(invSpecs);
        
        // Agregar AISpec como SpecParameter
        List<SpecParameter> specParams = new ArrayList<>();
        specParams.add(aiSpec);
        rospec.specParameter(specParams);
        
        // ROReportSpec
        ROReportSpec reportSpec = new ROReportSpec();
        reportSpec.roReportTrigger(ROReportTriggerType.Upon_N_Tags_Or_End_Of_AISpec_Or_End_Of_RFSurveySpec);
        reportSpec.n(1); // Reportar cada tag
        
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
        
        // C1G2EPCMemorySelector
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
    
    /**
     * Maneja mensajes recibidos del lector.
     */
    private void handleMessage(LlrpMessage message) {
        try {
            if (message instanceof RO_ACCESS_REPORT) {
                RO_ACCESS_REPORT report = (RO_ACCESS_REPORT) message;
                processTagReport(report);
            } else if (message instanceof KEEPALIVE) {
                // Responder keepalive
                KEEPALIVE_ACK ack = new KEEPALIVE_ACK();
                if (client != null) {
                    client.send(ack);
                }
            } else if (message instanceof READER_EVENT_NOTIFICATION) {
                log("Evento del lector recibido");
            }
        } catch (Exception e) {
            logError("Error procesando mensaje: " + e.getMessage(), e);
        }
    }
    
    /**
     * Procesa un reporte de tags.
     */
    private void processTagReport(RO_ACCESS_REPORT report) {
        List<TagReportData> tagReports = report.tagReportData();
        
        if (tagReports == null || tagReports.isEmpty()) {
            return;
        }
        
        log("Recibidos " + tagReports.size() + " tags");
        
        for (TagReportData tagReport : tagReports) {
            String epc = "";
            int antenna = 0;
            int rssi = 0;
            long timestamp = System.currentTimeMillis();
            int readCount = 1;
            
            // Obtener EPC
            EPCParameter epcParam = tagReport.epcParameter();
            if (epcParam != null) {
                if (epcParam instanceof EPC_96) {
                    EPC_96 epc96 = (EPC_96) epcParam;
                    BigInteger epcValue = epc96.epc();
                    if (epcValue != null) {
                        // Convertir BigInteger a hex string con padding a 24 caracteres
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
            
            // Obtener timestamp
            FirstSeenTimestampUTC firstSeen = tagReport.firstSeenTimestampUTC();
            if (firstSeen != null) {
                BigInteger microseconds = firstSeen.microseconds();
                if (microseconds != null) {
                    timestamp = microseconds.divide(BigInteger.valueOf(1000)).longValue();
                }
            }
            
            // Obtener conteo
            TagSeenCount seenCount = tagReport.tagSeenCount();
            if (seenCount != null) {
                readCount = seenCount.tagCount();
            }
            
            // Notificar callback
            if (epc != null && !epc.isEmpty()) {
                log("Tag leído: " + epc + " (Antena: " + antenna + ", RSSI: " + rssi + ")");
                
                if (tagCallback != null) {
                    String[] tagData = new String[]{
                        epc,
                        String.valueOf(antenna),
                        String.valueOf(rssi),
                        String.valueOf(timestamp),
                        String.valueOf(readCount)
                    };
                    tagCallback.accept(tagData);
                }
            }
        }
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public boolean isReading() {
        return reading;
    }
    
    @Override
    public void close() {
        disconnect();
    }
    
    /**
     * Método de prueba simple.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java LLRP4JReader <IP_del_lector>");
            return;
        }
        
        String readerIP = args[0];
        
        try (LLRP4JReader reader = new LLRP4JReader(readerIP)) {
            reader.setTagCallback(tagData -> {
                System.out.println(">>> TAG: EPC=" + tagData[0] + 
                    ", Antena=" + tagData[1] + 
                    ", RSSI=" + tagData[2]);
            });
            
            if (reader.connect()) {
                System.out.println("Conectado! Iniciando lectura...");
                
                if (reader.startReading()) {
                    System.out.println("Leyendo tags por 30 segundos...");
                    Thread.sleep(30000);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
