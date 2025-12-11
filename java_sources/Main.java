import java.io.*;
import java.util.*;
import javax.swing.*;

/**
 * Punto de entrada principal de la aplicación RFID Zebra FX7500.
 * Proporciona modo GUI (por defecto) y modo consola para operación sin interfaz gráfica.
 * 
 * Argumentos de línea de comandos soportados:
 * --nogui       : Ejecutar en modo consola sin interfaz gráfica
 * --config FILE : Cargar configuración desde archivo JSON
 * --ip ADDRESS  : Establecer IP del lector (override de config)
 * --autostart   : Conectar automáticamente al iniciar
 * --help, -h    : Mostrar ayuda
 * 
 * @author Sistema RFID
 * @version 2.0
 */
public class Main {
    
    /** Versión de la aplicación */
    public static final String VERSION = "2.0.0";
    
    /** Nombre de la aplicación */
    public static final String APP_NAME = "Zebra FX7500 RFID Manager by Daemon4 v1.2";
    
    /** Ruta por defecto del archivo de configuración */
    public static final String DEFAULT_CONFIG_PATH = "rfid_config.json";
    
    /** Configuración cargada */
    private static RFIDConfig config;
    
    /** Conexión al lector usando Zebra SDK */
    private static ZebraSDKConnection connection;
    
    /** Cliente API */
    private static APIClient apiClient;
    
    /** Estadísticas */
    private static ReaderStatistics statistics;
    
    /** Ventana principal (modo GUI) */
    private static RFIDMainWindow mainWindow;
    
    /** Flag para modo consola */
    private static boolean noGui = false;
    
    /** Flag para auto-conectar */
    private static boolean autoStart = false;
    
    /** Ruta del archivo de configuración */
    private static String configPath = DEFAULT_CONFIG_PATH;
    
    /** IP del lector (override) */
    private static String readerIp = null;
    
    /** Scanner para entrada de consola */
    private static Scanner scanner;
    
    /** Flag para indicar que la aplicación está corriendo */
    private static volatile boolean running = true;
    
    /**
     * Método principal de la aplicación.
     * 
     * @param args Argumentos de línea de comandos
     */
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("  " + APP_NAME);
        System.out.println("  Versión " + VERSION);
        System.out.println("========================================");
        System.out.println();
        
        // Parsear argumentos
        if (!parseArguments(args)) {
            return;
        }
        
        // Cargar configuración
        loadConfiguration();
        
        // Aplicar overrides de línea de comandos
        if (readerIp != null && !readerIp.isEmpty()) {
            config.setReaderIP(readerIp);
            System.out.println("[Config] IP del lector establecida a: " + readerIp);
        }
        
