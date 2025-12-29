import java.io.*;
import java.util.Properties;

/**
 * Configuracion de la aplicacion PLC COGER
 * Persiste en archivo config.ini (formato INI)
 */
public class PLCCogerConfig {
    
    private static final String CONFIG_FILE = "config.ini";
    
    // Configuracion API
    private String apiEndpoint = "http://localhost:8080";
    private String apiToken = "";
    private String apiIP = "192.168.1.100";
    private String apiTrabajo = "TRABAJO01";
    private int apiPollingInterval = 1000;
    private boolean apiPollingEnabled = false;
    private boolean apiAutoStart = false;
    
    // Configuracion PLC
    private String plcIP = "127.0.0.1";
    private int plcPort = 502;
    private int plcUnitId = 1;
    private int plcEnablerCoil = 0;
    private int plcPollingInterval = 500;
    private boolean plcEnabled = false;
    private boolean plcAutoStart = false;
    
    // Holding Registers
    private int hrTipoEmbalaje = 0;
    private int hrAncho = 1;
    private int hrLargo = 2;
    private int hrPila = 3;
    private int hrControl = 4;
    
    // Opciones generales
    private boolean startMinimized = false;
    private boolean startWithWindows = false;
    
    /** Constructor - carga configuracion si existe */
    public PLCCogerConfig() {
        load();
    }
    
    /** Carga la configuracion desde archivo INI */
    public void load() {
        File file = new File(CONFIG_FILE);
        
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                String currentSection = "";
                
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    
                    // Ignorar comentarios y lineas vacias
                    if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                        continue;
                    }
                    
                    // Detectar seccion
                    if (line.startsWith("[") && line.endsWith("]")) {
                        currentSection = line.substring(1, line.length() - 1).toLowerCase();
                        continue;
                    }
                    
