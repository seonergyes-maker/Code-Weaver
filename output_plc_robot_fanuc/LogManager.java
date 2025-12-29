import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Gestor de logs para PLC ROBOT FANUC v1.0.2 by Daemon4
 * Mantiene dos archivos de log separados:
 * - tags_YYYYMMDD.log: Tags gestionados
 * - errores_YYYYMMDD.log: Errores del sistema
 */
public class LogManager {
    
    private static final String LOG_DIR = "logs";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
    
    private static LogManager instance;
    
    private PrintWriter tagsWriter;
    private PrintWriter erroresWriter;
    private String currentDate;
    
    /** Obtiene la instancia singleton */
    public static synchronized LogManager getInstance() {
        if (instance == null) {
            instance = new LogManager();
        }
        return instance;
    }
    
    /** Constructor privado */
    private LogManager() {
        // Crear directorio de logs si no existe
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        openLogFiles();
    }
    
    /** Abre los archivos de log del dia actual */
    private void openLogFiles() {
        try {
            String today = FILE_DATE_FORMAT.format(new Date());
            
            // Si cambio el dia, cerrar archivos anteriores
            if (currentDate != null && !currentDate.equals(today)) {
                closeLogFiles();
            }
            
            currentDate = today;
            
            // Abrir archivo de tags
            File tagsFile = new File(LOG_DIR, "tags_" + today + ".log");
            tagsWriter = new PrintWriter(new FileWriter(tagsFile, true), true);
            
            // Abrir archivo de errores
            File erroresFile = new File(LOG_DIR, "errores_" + today + ".log");
            erroresWriter = new PrintWriter(new FileWriter(erroresFile, true), true);
            
        } catch (Exception e) {
            System.err.println("[LOG] Error abriendo archivos de log: " + e.getMessage());
        }
    }
    
    /** Cierra los archivos de log */
    private void closeLogFiles() {
        if (tagsWriter != null) {
            tagsWriter.close();
            tagsWriter = null;
        }
        if (erroresWriter != null) {
            erroresWriter.close();
            erroresWriter = null;
        }
    }
    
    /** Verifica si hay que rotar los logs (cambio de dia) */
    private void checkRotation() {
        String today = FILE_DATE_FORMAT.format(new Date());
        if (!today.equals(currentDate)) {
            openLogFiles();
        }
    }
    
    /** Registra un tag gestionado */
    public synchronized void logTag(String action, String tag, String codigo, String descripcion) {
        checkRotation();
        
        String timestamp = DATE_FORMAT.format(new Date());
        String logLine = String.format("[%s] %s | Tag: %s | Codigo: %s | %s",
            timestamp, action, tag, codigo, descripcion);
        
        if (tagsWriter != null) {
            tagsWriter.println(logLine);
        }
        
        System.out.println("[TAG] " + logLine);
    }
    
    /** Registra un tag enviado al PLC */
    public void logTagEnviado(String tag, String codigo, int ancho, int largo, int tipo) {
        logTag("ENVIADO_PLC", tag, codigo, 
            String.format("Ancho=%d, Largo=%d, Tipo=%d", ancho, largo, tipo));
    }
    
    /** Registra una baja de tag */
    public void logTagBaja(String tag, String codigo, int orden) {
        logTag("BAJA_API", tag, codigo, "Orden=" + orden);
    }
    
    /** Registra un error */
    public synchronized void logError(String componente, String mensaje) {
        checkRotation();
        
        String timestamp = DATE_FORMAT.format(new Date());
        String logLine = String.format("[%s] [%s] %s", timestamp, componente, mensaje);
        
        if (erroresWriter != null) {
            erroresWriter.println(logLine);
        }
        
        System.err.println("[ERROR] " + logLine);
    }
    
    /** Registra un error con excepcion */
    public synchronized void logError(String componente, String mensaje, Exception e) {
        checkRotation();
        
        String timestamp = DATE_FORMAT.format(new Date());
        String logLine = String.format("[%s] [%s] %s - %s: %s", 
            timestamp, componente, mensaje, e.getClass().getSimpleName(), e.getMessage());
        
        if (erroresWriter != null) {
            erroresWriter.println(logLine);
            e.printStackTrace(erroresWriter);
        }
        
        System.err.println("[ERROR] " + logLine);
    }
    
    /** Registra informacion general */
    public synchronized void logInfo(String componente, String mensaje) {
        String timestamp = DATE_FORMAT.format(new Date());
        System.out.println(String.format("[%s] [%s] %s", timestamp, componente, mensaje));
    }
    
    /** Cierra el gestor de logs */
    public void shutdown() {
        closeLogFiles();
    }
}