        // Iniciar en modo apropiado
        if (noGui) {
            runConsoleMode();
        } else {
            runGuiMode();
        }
    }
    
    /**
     * Parsea los argumentos de línea de comandos.
     * 
     * @param args Argumentos a parsear
     * @return true si se debe continuar, false si se debe salir
     */
    private static boolean parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            
            switch (arg) {
                case "--nogui":
                case "-nogui":
                case "--headless":
                case "-headless":
                    noGui = true;
                    System.out.println("[Args] Modo consola/headless habilitado");
                    break;
                    
                case "--config":
                case "-config":
                case "-c":
                    if (i + 1 < args.length) {
                        configPath = args[++i];
                        System.out.println("[Args] Archivo de configuración: " + configPath);
                    } else {
                        System.err.println("Error: --config requiere un argumento");
                        return false;
                    }
                    break;
                    
                case "--ip":
                case "-ip":
                    if (i + 1 < args.length) {
                        readerIp = args[++i];
                        System.out.println("[Args] IP del lector: " + readerIp);
                    } else {
                        System.err.println("Error: --ip requiere un argumento");
                        return false;
                    }
                    break;
                    
                case "--autostart":
                case "-autostart":
                case "-a":
                    autoStart = true;
                    System.out.println("[Args] Auto-conexión habilitada");
                    break;
                    
                case "--help":
                case "-help":
                case "-h":
                case "/?":
                    printHelp();
                    return false;
                    
                case "--version":
                case "-version":
                case "-v":
                    System.out.println(APP_NAME + " versión " + VERSION);
                    return false;
                    
                default:
                    if (arg.startsWith("-")) {
                        System.err.println("Argumento desconocido: " + args[i]);
                        printHelp();
                        return false;
                    }
                    break;
            }
        }
        
        return true;
    }
    
    /**
     * Imprime la ayuda de uso.
     */
    private static void printHelp() {
        System.out.println();
        System.out.println("Uso: java -jar Main.jar [opciones]");
        System.out.println();
        System.out.println("Opciones:");
        System.out.println("  --nogui, --headless     Ejecutar en modo consola sin GUI (para servicios)");
        System.out.println("  --config, -c FILE       Cargar configuración desde archivo JSON");
        System.out.println("  --ip ADDRESS            Establecer IP del lector (override)");
        System.out.println("  --autostart, -a         Conectar automáticamente al iniciar");
        System.out.println("  --help, -h              Mostrar esta ayuda");
        System.out.println("  --version, -v           Mostrar versión");
        System.out.println();
        System.out.println("Ejemplos:");
        System.out.println("  java -jar Main.jar                              # Iniciar GUI");
        System.out.println("  java -jar Main.jar --nogui --ip 192.168.1.100   # Modo consola");
        System.out.println("  java -jar Main.jar --autostart                  # Auto-conectar");
        System.out.println();
    }
    
    /**
     * Carga la configuración desde archivo o crea una nueva.
     */
    private static void loadConfiguration() {
        config = new RFIDConfig();
        File configFile = new File(configPath);
        
        if (configFile.exists()) {
            try {
                config = RFIDConfig.loadFromFile(configPath);
                System.out.println("[Config] Configuración cargada desde: " + configPath);
            } catch (Exception e) {
                System.err.println("[Config] Error al cargar configuración: " + e.getMessage());
                System.out.println("[Config] Usando configuración por defecto");
            }
        } else {
            System.out.println("[Config] Archivo no encontrado, usando configuración por defecto");
        }
        
        statistics = new ReaderStatistics();
    }
    
    /**
     * Ejecuta la aplicación en modo GUI.
     */
    private static void runGuiMode() {
        System.out.println("[GUI] Iniciando interfaz gráfica...");
        
        SwingUtilities.invokeLater(() -> {
            try {
                mainWindow = new RFIDMainWindow(config);
                mainWindow.setVisible(true);
                
                if (autoStart) {
                    System.out.println("[GUI] Auto-conexión iniciada...");
                }
                
            } catch (Exception e) {
                System.err.println("[GUI] Error al iniciar GUI: " + e.getMessage());
                e.printStackTrace();
                
                int choice = JOptionPane.showConfirmDialog(null,
                    "Error al iniciar la interfaz gráfica.\n" +
                    "¿Desea continuar en modo consola?\n\n" +
                    "Error: " + e.getMessage(),
                    "Error de GUI",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE);
                
                if (choice == JOptionPane.YES_OPTION) {
                    noGui = true;
                    runConsoleMode();
                } else {
                    System.exit(1);
                }
            }
        });
    }
    
    /**
     * Ejecuta la aplicación en modo consola.
     */
    private static void runConsoleMode() {
        System.out.println("[Console] Modo consola iniciado");
        System.out.println();
        
        scanner = new Scanner(System.in);
        
        // Registrar shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Console] Cerrando aplicación...");
            shutdown();
        }));
        
        // Auto-conectar si está habilitado
        if (autoStart) {
            System.out.println("[Console] Conectando automáticamente a " + config.getReaderIP() + "...");
            connectReader();
        }
        
        // Bucle principal del menú
        while (running) {
            printMenu();
            String choice = readLine("Opción: ").trim();
            
            try {
                processMenuChoice(choice);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
            
            System.out.println();
        }
        
        shutdown();
    }
    
    /**
     * Imprime el menú principal de consola.
     */
    private static void printMenu() {
        System.out.println("=== MENÚ PRINCIPAL ===");
        System.out.println("1. Conectar al lector");
        System.out.println("2. Desconectar");
        System.out.println("3. Ver estado de conexión");
        System.out.println("4. Configurar IP del lector");
        System.out.println("5. Control GPIO");
        System.out.println("6. Ver estadísticas");
        System.out.println("7. Iniciar lectura continua");
        System.out.println("8. Detener lectura");
        System.out.println("9. Configurar API");
        System.out.println("0. Salir");
        System.out.println("======================");
    }
    
    /**
     * Procesa la opción del menú seleccionada.
     * 
     * @param choice Opción seleccionada
     */
    private static void processMenuChoice(String choice) {
        switch (choice) {
            case "1":
                connectReader();
                break;
                
            case "2":
                disconnectReader();
                break;
                
            case "3":
                showConnectionStatus();
                break;
                
            case "4":
                configureReaderIp();
                break;
                
            case "5":
                gpioMenu();
                break;
                
            case "6":
                showStatistics();
                break;
                
            case "7":
                startReading();
                break;
                
            case "8":
                stopReading();
                break;
                
            case "9":
                configureApi();
                break;
                
            case "0":
            case "q":
            case "exit":
            case "quit":
                running = false;
                break;
                
            default:
                System.out.println("Opción no válida");
                break;
        }
    }
    
    /**
     * Conecta al lector RFID usando Zebra SDK.
     */
    private static void connectReader() {
        if (connection != null && connection.isConnected()) {
            System.out.println("Ya está conectado al lector");
            return;
        }
        
        System.out.println("Conectando a " + config.getReaderIP() + ":" + config.getReaderPort() + "...");
        
        try {
            connection = new ZebraSDKConnection(config);
            
            // Configurar callbacks
            connection.setTagCallback(tag -> processTag(tag));
            connection.setStatusCallback(System.out::println);
            connection.setErrorCallback(System.err::println);
            
            if (connection.connect()) {
                System.out.println("¡Conexión exitosa!");
                statistics.recordConnection();
            } else {
                System.err.println("No se pudo establecer la conexión");
                connection = null;
            }
            
        } catch (Exception e) {
            System.err.println("Error de conexión: " + e.getMessage());
            statistics.recordError(e.getMessage());
            connection = null;
        }
    }
    
    /**
     * Desconecta del lector RFID.
     */
    private static void disconnectReader() {
        if (connection == null || !connection.isConnected()) {
            System.out.println("No hay conexión activa");
            return;
        }
        
        try {
            connection.disconnect();
            System.out.println("Desconectado correctamente");
            statistics.recordDisconnection();
            
        } catch (Exception e) {
            System.err.println("Error al desconectar: " + e.getMessage());
        }
        
        connection = null;
    }
    
    /**
     * Muestra el estado de la conexión.
     */
    private static void showConnectionStatus() {
        System.out.println("\n=== ESTADO DE CONEXIÓN ===");
        
        if (connection != null && connection.isConnected()) {
            System.out.println("Estado: CONECTADO");
            System.out.println("IP: " + config.getReaderIP());
            System.out.println("Puerto: " + config.getReaderPort());
            System.out.println("Modelo: " + connection.getModelName());
            System.out.println("Firmware: " + connection.getFirmwareVersion());
            System.out.println("Antenas: " + connection.getNumAntennas());
            System.out.println("Leyendo: " + (connection.isReading() ? "SÍ" : "NO"));
            System.out.println("Uptime: " + statistics.getUptimeFormatted());
            System.out.println("Reconexiones: " + statistics.getReconnectCount());
        } else {
            System.out.println("Estado: DESCONECTADO");
            System.out.println("IP configurada: " + config.getReaderIP());
        }
    }
    
    /**
     * Configura la IP del lector.
     */
    private static void configureReaderIp() {
        System.out.println("IP actual: " + config.getReaderIP());
        String newIp = readLine("Nueva IP (Enter para mantener): ").trim();
        
        if (!newIp.isEmpty()) {
            config.setReaderIP(newIp);
            System.out.println("IP actualizada a: " + newIp);
            
            try {
                config.saveToFile(configPath);
                System.out.println("Configuración guardada");
            } catch (Exception e) {
                System.err.println("Error al guardar configuración: " + e.getMessage());
            }
        }
    }
    
    /**
     * Submenú de control GPIO.
     */
    private static void gpioMenu() {
        if (connection == null || !connection.isConnected()) {
            System.out.println("Debe conectar al lector primero");
            return;
        }
        
        boolean[] gpoStates = new boolean[4];
        
        while (true) {
            System.out.println("\n=== CONTROL GPIO ===");
            System.out.println("1-4. Toggle GPO 1-4");
            System.out.println("5. Ver estado GPO");
            System.out.println("6. Ver estado GPI");
            System.out.println("7. Desactivar todos los GPO");
            System.out.println("0. Volver");
            
            String choice = readLine("Opción: ").trim();
            
            switch (choice) {
                case "1": case "2": case "3": case "4":
                    int port = Integer.parseInt(choice);
                    gpoStates[port - 1] = !gpoStates[port - 1];
                    boolean newState = gpoStates[port - 1];
                    if (connection.setGPO(port, newState)) {
                        System.out.println("GPO " + port + " -> " + (newState ? "ALTO" : "BAJO"));
                    } else {
                        System.out.println("Error al cambiar GPO " + port);
                        gpoStates[port - 1] = !gpoStates[port - 1];
                    }
                    break;
                    
                case "5":
                    System.out.println("Estado GPO: " + Arrays.toString(gpoStates));
                    break;
                    
                case "6":
                    System.out.print("Estado GPI: [");
                    for (int i = 1; i <= 4; i++) {
                        boolean gpiState = connection.getGPI(i);
                        System.out.print(gpiState ? "HIGH" : "LOW");
                        if (i < 4) System.out.print(", ");
                    }
                    System.out.println("]");
                    break;
                    
                case "7":
                    for (int i = 1; i <= 4; i++) {
                        connection.setGPO(i, false);
                        gpoStates[i - 1] = false;
                    }
                    System.out.println("Todos los GPO desactivados");
                    break;
                    
                case "0":
                    return;
                    
                default:
                    System.out.println("Opción no válida");
            }
        }
    }
    
    /**
     * Muestra las estadísticas actuales.
     */
    private static void showStatistics() {
        System.out.println("\n" + statistics.toString());
    }
    
    /**
     * Inicia la lectura continua de tags.
     */
    private static void startReading() {
        if (connection == null || !connection.isConnected()) {
            System.out.println("Debe conectar al lector primero");
            return;
        }
        
        System.out.println("Iniciando lectura continua...");
        
        if (connection.startReading()) {
            System.out.println("Lectura iniciada. Los tags se mostrarán automáticamente.");
            System.out.println("Use la opción 8 para detener la lectura.");
        } else {
            System.err.println("Error al iniciar la lectura");
        }
    }
    
    /**
     * Detiene la lectura de tags.
     */
    private static void stopReading() {
        if (connection == null) {
            System.out.println("No hay conexión activa");
            return;
        }
        
        if (connection.stopReading()) {
            System.out.println("Lectura detenida");
        } else {
            System.err.println("Error al detener la lectura");
        }
    }
    
    /**
     * Procesa un tag leído y lo muestra en consola.
     * 
     * @param tag Datos del tag leído
     */
    private static void processTag(TagData tag) {
        statistics.recordTagRead(tag);
        
        System.out.println("Tag: " + tag.getEpc() + 
            " | RSSI: " + String.format("%.1f", tag.getRssi()) +
            " | Antena: " + tag.getAntennaPort() +
            " | Lecturas: " + tag.getReadCount());
        
        // Enviar a API si está configurado
        if (apiClient != null && apiClient.isRunning()) {
            apiClient.enqueue(tag);
        }
    }
    
    /**
     * Configura los parámetros de la API.
     */
    private static void configureApi() {
        System.out.println("\n=== CONFIGURACIÓN DE API ===");
        System.out.println("Endpoint actual: " + (config.getApiEndpoint() != null ? config.getApiEndpoint() : "(no configurado)"));
        
        String endpoint = readLine("Nuevo endpoint (Enter para mantener): ").trim();
        if (!endpoint.isEmpty()) {
            config.setApiEndpoint(endpoint);
        }
        
        String apiKey = readLine("API Key (Enter para mantener): ").trim();
        if (!apiKey.isEmpty()) {
            config.setApiKey(apiKey);
        }
        
        if (config.getApiEndpoint() != null && !config.getApiEndpoint().isEmpty()) {
            System.out.print("¿Iniciar cliente API? (s/n): ");
            String choice = readLine("").trim().toLowerCase();
            
            if (choice.equals("s") || choice.equals("si") || choice.equals("y")) {
                if (apiClient != null) {
                    apiClient.stop();
                }
                
                apiClient = new APIClient(config.getApiEndpoint(), config.getApiKey());
                apiClient.setSendResultHandler(result -> {
                    if (result.success) {
                        statistics.recordApiSuccess(result.tagCount, result.responseTimeMs);
                    } else {
                        statistics.recordApiFailure(result.tagCount);
                    }
                });
                apiClient.start();
                
                System.out.println("Cliente API iniciado");
            }
        }
        
        try {
            config.saveToFile(configPath);
            System.out.println("Configuración guardada");
        } catch (Exception e) {
            System.err.println("Error al guardar: " + e.getMessage());
        }
    }
    
    /**
     * Lee una línea de la consola.
     * 
     * @param prompt Mensaje a mostrar
     * @return Línea leída
     */
    private static String readLine(String prompt) {
        System.out.print(prompt);
        if (scanner != null && scanner.hasNextLine()) {
            return scanner.nextLine();
        }
        return "";
    }
    
    /**
     * Cierra todos los recursos.
     */
    private static void shutdown() {
        running = false;
        
        if (apiClient != null) {
            apiClient.close();
            apiClient = null;
        }
        
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                // Ignorar
            }
            connection = null;
        }
        
        if (mainWindow != null) {
            mainWindow.shutdown();
        }
        
        // Guardar configuración
        try {
            if (config != null) {
                config.saveToFile(configPath);
            }
        } catch (Exception e) {
            // Ignorar errores al guardar
        }
        
        System.out.println("Aplicación cerrada correctamente");
    }
}