                    // Parsear clave=valor
                    int eqIndex = line.indexOf("=");
                    if (eqIndex > 0) {
                        String key = line.substring(0, eqIndex).trim().toLowerCase();
                        String value = line.substring(eqIndex + 1).trim();
                        
                        parseValue(currentSection, key, value);
                    }
                }
                
                System.out.println("[CONFIG] Configuracion cargada desde " + CONFIG_FILE);
                
            } catch (Exception e) {
                System.err.println("[CONFIG] Error cargando configuracion: " + e.getMessage());
            }
        } else {
            System.out.println("[CONFIG] Archivo " + CONFIG_FILE + " no existe, usando valores por defecto");
        }
    }
    
    /** Parsea un valor de configuracion */
    private void parseValue(String section, String key, String value) {
        try {
            switch (section) {
                case "api":
                    switch (key) {
                        case "endpoint": apiEndpoint = value; break;
                        case "token": apiToken = value; break;
                        case "ip": apiIP = value; break;
                        case "trabajo": apiTrabajo = value; break;
                        case "pollinginterval": apiPollingInterval = Integer.parseInt(value); break;
                        case "enabled": apiPollingEnabled = Boolean.parseBoolean(value); break;
                        case "autostart": apiAutoStart = Boolean.parseBoolean(value); break;
                    }
                    break;
                    
                case "plc":
                    switch (key) {
                        case "ip": plcIP = value; break;
                        case "port": plcPort = Integer.parseInt(value); break;
                        case "unitid": plcUnitId = Integer.parseInt(value); break;
                        case "enablercoil": plcEnablerCoil = Integer.parseInt(value); break;
                        case "pollinginterval": plcPollingInterval = Integer.parseInt(value); break;
                        case "enabled": plcEnabled = Boolean.parseBoolean(value); break;
                        case "autostart": plcAutoStart = Boolean.parseBoolean(value); break;
                    }
                    break;
                    
                case "holdingregisters":
                    switch (key) {
                        case "tipoembalaje": hrTipoEmbalaje = Integer.parseInt(value); break;
                        case "ancho": hrAncho = Integer.parseInt(value); break;
                        case "largo": hrLargo = Integer.parseInt(value); break;
                        case "pila": hrPila = Integer.parseInt(value); break;
                        case "control": hrControl = Integer.parseInt(value); break;
                    }
                    break;
                    
                case "opciones":
                    switch (key) {
                        case "startminimized": startMinimized = Boolean.parseBoolean(value); break;
                        case "startwithwindows": startWithWindows = Boolean.parseBoolean(value); break;
                    }
                    break;
            }
        } catch (NumberFormatException e) {
            System.err.println("[CONFIG] Error parseando " + section + "." + key + ": " + e.getMessage());
        }
    }
    
    /** Guarda la configuracion a archivo INI */
    public void save() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONFIG_FILE))) {
            writer.println("; PLC COGER - Archivo de Configuracion");
            writer.println("; Generado automaticamente");
            writer.println();
            
            writer.println("[API]");
            writer.println("Endpoint=" + apiEndpoint);
            writer.println("Token=" + apiToken);
            writer.println("IP=" + apiIP);
            writer.println("Trabajo=" + apiTrabajo);
            writer.println("PollingInterval=" + apiPollingInterval);
            writer.println("Enabled=" + apiPollingEnabled);
            writer.println("AutoStart=" + apiAutoStart);
            writer.println();
            
            writer.println("[PLC]");
            writer.println("IP=" + plcIP);
            writer.println("Port=" + plcPort);
            writer.println("UnitId=" + plcUnitId);
            writer.println("EnablerCoil=" + plcEnablerCoil);
            writer.println("PollingInterval=" + plcPollingInterval);
            writer.println("Enabled=" + plcEnabled);
            writer.println("AutoStart=" + plcAutoStart);
            writer.println();
            
            writer.println("[HoldingRegisters]");
            writer.println("TipoEmbalaje=" + hrTipoEmbalaje);
            writer.println("Ancho=" + hrAncho);
            writer.println("Largo=" + hrLargo);
            writer.println("Pila=" + hrPila);
            writer.println("Control=" + hrControl);
            writer.println();
            
            writer.println("[Opciones]");
            writer.println("StartMinimized=" + startMinimized);
            writer.println("StartWithWindows=" + startWithWindows);
            
            System.out.println("[CONFIG] Configuracion guardada en " + CONFIG_FILE);
            
        } catch (Exception e) {
            System.err.println("[CONFIG] Error guardando configuracion: " + e.getMessage());
        }
    }
    
    // ============ GETTERS Y SETTERS ============
    
    // API
    public String getApiEndpoint() { return apiEndpoint; }
    public void setApiEndpoint(String apiEndpoint) { this.apiEndpoint = apiEndpoint; }
    
    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }
    
    public String getApiIP() { return apiIP; }
    public void setApiIP(String apiIP) { this.apiIP = apiIP; }
    
    public String getApiTrabajo() { return apiTrabajo; }
    public void setApiTrabajo(String apiTrabajo) { this.apiTrabajo = apiTrabajo; }
    
    public int getApiPollingInterval() { return apiPollingInterval; }
    public void setApiPollingInterval(int apiPollingInterval) { this.apiPollingInterval = apiPollingInterval; }
    
    public boolean isApiPollingEnabled() { return apiPollingEnabled; }
    public void setApiPollingEnabled(boolean apiPollingEnabled) { this.apiPollingEnabled = apiPollingEnabled; }
    
    public boolean isApiAutoStart() { return apiAutoStart; }
    public void setApiAutoStart(boolean apiAutoStart) { this.apiAutoStart = apiAutoStart; }
    
    // PLC
    public String getPlcIP() { return plcIP; }
    public void setPlcIP(String plcIP) { this.plcIP = plcIP; }
    
    public int getPlcPort() { return plcPort; }
    public void setPlcPort(int plcPort) { this.plcPort = plcPort; }
    
    public int getPlcUnitId() { return plcUnitId; }
    public void setPlcUnitId(int plcUnitId) { this.plcUnitId = plcUnitId; }
    
    public int getPlcEnablerCoil() { return plcEnablerCoil; }
    public void setPlcEnablerCoil(int plcEnablerCoil) { this.plcEnablerCoil = plcEnablerCoil; }
    
    public int getPlcPollingInterval() { return plcPollingInterval; }
    public void setPlcPollingInterval(int plcPollingInterval) { this.plcPollingInterval = plcPollingInterval; }
    
    public boolean isPlcEnabled() { return plcEnabled; }
    public void setPlcEnabled(boolean plcEnabled) { this.plcEnabled = plcEnabled; }
    
    public boolean isPlcAutoStart() { return plcAutoStart; }
    public void setPlcAutoStart(boolean plcAutoStart) { this.plcAutoStart = plcAutoStart; }
    
    // Holding Registers
    public int getHrTipoEmbalaje() { return hrTipoEmbalaje; }
    public void setHrTipoEmbalaje(int hrTipoEmbalaje) { this.hrTipoEmbalaje = hrTipoEmbalaje; }
    
    public int getHrAncho() { return hrAncho; }
    public void setHrAncho(int hrAncho) { this.hrAncho = hrAncho; }
    
    public int getHrLargo() { return hrLargo; }
    public void setHrLargo(int hrLargo) { this.hrLargo = hrLargo; }
    
    public int getHrPila() { return hrPila; }
    public void setHrPila(int hrPila) { this.hrPila = hrPila; }
    
    public int getHrControl() { return hrControl; }
    public void setHrControl(int hrControl) { this.hrControl = hrControl; }
    
    // Opciones
    public boolean isStartMinimized() { return startMinimized; }
    public void setStartMinimized(boolean startMinimized) { this.startMinimized = startMinimized; }
    
    public boolean isStartWithWindows() { return startWithWindows; }
    public void setStartWithWindows(boolean startWithWindows) { this.startWithWindows = startWithWindows; }
}
