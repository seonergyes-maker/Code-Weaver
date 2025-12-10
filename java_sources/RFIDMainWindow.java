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
    private static final String WINDOW_TITLE = "Zebra FX7500 RFID Manager";
    
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
    
    // ==================== Componentes de antenas ====================
    private JSlider[] powerSliders = new JSlider[4];
    private JCheckBox[] antennaEnableChecks = new JCheckBox[4];
    private JLabel[] powerLabels = new JLabel[4];
    
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
        
        connectButton = new JButton("\u25B6 Conectar");
        ModernUIStyle.stylePrimaryButton(connectButton);
        
        disconnectButton = new JButton("\u25A0 Desconectar");
        ModernUIStyle.styleDangerButton(disconnectButton);
        disconnectButton.setEnabled(false);
        
        startReadingButton = new JButton("\u25B6 Iniciar Lectura");
        ModernUIStyle.stylePrimaryButton(startReadingButton);
        startReadingButton.setEnabled(false);
        startReadingButton.setToolTipText("Iniciar la lectura de etiquetas RFID");
        
        stopReadingButton = new JButton("\u25A0 Detener Lectura");
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
            powerSliders[i] = new JSlider(10, 30, (int) config.getAntennaConfig(i + 1).getTransmitPower());
            powerSliders[i].setMajorTickSpacing(5);
            powerSliders[i].setMinorTickSpacing(1);
            powerSliders[i].setPaintTicks(true);
            powerSliders[i].setPaintLabels(true);
            ModernUIStyle.styleSlider(powerSliders[i]);
            
            antennaEnableChecks[i] = new JCheckBox("Habilitada", config.getAntennaConfig(i + 1).isEnabled());
            ModernUIStyle.styleCheckBox(antennaEnableChecks[i]);
            
            powerLabels[i] = new JLabel(powerSliders[i].getValue() + " dBm");
            powerLabels[i].setForeground(ModernUIStyle.ACCENT_PRIMARY);
            powerLabels[i].setFont(new Font("Consolas", Font.BOLD, 14));
        }
        
        String[] columnNames = {"EPC", "RSSI (dBm)", "Antena", "Lecturas", "Última Lectura"};
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
        
        clearTagsButton = new JButton("\u2715 Limpiar");
        ModernUIStyle.styleSecondaryButton(clearTagsButton);
        
        autoScrollCheck = new JCheckBox("Auto-scroll", true);
        ModernUIStyle.styleCheckBox(autoScrollCheck);
        
        hexDecimalToggle = new JToggleButton("HEX", true);
        hexDecimalToggle.setToolTipText("Alternar entre visualización Hexadecimal y Decimal del EPC");
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
        
        testApiButton = new JButton("\u21C4 Probar Conexion");
        ModernUIStyle.styleSecondaryButton(testApiButton);
        
        apiStatusLabel = new JLabel("No configurado");
        apiStatusLabel.setForeground(ModernUIStyle.TEXT_MUTED);
        
        apiEnabledCheck = new JCheckBox("Envío automático habilitado", false);
        ModernUIStyle.styleCheckBox(apiEnabledCheck);
        
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
        
        statusLabel = new JLabel("\u25CF Listo");
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
        
        generateWindowsServiceButton = new JButton("\u229E Generar Instalador Windows");
        ModernUIStyle.stylePrimaryButton(generateWindowsServiceButton);
        
        generateUbuntuServiceButton = new JButton("\u2318 Generar Script Ubuntu");
        ModernUIStyle.styleSecondaryButton(generateUbuntuServiceButton);
        
        saveConfigButton = new JButton("\u2714 Guardar Configuracion");
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
        tabbedPane.addTab("Antenas", createAntennasPanel());
        tabbedPane.addTab("Monitoreo", createMonitorPanel());
        tabbedPane.addTab("API", createApiPanel());
        tabbedPane.addTab("GPIO", createGpioPanel());
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
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ModernUIStyle.BORDER_DEFAULT),
                "Antena " + (antennaIndex + 1)
            ),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(antennaEnableChecks[antennaIndex]);
        topPanel.add(powerLabels[antennaIndex]);
        panel.add(topPanel, BorderLayout.NORTH);
        
        panel.add(powerSliders[antennaIndex], BorderLayout.CENTER);
        
        final int idx = antennaIndex;
        powerSliders[antennaIndex].addChangeListener(e -> {
            powerLabels[idx].setText(powerSliders[idx].getValue() + " dBm");
        });
        
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
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Endpoint:"), gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        panel.add(apiEndpointField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("API Key:"), gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        panel.add(apiKeyField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        panel.add(new JLabel("ID Trabajo:"), gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        panel.add(apiTrabajoField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(testApiButton, gbc);
        
        gbc.gridx = 1;
        panel.add(apiStatusLabel, gbc);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        panel.add(apiEnabledCheck, gbc);
        
        gbc.gridy = 5; gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);
        
        return panel;
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
     * Crea el panel de configuración avanzada.
     * 
     * @return Panel de configuración avanzada
     */
    private JPanel createAdvancedPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 10, 8, 10);
        gbc.anchor = GridBagConstraints.WEST;
        
        int row = 0;
        
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 4;
        JLabel rssiTitle = new JLabel("Filtrado por RSSI");
        rssiTitle.setFont(rssiTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(rssiTitle, gbc);
        
        gbc.gridy = row; gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(rssiFilterCheck, gbc);
        gbc.gridx = 1;
        panel.add(new JLabel("Umbral mínimo:"), gbc);
        gbc.gridx = 2;
        panel.add(rssiThresholdSpinner, gbc);
        gbc.gridx = 3;
        panel.add(new JLabel("dBm"), gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 4;
        panel.add(rssiStatsLabel, gbc);
        
        gbc.gridy = row++; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        
        gbc.gridy = row++; gbc.fill = GridBagConstraints.NONE;
        JLabel invTitle = new JLabel("Inventario C1G2");
        invTitle.setFont(invTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(invTitle, gbc);
        
        gbc.gridy = row; gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(new JLabel("Sesión:"), gbc);
        gbc.gridx = 1;
        panel.add(sessionCombo, gbc);
        gbc.gridx = 2;
        panel.add(new JLabel("Target:"), gbc);
        gbc.gridx = 3;
        panel.add(targetCombo, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row;
        panel.add(new JLabel("Población tags:"), gbc);
        gbc.gridx = 1;
        panel.add(tagPopulationSpinner, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 4;
        panel.add(new JSeparator(), gbc);
        
        gbc.gridy = row++;
        JLabel drmTitle = new JLabel("Dense Reader Mode");
        drmTitle.setFont(drmTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(drmTitle, gbc);
        
        gbc.gridy = row; gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(denseReaderCheck, gbc);
        gbc.gridx = 1;
        panel.add(new JLabel("Índice modo RF:"), gbc);
        gbc.gridx = 2;
        panel.add(rfModeSpinner, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 4;
        JLabel drmInfo = new JLabel("<html><i>DRM reduce interferencia cuando hay múltiples lectores cercanos.</i></html>");
        panel.add(drmInfo, gbc);
        
        gbc.gridy = row++; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        
        gbc.gridy = row++; gbc.fill = GridBagConstraints.NONE;
        JLabel triggerTitle = new JLabel("Trigger por Sensor (GPI)");
        triggerTitle.setFont(triggerTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(triggerTitle, gbc);
        
        gbc.gridy = row; gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(gpiTriggerCheck, gbc);
        gbc.gridx = 1;
        panel.add(new JLabel("Puerto:"), gbc);
        gbc.gridx = 2;
        panel.add(gpiTriggerPortCombo, gbc);
        gbc.gridx = 3;
        panel.add(gpiTriggerStateCombo, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 4;
        JLabel triggerInfo = new JLabel("<html><i>Inicia/para lectura cuando el sensor detecta presencia.</i></html>");
        panel.add(triggerInfo, gbc);
        
        gbc.gridy = row++; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        
        gbc.gridy = row++; gbc.fill = GridBagConstraints.NONE;
        JLabel reportTitle = new JLabel("Reportes Avanzados");
        reportTitle.setFont(reportTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(reportTitle, gbc);
        
        gbc.gridy = row; gbc.gridwidth = 2;
        gbc.gridx = 0;
        panel.add(reportPhaseCheck, gbc);
        gbc.gridx = 2;
        panel.add(reportChannelCheck, gbc);
        row++;
        
        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 4;
        JLabel reportInfo = new JLabel("<html><i>Phase Angle es útil para localización de tags.</i></html>");
        panel.add(reportInfo, gbc);
        
        gbc.gridy = row; gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);
        
        return panel;
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
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        JLabel filterTitle = new JLabel("Filtro de Tags Duplicados");
        filterTitle.setFont(filterTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(filterTitle, gbc);
        
        gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(duplicateFilterCheck, gbc);
        
        gbc.gridx = 1;
        panel.add(new JLabel("Tiempo de expiración (segundos):"), gbc);
        
        gbc.gridx = 2;
        panel.add(duplicateExpirationSpinner, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3;
        panel.add(duplicateStatsLabel, gbc);
        
        gbc.gridy = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        
        gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE;
        JLabel serviceTitle = new JLabel("Generadores de Servicio");
        serviceTitle.setFont(serviceTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(serviceTitle, gbc);
        
        gbc.gridy = 5; gbc.gridwidth = 1;
        gbc.gridx = 0;
        panel.add(generateWindowsServiceButton, gbc);
        
        gbc.gridx = 1;
        panel.add(generateUbuntuServiceButton, gbc);
        
        gbc.gridy = 6; gbc.gridx = 0; gbc.gridwidth = 3;
        JLabel serviceInfo = new JLabel("<html><i>Genera scripts de instalación para ejecutar como servicio del sistema.</i></html>");
        panel.add(serviceInfo, gbc);
        
        gbc.gridy = 7; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        
        gbc.gridy = 8; gbc.fill = GridBagConstraints.NONE;
        JLabel configTitle = new JLabel("Configuración");
        configTitle.setFont(configTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(configTitle, gbc);
        
        gbc.gridy = 9; gbc.gridwidth = 1;
        panel.add(saveConfigButton, gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        JLabel saveInfo = new JLabel("Guarda la configuración actual en rfid_config.json");
        panel.add(saveInfo, gbc);
        
        gbc.gridx = 0; gbc.gridy = 10; gbc.gridwidth = 3;
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
                "Mostrando en Hexadecimal - Clic para cambiar a Decimal" : 
                "Mostrando en Decimal - Clic para cambiar a Hexadecimal");
            updateTagTable();
            if (apiClient != null) {
                apiClient.setDisplayHexMode(displayHexMode);
            }
            config.setDisplayHexMode(displayHexMode);
            autoSaveConfiguration();
            System.out.println("[Config] Formato EPC cambiado a: " + (displayHexMode ? "Hexadecimal" : "Decimal"));
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
        setStatus("Iniciando lectura de etiquetas...");
        activityIndicator.setIndeterminate(true);
        startReadingButton.setEnabled(false);
        
        new SwingWorker<Boolean, Void>() {
            private String errorMessage = "";
            
            @Override
            protected Boolean doInBackground() {
                try {
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
                connectionStatusLabel.setText("Conectado");
                setStatus("Lectura detenida - Conectado a " + config.getReaderIP());
                System.out.println("[RFID] Lectura detenida");
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
        connectionIndicator.setBackground(ModernUIStyle.ACCENT_SUCCESS);
        connectionStatusLabel.setText("Conectado");
        connectionStatusLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
        connectButton.setEnabled(false);
        disconnectButton.setEnabled(true);
        startReadingButton.setEnabled(true);
        stopReadingButton.setEnabled(false);
        setStatus("Conectado a " + config.getReaderIP() + " - Presione 'Iniciar Lectura' para leer etiquetas");
        statistics.recordConnection();
        
        config.setLastConnectedIP(config.getReaderIP());
        autoSaveConfiguration();
        System.out.println("[Config] Última IP conectada guardada: " + config.getReaderIP());
        
        if (apiEnabledCheck.isSelected() && !apiEndpointField.getText().trim().isEmpty()) {
            startApiClient();
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
        
        if (apiClient != null && apiClient.isRunning()) {
            apiClient.enqueue(tag);
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
            String displayEpc = displayHexMode ? tag.getEpc() : hexToDecimal(tag.getEpc());
            
            tagTableModel.addRow(new Object[]{
                displayEpc,
                String.format("%.1f", tag.getRssi()),
                tag.getAntennaPort(),
                tag.getReadCount(),
                sdf.format(new Date(tag.getLastSeen()))
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
        statusLabel.setText("\u25CF " + status);
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
            apiClient.setApiTrabajo(trabajo);
            apiClient.setDisplayHexMode(displayHexMode);
            apiClient.start();
            apiStatusLabel.setText("Cliente API iniciado");
            apiStatusLabel.setForeground(ModernUIStyle.ACCENT_SUCCESS);
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
