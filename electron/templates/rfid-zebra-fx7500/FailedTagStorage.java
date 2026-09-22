// Clase para gestionar lecturas fallidas y reintentos

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Gestiona el almacenamiento y reintento de lecturas fallidas.
 * Guarda las lecturas en un archivo JSON y permite reintentarlas.
 */
public class FailedTagStorage {
    
    private static final String STORAGE_DIR = "./failed_readings";
    private final LogManager logger;
    private final ScheduledExecutorService retryExecutor;
    private volatile boolean retryEnabled = true;
    private int retryIntervalSeconds = 60;
    
    public FailedTagStorage(LogManager logger) {
        this.logger = logger;
        this.retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FailedTag-Retry");
            t.setDaemon(true);
            return t;
        });
        
        // Crear directorio si no existe
        try {
            Files.createDirectories(Paths.get(STORAGE_DIR));
        } catch (IOException e) {
            logger.error("FailedTagStorage", "Error creando directorio: " + e.getMessage());
        }
    }
    
    /**
     * Clase interna para representar una lectura fallida.
     */
    public static class FailedReading {
        public String timestamp;
        public String ip;
        public int antena;
        public int rssi;
        public String tag;
        public int intentos;
        
        public FailedReading() {}
        
        public FailedReading(String ip, int antena, int rssi, String tag) {
            this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            this.ip = ip;
            this.antena = antena;
            this.rssi = rssi;
            this.tag = tag;
            this.intentos = 0;
        }
        
        public String toJson() {
            return String.format(
                "{\"timestamp\":\"%s\",\"ip\":\"%s\",\"antena\":%d,\"rssi\":%d,\"tag\":\"%s\",\"intentos\":%d}",
                timestamp, ip, antena, rssi, tag, intentos);
        }
        
        public static FailedReading fromJson(String json) {
            FailedReading r = new FailedReading();
            r.timestamp = extractString(json, "timestamp");
            r.ip = extractString(json, "ip");
            r.antena = extractInt(json, "antena");
            r.rssi = extractInt(json, "rssi");
            r.tag = extractString(json, "tag");
            r.intentos = extractInt(json, "intentos");
            return r;
        }
        
        private static String extractString(String json, String key) {
            try {
                String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
                return m.find() ? m.group(1) : "";
            } catch (Exception e) { return ""; }
        }
        
        private static int extractInt(String json, String key) {
            try {
                String pattern = "\"" + key + "\"\\s*:\\s*(-?\\d+)";
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(pattern).matcher(json);
                return m.find() ? Integer.parseInt(m.group(1)) : 0;
            } catch (Exception e) { return 0; }
        }
    }
    
    /**
     * Almacena una lectura fallida.
     */
    public void almacenarLecturaFallida(String ip, int antena, int rssi, String tag) {
        FailedReading reading = new FailedReading(ip, antena, rssi, tag);
        String fileName = getFileName();
        
        try {
            List<String> readings = new ArrayList<>();
            Path filePath = Paths.get(STORAGE_DIR, fileName);
            
            // Leer existentes
            if (Files.exists(filePath)) {
                String content = new String(Files.readAllBytes(filePath));
                // Extraer array de lecturas
                int start = content.indexOf("[");
                int end = content.lastIndexOf("]");
                if (start >= 0 && end > start) {
                    String arrayContent = content.substring(start + 1, end).trim();
                    if (!arrayContent.isEmpty()) {
                        // Parsear lecturas existentes
                        int depth = 0;
                        StringBuilder current = new StringBuilder();
                        for (char c : arrayContent.toCharArray()) {
                            if (c == '{') depth++;
                            if (c == '}') depth--;
                            current.append(c);
                            if (depth == 0 && current.length() > 0) {
                                String r = current.toString().trim();
                                if (r.startsWith("{")) {
                                    readings.add(r);
                                }
                                current = new StringBuilder();
                            }
                        }
                    }
                }
            }
            
            // Agregar nueva lectura
            readings.add(reading.toJson());
            
            // Guardar
            StringBuilder json = new StringBuilder();
            json.append("{\"fecha\":\"").append(LocalDate.now().toString()).append("\",\n");
            json.append("\"lecturas_fallidas\":[\n");
            for (int i = 0; i < readings.size(); i++) {
                json.append("  ").append(readings.get(i));
                if (i < readings.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("]}");
            
            Files.write(filePath, json.toString().getBytes());
            logger.info("FailedTagStorage", "Lectura fallida almacenada: " + tag);
            
        } catch (Exception e) {
            logger.error("FailedTagStorage", "Error almacenando lectura: " + e.getMessage());
        }
    }
    
    /**
     * Obtiene las lecturas fallidas pendientes.
     */
    public List<FailedReading> obtenerLecturasFallidas() {
        List<FailedReading> result = new ArrayList<>();
        String fileName = getFileName();
        Path filePath = Paths.get(STORAGE_DIR, fileName);
        
        try {
            if (Files.exists(filePath)) {
                String content = new String(Files.readAllBytes(filePath));
                int start = content.indexOf("[");
                int end = content.lastIndexOf("]");
                if (start >= 0 && end > start) {
                    String arrayContent = content.substring(start + 1, end).trim();
                    if (!arrayContent.isEmpty()) {
                        int depth = 0;
                        StringBuilder current = new StringBuilder();
                        for (char c : arrayContent.toCharArray()) {
                            if (c == '{') depth++;
                            if (c == '}') depth--;
                            current.append(c);
                            if (depth == 0 && current.length() > 0) {
                                String r = current.toString().trim();
                                if (r.startsWith("{")) {
                                    result.add(FailedReading.fromJson(r));
                                }
                                current = new StringBuilder();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("FailedTagStorage", "Error leyendo lecturas fallidas: " + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * Elimina una lectura del archivo (cuando se envía exitosamente).
     */
    public void eliminarLectura(String tag) {
        String fileName = getFileName();
        Path filePath = Paths.get(STORAGE_DIR, fileName);
        
        try {
            if (!Files.exists(filePath)) return;
            
            List<FailedReading> readings = obtenerLecturasFallidas();
            readings.removeIf(r -> r.tag.equals(tag));
            
            // Guardar archivo actualizado
            StringBuilder json = new StringBuilder();
            json.append("{\"fecha\":\"").append(LocalDate.now().toString()).append("\",\n");
            json.append("\"lecturas_fallidas\":[\n");
            for (int i = 0; i < readings.size(); i++) {
                json.append("  ").append(readings.get(i).toJson());
                if (i < readings.size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("]}");
            
            Files.write(filePath, json.toString().getBytes());
            
        } catch (Exception e) {
            logger.error("FailedTagStorage", "Error eliminando lectura: " + e.getMessage());
        }
    }
    
    /**
     * Inicia el proceso de reintentos automáticos.
     */
    public void iniciarReintentos(APIClient apiClient) {
        if (!retryEnabled) return;
        
        retryExecutor.scheduleAtFixedRate(() -> {
            try {
                procesarReintentos(apiClient);
            } catch (Exception e) {
                logger.error("FailedTagStorage", "Error en reintentos: " + e.getMessage());
            }
        }, retryIntervalSeconds, retryIntervalSeconds, TimeUnit.SECONDS);
        
        logger.info("FailedTagStorage", "Reintentos automáticos iniciados (cada " + retryIntervalSeconds + "s)");
    }
    
    /**
     * Procesa y reintenta enviar lecturas fallidas.
     */
    public void procesarReintentos(APIClient apiClient) {
        if (apiClient == null) return;
        
        List<FailedReading> fallidas = obtenerLecturasFallidas();
        if (fallidas.isEmpty()) return;
        
        logger.info("FailedTagStorage", "Reintentando " + fallidas.size() + " lecturas fallidas...");
        
        int exitosas = 0;
        for (FailedReading reading : fallidas) {
            try {
                boolean exito = apiClient.enviarTagAPI(reading.tag, reading.antena, reading.rssi);
                if (exito) {
                    eliminarLectura(reading.tag);
                    exitosas++;
                    logger.info("FailedTagStorage", "Reintento exitoso: " + reading.tag);
                }
            } catch (Exception e) {
                logger.error("FailedTagStorage", "Error reintentando " + reading.tag + ": " + e.getMessage());
            }
        }
        
        logger.info("FailedTagStorage", "Reintentos completados: " + exitosas + "/" + fallidas.size() + " exitosos");
    }
    
    /**
     * Obtiene el número de lecturas fallidas pendientes.
     */
    public int contarPendientes() {
        return obtenerLecturasFallidas().size();
    }
    
    public void setRetryEnabled(boolean enabled) {
        this.retryEnabled = enabled;
    }
    
    public void setRetryIntervalSeconds(int seconds) {
        this.retryIntervalSeconds = seconds;
    }
    
    public void shutdown() {
        retryExecutor.shutdown();
    }
    
    private String getFileName() {
        return "failed_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd")) + ".json";
    }
}
