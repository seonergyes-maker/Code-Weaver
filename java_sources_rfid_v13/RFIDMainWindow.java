import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * Ventana principal de la aplicación GUI para el lector RFID Zebra FX7500.
 * Proporciona una interfaz gráfica con pestañas para gestionar la conexión,
 * configuración de antenas, monitoreo en tiempo real, API y GPIO.
 * 
 * Características principales:
 * - Tab 1: Panel de conexión (IP, puerto, botones conectar/desconectar, indicador de estado)
 * - Tab 2: Configuración de antenas (4 paneles con sliders de potencia, checkboxes de habilitación)
 * - Tab 3: Monitoreo en tiempo real (JTable con datos de tags, auto-refresh)
 * - Tab 4: Configuración de API (endpoint, key, botón de prueba)
 * - Tab 5: Control GPIO (botones GPO, indicadores de estado GPI)
 * - Barra de estado con estado de conexión y estadísticas
 * 
 * @author Sistema RFID
 * @version 2.0 - Migrado a ZebraSDKConnection
 */
public class RFIDMainWindow extends JFrame {
    
    private static final long serialVersionUID = 1L;
    
    /** Título de la ventana */
    private static final String WINDOW_TITLE = "Zebra FX7500 RFID Manager by Daemon4 v1.2";
    
    /** Ruta del archivo de configuración */
    private static final String CONFIG_FILE_PATH = "rfid_config.json";
    
    /** Tamaño por defecto de la ventana */
    private static final Dimension DEFAULT_SIZE = new Dimension(1024, 768);
    
    /** Intervalo de actualización del monitoreo en milisegundos */
    private static final int MONITOR_REFRESH_INTERVAL = 500;
    
    // ==================== Componentes de conexión ====================
    private JTextField ipField;
    private JSpinner portSpinner;
    private JButton connectButton;
    private JButton disconnectButton;
    private JButton startReadingButton;
    private JButton stopReadingButton;
    private JLabel connectionStatusLabel;
    private JPanel connectionIndicator;
    private volatile boolean isReading = false;
    
    // ==================== Componentes de PLC ====================
    private JCheckBox plcEnabledCheck;
    private JTextField plcIpField;
    private JSpinner plcPortSpinner;
    private JSpinner plcPollingSpinner;
    private JSpinner plcRefEnableSpinner;
    private JSpinner plcUnitIdEnableSpinner;
    private JSpinner plcRefTipoEmbalajeSpinner;
    private JSpinner plcUnitIdTipoEmbalajeSpinner;
    private JSpinner plcRefAnchoSpinner;
    private JSpinner plcUnitIdAnchoSpinner;
    private JSpinner plcRefLargoSpinner;
    private JSpinner plcUnitIdLargoSpinner;
    private JSpinner plcRefActivaSpinner;
    private JSpinner plcUnitIdActivaSpinner;
    private JLabel plcStatusLabel;
    private JPanel plcStatusIndicator;
    private JButton plcTestButton;
    private volatile boolean plcRunning = false;
    private Thread plcPollingThread;
    
    // ==================== Componentes de antenas ====================
    private JSlider[] powerSliders = new JSlider[4];
    private JCheckBox[] antennaEnableChecks = new JCheckBox[4];
    private JLabel[] powerLabels = new JLabel[4];
    private JSlider[] rssiSliders = new JSlider[4];
    private JCheckBox[] rssiFilterChecks = new JCheckBox[4];
    private JLabel[] rssiLabels = new JLabel[4];
    private JSpinner[] cableLossSpinners = new JSpinner[4];
    
    // ==================== Componentes de monitoreo ====================
    private JTable tagTable;
    private DefaultTableModel tagTableModel;
    private JLabel tagsReadLabel;
    private JLabel uniqueTagsLabel;
    private JLabel tpsLabel;
    private JButton clearTagsButton;
    private JCheckBox autoScrollCheck;
    private JToggleButton hexDecimalToggle;
    private boolean displayHexMode = true; // true = hexadecimal, false = decimal
    
    // ==================== Componentes de API ====================
    private JTextField apiEndpointField;
    private JPasswordField apiKeyField;
    private JTextField apiTrabajoField;
    private JButton testApiButton;
    private JLabel apiStatusLabel;
    private JCheckBox apiEnabledCheck;
    private JCheckBox apiStartControlCheck;
    private JCheckBox apiApiladoCheck;
    private JCheckBox clearQueueOnStopCheck;
    private JCheckBox apiPollingCheck;
    private JSpinner apiPollingIntervalSpinner;
    
    // ==================== Componentes de GPIO ====================
    private JToggleButton[] gpoButtons = new JToggleButton[4];
    private JPanel[] gpiIndicators = new JPanel[4];
    private JLabel[] gpiLabels = new JLabel[4];
    
    // ==================== Barra de estado ====================
    private JLabel statusLabel;
    private JLabel statsLabel;
    private JProgressBar activityIndicator;
    
    // ==================== Componentes de Herramientas ====================
    private JCheckBox duplicateFilterCheck;
    private JSpinner duplicateExpirationSpinner;
    private JCheckBox noExpirationCheck;
    private JButton generateWindowsServiceButton;
    private JButton generateUbuntuServiceButton;
    private JButton saveConfigButton;
    private JLabel duplicateStatsLabel;
    
    // ==================== Filtro de duplicados ====================
    private DuplicateFilter duplicateFilter;
    
    // ==================== Componentes de Configuración Avanzada ====================
    private JCheckBox rssiFilterCheck;
    private JSpinner rssiThresholdSpinner;
    private JLabel rssiStatsLabel;
    private JComboBox<String> sessionCombo;
    private JComboBox<String> targetCombo;
    private JSpinner tagPopulationSpinner;
    private JCheckBox denseReaderCheck;
    private JSpinner rfModeSpinner;
    private JCheckBox gpiTriggerCheck;
    private JComboBox<String> gpiTriggerPortCombo;
    private JComboBox<String> gpiTriggerStateCombo;
    private JCheckBox reportPhaseCheck;
    private JCheckBox reportChannelCheck;
    private JCheckBox autoConnectCheck;
    
    // ==================== Panel de pestañas ====================
    private JTabbedPane tabbedPane;
    
    // ==================== Modelo y controladores ====================
    private RFIDConfig config;
    private ZebraSDKConnection connection;
    private APIClient apiClient;
    private ReaderStatistics statistics;
    private LogManager logger = LogManager.getInstance();
    
    // Control de fecha de inicio API (estilo VZEBRA)
    private volatile long ultimaFechaInicioAPI = 0;
    private volatile int milisegundosParada = 100;
    
    // Almacenamiento de lecturas fallidas
    private FailedTagStorage failedTagStorage;
    
    // Throttling para envío a API (usa milisegundosParada de la respuesta)
    private volatile long ultimoEnvioAPI = 0;
    
    // Polling periódico a la API de inicio (estilo VZEBRA)
    private ScheduledExecutorService apiPollingExecutor;
    private volatile boolean apiPollingEnabled = false;
    private int apiPollingIntervalSeconds = 5;
    
    // ==================== Executor para actualizaciones ====================
    private ScheduledExecutorService updateExecutor;
    private volatile boolean running = true;
    
    // ==================== Cache de tags para la tabla ====================
    private final Map<String, TagData> tagCache = new ConcurrentHashMap<>();
    private final int maxTagsInTable = 1000;
    
    // ==================== Contador de filtrado RSSI ====================
    private volatile long rssiFilteredCount = 0;
    
    // ==================== System Tray ====================
    private TrayIcon trayIcon;
    private boolean minimizedToTray = false;
    
    /**
     * Constructor por defecto. Crea la ventana cargando configuración desde archivo.
     */
    public RFIDMainWindow() {
        this(loadConfigFromFile());
    }
    
    /**
     * Carga la configuración desde archivo o crea una nueva si no existe.
     * 
     * @return Configuración cargada o por defecto
     */
    private static RFIDConfig loadConfigFromFile() {
        java.io.File configFile = new java.io.File(CONFIG_FILE_PATH);
        if (configFile.exists()) {
            try {
                RFIDConfig loaded = RFIDConfig.loadFromFile(CONFIG_FILE_PATH);
                System.out.println("[Config] Configuración cargada desde: " + CONFIG_FILE_PATH);
                return loaded;
            } catch (Exception e) {
                System.err.println("[Config] Error al cargar configuración: " + e.getMessage());
                System.out.println("[Config] Usando configuración por defecto");
            }
        } else {
            System.out.println("[Config] Archivo no encontrado: " + CONFIG_FILE_PATH);
            System.out.println("[Config] Usando configuración por defecto");
        }
        return new RFIDConfig();
    }
    
    /**
     * Constructor con configuración.
     * 
     * @param config Configuración RFID a usar
     */
    public RFIDMainWindow(RFIDConfig config) {
        super(WINDOW_TITLE);
        this.config = config != null ? config : loadConfigFromFile();
        this.statistics = new ReaderStatistics();
        this.duplicateFilter = new DuplicateFilter(this.config.getDuplicateFilterExpiration() * 1000L);
        this.duplicateFilter.setEnabled(this.config.isDuplicateFilterEnabled());
        this.duplicateFilter.setNoExpiration(this.config.isDuplicateFilterNoExpiration());
        
        // Inicializar almacenamiento de lecturas fallidas
        this.failedTagStorage = new FailedTagStorage(logger);
        
        initializeLookAndFeel();
        initializeComponents();
        initializeLayout();
        initializeEventHandlers();
        loadConfigToUI();
        
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(DEFAULT_SIZE);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);
        
        initializeSystemTray();
        
        startUpdateTimer();
        
