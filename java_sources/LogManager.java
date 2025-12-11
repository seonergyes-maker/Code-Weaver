import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sistema de logs para la aplicación RFID.
 * Genera archivos de log diarios en la carpeta ./logs/
 * Formato: log2025_12_11.txt
 * 
 * Basado en logs_ficheros de VZEBRA.
 */
public class LogManager {
    
    private static LogManager instance;
    private static final String LOG_DIR = "./logs";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Obtiene la instancia singleton del LogManager.
     */
    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }
    
    /**
     * Constructor privado - usa getInstance().
     */
    private LogManager() {
        // Crear directorio de logs si no existe
        try {
            if (!Files.exists(Paths.get(LOG_DIR))) {
                Files.createDirectories(Paths.get(LOG_DIR));
                System.out.println("[LogManager] Directorio de logs creado: " + LOG_DIR);
            }
        } catch (IOException e) {
            System.err.println("[LogManager] Error creando directorio de logs: " + e.getMessage());
        }
    }
    
    /**
     * Genera el nombre del archivo de log para hoy.
     * Formato: ./logs/logAAAA_MM_DD.txt
     */
    private String getLogFileName() {
        LocalDate fecha = LocalDate.now();
        String logFile = LOG_DIR + "/log" + fecha.getYear() + "_" + 
                        fecha.getMonthValue() + "_" + 
                        fecha.getDayOfMonth() + ".txt";
        
        // Crear archivo si no existe
        try {
            if (!Files.exists(Paths.get(logFile))) {
                Files.createFile(Paths.get(logFile));
            }
        } catch (IOException e) {
            System.err.println("[LogManager] Error creando archivo de log: " + e.getMessage());
        }
        
        return logFile;
    }
    
    /**
     * Lee el contenido actual del archivo de log.
     */
    private StringBuilder readLogFile() {
        String path = getLogFileName();
        StringBuilder content = new StringBuilder();
        
        if (!Files.exists(Paths.get(path))) {
            return content;
        }
        
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            System.err.println("[LogManager] Error leyendo log: " + e.getMessage());
        }
        
        return content;
    }
    
    /**
     * Agrega una entrada al log.
     * 
     * @param clase Nombre de la clase que genera el log
     * @param mensaje Mensaje a registrar
     */
    public synchronized void log(String clase, String mensaje) {
        String path = getLogFileName();
        StringBuilder content = readLogFile();
        
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
        String entry = "[" + clase + "] [" + timestamp + "] " + mensaje;
        
        content.append(entry).append("\n");
        
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path))) {
            writer.write(content.toString());
        } catch (IOException e) {
            System.err.println("[LogManager] Error escribiendo log: " + e.getMessage());
        }
        
        // También imprimir en consola
        System.out.println(entry);
    }
    
    /**
     * Log de información.
     */
    public void info(String clase, String mensaje) {
        log(clase, "[INFO] " + mensaje);
    }
    
    /**
     * Log de advertencia.
     */
    public void warn(String clase, String mensaje) {
        log(clase, "[WARN] " + mensaje);
    }
    
    /**
     * Log de error.
     */
    public void error(String clase, String mensaje) {
        log(clase, "[ERROR] " + mensaje);
    }
    
    /**
     * Log de error con excepción.
     */
    public void error(String clase, String mensaje, Exception e) {
        log(clase, "[ERROR] " + mensaje + " - " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
    
    /**
     * Log de depuración.
     */
    public void debug(String clase, String mensaje) {
        log(clase, "[DEBUG] " + mensaje);
    }
    
    /**
     * Log de tag RFID leído.
     */
    public void logTag(String epc, int antena, int rssi, boolean enviadoAPI) {
        String estado = enviadoAPI ? "API:OK" : "API:--";
        log("RFID", "[TAG] EPC=" + epc + " ANT=" + antena + " RSSI=" + rssi + " " + estado);
    }
    
    /**
     * Log de conexión al lector.
     */
    public void logConnection(String ip, boolean connected) {
        if (connected) {
            info("RFID", "Conectado al lector: " + ip);
        } else {
            info("RFID", "Desconectado del lector: " + ip);
        }
    }
    
    /**
     * Log de envío a API.
     */
    public void logAPIRequest(String url, int responseCode) {
        if (responseCode == 200) {
            info("API", "Petición exitosa: " + url);
        } else {
            warn("API", "Petición fallida (HTTP " + responseCode + "): " + url);
        }
    }
    
    /**
     * Obtiene la ruta del archivo de log actual.
     */
    public String getCurrentLogPath() {
        return getLogFileName();
    }
}