        if (config.isAutoConnectEnabled()) {
            SwingUtilities.invokeLater(() -> {
                System.out.println("[AutoConnect] Auto-conexión habilitada, intentando conectar a " + config.getReaderIP() + "...");
                statusLabel.setText("Auto-conectando a " + config.getReaderIP() + "...");
                javax.swing.Timer timer = new javax.swing.Timer(1500, e -> {
                    connect();
                });
                timer.setRepeats(false);
                timer.start();
            });
        }
    }
    
    /**
     * Inicializa el look and feel de la aplicación con tema oscuro moderno.
     */
    private void initializeLookAndFeel() {
        ModernUIStyle.applyDarkTheme();
    }
    
    /**
     * Inicializa el icono en la bandeja del sistema (System Tray).
     * Permite minimizar la aplicación a la bandeja y restaurarla.
     */
    private void initializeSystemTray() {
        if (!SystemTray.isSupported()) {
            System.out.println("[Tray] System Tray no soportado en este sistema");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            return;
        }
        
        try {
            SystemTray tray = SystemTray.getSystemTray();
            
            Image trayImage = createTrayIcon();
            
            PopupMenu popup = new PopupMenu();
            
            MenuItem showItem = new MenuItem("Mostrar");
            showItem.addActionListener(e -> restoreFromTray());
            
            MenuItem connectItem = new MenuItem("Conectar");
            connectItem.addActionListener(e -> {
                restoreFromTray();
                connect();
            });
            
            MenuItem disconnectItem = new MenuItem("Desconectar");
            disconnectItem.addActionListener(e -> disconnect());
            
            MenuItem exitItem = new MenuItem("Salir");
            exitItem.addActionListener(e -> exitApplication());
            
            popup.add(showItem);
            popup.addSeparator();
            popup.add(connectItem);
            popup.add(disconnectItem);
            popup.addSeparator();
            popup.add(exitItem);
            
            trayIcon = new TrayIcon(trayImage, WINDOW_TITLE, popup);
            trayIcon.setImageAutoSize(true);
            
            trayIcon.addActionListener(e -> restoreFromTray());
            
            tray.add(trayIcon);
            
            System.out.println("[Tray] Icono de bandeja del sistema inicializado");
            
        } catch (Exception e) {
            System.err.println("[Tray] Error al inicializar System Tray: " + e.getMessage());
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }
    }
    
    /**
     * Crea una imagen simple para el icono de la bandeja.
     * 
     * @return Imagen del icono
     */
    private Image createTrayIcon() {
        try {
            // Intentar cargar icono desde archivo
            java.io.File iconFile = new java.io.File("icon.ico");
            if (iconFile.exists()) {
                Image img = javax.imageio.ImageIO.read(iconFile);
                if (img != null) {
                    System.out.println("[Tray] Icono cargado desde archivo: icon.ico");
                    return img;
                }
            }
            // Intentar cargar PNG como alternativa
            java.io.File pngFile = new java.io.File("icon.png");
            if (pngFile.exists()) {
                Image img = javax.imageio.ImageIO.read(pngFile);
                if (img != null) {
                    System.out.println("[Tray] Icono cargado desde archivo: icon.png");
                    return img;
                }
            }
        } catch (Exception e) {
            System.err.println("[Tray] Error cargando icono: " + e.getMessage());
        }
        
        // Fallback: icono generado por codigo
        int size = 16;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
            size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setComposite(java.awt.AlphaComposite.Clear);
        g2d.fillRect(0, 0, size, size);
        g2d.setComposite(java.awt.AlphaComposite.SrcOver);
        
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setColor(new Color(0, 120, 215));
        g2d.setStroke(new BasicStroke(1.5f));
        
        g2d.drawArc(2, 4, 6, 8, 45, 90);
        g2d.drawArc(5, 3, 8, 10, 45, 90);
        g2d.drawArc(8, 2, 10, 12, 45, 90);
        
        g2d.fillOval(1, 6, 4, 4);
        
        g2d.dispose();
        return image;
    }
    
    /**
     * Minimiza la ventana a la bandeja del sistema.
     */
    private void minimizeToTray() {
        if (trayIcon != null) {
            setVisible(false);
            minimizedToTray = true;
            trayIcon.displayMessage(WINDOW_TITLE, 
                "La aplicación sigue ejecutándose en segundo plano.\n" +
                "Haga doble clic en el icono para restaurar.",
                TrayIcon.MessageType.INFO);
            System.out.println("[Tray] Aplicación minimizada a la bandeja");
        }
    }
    
    /**
     * Restaura la ventana desde la bandeja del sistema.
     */
    private void restoreFromTray() {
        if (minimizedToTray) {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            toFront();
            requestFocus();
            minimizedToTray = false;
            System.out.println("[Tray] Aplicación restaurada desde la bandeja");
        } else {
            setVisible(true);
            toFront();
        }
    }
    
    /**
     * Cierra la aplicación completamente.
     */
    private void exitApplication() {
        int confirm = JOptionPane.showConfirmDialog(this,
            "¿Está seguro que desea salir de la aplicación?\n" +
            "Se desconectará del lector RFID.",
            "Confirmar salida",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE);
        
        if (confirm == JOptionPane.YES_OPTION) {
            if (trayIcon != null) {
                SystemTray.getSystemTray().remove(trayIcon);
            }
            shutdown();
            System.exit(0);
        }
    }
    
    /**
     * Inicializa todos los componentes de la interfaz con estilos modernos.
     */
    private void initializeComponents() {
        ipField = new JTextField(config.getReaderIP(), 15);
        ModernUIStyle.styleTextField(ipField);
        
        portSpinner = new JSpinner(new SpinnerNumberModel(config.getReaderPort(), 1, 65535, 1));
        ModernUIStyle.styleSpinner(portSpinner);
        
        connectButton = new JButton("Conectar");
        ModernUIStyle.stylePrimaryButton(connectButton);
        
        disconnectButton = new JButton("Desconectar");
        ModernUIStyle.styleDangerButton(disconnectButton);
        disconnectButton.setEnabled(false);
        
        startReadingButton = new JButton("Iniciar Lectura");
        ModernUIStyle.stylePrimaryButton(startReadingButton);
        startReadingButton.setEnabled(false);
        startReadingButton.setToolTipText("Iniciar la lectura de etiquetas RFID");
        
        stopReadingButton = new JButton("Detener Lectura");
        ModernUIStyle.styleDangerButton(stopReadingButton);
        stopReadingButton.setEnabled(false);
        stopReadingButton.setToolTipText("Detener la lectura de etiquetas RFID");
        
        connectionStatusLabel = new JLabel("Desconectado");
        connectionStatusLabel.setForeground(ModernUIStyle.TEXT_SECONDARY);
        connectionStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        connectionIndicator = ModernUIStyle.createStatusIndicator(ModernUIStyle.ACCENT_ERROR);
        
        autoConnectCheck = new JCheckBox("Auto-conectar al iniciar", config.isAutoConnectEnabled());
        ModernUIStyle.styleCheckBox(autoConnectCheck);
        
        for (int i = 0; i < 4; i++) {
            AntennaConfig ac = config.getAntennaConfig(i + 1);
            
            powerSliders[i] = new JSlider(10, 30, (int) ac.getTransmitPower());
            powerSliders[i].setMajorTickSpacing(5);
            powerSliders[i].setMinorTickSpacing(1);
            powerSliders[i].setPaintTicks(true);
            powerSliders[i].setPaintLabels(true);
            ModernUIStyle.styleSlider(powerSliders[i]);
            
            antennaEnableChecks[i] = new JCheckBox("Habilitada", ac.isEnabled());
            ModernUIStyle.styleCheckBox(antennaEnableChecks[i]);
            
            powerLabels[i] = new JLabel(powerSliders[i].getValue() + " dBm");
            powerLabels[i].setForeground(ModernUIStyle.ACCENT_PRIMARY);
            powerLabels[i].setFont(new Font("Consolas", Font.BOLD, 14));
            
            rssiSliders[i] = new JSlider(-80, 0, ac.getRssiThreshold());
            rssiSliders[i].setMajorTickSpacing(20);
            rssiSliders[i].setMinorTickSpacing(5);
            rssiSliders[i].setPaintTicks(true);
            rssiSliders[i].setPaintLabels(true);
            ModernUIStyle.styleSlider(rssiSliders[i]);
            
            rssiFilterChecks[i] = new JCheckBox("Filtrar RSSI", ac.isRssiFilterEnabled());
            ModernUIStyle.styleCheckBox(rssiFilterChecks[i]);
            
            rssiLabels[i] = new JLabel(rssiSliders[i].getValue() + " dBm");
            rssiLabels[i].setForeground(ModernUIStyle.ACCENT_WARNING);
            rssiLabels[i].setFont(new Font("Consolas", Font.BOLD, 12));
            
            cableLossSpinners[i] = new JSpinner(new SpinnerNumberModel(ac.getCableLoss(), 0.0, 20.0, 0.5));
            ModernUIStyle.styleSpinner(cableLossSpinners[i]);
        }
        
        String[] columnNames = {"EPC", "RSSI (dBm)", "Antena", "Lecturas", "Última Lectura", "API"};
        tagTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tagTable = new JTable(tagTableModel);
        tagTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tagTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        tagTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        ModernUIStyle.styleTable(tagTable);
        
        tagsReadLabel = new JLabel("0");
        tagsReadLabel.setForeground(ModernUIStyle.ACCENT_PRIMARY);
        tagsReadLabel.setFont(new Font("Consolas", Font.BOLD, 16));
        
        uniqueTagsLabel = new JLabel("0");
        uniqueTagsLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
        uniqueTagsLabel.setFont(new Font("Consolas", Font.BOLD, 16));
        
        tpsLabel = new JLabel("0.0");
        tpsLabel.setForeground(ModernUIStyle.ACCENT_WARNING);
        tpsLabel.setFont(new Font("Consolas", Font.BOLD, 16));
        
        clearTagsButton = new JButton("Limpiar");
        ModernUIStyle.styleSecondaryButton(clearTagsButton);
        
        autoScrollCheck = new JCheckBox("Auto-scroll", true);
        ModernUIStyle.styleCheckBox(autoScrollCheck);
        
        hexDecimalToggle = new JToggleButton("HEX", true);
        hexDecimalToggle.setToolTipText(displayHexMode ? "Filtro HEX: Acepta todos los tags (A-F, 0-9)" : "Filtro DEC: Solo tags numéricos (0-9)");
        hexDecimalToggle.setFont(new Font("Consolas", Font.BOLD, 11));
        hexDecimalToggle.setBackground(ModernUIStyle.ACCENT_INFO);
        hexDecimalToggle.setForeground(Color.WHITE);
        hexDecimalToggle.setFocusPainted(false);
        hexDecimalToggle.setPreferredSize(new Dimension(65, 28));
        
        apiEndpointField = new JTextField(config.getApiEndpoint() != null ? config.getApiEndpoint() : "", 30);
        ModernUIStyle.styleTextField(apiEndpointField);
        
        apiKeyField = new JPasswordField(config.getApiKey() != null ? config.getApiKey() : "", 20);
        apiKeyField.setBackground(ModernUIStyle.BG_INPUT);
        apiKeyField.setForeground(ModernUIStyle.TEXT_PRIMARY);
        apiKeyField.setCaretColor(ModernUIStyle.TEXT_PRIMARY);
        apiKeyField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ModernUIStyle.BORDER_DEFAULT, 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        
        apiTrabajoField = new JTextField(config.getApiTrabajo() != null ? config.getApiTrabajo() : "", 20);
        ModernUIStyle.styleTextField(apiTrabajoField);
        apiTrabajoField.setToolTipText("Identificador del trabajo/turno para la API");
        
        testApiButton = new JButton("Probar Conexion");
        ModernUIStyle.styleSecondaryButton(testApiButton);
        
        apiStatusLabel = new JLabel("No configurado");
        apiStatusLabel.setForeground(ModernUIStyle.TEXT_MUTED);
        
        apiEnabledCheck = new JCheckBox("Envío automático habilitado", config.isApiEnabled());
        ModernUIStyle.styleCheckBox(apiEnabledCheck);
        
        apiStartControlCheck = new JCheckBox("Consultar API antes de iniciar lectura", config.isApiStartControlEnabled());
        ModernUIStyle.styleCheckBox(apiStartControlCheck);
        
        apiApiladoCheck = new JCheckBox("Apilado", config.isApiApilado());
        ModernUIStyle.styleCheckBox(apiApiladoCheck);
        apiApiladoCheck.setToolTipText("Si está marcado, envía 'S' como parámetro de apilado en la URL");
        
        clearQueueOnStopCheck = new JCheckBox("Borrar cola al parar lectura", config.isClearQueueOnStop());
        ModernUIStyle.styleCheckBox(clearQueueOnStopCheck);
        clearQueueOnStopCheck.setToolTipText("Si está marcado, elimina los tags pendientes de envío al detener la lectura");
        
        apiPollingCheck = new JCheckBox("Polling automático (control por API)", config.isApiPollingEnabled());
        ModernUIStyle.styleCheckBox(apiPollingCheck);
        apiPollingCheck.setToolTipText("Consulta la API periódicamente e inicia/detiene lectura automáticamente");
        
        apiPollingIntervalSpinner = new JSpinner(new SpinnerNumberModel(
            config.getApiPollingIntervalSeconds(), 1, 60, 1));
        apiPollingIntervalSpinner.setPreferredSize(new Dimension(60, 25));
        
        for (int i = 0; i < 4; i++) {
            gpoButtons[i] = new JToggleButton("GPO " + (i + 1));
            gpoButtons[i].setPreferredSize(new Dimension(110, 45));
            gpoButtons[i].setBackground(ModernUIStyle.BG_CARD);
            gpoButtons[i].setForeground(ModernUIStyle.TEXT_PRIMARY);
            gpoButtons[i].setFont(new Font("Segoe UI", Font.BOLD, 12));
            gpoButtons[i].setFocusPainted(false);
            gpoButtons[i].setBorder(BorderFactory.createLineBorder(ModernUIStyle.BORDER_DEFAULT, 2));
            
            gpiIndicators[i] = ModernUIStyle.createStatusIndicator(ModernUIStyle.TEXT_MUTED);
            gpiIndicators[i].setPreferredSize(new Dimension(30, 30));
            
            gpiLabels[i] = new JLabel("GPI " + (i + 1) + ": --");
            gpiLabels[i].setForeground(ModernUIStyle.TEXT_SECONDARY);
            gpiLabels[i].setFont(new Font("Consolas", Font.PLAIN, 12));
        }
        
        statusLabel = new JLabel("Listo");
        statusLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        statsLabel = new JLabel("Tags: 0 | Únicos: 0 | TPS: 0.0");
        statsLabel.setForeground(ModernUIStyle.TEXT_SECONDARY);
        statsLabel.setFont(new Font("Consolas", Font.PLAIN, 12));
        
        activityIndicator = ModernUIStyle.createModernProgressBar();
        activityIndicator.setIndeterminate(false);
        activityIndicator.setPreferredSize(new Dimension(120, 8));
        
        duplicateFilterCheck = new JCheckBox("Filtrar tags duplicados", config.isDuplicateFilterEnabled());
        ModernUIStyle.styleCheckBox(duplicateFilterCheck);
        
        duplicateExpirationSpinner = new JSpinner(new SpinnerNumberModel(
            config.getDuplicateFilterExpiration(), 1, 300, 1));
        ModernUIStyle.styleSpinner(duplicateExpirationSpinner);
        
        noExpirationCheck = new JCheckBox("Sin expiración (estilo VZEBRA)", config.isDuplicateFilterNoExpiration());
        ModernUIStyle.styleCheckBox(noExpirationCheck);
        
        generateWindowsServiceButton = new JButton("Generar Instalador Windows");
        ModernUIStyle.stylePrimaryButton(generateWindowsServiceButton);
        
        generateUbuntuServiceButton = new JButton("Generar Script Ubuntu");
        ModernUIStyle.styleSecondaryButton(generateUbuntuServiceButton);
        
        saveConfigButton = new JButton("Guardar Configuracion");
        ModernUIStyle.styleSuccessButton(saveConfigButton);
        
        duplicateStatsLabel = new JLabel("Filtrados: 0 | Procesados: 0");
        duplicateStatsLabel.setForeground(ModernUIStyle.TEXT_SECONDARY);
        duplicateStatsLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        
        rssiFilterCheck = new JCheckBox("Filtrar por RSSI", config.isRssiFilterEnabled());
        ModernUIStyle.styleCheckBox(rssiFilterCheck);
        
        rssiThresholdSpinner = new JSpinner(new SpinnerNumberModel(
            config.getRssiThreshold(), -80, 0, 1));
        ModernUIStyle.styleSpinner(rssiThresholdSpinner);
        
        rssiStatsLabel = new JLabel("Filtrados por RSSI: 0");
        rssiStatsLabel.setForeground(ModernUIStyle.TEXT_SECONDARY);
        
        sessionCombo = new JComboBox<>(new String[]{"S0 (Volátil)", "S1 (Persistente)", "S2 (Multi-lector)", "S3 (Multi-lector)"});
        sessionCombo.setSelectedIndex(config.getInventorySession());
        ModernUIStyle.styleComboBox(sessionCombo);
        
        targetCombo = new JComboBox<>(new String[]{"A", "B", "A↔B (Alternado)"});
        targetCombo.setSelectedIndex(config.getInventoryTarget());
        ModernUIStyle.styleComboBox(targetCombo);
        
        tagPopulationSpinner = new JSpinner(new SpinnerNumberModel(
            config.getTagPopulation(), 1, 1000, 10));
        ModernUIStyle.styleSpinner(tagPopulationSpinner);
        
        denseReaderCheck = new JCheckBox("Dense Reader Mode", config.isDenseReaderMode());
        ModernUIStyle.styleCheckBox(denseReaderCheck);
        
        rfModeSpinner = new JSpinner(new SpinnerNumberModel(
            config.getRfModeIndex(), 0, 50, 1));
        ModernUIStyle.styleSpinner(rfModeSpinner);
        
        gpiTriggerCheck = new JCheckBox("Trigger por GPI", config.isGpiTriggerEnabled());
        ModernUIStyle.styleCheckBox(gpiTriggerCheck);
        
        gpiTriggerPortCombo = new JComboBox<>(new String[]{"Deshabilitado", "GPI 1", "GPI 2", "GPI 3", "GPI 4"});
        gpiTriggerPortCombo.setSelectedIndex(config.getGpiTriggerPort());
        ModernUIStyle.styleComboBox(gpiTriggerPortCombo);
        
        gpiTriggerStateCombo = new JComboBox<>(new String[]{"HIGH", "LOW"});
        gpiTriggerStateCombo.setSelectedIndex(config.isGpiTriggerState() ? 0 : 1);
        ModernUIStyle.styleComboBox(gpiTriggerStateCombo);
        
        reportPhaseCheck = new JCheckBox("Reportar Phase Angle", config.isReportPhaseAngle());
        ModernUIStyle.styleCheckBox(reportPhaseCheck);
        
        reportChannelCheck = new JCheckBox("Reportar Canal RF", config.isReportChannelIndex());
        ModernUIStyle.styleCheckBox(reportChannelCheck);
    }
    
    /**
     * Inicializa el layout de la ventana.
     */
    private void initializeLayout() {
        setLayout(new BorderLayout());
        
        tabbedPane = new JTabbedPane();
        ModernUIStyle.styleTabbedPane(tabbedPane);
        
        tabbedPane.addTab("Conexión", createConnectionPanel());
        tabbedPane.addTab("Monitoreo", createMonitorPanel());
        tabbedPane.addTab("Antenas", createAntennasPanel());
        tabbedPane.addTab("API", createApiPanel());
        tabbedPane.addTab("GPIO", createGpioPanel());
        tabbedPane.addTab("PLC", createPlcPanel());
        tabbedPane.addTab("Avanzado", createAdvancedPanel());
        tabbedPane.addTab("Herramientas", createToolsPanel());
        
        add(tabbedPane, BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
    }
    
    /**
     * Crea el panel de conexión.
     * 
     * @return Panel de conexión
     */
    private JPanel createConnectionPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Dirección IP:"), gbc);
        
        gbc.gridx = 1;
        panel.add(ipField, gbc);
        
        gbc.gridx = 2;
        panel.add(new JLabel("Puerto:"), gbc);
        
        gbc.gridx = 3;
        panel.add(portSpinner, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(connectButton, gbc);
        
        gbc.gridx = 1;
        panel.add(disconnectButton, gbc);
        
        gbc.gridx = 2;
        panel.add(connectionIndicator, gbc);
        
        gbc.gridx = 3;
        panel.add(connectionStatusLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(startReadingButton, gbc);
        
        gbc.gridx = 1;
        panel.add(stopReadingButton, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4;
        panel.add(autoConnectCheck, gbc);
        
        gbc.gridy = 4; gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);
        
        return panel;
    }
    
    /**
     * Crea el panel de configuración de antenas.
     * 
     * @return Panel de antenas
     */
    private JPanel createAntennasPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 15, 15));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        for (int i = 0; i < 4; i++) {
            JPanel antennaPanel = createAntennaSubPanel(i);
            panel.add(antennaPanel);
        }
        
        return panel;
    }
    
    /**
     * Crea un subpanel para una antena individual.
     * 
     * @param antennaIndex Índice de la antena (0-3)
     * @return Subpanel de antena
     */
    private JPanel createAntennaSubPanel(int antennaIndex) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ModernUIStyle.BORDER_DEFAULT),
                "Antena " + (antennaIndex + 1)
            ),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(antennaEnableChecks[antennaIndex], gbc);
        
        gbc.gridy = 1; gbc.gridwidth = 1;
        JLabel pwrLabel = new JLabel("Potencia TX:");
        pwrLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panel.add(pwrLabel, gbc);
        
        gbc.gridx = 1;
        panel.add(powerLabels[antennaIndex], gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0;
        panel.add(powerSliders[antennaIndex], gbc);
        
        gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0;
        JLabel cableLabel = new JLabel("Pérdida cable (dB):");
        cableLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panel.add(cableLabel, gbc);
        
        gbc.gridx = 1;
        panel.add(cableLossSpinners[antennaIndex], gbc);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        panel.add(rssiFilterChecks[antennaIndex], gbc);
        
        gbc.gridy = 6; gbc.gridwidth = 1;
        JLabel rssiLabel = new JLabel("Umbral RSSI:");
        rssiLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panel.add(rssiLabel, gbc);
        
        gbc.gridx = 1;
        panel.add(rssiLabels[antennaIndex], gbc);
        
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.weightx = 1.0;
        panel.add(rssiSliders[antennaIndex], gbc);
        
        final int idx = antennaIndex;
        powerSliders[antennaIndex].addChangeListener(e -> {
            powerLabels[idx].setText(powerSliders[idx].getValue() + " dBm");
        });
        rssiSliders[antennaIndex].addChangeListener(e -> {
            rssiLabels[idx].setText(rssiSliders[idx].getValue() + " dBm");
        });
        
        rssiFilterChecks[antennaIndex].addActionListener(e -> {
            rssiSliders[idx].setEnabled(rssiFilterChecks[idx].isSelected());
        });
        rssiSliders[antennaIndex].setEnabled(rssiFilterChecks[antennaIndex].isSelected());
        
        return panel;
    }
    
    /**
     * Crea el panel de monitoreo de tags.
     * 
     * @return Panel de monitoreo
     */
    private JPanel createMonitorPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        statsPanel.add(new JLabel("Total:"));
        statsPanel.add(tagsReadLabel);
        statsPanel.add(new JLabel("Únicos:"));
        statsPanel.add(uniqueTagsLabel);
        statsPanel.add(new JLabel("Tags/s:"));
        statsPanel.add(tpsLabel);
        statsPanel.add(clearTagsButton);
        statsPanel.add(autoScrollCheck);
        statsPanel.add(hexDecimalToggle);
        panel.add(statsPanel, BorderLayout.NORTH);
        
        JScrollPane scrollPane = new JScrollPane(tagTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(ModernUIStyle.BORDER_DEFAULT));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    /**
     * Crea el panel de configuración de API.
     * 
     * @return Panel de API
     */
    private JPanel createApiPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // ========== SECCIÓN 1: CONFIGURACIÓN DEL SERVIDOR ==========
        JPanel serverSection = new JPanel(new BorderLayout(0, 8));
        serverSection.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ModernUIStyle.BORDER_DEFAULT, 1),
            BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));
        serverSection.setBackground(ModernUIStyle.BG_CARD);
        
        JLabel serverTitle = new JLabel("Configuración del Servidor API");
        serverTitle.setFont(serverTitle.getFont().deriveFont(Font.BOLD, 14f));
        serverTitle.setForeground(ModernUIStyle.ACCENT_PRIMARY);
        serverSection.add(serverTitle, BorderLayout.NORTH);
        
        JPanel serverContent = new JPanel(new GridBagLayout());
        serverContent.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        serverContent.add(new JLabel("Endpoint:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        serverContent.add(apiEndpointField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        serverContent.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        serverContent.add(apiKeyField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        serverContent.add(new JLabel("ID Trabajo:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        serverContent.add(apiTrabajoField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE;
        serverContent.add(testApiButton, gbc);
        gbc.gridx = 1;
        serverContent.add(apiStatusLabel, gbc);
        
        serverSection.add(serverContent, BorderLayout.CENTER);
        mainPanel.add(serverSection);
        mainPanel.add(Box.createVerticalStrut(12));
        
        // ========== SECCIÓN 2: OPCIONES DE ENVÍO ==========
        JPanel optionsSection = new JPanel(new BorderLayout(0, 8));
        optionsSection.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ModernUIStyle.BORDER_DEFAULT, 1),
            BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));
        optionsSection.setBackground(ModernUIStyle.BG_CARD);
        
        JLabel optionsTitle = new JLabel("Opciones de Envío");
        optionsTitle.setFont(optionsTitle.getFont().deriveFont(Font.BOLD, 14f));
        optionsTitle.setForeground(ModernUIStyle.ACCENT_PRIMARY);
        optionsSection.add(optionsTitle, BorderLayout.NORTH);
        
        JPanel optionsContent = new JPanel();
        optionsContent.setLayout(new BoxLayout(optionsContent, BoxLayout.Y_AXIS));
        optionsContent.setOpaque(false);
        
        optionsContent.add(apiEnabledCheck);
        optionsContent.add(Box.createVerticalStrut(5));
        
        optionsSection.add(optionsContent, BorderLayout.CENTER);
        mainPanel.add(optionsSection);
        mainPanel.add(Box.createVerticalStrut(12));
        
        // ========== SECCIÓN 3: CONTROL AUTOMÁTICO ==========
        JPanel controlSection = new JPanel(new BorderLayout(0, 8));
        controlSection.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ModernUIStyle.BORDER_DEFAULT, 1),
            BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));
        controlSection.setBackground(ModernUIStyle.BG_CARD);
        
        JLabel controlTitle = new JLabel("Control Automático (Polling)");
        controlTitle.setFont(controlTitle.getFont().deriveFont(Font.BOLD, 14f));
        controlTitle.setForeground(ModernUIStyle.ACCENT_PRIMARY);
        controlSection.add(controlTitle, BorderLayout.NORTH);
        
        JPanel controlContent = new JPanel();
        controlContent.setLayout(new BoxLayout(controlContent, BoxLayout.Y_AXIS));
        controlContent.setOpaque(false);
        
        controlContent.add(apiStartControlCheck);
        controlContent.add(Box.createVerticalStrut(5));
        
        JPanel pollingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        pollingPanel.setOpaque(false);
        pollingPanel.add(apiPollingCheck);
        pollingPanel.add(new JLabel("cada"));
        pollingPanel.add(apiPollingIntervalSpinner);
        pollingPanel.add(new JLabel("segundos"));
        controlContent.add(pollingPanel);
        controlContent.add(Box.createVerticalStrut(5));
        
        // Opciones de Alta
        JPanel altaOptionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        altaOptionsPanel.setOpaque(false);
        altaOptionsPanel.add(apiApiladoCheck);
        altaOptionsPanel.add(clearQueueOnStopCheck);
        controlContent.add(altaOptionsPanel);
        
        controlSection.add(controlContent, BorderLayout.CENTER);
        mainPanel.add(controlSection);
        
        mainPanel.add(Box.createVerticalGlue());
        
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scrollPane, BorderLayout.CENTER);
        
        return wrapper;
    }
    
    /**
     * Crea el panel de control GPIO.
     * 
     * @return Panel GPIO
     */
    private JPanel createGpioPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        JLabel gpoTitle = new JLabel("Salidas GPO (Outputs)");
        gpoTitle.setFont(gpoTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(gpoTitle, gbc);
        
        gbc.gridy = 1; gbc.gridwidth = 1;
        for (int i = 0; i < 4; i++) {
            gbc.gridx = i;
            panel.add(gpoButtons[i], gbc);
        }
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4;
        panel.add(new JSeparator(), gbc);
        
        gbc.gridy = 3;
        JLabel gpiTitle = new JLabel("Entradas GPI (Inputs)");
        gpiTitle.setFont(gpiTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(gpiTitle, gbc);
        
        gbc.gridy = 4; gbc.gridwidth = 1;
        for (int i = 0; i < 4; i++) {
            gbc.gridx = i;
            JPanel gpiPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
            gpiPanel.add(gpiIndicators[i]);
            gpiPanel.add(gpiLabels[i]);
            panel.add(gpiPanel, gbc);
        }
        
        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 4;
        gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);
        
        return panel;
    }
    
    /**
     * Crea el panel de configuración PLC (Modbus TCP).
     * Permite configurar la comunicación con el PLC para automatización industrial.
     * 
     * @return Panel de configuración PLC
     */
    private JPanel createPlcPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // ========== SECCIÓN 1: CONEXIÓN PLC ==========
        JPanel connectionSection = new JPanel(new GridBagLayout());
        connectionSection.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Conexion PLC (Modbus TCP)"));
        
        // Leyenda explicativa - Panel con ancho completo
        JPanel legendPanel = new JPanel();
        legendPanel.setLayout(new BoxLayout(legendPanel, BoxLayout.Y_AXIS));
        legendPanel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Guia de Configuracion"));
        legendPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        JLabel lbl1 = new JLabel("<html><b>Modbus TCP:</b> Protocolo industrial para comunicacion con PLCs</html>");
        JLabel lbl2 = new JLabel("<html><b>IP del PLC:</b> Direccion de red del controlador (ej: 192.168.1.100)</html>");
        JLabel lbl3 = new JLabel("<html><b>Puerto:</b> Puerto Modbus, normalmente 502</html>");
        JLabel lbl4 = new JLabel("<html><b>Intervalo polling:</b> Cada cuantos milisegundos se verifica el PLC</html>");
        JLabel lbl5 = new JLabel("<html><b>Referencia:</b> Direccion del registro en el PLC (0, 1, 2, 3...)</html>");
        JLabel lbl6 = new JLabel("<html><b>Unit ID:</b> Identificador del dispositivo esclavo (normalmente 1)</html>");
        JLabel lbl7 = new JLabel("<html><b>Coil:</b> Variable booleana del PLC (ON/OFF) - Controla inicio/parada de lectura</html>");
        JLabel lbl8 = new JLabel("<html><b>Holding Register:</b> Variable numerica del PLC (0-65535) - Para escribir datos</html>");
        
        legendPanel.add(lbl1);
        legendPanel.add(Box.createVerticalStrut(3));
        legendPanel.add(lbl2);
        legendPanel.add(lbl3);
        legendPanel.add(lbl4);
        legendPanel.add(Box.createVerticalStrut(8));
        legendPanel.add(lbl5);
        legendPanel.add(lbl6);
        legendPanel.add(Box.createVerticalStrut(8));
        legendPanel.add(lbl7);
        legendPanel.add(lbl8);
        
        // Envolver en panel con BorderLayout para ocupar ancho completo
        JPanel legendWrapper = new JPanel(new BorderLayout());
        legendWrapper.add(legendPanel, BorderLayout.CENTER);
        mainPanel.add(legendWrapper);
        mainPanel.add(Box.createVerticalStrut(15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Checkbox habilitar PLC
        gbc.gridx = 0; gbc.gridy = 0;
        plcEnabledCheck = new JCheckBox("Habilitar comunicacion PLC");
        plcEnabledCheck.setSelected(config.isPlcEnabled());
        plcEnabledCheck.addActionListener(e -> {
            config.setPlcEnabled(plcEnabledCheck.isSelected());
            updatePlcFieldsState();
        });
        connectionSection.add(plcEnabledCheck, gbc);
        
        // IP del PLC
        gbc.gridx = 0; gbc.gridy = 1;
        connectionSection.add(new JLabel("IP del PLC:"), gbc);
        gbc.gridx = 1;
        plcIpField = new JTextField(config.getPlcIP(), 15);
        plcIpField.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                config.setPlcIP(plcIpField.getText().trim());
            }
        });
        connectionSection.add(plcIpField, gbc);
        
        // Puerto Modbus
        gbc.gridx = 2;
        connectionSection.add(new JLabel("Puerto:"), gbc);
        gbc.gridx = 3;
        plcPortSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcPort(), 1, 65535, 1));
        plcPortSpinner.addChangeListener(e -> config.setPlcPort((Integer)plcPortSpinner.getValue()));
        connectionSection.add(plcPortSpinner, gbc);
        
        // Intervalo de polling
        gbc.gridx = 0; gbc.gridy = 2;
        connectionSection.add(new JLabel("Intervalo polling (ms):"), gbc);
        gbc.gridx = 1;
        plcPollingSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcPollingInterval(), 100, 10000, 100));
        plcPollingSpinner.addChangeListener(e -> config.setPlcPollingInterval((Integer)plcPollingSpinner.getValue()));
        connectionSection.add(plcPollingSpinner, gbc);
        
        // Estado de conexión
        gbc.gridx = 2;
        connectionSection.add(new JLabel("Estado:"), gbc);
        gbc.gridx = 3;
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        plcStatusIndicator = new JPanel();
        plcStatusIndicator.setPreferredSize(new Dimension(12, 12));
        plcStatusIndicator.setBackground(Color.GRAY);
        plcStatusIndicator.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        plcStatusLabel = new JLabel("Desconectado");
        statusPanel.add(plcStatusIndicator);
        statusPanel.add(plcStatusLabel);
        connectionSection.add(statusPanel, gbc);
        
        // Botón de prueba
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        plcTestButton = new JButton("Probar Conexion");
        plcTestButton.addActionListener(e -> testPlcConnection());
        connectionSection.add(plcTestButton, gbc);
        
        mainPanel.add(connectionSection);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // ========== SECCIÓN 2: REGISTRO ENABLER (COIL) ==========
        JPanel enablerSection = new JPanel(new GridBagLayout());
        enablerSection.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Registro Enabler (Coil de activacion)"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        enablerSection.add(new JLabel("Referencia (Coil):"), gbc);
        gbc.gridx = 1;
        plcRefEnableSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcRefEnable(), 0, 65535, 1));
        plcRefEnableSpinner.addChangeListener(e -> config.setPlcRefEnable((Integer)plcRefEnableSpinner.getValue()));
        enablerSection.add(plcRefEnableSpinner, gbc);
        
        gbc.gridx = 2;
        enablerSection.add(new JLabel("Unit ID:"), gbc);
        gbc.gridx = 3;
        plcUnitIdEnableSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcUnitIdEnable(), 0, 255, 1));
        plcUnitIdEnableSpinner.addChangeListener(e -> config.setPlcUnitIdEnable((Integer)plcUnitIdEnableSpinner.getValue()));
        enablerSection.add(plcUnitIdEnableSpinner, gbc);
        
        mainPanel.add(enablerSection);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // ========== SECCIÓN 3: REGISTROS DE DATOS (HOLDING REGISTERS) ==========
        JPanel registersSection = new JPanel(new GridBagLayout());
        registersSection.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(), "Registros de Datos (Holding Registers)"));
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Encabezados
        gbc.gridx = 0; gbc.gridy = 0;
        registersSection.add(new JLabel("Registro"), gbc);
        gbc.gridx = 1;
        registersSection.add(new JLabel("Referencia"), gbc);
        gbc.gridx = 2;
        registersSection.add(new JLabel("Unit ID"), gbc);
        gbc.gridx = 3;
        registersSection.add(new JLabel("Descripcion"), gbc);
        
        // Tipo Embalaje
        gbc.gridx = 0; gbc.gridy = 1;
        registersSection.add(new JLabel("Tipo Embalaje:"), gbc);
        gbc.gridx = 1;
        plcRefTipoEmbalajeSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcRefTipoEmbalaje(), 0, 65535, 1));
        plcRefTipoEmbalajeSpinner.addChangeListener(e -> config.setPlcRefTipoEmbalaje((Integer)plcRefTipoEmbalajeSpinner.getValue()));
        registersSection.add(plcRefTipoEmbalajeSpinner, gbc);
        gbc.gridx = 2;
        plcUnitIdTipoEmbalajeSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcUnitIdTipoEmbalaje(), 0, 255, 1));
        plcUnitIdTipoEmbalajeSpinner.addChangeListener(e -> config.setPlcUnitIdTipoEmbalaje((Integer)plcUnitIdTipoEmbalajeSpinner.getValue()));
        registersSection.add(plcUnitIdTipoEmbalajeSpinner, gbc);
        gbc.gridx = 3;
        registersSection.add(new JLabel("Codigo tipo de embalaje"), gbc);
        
        // Ancho
        gbc.gridx = 0; gbc.gridy = 2;
        registersSection.add(new JLabel("Ancho:"), gbc);
        gbc.gridx = 1;
        plcRefAnchoSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcRefAncho(), 0, 65535, 1));
        plcRefAnchoSpinner.addChangeListener(e -> config.setPlcRefAncho((Integer)plcRefAnchoSpinner.getValue()));
        registersSection.add(plcRefAnchoSpinner, gbc);
        gbc.gridx = 2;
        plcUnitIdAnchoSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcUnitIdAncho(), 0, 255, 1));
        plcUnitIdAnchoSpinner.addChangeListener(e -> config.setPlcUnitIdAncho((Integer)plcUnitIdAnchoSpinner.getValue()));
        registersSection.add(plcUnitIdAnchoSpinner, gbc);
        gbc.gridx = 3;
        registersSection.add(new JLabel("Dimension ancho en mm"), gbc);
        
        // Largo
        gbc.gridx = 0; gbc.gridy = 3;
        registersSection.add(new JLabel("Largo:"), gbc);
        gbc.gridx = 1;
        plcRefLargoSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcRefLargo(), 0, 65535, 1));
        plcRefLargoSpinner.addChangeListener(e -> config.setPlcRefLargo((Integer)plcRefLargoSpinner.getValue()));
        registersSection.add(plcRefLargoSpinner, gbc);
        gbc.gridx = 2;
        plcUnitIdLargoSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcUnitIdLargo(), 0, 255, 1));
        plcUnitIdLargoSpinner.addChangeListener(e -> config.setPlcUnitIdLargo((Integer)plcUnitIdLargoSpinner.getValue()));
        registersSection.add(plcUnitIdLargoSpinner, gbc);
        gbc.gridx = 3;
        registersSection.add(new JLabel("Dimension largo en mm"), gbc);
        
        // Activa
        gbc.gridx = 0; gbc.gridy = 4;
        registersSection.add(new JLabel("Activa:"), gbc);
        gbc.gridx = 1;
        plcRefActivaSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcRefActiva(), 0, 65535, 1));
        plcRefActivaSpinner.addChangeListener(e -> config.setPlcRefActiva((Integer)plcRefActivaSpinner.getValue()));
        registersSection.add(plcRefActivaSpinner, gbc);
        gbc.gridx = 2;
        plcUnitIdActivaSpinner = new JSpinner(new SpinnerNumberModel(config.getPlcUnitIdActiva(), 0, 255, 1));
        plcUnitIdActivaSpinner.addChangeListener(e -> config.setPlcUnitIdActiva((Integer)plcUnitIdActivaSpinner.getValue()));
        registersSection.add(plcUnitIdActivaSpinner, gbc);
        gbc.gridx = 3;
        registersSection.add(new JLabel("Bandera de confirmacion"), gbc);
        
        mainPanel.add(registersSection);
        
        // Espacio flexible al final
        mainPanel.add(Box.createVerticalGlue());
        
        // Actualizar estado de campos según checkbox
        updatePlcFieldsState();
        
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        JPanel containerPanel = new JPanel(new BorderLayout());
        containerPanel.add(scrollPane, BorderLayout.CENTER);
        return containerPanel;
    }
    
    /**
     * Actualiza el estado habilitado/deshabilitado de los campos PLC.
     */
    private void updatePlcFieldsState() {
        boolean enabled = plcEnabledCheck.isSelected();
        plcIpField.setEnabled(enabled);
        plcPortSpinner.setEnabled(enabled);
        plcPollingSpinner.setEnabled(enabled);
        plcRefEnableSpinner.setEnabled(enabled);
        plcUnitIdEnableSpinner.setEnabled(enabled);
        plcRefTipoEmbalajeSpinner.setEnabled(enabled);
        plcUnitIdTipoEmbalajeSpinner.setEnabled(enabled);
        plcRefAnchoSpinner.setEnabled(enabled);
        plcUnitIdAnchoSpinner.setEnabled(enabled);
        plcRefLargoSpinner.setEnabled(enabled);
        plcUnitIdLargoSpinner.setEnabled(enabled);
        plcRefActivaSpinner.setEnabled(enabled);
        plcUnitIdActivaSpinner.setEnabled(enabled);
        plcTestButton.setEnabled(enabled);
    }
    
    /**
     * Prueba la conexión con el PLC.
     */
    private void testPlcConnection() {
        plcStatusLabel.setText("Conectando...");
        plcStatusIndicator.setBackground(Color.YELLOW);
        
        new Thread(() -> {
            try {
                ModbusClient client = new ModbusClient(config.getPlcIP(), config.getPlcPort());
                client.connect();
                boolean enablerValue = client.checkEnabler(
                    config.getPlcRefEnable(), 
                    config.getPlcUnitIdEnable()
                );
                client.disconnect();
                
                SwingUtilities.invokeLater(() -> {
                    plcStatusLabel.setText("Conectado (Enabler: " + (enablerValue ? "ON" : "OFF") + ")");
                    plcStatusIndicator.setBackground(Color.GREEN);
                    System.out.println("[PLC] Conexion exitosa - Enabler: " + (enablerValue ? "ON" : "OFF"));
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    plcStatusLabel.setText("Error: " + ex.getMessage());
                    plcStatusIndicator.setBackground(Color.RED);
                    System.out.println("[PLC] Error de conexion: " + ex.getMessage());
                });
            }
        }).start();
    }
    
    /**
     * Inicia el polling del PLC.
     * Verifica el coil enabler cada intervalo configurado.
     */
    private void startPlcPolling() {
        System.out.println("[PLC-DEBUG] startPlcPolling() llamado");
        System.out.println("[PLC-DEBUG] config.isPlcEnabled() = " + config.isPlcEnabled());
        System.out.println("[PLC-DEBUG] plcRunning = " + plcRunning);
        System.out.println("[PLC-DEBUG] IP = " + config.getPlcIP());
        System.out.println("[PLC-DEBUG] Puerto = " + config.getPlcPort());
        
        if (!config.isPlcEnabled()) {
            System.out.println("[PLC-DEBUG] PLC NO habilitado - Verifica el checkbox");
            return;
        }
        if (plcRunning) {
            System.out.println("[PLC-DEBUG] PLC ya en ejecucion");
            return;
        }
        
        plcRunning = true;
        System.out.println("[PLC] Iniciando polling cada " + config.getPlcPollingInterval() + "ms");
        
        plcPollingThread = new Thread(() -> {
            ModbusClient modbusClient = null;
            boolean wasEnabled = false;
            
            while (plcRunning) {
                try {
                    // Conectar si no está conectado
                    if (modbusClient == null || !modbusClient.isConnected()) {
                        modbusClient = new ModbusClient(config.getPlcIP(), config.getPlcPort());
                        modbusClient.connect();
                        updatePlcStatus("Conectado", Color.GREEN);
                    }
                    
                    // Leer coil enabler
                    boolean enablerActive = modbusClient.checkEnabler(
                        config.getPlcRefEnable(),
                        config.getPlcUnitIdEnable()
                    );
                    
                    // Controlar lectura RFID segun estado del enabler
                    if (enablerActive && !wasEnabled) {
                        // Flanco de subida: OFF -> ON = Iniciar lectura
                        System.out.println("[PLC] Enabler ON - Iniciando lectura RFID");
                        SwingUtilities.invokeLater(() -> {
                            if (!isReading && connection != null && connection.isConnected()) {
                                startReadingFromPLC();
                            }
                        });
                    } else if (!enablerActive && wasEnabled) {
                        // Flanco de bajada: ON -> OFF = Detener lectura
                        System.out.println("[PLC] Enabler OFF - Deteniendo lectura RFID");
                        SwingUtilities.invokeLater(() -> {
                            if (isReading) {
                                stopReadingFromPLC();
                            }
                        });
                    }
                    
                    wasEnabled = enablerActive;
                    
                    Thread.sleep(config.getPlcPollingInterval());
                    
                } catch (InterruptedException ie) {
                    break;
                } catch (Exception ex) {
                    System.out.println("[PLC] Error de polling: " + ex.getMessage());
                    updatePlcStatus("Error", Color.RED);
                    // Intentar reconectar
                    try {
                        if (modbusClient != null) {
                            modbusClient.disconnect();
                        }
                    } catch (Exception ignored) {}
                    modbusClient = null;
                    
                    try {
                        Thread.sleep(2000); // Esperar antes de reintentar
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            
            // Cerrar conexión al terminar
            if (modbusClient != null) {
                try {
                    modbusClient.disconnect();
                } catch (Exception ignored) {}
            }
            updatePlcStatus("Desconectado", Color.GRAY);
        });
        
        plcPollingThread.setDaemon(true);
        plcPollingThread.setName("PLC-Polling-Thread");
        plcPollingThread.start();
    }
    
    /**
     * Detiene el polling del PLC.
     */
    private void stopPlcPolling() {
        if (!plcRunning) {
            return;
        }
        
        plcRunning = false;
        System.out.println("[PLC] Deteniendo polling");
        
        if (plcPollingThread != null) {
            plcPollingThread.interrupt();
            try {
                plcPollingThread.join(2000);
            } catch (InterruptedException ignored) {}
        }
    }
    
    /**
     * Inicia la lectura RFID desde el control del PLC.
     * Se ejecuta en el EDT.
     */
    private void startReadingFromPLC() {
        if (connection == null || !connection.isConnected()) {
            System.out.println("[PLC] No hay conexion RFID activa");
            return;
        }
        
        try {
            if (connection.startReading()) {
                isReading = true;
                startReadingButton.setEnabled(false);
                stopReadingButton.setEnabled(true);
                connectionStatusLabel.setText("Leyendo (PLC)");
                connectionStatusLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
                setStatus("Lectura controlada por PLC - Activa");
                logger.info("PLC", "Lectura iniciada por enabler PLC");
            }
        } catch (Exception e) {
            System.out.println("[PLC] Error iniciando lectura: " + e.getMessage());
            logger.error("PLC", "Error iniciando lectura: " + e.getMessage());
        }
    }
    
    /**
     * Detiene la lectura RFID desde el control del PLC.
     * Se ejecuta en el EDT.
     */
    private void stopReadingFromPLC() {
        if (connection == null) return;
        
        try {
            connection.stopReading();
            isReading = false;
            startReadingButton.setEnabled(true);
            stopReadingButton.setEnabled(false);
            connectionStatusLabel.setText("Conectado");
            connectionStatusLabel.setForeground(ModernUIStyle.ACCENT_WARNING);
            setStatus("Lectura detenida por PLC - Esperando enabler");
            logger.info("PLC", "Lectura detenida por enabler PLC");
        } catch (Exception e) {
            System.out.println("[PLC] Error deteniendo lectura: " + e.getMessage());
            logger.error("PLC", "Error deteniendo lectura: " + e.getMessage());
        }
    }
    
    /**
     * Procesa un ciclo de lectura PLC -> RFID -> API -> PLC.
     * 
     * @param modbusClient Cliente Modbus conectado
     */
    private void processPlcReadCycle(ModbusClient modbusClient) {
        try {
            // 1. Obtener último tag leído (el más reciente del RFID)
            String lastEpc = getLastReadTag();
            if (lastEpc == null || lastEpc.isEmpty()) {
                System.out.println("[PLC] No hay tags RFID leidos para procesar");
                return;
            }
            
            System.out.println("[PLC] Procesando tag: " + lastEpc);
            
            // 2. Consultar API para obtener datos del producto
            APIClient apiClient = new APIClient(config);
            String productDataJson = apiClient.getProductData(lastEpc);
            
            if (productDataJson == null) {
                System.out.println("[PLC] No se obtuvieron datos del producto desde API");
                return;
            }
            
            // 3. Parsear datos del producto
            int tipoEmbalaje = extractIntFromJson(productDataJson, "tipo_embalaje");
            int ancho = extractIntFromJson(productDataJson, "ancho");
            int largo = extractIntFromJson(productDataJson, "largo");
            
            System.out.println("[PLC] Datos producto - Tipo: " + tipoEmbalaje + ", Ancho: " + ancho + ", Largo: " + largo);
            
            // 4. Escribir datos al PLC
            modbusClient.writeRegister(config.getPlcRefTipoEmbalaje(), config.getPlcUnitIdTipoEmbalaje(), tipoEmbalaje);
            modbusClient.writeRegister(config.getPlcRefAncho(), config.getPlcUnitIdAncho(), ancho);
            modbusClient.writeRegister(config.getPlcRefLargo(), config.getPlcUnitIdLargo(), largo);
            
            // 5. Activar bandera de confirmación
            modbusClient.writeRegister(config.getPlcRefActiva(), config.getPlcUnitIdActiva(), 1);
            
            System.out.println("[PLC] Datos escritos al PLC correctamente");
            
        } catch (Exception ex) {
            System.out.println("[PLC] Error en ciclo de lectura: " + ex.getMessage());
        }
    }
    
    /**
     * Obtiene el último tag leído del RFID.
     * 
     * @return EPC del último tag o null
     */
    private String getLastReadTag() {
        // Obtener del modelo de la tabla
        int rowCount = tagTableModel.getRowCount();
        if (rowCount > 0) {
            return (String) tagTableModel.getValueAt(0, 0); // Primera columna es EPC
        }
        return null;
    }
    
    /**
     * Extrae un valor entero de un JSON simple.
     * 
     * @param json String JSON
     * @param key Clave a buscar
     * @return Valor entero o 0 si no se encuentra
     */
    private int extractIntFromJson(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":";
            int idx = json.indexOf(searchKey);
            if (idx >= 0) {
                int start = idx + searchKey.length();
                // Saltar espacios
                while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
                    start++;
                }
                int end = start;
                while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
                    end++;
                }
                if (end > start) {
                    return Integer.parseInt(json.substring(start, end));
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }
    
    /**
     * Actualiza el estado visual del PLC.
     */
    private void updatePlcStatus(String status, Color color) {
        SwingUtilities.invokeLater(() -> {
            if (plcStatusLabel != null) {
                plcStatusLabel.setText(status);
            }
            if (plcStatusIndicator != null) {
                plcStatusIndicator.setBackground(color);
            }
        });
    }
    
    /**
     * Crea el panel de configuración avanzada.
     * Organizado en secciones lógicas con descripciones detalladas.
     * 
     * @return Panel de configuración avanzada
     */
    private JPanel createAdvancedPanel() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // ========== SECCIÓN 1: PARÁMETROS DE INVENTARIO C1G2 ==========
        JPanel inventorySection = createAdvancedSection(
            "1. Parámetros de Inventario RFID (C1G2)",
            "Configura cómo el lector interroga las etiquetas RFID según el estándar EPC Class1 Gen2."
        );
        
        JPanel invContent = new JPanel(new GridBagLayout());
        invContent.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel sessionLabel = new JLabel("Sesión:");
        sessionLabel.setFont(sessionLabel.getFont().deriveFont(Font.BOLD));
        invContent.add(sessionLabel, gbc);
        
        gbc.gridx = 1;
        invContent.add(sessionCombo, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel sessionDesc = new JLabel("<html><div style='width:400px; color:#888888;'>" +
            "<b>S0:</b> Volátil - El tag responde siempre (ideal para pocos tags en movimiento)<br>" +
            "<b>S1:</b> Persistente 0.5-5s - El tag espera antes de responder de nuevo<br>" +
            "<b>S2/S3:</b> Persistente 2s+ - Para entornos con múltiples lectores simultáneos" +
            "</div></html>");
        invContent.add(sessionDesc, gbc);
        
        gbc.gridx = 2; gbc.gridy = 0; gbc.gridwidth = 1;
        JLabel targetLabel = new JLabel("Target:");
        targetLabel.setFont(targetLabel.getFont().deriveFont(Font.BOLD));
        invContent.add(targetLabel, gbc);
        
        gbc.gridx = 3;
        invContent.add(targetCombo, gbc);
        
        gbc.gridx = 2; gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel targetDesc = new JLabel("<html><div style='width:300px; color:#888888;'>" +
            "<b>A/B:</b> Lee solo tags en estado A o B<br>" +
            "<b>A↔B:</b> Alterna entre ambos estados (máxima cobertura)" +
            "</div></html>");
        invContent.add(targetDesc, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        JLabel popLabel = new JLabel("Población estimada:");
        popLabel.setFont(popLabel.getFont().deriveFont(Font.BOLD));
        invContent.add(popLabel, gbc);
        
        gbc.gridx = 1;
        invContent.add(tagPopulationSpinner, gbc);
        
        gbc.gridx = 2; gbc.gridwidth = 2;
        JLabel popDesc = new JLabel("<html><div style='color:#888888;'>" +
            "Cantidad aproximada de tags en el campo (optimiza tiempos de respuesta)" +
            "</div></html>");
        invContent.add(popDesc, gbc);
        
        inventorySection.add(invContent, BorderLayout.CENTER);
        mainPanel.add(inventorySection);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // ========== SECCIÓN 2: MODO DENSE READER ==========
        JPanel drmSection = createAdvancedSection(
            "2. Modo Dense Reader (DRM)",
            "Reduce interferencia electromagnética cuando hay múltiples lectores RFID operando en el mismo espacio."
        );
        
        JPanel drmContent = new JPanel(new GridBagLayout());
        drmContent.setOpaque(false);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        invContent.add(denseReaderCheck, gbc);
        drmContent.add(denseReaderCheck, gbc);
        
        gbc.gridx = 1;
        JLabel rfLabel = new JLabel("Índice modo RF:");
        rfLabel.setFont(rfLabel.getFont().deriveFont(Font.BOLD));
        drmContent.add(rfLabel, gbc);
        
        gbc.gridx = 2;
        drmContent.add(rfModeSpinner, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 4;
        JLabel drmDesc = new JLabel("<html><div style='width:600px; color:#888888;'>" +
            "<b>¿Cuándo activar?</b> Cuando hay 2 o más lectores en la misma área (almacén, línea de producción).<br>" +
            "<b>Índice RF:</b> Cada lector debe usar un índice diferente para evitar colisiones de señal.<br>" +
            "<b>Nota:</b> Activar DRM puede reducir ligeramente la velocidad de lectura." +
            "</div></html>");
        drmContent.add(drmDesc, gbc);
        
        drmSection.add(drmContent, BorderLayout.CENTER);
        mainPanel.add(drmSection);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // ========== SECCIÓN 3: TRIGGER POR SENSOR GPI ==========
        JPanel triggerSection = createAdvancedSection(
            "3. Trigger Automático por Sensor (GPI)",
            "Inicia o detiene la lectura automáticamente cuando un sensor externo detecta presencia."
        );
        
        JPanel triggerContent = new JPanel(new GridBagLayout());
        triggerContent.setOpaque(false);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        triggerContent.add(gpiTriggerCheck, gbc);
        
        gbc.gridx = 1;
        JLabel portLabel = new JLabel("Puerto GPI:");
        portLabel.setFont(portLabel.getFont().deriveFont(Font.BOLD));
        triggerContent.add(portLabel, gbc);
        
        gbc.gridx = 2;
        triggerContent.add(gpiTriggerPortCombo, gbc);
        
        gbc.gridx = 3;
        JLabel stateLabel = new JLabel("Activar cuando:");
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD));
        triggerContent.add(stateLabel, gbc);
        
        gbc.gridx = 4;
        triggerContent.add(gpiTriggerStateCombo, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 5;
        JLabel triggerDesc = new JLabel("<html><div style='width:600px; color:#888888;'>" +
            "<b>Uso típico:</b> Sensor de movimiento o barrera fotoeléctrica conectado a un puerto GPI.<br>" +
            "<b>HIGH:</b> Activa lectura cuando el sensor detecta objeto (nivel alto).<br>" +
            "<b>LOW:</b> Activa lectura cuando el sensor NO detecta objeto (nivel bajo).<br>" +
            "<b>Ejemplo:</b> Un montacargas pasa frente al lector → sensor detecta → lectura automática." +
            "</div></html>");
        triggerContent.add(triggerDesc, gbc);
        
        triggerSection.add(triggerContent, BorderLayout.CENTER);
        mainPanel.add(triggerSection);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // ========== SECCIÓN 4: FILTRADO RSSI GLOBAL ==========
        JPanel rssiSection = createAdvancedSection(
            "4. Filtrado Global por Intensidad de Señal (RSSI)",
            "Filtra etiquetas por la fuerza de su señal. Tags con señal débil (lejanos) son ignorados."
        );
        
        JPanel rssiContent = new JPanel(new GridBagLayout());
        rssiContent.setOpaque(false);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        rssiContent.add(rssiFilterCheck, gbc);
        
        gbc.gridx = 1;
        JLabel threshLabel = new JLabel("Umbral mínimo:");
        threshLabel.setFont(threshLabel.getFont().deriveFont(Font.BOLD));
        rssiContent.add(threshLabel, gbc);
        
        gbc.gridx = 2;
        rssiContent.add(rssiThresholdSpinner, gbc);
        
        gbc.gridx = 3;
        rssiContent.add(new JLabel("dBm"), gbc);
        
        gbc.gridx = 4;
        rssiContent.add(rssiStatsLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 5;
        JLabel rssiDesc = new JLabel("<html><div style='width:600px; color:#888888;'>" +
            "<b>RSSI</b> = Received Signal Strength Indicator (Indicador de fuerza de señal recibida).<br>" +
            "<b>Valores típicos:</b> -30 dBm (muy cerca) a -70 dBm (lejos). Valores menores = señal más débil.<br>" +
            "<b>Ejemplo:</b> Umbral -50 dBm → Solo procesa tags con señal fuerte (cercanos al lector).<br>" +
            "<b>Nota:</b> También puedes configurar filtros RSSI individuales por antena en la pestaña 'Antenas'." +
            "</div></html>");
        rssiContent.add(rssiDesc, gbc);
        
        rssiSection.add(rssiContent, BorderLayout.CENTER);
        mainPanel.add(rssiSection);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // ========== SECCIÓN 5: REPORTES ADICIONALES ==========
        JPanel reportSection = createAdvancedSection(
            "5. Datos Adicionales de Lectura",
            "Información extra que el lector puede reportar para aplicaciones especializadas."
        );
        
        JPanel reportContent = new JPanel(new GridBagLayout());
        reportContent.setOpaque(false);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        reportContent.add(reportPhaseCheck, gbc);
        
        gbc.gridx = 1;
        JLabel phaseDesc = new JLabel("<html><div style='color:#888888;'>" +
            "Ángulo de fase de la señal (usado para estimar distancia/posición del tag)" +
            "</div></html>");
        reportContent.add(phaseDesc, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        reportContent.add(reportChannelCheck, gbc);
        
        gbc.gridx = 1;
        JLabel channelDesc = new JLabel("<html><div style='color:#888888;'>" +
            "Frecuencia/canal RF usado para la lectura (útil para diagnóstico de interferencias)" +
            "</div></html>");
        reportContent.add(channelDesc, gbc);
        
        reportSection.add(reportContent, BorderLayout.CENTER);
        mainPanel.add(reportSection);
        
        // ========== SECCIÓN: FILTRO DE DUPLICADOS ==========
        JPanel dupSection = createAdvancedSection(
            "Filtro de Tags Duplicados",
            "Evita procesar el mismo tag múltiples veces dentro de un período de tiempo."
        );
        
        JPanel dupContent = new JPanel(new GridBagLayout());
        dupContent.setOpaque(false);
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.anchor = GridBagConstraints.WEST;
        
        gbc.gridx = 0; gbc.gridy = 0;
        dupContent.add(duplicateFilterCheck, gbc);
        
        gbc.gridx = 1;
        dupContent.add(new JLabel("Expiración (segundos):"), gbc);
        
        gbc.gridx = 2;
        dupContent.add(duplicateExpirationSpinner, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3;
        dupContent.add(noExpirationCheck, gbc);
        
        gbc.gridy = 2;
        dupContent.add(duplicateStatsLabel, gbc);
        
        dupSection.add(dupContent, BorderLayout.CENTER);
        mainPanel.add(dupSection);
        mainPanel.add(Box.createVerticalStrut(15));
        
        mainPanel.add(Box.createVerticalGlue());
        
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scrollPane, BorderLayout.CENTER);
        
        return wrapper;
    }
    
    /**
     * Crea una sección del panel avanzado con título y descripción.
     * 
     * @param title Título de la sección
     * @param description Descripción de lo que hace esta sección
     * @return Panel con estilo de sección
     */
    private JPanel createAdvancedSection(String title, String description) {
        JPanel section = new JPanel(new BorderLayout(0, 8));
        section.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ModernUIStyle.BORDER_DEFAULT, 1),
            BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));
        section.setBackground(ModernUIStyle.BG_CARD);
        
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(ModernUIStyle.ACCENT_PRIMARY);
        header.add(titleLabel, BorderLayout.NORTH);
        
        JLabel descLabel = new JLabel("<html><div style='width:600px;'>" + description + "</div></html>");
        descLabel.setForeground(ModernUIStyle.TEXT_SECONDARY);
        descLabel.setFont(descLabel.getFont().deriveFont(Font.ITALIC, 11f));
        header.add(descLabel, BorderLayout.CENTER);
        
        section.add(header, BorderLayout.NORTH);
        
        return section;
    }
    
    /**
     * Crea el panel de herramientas.
     * 
     * @return Panel de herramientas
     */
    private JPanel createToolsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // Generadores de Servicio
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        JLabel serviceTitle = new JLabel("Generadores de Servicio");
        serviceTitle.setFont(serviceTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(serviceTitle, gbc);
        
        gbc.gridy = 1; gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(generateWindowsServiceButton, gbc);
        
        gbc.gridx = 1;
        panel.add(generateUbuntuServiceButton, gbc);
        
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 3;
        JLabel serviceInfo = new JLabel("<html><i>Genera scripts de instalación para ejecutar como servicio del sistema.</i></html>");
        panel.add(serviceInfo, gbc);
        
        gbc.gridy = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        
        // Configuración
        gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
        JLabel configTitle = new JLabel("Configuración");
        configTitle.setFont(configTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(configTitle, gbc);
        
        gbc.gridy = 5; gbc.gridwidth = 1;
        panel.add(saveConfigButton, gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        JLabel saveInfo = new JLabel("Guarda la configuración actual en rfid_config.json");
        panel.add(saveInfo, gbc);
        
        // Espaciador
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);
        
        return panel;
    }
    
    /**
     * Crea la barra de estado inferior con estilo moderno.
     * 
     * @return Panel de barra de estado
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(15, 0));
        statusBar.setBackground(ModernUIStyle.BG_CARD);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(2, 0, 0, 0, ModernUIStyle.ACCENT_PRIMARY),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));
        
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(statsLabel, BorderLayout.CENTER);
        statusBar.add(activityIndicator, BorderLayout.EAST);
        
        return statusBar;
    }
    
    /**
     * Inicializa los manejadores de eventos.
     */
    private void initializeEventHandlers() {
        connectButton.addActionListener(e -> connect());
        
        disconnectButton.addActionListener(e -> disconnect());
        
        startReadingButton.addActionListener(e -> startReading());
        
        stopReadingButton.addActionListener(e -> stopReading());
        
        autoConnectCheck.addActionListener(e -> {
            config.setAutoConnectEnabled(autoConnectCheck.isSelected());
            autoSaveConfiguration();
        });
        
        clearTagsButton.addActionListener(e -> clearTags());
        
        hexDecimalToggle.addActionListener(e -> {
            displayHexMode = hexDecimalToggle.isSelected();
            hexDecimalToggle.setText(displayHexMode ? "HEX" : "DEC");
            hexDecimalToggle.setToolTipText(displayHexMode ? 
                "Filtro HEX: Acepta todos los tags (A-F, 0-9)" : 
                "Filtro DEC: Solo tags numéricos (0-9)");
            updateTagTable();
            if (apiClient != null) {
                apiClient.setDisplayHexMode(displayHexMode);
            }
            config.setDisplayHexMode(displayHexMode);
            autoSaveConfiguration();
            System.out.println("[Config] Filtro EPC cambiado a: " + (displayHexMode ? "HEX (todos)" : "DEC (solo numéricos)"));
        });
        
        testApiButton.addActionListener(e -> testApiConnection());
        
        for (int i = 0; i < 4; i++) {
            final int port = i + 1;
            gpoButtons[i].addActionListener(e -> toggleGpo(port));
        }
        
        duplicateFilterCheck.addActionListener(e -> {
            duplicateFilter.setEnabled(duplicateFilterCheck.isSelected());
            config.setDuplicateFilterEnabled(duplicateFilterCheck.isSelected());
        });
        
        duplicateExpirationSpinner.addChangeListener(e -> {
            int seconds = (Integer) duplicateExpirationSpinner.getValue();
            duplicateFilter.setExpirationSeconds(seconds);
            config.setDuplicateFilterExpiration(seconds);
        });
        
        noExpirationCheck.addActionListener(e -> {
            boolean noExp = noExpirationCheck.isSelected();
            duplicateFilter.setNoExpiration(noExp);
            duplicateExpirationSpinner.setEnabled(!noExp);
            config.setDuplicateFilterNoExpiration(noExp);
            autoSaveConfiguration();
            if (noExp) {
                duplicateFilter.clear();
            }
        });
        
        apiEnabledCheck.addActionListener(e -> {
            boolean enabled = apiEnabledCheck.isSelected();
            config.setApiEnabled(enabled);
            autoSaveConfiguration();
            logger.debug("Config", "Envío automático API: " + (enabled ? "ACTIVADO" : "DESACTIVADO"));
            System.out.println("[Config] Envío automático API: " + (enabled ? "ACTIVADO" : "DESACTIVADO"));
        });
        
        apiStartControlCheck.addActionListener(e -> {
            config.setApiStartControlEnabled(apiStartControlCheck.isSelected());
            autoSaveConfiguration();
        });
        
        apiPollingCheck.addActionListener(e -> {
            boolean enabled = apiPollingCheck.isSelected();
            config.setApiPollingEnabled(enabled);
            apiPollingEnabled = enabled;
            autoSaveConfiguration();
            
            if (enabled && connection != null && connection.isConnected() && apiClient != null) {
                startApiPolling();
            } else {
                stopApiPolling();
            }
        });
        
        apiPollingIntervalSpinner.addChangeListener(e -> {
            int interval = (Integer) apiPollingIntervalSpinner.getValue();
            config.setApiPollingIntervalSeconds(interval);
            apiPollingIntervalSeconds = interval;
            autoSaveConfiguration();
        });
        
        generateWindowsServiceButton.addActionListener(e -> generateWindowsService());
        generateUbuntuServiceButton.addActionListener(e -> generateUbuntuService());
        
        saveConfigButton.addActionListener(e -> saveConfiguration());
        
        rssiFilterCheck.addActionListener(e -> {
            config.setRssiFilterEnabled(rssiFilterCheck.isSelected());
        });
        
        rssiThresholdSpinner.addChangeListener(e -> {
            config.setRssiThreshold((Integer) rssiThresholdSpinner.getValue());
        });
        
        sessionCombo.addActionListener(e -> {
            config.setInventorySession(sessionCombo.getSelectedIndex());
        });
        
        targetCombo.addActionListener(e -> {
            config.setInventoryTarget(targetCombo.getSelectedIndex());
        });
        
        tagPopulationSpinner.addChangeListener(e -> {
            config.setTagPopulation((Integer) tagPopulationSpinner.getValue());
        });
        
        denseReaderCheck.addActionListener(e -> {
            config.setDenseReaderMode(denseReaderCheck.isSelected());
        });
        
        rfModeSpinner.addChangeListener(e -> {
            config.setRfModeIndex((Integer) rfModeSpinner.getValue());
        });
        
        gpiTriggerCheck.addActionListener(e -> {
            config.setGpiTriggerEnabled(gpiTriggerCheck.isSelected());
        });
        
        gpiTriggerPortCombo.addActionListener(e -> {
            config.setGpiTriggerPort(gpiTriggerPortCombo.getSelectedIndex());
        });
        
        gpiTriggerStateCombo.addActionListener(e -> {
            config.setGpiTriggerState(gpiTriggerStateCombo.getSelectedIndex() == 0);
        });
        
        reportPhaseCheck.addActionListener(e -> {
            config.setReportPhaseAngle(reportPhaseCheck.isSelected());
        });
        
        reportChannelCheck.addActionListener(e -> {
            config.setReportChannelIndex(reportChannelCheck.isSelected());
        });
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (trayIcon != null && SystemTray.isSupported()) {
                    minimizeToTray();
                } else {
                    exitApplication();
                }
            }
        });
    }
    
    /**
     * Carga la configuración actual a la interfaz.
     */
    private void loadConfigToUI() {
        ipField.setText(config.getReaderIP());
        portSpinner.setValue(config.getReaderPort());
        
        for (int i = 0; i < 4; i++) {
            AntennaConfig ac = config.getAntennaConfig(i + 1);
            powerSliders[i].setValue((int) ac.getTransmitPower());
            antennaEnableChecks[i].setSelected(ac.isEnabled());
            rssiSliders[i].setValue(ac.getRssiThreshold());
            rssiFilterChecks[i].setSelected(ac.isRssiFilterEnabled());
            cableLossSpinners[i].setValue(ac.getCableLoss());
            rssiSliders[i].setEnabled(ac.isRssiFilterEnabled());
        }
        
        if (config.getApiEndpoint() != null) {
            apiEndpointField.setText(config.getApiEndpoint());
        }
        if (config.getApiKey() != null) {
            apiKeyField.setText(config.getApiKey());
        }
        if (config.getApiTrabajo() != null) {
            apiTrabajoField.setText(config.getApiTrabajo());
        }
        
        displayHexMode = config.isDisplayHexMode();
        hexDecimalToggle.setSelected(displayHexMode);
        hexDecimalToggle.setText(displayHexMode ? "HEX" : "DEC");
    }
    
    /**
     * Guarda la configuración de la interfaz al modelo.
     */
    private void saveUIToConfig() {
        config.setReaderIP(ipField.getText().trim());
        config.setReaderPort((Integer) portSpinner.getValue());
        
        for (int i = 0; i < 4; i++) {
            AntennaConfig ac = config.getAntennaConfig(i + 1);
            ac.setTransmitPower(powerSliders[i].getValue());
            ac.setEnabled(antennaEnableChecks[i].isSelected());
            ac.setRssiThreshold(rssiSliders[i].getValue());
            ac.setRssiFilterEnabled(rssiFilterChecks[i].isSelected());
            ac.setCableLoss((Double) cableLossSpinners[i].getValue());
        }
        
        config.setApiEndpoint(apiEndpointField.getText().trim());
        config.setApiKey(new String(apiKeyField.getPassword()));
        config.setApiTrabajo(apiTrabajoField.getText().trim());
        
        config.setDisplayHexMode(displayHexMode);
    }
    
    // ==================== Acciones ====================
    
    /**
     * Conecta al lector RFID usando ZebraSDKConnection.
     */
    private void connect() {
        saveUIToConfig();
        
        setStatus("Conectando a " + config.getReaderIP() + "...");
        activityIndicator.setIndeterminate(true);
        connectButton.setEnabled(false);
        
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                connection = new ZebraSDKConnection(config);
                
                connection.setTagCallback(tag -> processTag(tag));
                connection.setStatusCallback(msg -> SwingUtilities.invokeLater(() -> statusLabel.setText(msg)));
                connection.setErrorCallback(msg -> SwingUtilities.invokeLater(() -> showError(msg)));
                
                return connection.connect();
            }
            
            @Override
            protected void done() {
                try {
                    boolean success = get();
                    if (success) {
                        onConnected();
                    } else {
                        onConnectionFailed("No se pudo establecer la conexión");
                    }
                } catch (Exception e) {
                    onConnectionFailed(e.getMessage());
                }
                activityIndicator.setIndeterminate(false);
            }
        }.execute();
    }
    
    /**
     * Desconecta del lector RFID.
     */
    private void disconnect() {
        if (isReading && connection != null) {
            try {
                connection.stopReading();
            } catch (Exception e) {
                // Ignorar errores al detener
            }
        }
        
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                // Ignorar errores al desconectar
            }
        }
        onDisconnected();
    }
    
    /**
     * Inicia la lectura de etiquetas RFID.
     * Si la API está habilitada, primero consulta si tiene autorización para iniciar.
     */
    private void startReading() {
        if (connection == null || !connection.isConnected()) {
            JOptionPane.showMessageDialog(this,
                "No hay conexión con el lector",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        saveUIToConfig();
        setStatus("Consultando API para iniciar lectura...");
        activityIndicator.setIndeterminate(true);
        startReadingButton.setEnabled(false);
        
        new SwingWorker<Boolean, Void>() {
            private String errorMessage = "";
            private boolean apiAutorizado = true;
            
            @Override
            protected Boolean doInBackground() {
                try {
                    // Si la API está habilitada, consultar primero
                    if (apiClient != null && apiStartControlCheck.isSelected()) {
                        System.out.println("[RFID] Consultando API antes de iniciar lectura...");
                        APIClient.InicioResponse respuesta = apiClient.consultarAPIInicio();
                        
                        if (!respuesta.resultado) {
                            apiAutorizado = false;
                            errorMessage = "API denegó inicio: " + respuesta.mensaje;
                            logger.warn("RFID", errorMessage);
                            System.out.println("[RFID] " + errorMessage);
                            return false;
                        }
                        
                        logger.info("RFID", "API autorizó inicio. Iniciando lectura...");
                        System.out.println("[RFID] API autorizó inicio. Fecha servidor: " + 
                                         respuesta.fechaInicio + ", Pausa: " + respuesta.milisegundosParada + "ms");
                        
                        // Verificar si cambió la fecha de inicio (estilo VZEBRA)
                        if (ultimaFechaInicioAPI != respuesta.fechaInicio) {
                            if (ultimaFechaInicioAPI != 0) {
                                // Si ya teníamos una fecha anterior y cambió, resetear duplicados
                                duplicateFilter.clear();
                                logger.info("RFID", "Nueva sesión API - Filtro de duplicados reseteado");
                                System.out.println("[RFID] Nueva sesión API detectada. Filtro de duplicados limpiado.");
                            }
                            ultimaFechaInicioAPI = respuesta.fechaInicio;
                        }
                        
                        // Guardar milisegundos de parada
                        milisegundosParada = respuesta.milisegundosParada > 0 ? respuesta.milisegundosParada : 100;
                        
                        // Iniciar reintentos automáticos de lecturas fallidas
                        if (failedTagStorage != null && config.isRetryFailedEnabled()) {
                            failedTagStorage.setRetryEnabled(true);
                            failedTagStorage.setRetryIntervalSeconds(config.getRetryIntervalSeconds());
                            failedTagStorage.iniciarReintentos(apiClient);
                            logger.info("RFID", "Reintentos automáticos activados (cada " + 
                                       config.getRetryIntervalSeconds() + "s)");
                        }
                    }
                    
                    // Iniciar lectura en el lector
                    return connection.startReading();
                } catch (Exception e) {
                    errorMessage = e.getMessage();
                    return false;
                }
            }
            
            @Override
            protected void done() {
                activityIndicator.setIndeterminate(false);
                try {
                    if (get()) {
                        isReading = true;
                        startReadingButton.setEnabled(false);
                        stopReadingButton.setEnabled(true);
                        
                        // Iniciar polling PLC si está habilitado
                        startPlcPolling();
                        connectionStatusLabel.setText("Leyendo");
                        connectionStatusLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
                        setStatus("Lectura activa - Esperando etiquetas RFID...");
                        System.out.println("[RFID] Lectura iniciada correctamente");
                    } else {
                        startReadingButton.setEnabled(true);
                        String msg = errorMessage.isEmpty() ? "No se pudo iniciar la lectura" : errorMessage;
                        setStatus("Error: " + msg);
                        JOptionPane.showMessageDialog(RFIDMainWindow.this,
                            "Error al iniciar lectura: " + msg,
                            "Error de Lectura",
                            JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    startReadingButton.setEnabled(true);
                    setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }
    
    /**
     * Detiene la lectura de etiquetas RFID.
     */
    private void stopReading() {
        if (connection == null || !connection.isConnected()) {
            return;
        }
        
        setStatus("Deteniendo lectura...");
        stopReadingButton.setEnabled(false);
        
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    return connection.stopReading();
                } catch (Exception e) {
                    return false;
                }
            }
            
            @Override
            protected void done() {
                isReading = false;
                startReadingButton.setEnabled(true);
                stopReadingButton.setEnabled(false);
                
                // Detener polling PLC
                stopPlcPolling();
                
                // Limpiar cola de envío si está configurado
                if (config.isClearQueueOnStop() && apiClient != null) {
                    int cleared = apiClient.clearQueue();
                    System.out.println("[RFID] Cola de API limpiada: " + cleared + " tags descartados");
                }
                
                connectionStatusLabel.setText("Conectado");
                setStatus("Lectura detenida - Conectado a " + config.getReaderIP());
                System.out.println("[RFID] Lectura detenida");
                
                // Limpiar lista de tags al detener
                tagCache.clear();
                updateTagTable();
                System.out.println("[RFID] Lista de tags limpiada");
            }
        }.execute();
    }
    
    /**
     * Procesa un tag leído recibido del callback de ZebraSDKConnection.
     * 
     * @param tag Datos del tag leído
     */
    private void processTag(TagData tag) {
        if (tag == null) {
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            addTag(tag);
        });
    }
    
    /**
     * Muestra un mensaje de error.
     * 
     * @param message Mensaje de error
     */
    private void showError(String message) {
        setStatus("Error: " + message);
        System.err.println("[RFID] Error: " + message);
    }
    
    /**
     * Llamado cuando la conexión es exitosa.
     */
    private void onConnected() {
        logger.logConnection(config.getReaderIP(), true);
        connectionIndicator.setBackground(ModernUIStyle.ACCENT_SUCCESS);
        connectionStatusLabel.setText("Conectado");
        connectionStatusLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
        connectButton.setEnabled(false);
        disconnectButton.setEnabled(true);
        startReadingButton.setEnabled(true);
        stopReadingButton.setEnabled(false);
        setStatus("Conectado a " + config.getReaderIP() + " - Presione 'Iniciar Lectura' para leer etiquetas");
        statistics.recordConnection();
        
        // Iniciar polling PLC si está habilitado (el PLC controlará la lectura)
        startPlcPolling();
        
        config.setLastConnectedIP(config.getReaderIP());
        autoSaveConfiguration();
        System.out.println("[Config] Última IP conectada guardada: " + config.getReaderIP());
        
        // PRIMERO: Iniciar cliente API si está configurado
        if (!apiEndpointField.getText().trim().isEmpty()) {
            startApiClient();
        }
        
        // DESPUÉS: Iniciar polling automático si está habilitado
        if (config.isApiPollingEnabled() && apiClient != null) {
            apiPollingEnabled = true;
            apiPollingIntervalSeconds = config.getApiPollingIntervalSeconds();
            startApiPolling();
            System.out.println("[APIPolling] Polling automático iniciado al conectar");
            logger.info("APIPolling", "Polling automático iniciado al conectar (cada " + apiPollingIntervalSeconds + "s)");
        }
    }
    
    /**
     * Llamado cuando la conexión falla.
     * 
     * @param error Mensaje de error
     */
    private void onConnectionFailed(String error) {
        connectionIndicator.setBackground(ModernUIStyle.ACCENT_ERROR);
        connectionStatusLabel.setText("Error: " + error);
        connectionStatusLabel.setForeground(ModernUIStyle.ACCENT_ERROR);
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        setStatus("Error de conexión: " + error);
        statistics.recordError("Connection error");
    }
    
    /**
     * Llamado cuando se desconecta.
     */
    private void onDisconnected() {
        // Detener reintentos y polling al desconectar
        if (failedTagStorage != null) {
            failedTagStorage.shutdown();
        }
        stopApiPolling();
        connectionIndicator.setBackground(ModernUIStyle.ACCENT_ERROR);
        connectionStatusLabel.setText("Desconectado");
        connectionStatusLabel.setForeground(ModernUIStyle.TEXT_SECONDARY);
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        startReadingButton.setEnabled(false);
        stopReadingButton.setEnabled(false);
        isReading = false;
        setStatus("Desconectado");
        
        connection = null;
        
        if (apiClient != null) {
            apiClient.stop();
            apiClient = null;
        }
    }
    
    /**
     * Cambia el estado de una salida GPO.
     * 
     * @param port Puerto GPO (1-4)
     */
    private void toggleGpo(int port) {
        if (connection == null || !connection.isConnected()) {
            JOptionPane.showMessageDialog(this,
                "No hay conexión con el lector",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            gpoButtons[port - 1].setSelected(false);
            return;
        }
        
        boolean newState = gpoButtons[port - 1].isSelected();
        
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    return connection.setGPO(port, newState);
                } catch (Exception e) {
                    return false;
                }
            }
            
            @Override
            protected void done() {
                try {
                    if (get()) {
                        gpoButtons[port - 1].setBackground(newState ? 
                            ModernUIStyle.ACCENT_SUCCESS : ModernUIStyle.BG_CARD);
                        System.out.println("[GPIO] GPO " + port + " = " + (newState ? "ON" : "OFF"));
                    } else {
                        gpoButtons[port - 1].setSelected(!newState);
                        JOptionPane.showMessageDialog(RFIDMainWindow.this,
                            "Error al cambiar estado GPO " + port,
                            "Error GPIO",
                            JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception e) {
                    gpoButtons[port - 1].setSelected(!newState);
                }
            }
        }.execute();
    }
    
    /**
     * Añade un tag leído a la tabla y cache.
     * 
     * @param tag Datos del tag
     */
    private void addTag(TagData tag) {
        if (config.isRssiFilterEnabled()) {
            if (tag.getRssi() < config.getRssiThreshold()) {
                rssiFilteredCount++;
                return;
            }
        }
        
        // Filtro HEX/DECIMAL: en modo DECIMAL solo acepta EPCs con dígitos 0-9
        if (!config.isDisplayHexMode()) {
            String epcToCheck = tag.getEpc();
            if (epcToCheck != null && !epcToCheck.matches("[0-9]+")) {
                // EPC contiene letras (A-F), ignorar en modo DECIMAL
                return;
            }
        }
        
        if (duplicateFilter.isEnabled() && !duplicateFilter.shouldProcess(tag.getEpc())) {
            return;
        }
        
        String epc = tag.getEpc();
        TagData existing = tagCache.get(epc);
        
        if (existing != null) {
            existing.merge(tag);
        } else {
            if (tagCache.size() >= maxTagsInTable) {
                String oldestKey = tagCache.keySet().iterator().next();
                tagCache.remove(oldestKey);
            }
            tagCache.put(epc, tag);
        }
        
        statistics.recordTagRead(tag);
        
        // Envío directo a la API estilo VZEBRA (throttling se aplica en el hilo de envío)
        if (apiClient != null && apiEnabledCheck.isSelected()) {
            final String tagEpc = tag.getEpc();
            final int antenna = tag.getAntennaPort();
            final int rssiValue = (int) tag.getRssi();
            final TagData tagRef = tag;
            
            // Marcar como "Enviando"
            tagRef.setApiStatus("Enviando...");
            SwingUtilities.invokeLater(this::updateTagTable);
            
            // Envío en hilo separado con throttling para no bloquear EDT
            new Thread(() -> {
                try {
                    long ahora = System.currentTimeMillis();
                    long espera = milisegundosParada - (ahora - ultimoEnvioAPI);
                    
                    // Aplicar throttling si es necesario (máximo 200ms de espera)
                    if (milisegundosParada > 0 && espera > 0 && espera < 200) {
                        Thread.sleep(espera);
                    }
                    
                    ultimoEnvioAPI = System.currentTimeMillis();
                    boolean exito = apiClient.enviarTagAPI(tagEpc, antenna, rssiValue);
                    
                    // Actualizar estado según resultado
                    tagRef.setApiStatus(exito ? "OK" : "Error");
                    SwingUtilities.invokeLater(this::updateTagTable);
                    
                } catch (Exception e) {
                    logger.error("API", "Error en envío throttled: " + e.getMessage());
                    tagRef.setApiStatus("Error");
                    SwingUtilities.invokeLater(this::updateTagTable);
                }
            }, "API-Send-" + tagEpc.substring(Math.max(0, tagEpc.length()-4))).start();
        }
        
        updateTagTable();
    }
    
    /**
     * Actualiza la tabla de tags con los datos del cache.
     */
    private void updateTagTable() {
        tagTableModel.setRowCount(0);
        
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        
        java.util.List<TagData> sorted = new ArrayList<>(tagCache.values());
        sorted.sort((a, b) -> Long.compare(b.getLastSeen(), a.getLastSeen()));
        
        for (TagData tag : sorted) {
            // Mostrar EPC original sin conversión (el filtro ya se aplicó en addTag)
            tagTableModel.addRow(new Object[]{
                tag.getEpc(),
                String.format("%.1f", tag.getRssi()),
                tag.getAntennaPort(),
                tag.getReadCount(),
                sdf.format(new Date(tag.getLastSeen())),
                tag.getApiStatus()
            });
        }
        
        if (autoScrollCheck.isSelected() && tagTableModel.getRowCount() > 0) {
            tagTable.scrollRectToVisible(tagTable.getCellRect(0, 0, true));
        }
    }
    
    /**
     * Convierte una cadena hexadecimal a representación decimal.
     * 
     * @param hex Cadena hexadecimal
     * @return Representación decimal
     */
    private String hexToDecimal(String hex) {
        if (hex == null || hex.isEmpty()) {
            return "";
        }
        try {
            StringBuilder decimal = new StringBuilder();
            for (int i = 0; i < hex.length(); i += 2) {
                String byteHex = hex.substring(i, Math.min(i + 2, hex.length()));
                int value = Integer.parseInt(byteHex, 16);
                if (decimal.length() > 0) {
                    decimal.append(".");
                }
                decimal.append(value);
            }
            return decimal.toString();
        } catch (Exception e) {
            return hex;
        }
    }
    
    /**
     * Limpia la tabla de tags.
     */
    private void clearTags() {
        tagCache.clear();
        tagTableModel.setRowCount(0);
        statistics.reset();
        duplicateFilter.clear();
        rssiFilteredCount = 0;
        updateStats();
    }
    
    /**
     * Actualiza las estadísticas de la barra de estado.
     */
    private void updateStats() {
        long total = statistics.getTagsRead();
        int unique = tagCache.size();
        double tps = statistics.getTagsPerSecond();
        
        tagsReadLabel.setText(String.valueOf(total));
        uniqueTagsLabel.setText(String.valueOf(unique));
        tpsLabel.setText(String.format("%.1f", tps));
        statsLabel.setText(String.format("Tags: %d | Únicos: %d | TPS: %.1f", total, unique, tps));
        
        duplicateStatsLabel.setText(String.format("Filtrados: %d | Procesados: %d",
            duplicateFilter.getDuplicatesFiltered(), duplicateFilter.getUniqueTagsProcessed()));
        
        rssiStatsLabel.setText("Filtrados por RSSI: " + rssiFilteredCount);
    }
    
    /**
     * Actualiza los indicadores GPI.
     */
    private void updateGpiIndicators() {
        if (connection != null && connection.isConnected()) {
            for (int i = 0; i < 4; i++) {
                try {
                    boolean state = connection.getGPI(i + 1);
                    gpiIndicators[i].setBackground(state ? 
                        ModernUIStyle.ACCENT_SUCCESS : ModernUIStyle.TEXT_MUTED);
                    gpiLabels[i].setText("GPI " + (i + 1) + ": " + (state ? "HIGH" : "LOW"));
                } catch (Exception e) {
                    gpiLabels[i].setText("GPI " + (i + 1) + ": --");
                }
            }
        }
    }
    
    /**
     * Establece el mensaje de estado.
     * 
     * @param status Mensaje de estado
     */
    private void setStatus(String status) {
        statusLabel.setText("" + status);
    }
    
    /**
     * Inicia el timer de actualización.
     */
    private void startUpdateTimer() {
        updateExecutor = Executors.newSingleThreadScheduledExecutor();
        updateExecutor.scheduleAtFixedRate(() -> {
            if (running) {
                SwingUtilities.invokeLater(() -> {
                    updateStats();
                    updateGpiIndicators();
                });
            }
        }, 0, MONITOR_REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Detiene el timer de actualización y libera recursos.
     */
    public void shutdown() {
        running = false;
        
        if (updateExecutor != null) {
            updateExecutor.shutdown();
            try {
                updateExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                updateExecutor.shutdownNow();
            }
        }
        
        disconnect();
    }
    
    /**
     * Prueba la conexión API.
     */
    private void testApiConnection() {
        String endpoint = apiEndpointField.getText().trim();
        String key = new String(apiKeyField.getPassword());
        
        if (endpoint.isEmpty()) {
            apiStatusLabel.setText("Endpoint vacío");
            apiStatusLabel.setForeground(ModernUIStyle.ACCENT_ERROR);
            return;
        }
        
        apiStatusLabel.setText("Probando...");
        apiStatusLabel.setForeground(ModernUIStyle.ACCENT_WARNING);
        
        new SwingWorker<Boolean, Void>() {
            private String resultMessage = "";
            
            @Override
            protected Boolean doInBackground() {
                try {
                    APIClient testClient = new APIClient(endpoint, key);
                    boolean result = testClient.testConnection();
                    resultMessage = result ? "Conexión exitosa" : "Sin respuesta";
                    return result;
                } catch (Exception e) {
                    resultMessage = "Error: " + e.getMessage();
                    return false;
                }
            }
            
            @Override
            protected void done() {
                try {
                    if (get()) {
                        apiStatusLabel.setText(resultMessage);
                        apiStatusLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
                    } else {
                        apiStatusLabel.setText(resultMessage);
                        apiStatusLabel.setForeground(ModernUIStyle.ACCENT_ERROR);
                    }
                } catch (Exception e) {
                    apiStatusLabel.setText("Error: " + e.getMessage());
                    apiStatusLabel.setForeground(ModernUIStyle.ACCENT_ERROR);
                }
            }
        }.execute();
    }
    
    /**
     * Inicia el cliente API.
     */
    private void startApiClient() {
        String endpoint = apiEndpointField.getText().trim();
        String key = new String(apiKeyField.getPassword());
        String trabajo = apiTrabajoField.getText().trim();
        
        if (!endpoint.isEmpty()) {
            apiClient = new APIClient(endpoint, key);
            apiClient.setReaderIP(config.getReaderIP());
            apiClient.setApiTrabajo(trabajo);
            apiClient.setDisplayHexMode(displayHexMode);
            apiClient.setFailedTagStorage(failedTagStorage);
            apiClient.start();
            apiStatusLabel.setText("Cliente API iniciado");
            apiStatusLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
            
            // Iniciar polling si está habilitado
            if (apiPollingEnabled && apiPollingCheck != null && apiPollingCheck.isSelected()) {
                startApiPolling();
            }
        }
    }
    
    /**
     * Inicia el polling periódico a la API de inicio (estilo VZEBRA).
     * Consulta la API cada X segundos y controla lectura automáticamente.
     */
    private void startApiPolling() {
        if (apiPollingExecutor != null && !apiPollingExecutor.isShutdown()) {
            logger.debug("APIPolling", "Polling ya activo");
            return; // Ya está corriendo
        }
        
        logger.info("APIPolling", "Iniciando polling cada " + apiPollingIntervalSeconds + "s");
        System.out.println("[APIPolling] Iniciando polling cada " + apiPollingIntervalSeconds + "s");
        
        apiPollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "API-Polling");
            t.setDaemon(true);
            return t;
        });
        
        int interval = config.getApiPollingIntervalSeconds();
        if (interval < 1) interval = 5;
        
        apiPollingExecutor.scheduleAtFixedRate(() -> {
            try {
                pollApiInicio();
            } catch (Exception e) {
                logger.error("APIPolling", "Error en polling: " + e.getMessage());
            }
        }, 0, interval, java.util.concurrent.TimeUnit.SECONDS);
        
        logger.info("APIPolling", "Polling iniciado (cada " + interval + "s)");
        System.out.println("[APIPolling] Polling iniciado cada " + interval + " segundos");
    }
    
    /**
     * Detiene el polling periódico.
     */
    private void stopApiPolling() {
        if (apiPollingExecutor != null && !apiPollingExecutor.isShutdown()) {
            apiPollingExecutor.shutdown();
            logger.info("APIPolling", "Polling detenido");
            System.out.println("[APIPolling] Polling detenido");
        }
    }
    
    /**
     * Ejecuta una consulta a la API de inicio y controla la lectura automáticamente.
     * Este método se ejecuta en un thread de background, pero todas las operaciones
     * de UI y lector se ejecutan en el EDT mediante SwingUtilities.invokeLater.
     */
    private void pollApiInicio() {
        if (apiClient == null || connection == null || !connection.isConnected()) {
            return;
        }
        
        try {
            final APIClient.InicioResponse respuesta = apiClient.consultarAPIInicio();
            
            // Todas las operaciones de UI/lector deben ir en el EDT
            SwingUtilities.invokeLater(() -> {
                procesarRespuestaPolling(respuesta);
            });
            
        } catch (Exception e) {
            logger.error("APIPolling", "Error consultando API: " + e.getMessage());
        }
    }
    
    /**
     * Procesa la respuesta del polling en el EDT.
     * Controla inicio/detención de lectura automáticamente.
     */
    private void procesarRespuestaPolling(APIClient.InicioResponse respuesta) {
        // Tiempo actual en segundos UNIX
        long ahoraUnix = System.currentTimeMillis() / 1000;
        
        // Ventana de validez: 15 segundos
        final int VENTANA_SEGUNDOS = 15;
        
        // Calcular diferencia entre fecha del servidor y ahora
        long diferencia = respuesta.fecha - ahoraUnix;
        
        System.out.println("[APIPolling] Respuesta: resultado=" + respuesta.resultado + 
                         ", fecha=" + respuesta.fecha + ", fecha_inicio=" + respuesta.fechaInicio + 
                         ", ahora=" + ahoraUnix + 
                         ", diferencia=" + diferencia + "s");
        
        // Determinar si debemos leer basado en resultado Y fecha
        boolean deberíaLeer = false;
        String razon = "";
        
        if (!respuesta.resultado) {
            // API explícitamente dice NO
            deberíaLeer = false;
            razon = respuesta.mensaje.isEmpty() ? "API denegó lectura" : respuesta.mensaje;
        } else {
            // API dice SÍ, pero verificar la fecha
            // La fecha debe estar dentro de la ventana: ahora-60s <= fecha <= ahora+60s
            if (diferencia >= -VENTANA_SEGUNDOS && diferencia <= VENTANA_SEGUNDOS) {
                // Fecha válida, dentro de la ventana de 60 segundos
                deberíaLeer = true;
                razon = "Fecha válida (diferencia: " + diferencia + "s)";
            } else if (diferencia < -VENTANA_SEGUNDOS) {
                // Fecha expirada (pasaron más de 60 segundos)
                deberíaLeer = false;
                razon = "Fecha expirada (hace " + Math.abs(diferencia) + "s)";
            } else {
                // Fecha en el futuro lejano
                deberíaLeer = false;
                razon = "Fecha futura (en " + diferencia + "s)";
            }
        }
        
        System.out.println("[APIPolling] Decisión: " + (deberíaLeer ? "LEER" : "DETENER") + " - " + razon);
        
        if (deberíaLeer) {
            // DEBE LEER
            
            // Verificar si cambió fecha_inicio (reset de duplicados)
            if (ultimaFechaInicioAPI != respuesta.fechaInicio) {
                if (ultimaFechaInicioAPI != 0) {
                    duplicateFilter.clear();
                    logger.info("APIPolling", "Nueva sesión - Filtro de duplicados reseteado");
                    System.out.println("[APIPolling] Nueva fecha_inicio detectada. Duplicados limpiados.");
                }
                ultimaFechaInicioAPI = respuesta.fechaInicio;
            }
            
            // Guardar milisegundos de parada
            milisegundosParada = respuesta.milisegundosParada > 0 ? respuesta.milisegundosParada : 100;
            
            // Iniciar lectura si no está leyendo
            if (!isReading && connection != null && connection.isConnected()) {
                logger.info("APIPolling", "Iniciando lectura: " + razon);
                System.out.println("[APIPolling] Iniciando lectura...");
                
                try {
                    if (connection.startReading()) {
                        isReading = true;
                        startReadingButton.setEnabled(false);
                        stopReadingButton.setEnabled(true);
                        
                        // Iniciar polling PLC si está habilitado
                        startPlcPolling();
                        connectionStatusLabel.setText("Leyendo (Auto)");
                        connectionStatusLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
                        setStatus("Lectura activa - Control por API");
                        
                        // Iniciar reintentos si está habilitado
                        if (failedTagStorage != null && config.isRetryFailedEnabled()) {
                            failedTagStorage.setRetryEnabled(true);
                            failedTagStorage.setRetryIntervalSeconds(config.getRetryIntervalSeconds());
                            failedTagStorage.iniciarReintentos(apiClient);
                        }
                    }
                } catch (Exception e) {
                    logger.error("APIPolling", "Error iniciando lectura: " + e.getMessage());
                }
            }
            
        } else {
            // DEBE DETENER
            if (isReading && connection != null) {
                logger.warn("APIPolling", "Deteniendo lectura: " + razon);
                System.out.println("[APIPolling] Deteniendo lectura: " + razon);
                
                try {
                    connection.stopReading();
                    isReading = false;
                    startReadingButton.setEnabled(true);
                    stopReadingButton.setEnabled(false);
                    connectionStatusLabel.setText("Pausado por API");
                    connectionStatusLabel.setForeground(ModernUIStyle.ACCENT_WARNING);
                    setStatus("Lectura pausada: " + razon);
                    
                    // Limpiar lista de tags al detener
                    tagCache.clear();
                    updateTagTable();
                    System.out.println("[APIPolling] Lista de tags limpiada");
                } catch (Exception e) {
                    logger.error("APIPolling", "Error deteniendo lectura: " + e.getMessage());
                }
            }
        }
    }
    
    // ==================== Getters ====================
    
    public RFIDConfig getConfig() {
        return config;
    }
    
    public ZebraSDKConnection getConnection() {
        return connection;
    }
    
    public ReaderStatistics getStatistics() {
        return statistics;
    }
    
    public APIClient getApiClient() {
        return apiClient;
    }
    
    public DuplicateFilter getDuplicateFilter() {
        return duplicateFilter;
    }
    
    // ==================== Métodos de Herramientas ====================
    
    /**
     * Genera los scripts de instalación para Windows Service.
     */
    private void generateWindowsService() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccionar carpeta de destino");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                WindowsServiceInstaller installer = new WindowsServiceInstaller();
                installer.setServiceName("RFIDZebraService");
                installer.setJarFileName("rfid-zebra.jar");
                installer.setAutoStart(true);
                installer.generateAll(chooser.getSelectedFile().getAbsolutePath());
                
                JOptionPane.showMessageDialog(this,
                    "Scripts de Windows generados exitosamente:\n" +
                    "- install-service-nssm.bat\n" +
                    "- install-service-winsw.bat\n" +
                    "- uninstall-service.bat\n" +
                    "- LEEME-Windows.txt",
                    "Éxito",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Error al generar scripts: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Genera el script de instalación para Ubuntu/Debian.
     */
    private void generateUbuntuService() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar script de instalación Ubuntu");
        chooser.setSelectedFile(new java.io.File("install-rfid-ubuntu.sh"));
        
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("#!/bin/bash\n");
                sb.append("# Script de instalación para Ubuntu/Debian\n");
                sb.append("# Ejecutar con: sudo bash install-rfid-ubuntu.sh\n\n");
                
                sb.append("set -e\n\n");
                
                sb.append("echo \"========================================\"\n");
                sb.append("echo \"  Instalador RFID Zebra FX7500\"\n");
                sb.append("echo \"========================================\"\n\n");
                
                sb.append("# Verificar permisos de root\n");
                sb.append("if [ \"$EUID\" -ne 0 ]; then\n");
                sb.append("    echo \"Error: Ejecutar con sudo\"\n");
                sb.append("    exit 1\n");
                sb.append("fi\n\n");
                
                sb.append("# Instalar Java si no existe\n");
                sb.append("if ! command -v java &> /dev/null; then\n");
                sb.append("    echo \"Instalando OpenJDK 17...\"\n");
                sb.append("    apt-get update\n");
                sb.append("    apt-get install -y openjdk-17-jre-headless\n");
                sb.append("fi\n\n");
                
                sb.append("# Crear directorio de instalación\n");
                sb.append("INSTALL_DIR=\"/opt/rfid-zebra\"\n");
                sb.append("mkdir -p $INSTALL_DIR\n");
                sb.append("mkdir -p $INSTALL_DIR/logs\n\n");
                
                sb.append("# Copiar archivos\n");
                sb.append("cp rfid-zebra.jar $INSTALL_DIR/\n");
                sb.append("[ -f rfid_config.json ] && cp rfid_config.json $INSTALL_DIR/\n\n");
                
                sb.append("# Crear servicio systemd\n");
                sb.append("cat > /etc/systemd/system/rfid-zebra.service << EOF\n");
                sb.append("[Unit]\n");
                sb.append("Description=RFID Zebra FX7500 Service\n");
                sb.append("After=network.target\n\n");
                sb.append("[Service]\n");
                sb.append("Type=simple\n");
                sb.append("User=root\n");
                sb.append("WorkingDirectory=$INSTALL_DIR\n");
                sb.append("ExecStart=/usr/bin/java -Xmx256m -jar rfid-zebra.jar --headless --autostart\n");
                sb.append("Restart=always\n");
                sb.append("RestartSec=10\n\n");
                sb.append("[Install]\n");
                sb.append("WantedBy=multi-user.target\n");
                sb.append("EOF\n\n");
                
                sb.append("# Habilitar e iniciar servicio\n");
                sb.append("systemctl daemon-reload\n");
                sb.append("systemctl enable rfid-zebra\n");
                sb.append("systemctl start rfid-zebra\n\n");
                
                sb.append("echo \"\"\n");
                sb.append("echo \"Instalación completada!\"\n");
                sb.append("echo \"Comandos útiles:\"\n");
                sb.append("echo \"  sudo systemctl status rfid-zebra\"\n");
                sb.append("echo \"  sudo journalctl -u rfid-zebra -f\"\n");
                
                java.io.FileWriter writer = new java.io.FileWriter(chooser.getSelectedFile());
                writer.write(sb.toString());
                writer.close();
                
                JOptionPane.showMessageDialog(this,
                    "Script de Ubuntu generado: " + chooser.getSelectedFile().getName(),
                    "Éxito",
                    JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "Error al generar script: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Guarda la configuración actual en archivo JSON.
     */
    private void saveConfiguration() {
        saveUIToConfig();
        
        try {
            config.saveToFile(CONFIG_FILE_PATH);
            JOptionPane.showMessageDialog(this,
                "Configuración guardada en " + CONFIG_FILE_PATH,
                "Guardado",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error al guardar: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
    
    /**
     * Guarda la configuración automáticamente sin mostrar mensaje.
     */
    private void autoSaveConfiguration() {
        saveUIToConfig();
        try {
            config.saveToFile(CONFIG_FILE_PATH);
        } catch (Exception e) {
            System.err.println("[Config] Error al auto-guardar: " + e.getMessage());
        }
    }
}
