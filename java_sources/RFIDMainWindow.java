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
 * @version 1.0
 */
public class RFIDMainWindow extends JFrame {
    
    private static final long serialVersionUID = 1L;
    
    /** Título de la ventana */
    private static final String WINDOW_TITLE = "Zebra FX7500 RFID Manager";
    
    /** Tamaño por defecto de la ventana */
    private static final Dimension DEFAULT_SIZE = new Dimension(1024, 768);
    
    /** Intervalo de actualización del monitoreo en milisegundos */
    private static final int MONITOR_REFRESH_INTERVAL = 500;
    
    // ==================== Componentes de conexión ====================
    private JTextField ipField;
    private JSpinner portSpinner;
    private JButton connectButton;
    private JButton disconnectButton;
    private JLabel connectionStatusLabel;
    private JPanel connectionIndicator;
    
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
    
    // ==================== Componentes de API ====================
    private JTextField apiEndpointField;
    private JPasswordField apiKeyField;
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
    
    // ==================== Panel de pestañas ====================
    private JTabbedPane tabbedPane;
    
    // ==================== Modelo y controladores ====================
    private RFIDConfig config;
    private LLRPConnection connection;
    private GPIOController gpioController;
    private APIClient apiClient;
    private ReaderStatistics statistics;
    
    // ==================== Executor para actualizaciones ====================
    private ScheduledExecutorService updateExecutor;
    private volatile boolean running = true;
    
    // ==================== Cache de tags para la tabla ====================
    private final Map<String, TagData> tagCache = new ConcurrentHashMap<>();
    private final int maxTagsInTable = 1000;
    
    /**
     * Constructor por defecto. Crea la ventana con configuración por defecto.
     */
    public RFIDMainWindow() {
        this(new RFIDConfig());
    }
    
    /**
     * Constructor con configuración.
     * 
     * @param config Configuración RFID a usar
     */
    public RFIDMainWindow(RFIDConfig config) {
        super(WINDOW_TITLE);
        this.config = config != null ? config : new RFIDConfig();
        this.statistics = new ReaderStatistics();
        this.duplicateFilter = new DuplicateFilter(this.config.getDuplicateFilterExpiration() * 1000L);
        this.duplicateFilter.setEnabled(this.config.isDuplicateFilterEnabled());
        
        initializeLookAndFeel();
        initializeComponents();
        initializeLayout();
        initializeEventHandlers();
        loadConfigToUI();
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(DEFAULT_SIZE);
        setMinimumSize(new Dimension(800, 600));
        setLocationRelativeTo(null);
        
        startUpdateTimer();
    }
    
    /**
     * Inicializa el look and feel de la aplicación.
     */
    private void initializeLookAndFeel() {
        try {
            // Intentar cargar FlatLaf si está disponible
            Class<?> flatLafClass = Class.forName("com.formdev.flatlaf.FlatLightLaf");
            UIManager.setLookAndFeel((LookAndFeel) flatLafClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            try {
                // Usar look and feel del sistema
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // Usar look and feel por defecto
            }
        }
    }
    
    /**
     * Inicializa todos los componentes de la interfaz.
     */
    private void initializeComponents() {
        // Componentes de conexión
        ipField = new JTextField(config.getReaderIP(), 15);
        portSpinner = new JSpinner(new SpinnerNumberModel(config.getReaderPort(), 1, 65535, 1));
        connectButton = new JButton("Conectar");
        disconnectButton = new JButton("Desconectar");
        disconnectButton.setEnabled(false);
        connectionStatusLabel = new JLabel("Desconectado");
        connectionIndicator = new JPanel();
        connectionIndicator.setPreferredSize(new Dimension(20, 20));
        connectionIndicator.setBackground(Color.RED);
        connectionIndicator.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        
        // Componentes de antenas
        for (int i = 0; i < 4; i++) {
            powerSliders[i] = new JSlider(10, 30, (int) config.getAntennaConfig(i + 1).getTransmitPower());
            powerSliders[i].setMajorTickSpacing(5);
            powerSliders[i].setMinorTickSpacing(1);
            powerSliders[i].setPaintTicks(true);
            powerSliders[i].setPaintLabels(true);
            
            antennaEnableChecks[i] = new JCheckBox("Habilitada", config.getAntennaConfig(i + 1).isEnabled());
            powerLabels[i] = new JLabel(powerSliders[i].getValue() + " dBm");
        }
        
        // Componentes de monitoreo
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
        
        tagsReadLabel = new JLabel("0");
        uniqueTagsLabel = new JLabel("0");
        tpsLabel = new JLabel("0.0");
        clearTagsButton = new JButton("Limpiar");
        autoScrollCheck = new JCheckBox("Auto-scroll", true);
        
        // Componentes de API
        apiEndpointField = new JTextField(config.getApiEndpoint() != null ? config.getApiEndpoint() : "", 30);
        apiKeyField = new JPasswordField(config.getApiKey() != null ? config.getApiKey() : "", 20);
        testApiButton = new JButton("Probar Conexión");
        apiStatusLabel = new JLabel("No configurado");
        apiEnabledCheck = new JCheckBox("Envío automático habilitado", false);
        
        // Componentes de GPIO
        for (int i = 0; i < 4; i++) {
            gpoButtons[i] = new JToggleButton("GPO " + (i + 1));
            gpoButtons[i].setPreferredSize(new Dimension(100, 40));
            
            gpiIndicators[i] = new JPanel();
            gpiIndicators[i].setPreferredSize(new Dimension(30, 30));
            gpiIndicators[i].setBackground(Color.GRAY);
            gpiIndicators[i].setBorder(BorderFactory.createLineBorder(Color.BLACK));
            
            gpiLabels[i] = new JLabel("GPI " + (i + 1) + ": --");
        }
        
        // Barra de estado
        statusLabel = new JLabel("Listo");
        statsLabel = new JLabel("Tags: 0 | Únicos: 0 | TPS: 0.0");
        activityIndicator = new JProgressBar();
        activityIndicator.setIndeterminate(false);
        activityIndicator.setPreferredSize(new Dimension(100, 15));
        
        // Componentes de Herramientas
        duplicateFilterCheck = new JCheckBox("Filtrar tags duplicados", config.isDuplicateFilterEnabled());
        duplicateExpirationSpinner = new JSpinner(new SpinnerNumberModel(
            config.getDuplicateFilterExpiration(), 1, 300, 1));
        generateWindowsServiceButton = new JButton("Generar Instalador Windows");
        generateUbuntuServiceButton = new JButton("Generar Script Ubuntu");
        saveConfigButton = new JButton("Guardar Configuración");
        duplicateStatsLabel = new JLabel("Filtrados: 0 | Procesados: 0");
        
        // Panel de pestañas
        tabbedPane = new JTabbedPane();
    }
    
    /**
     * Inicializa el layout de la interfaz.
     */
    private void initializeLayout() {
        setLayout(new BorderLayout());
        
        // Crear pestañas
        tabbedPane.addTab("Conexión", createConnectionPanel());
        tabbedPane.addTab("Antenas", createAntennasPanel());
        tabbedPane.addTab("Monitoreo", createMonitoringPanel());
        tabbedPane.addTab("API", createApiPanel());
        tabbedPane.addTab("GPIO", createGpioPanel());
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
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Título
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 4;
        JLabel titleLabel = new JLabel("Configuración de Conexión LLRP");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(titleLabel, gbc);
        
        // IP
        gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Dirección IP:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(ipField, gbc);
        
        // Puerto
        gbc.gridx = 2; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Puerto:"), gbc);
        gbc.gridx = 3;
        panel.add(portSpinner, gbc);
        
        // Botones
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(connectButton);
        buttonPanel.add(disconnectButton);
        panel.add(buttonPanel, gbc);
        
        // Estado
        gbc.gridx = 2; gbc.gridwidth = 2;
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statusPanel.add(new JLabel("Estado:"));
        statusPanel.add(connectionIndicator);
        statusPanel.add(connectionStatusLabel);
        panel.add(statusPanel, gbc);
        
        // Panel de información del lector
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4; gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBorder(BorderFactory.createTitledBorder("Información del Lector"));
        JTextArea infoText = new JTextArea("Conecte al lector para ver información.");
        infoText.setEditable(false);
        infoText.setBackground(panel.getBackground());
        infoPanel.add(new JScrollPane(infoText), BorderLayout.CENTER);
        panel.add(infoPanel, gbc);
        
        return panel;
    }
    
    /**
     * Crea el panel de configuración de antenas.
     * 
     * @return Panel de antenas
     */
    private JPanel createAntennasPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        for (int i = 0; i < 4; i++) {
            panel.add(createSingleAntennaPanel(i));
        }
        
        return panel;
    }
    
    /**
     * Crea un panel para una antena individual.
     * 
     * @param antennaIndex Índice de la antena (0-3)
     * @return Panel de configuración de la antena
     */
    private JPanel createSingleAntennaPanel(int antennaIndex) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Antena " + (antennaIndex + 1)));
        
        // Panel superior con checkbox
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(antennaEnableChecks[antennaIndex]);
        panel.add(topPanel, BorderLayout.NORTH);
        
        // Panel central con slider
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(new JLabel("Potencia TX:"), BorderLayout.WEST);
        centerPanel.add(powerSliders[antennaIndex], BorderLayout.CENTER);
        centerPanel.add(powerLabels[antennaIndex], BorderLayout.EAST);
        panel.add(centerPanel, BorderLayout.CENTER);
        
        // Panel inferior con estadísticas
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(new JLabel("RSSI Prom: --"));
        bottomPanel.add(new JLabel(" | Lecturas: 0"));
        panel.add(bottomPanel, BorderLayout.SOUTH);
        
        // Listener para actualizar el label de potencia
        final int idx = antennaIndex;
        powerSliders[antennaIndex].addChangeListener(e -> {
            powerLabels[idx].setText(powerSliders[idx].getValue() + " dBm");
        });
        
        return panel;
    }
    
    /**
     * Crea el panel de monitoreo en tiempo real.
     * 
     * @return Panel de monitoreo
     */
    private JPanel createMonitoringPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Panel superior con estadísticas
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        statsPanel.setBorder(BorderFactory.createTitledBorder("Estadísticas"));
        statsPanel.add(new JLabel("Tags Leídos:"));
        statsPanel.add(tagsReadLabel);
        statsPanel.add(new JLabel("Únicos:"));
        statsPanel.add(uniqueTagsLabel);
        statsPanel.add(new JLabel("Tags/seg:"));
        statsPanel.add(tpsLabel);
        statsPanel.add(Box.createHorizontalStrut(20));
        statsPanel.add(clearTagsButton);
        statsPanel.add(autoScrollCheck);
        panel.add(statsPanel, BorderLayout.NORTH);
        
        // Tabla de tags
        JScrollPane scrollPane = new JScrollPane(tagTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Etiquetas Detectadas"));
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
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Título
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        JLabel titleLabel = new JLabel("Configuración de API Externa");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        panel.add(titleLabel, gbc);
        
        // Endpoint
        gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Endpoint URL:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(apiEndpointField, gbc);
        
        // API Key
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("API Key:"), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(apiKeyField, gbc);
        
        // Checkbox habilitado
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        panel.add(apiEnabledCheck, gbc);
        
        // Botón de prueba y estado
        gbc.gridy = 4;
        JPanel testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        testPanel.add(testApiButton);
        testPanel.add(new JLabel("Estado:"));
        testPanel.add(apiStatusLabel);
        panel.add(testPanel, gbc);
        
        // Área de logs de API
        gbc.gridy = 5; gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0; gbc.weighty = 1.0;
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Log de Envíos"));
        JTextArea logText = new JTextArea();
        logText.setEditable(false);
        logText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logPanel.add(new JScrollPane(logText), BorderLayout.CENTER);
        panel.add(logPanel, gbc);
        
        return panel;
    }
    
    /**
     * Crea el panel de control GPIO.
     * 
     * @return Panel de GPIO
     */
    private JPanel createGpioPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 20, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        // Panel GPO
        JPanel gpoPanel = new JPanel(new GridLayout(5, 1, 5, 10));
        gpoPanel.setBorder(BorderFactory.createTitledBorder("Salidas (GPO) - Relés/Luces/Alarmas"));
        
        JLabel gpoInfo = new JLabel("Haga clic para activar/desactivar las salidas:");
        gpoPanel.add(gpoInfo);
        
        for (int i = 0; i < 4; i++) {
            JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            portPanel.add(gpoButtons[i]);
            portPanel.add(new JLabel("Puerto " + (i + 1)));
            gpoPanel.add(portPanel);
        }
        
        // Panel GPI
        JPanel gpiPanel = new JPanel(new GridLayout(5, 1, 5, 10));
        gpiPanel.setBorder(BorderFactory.createTitledBorder("Entradas (GPI) - Sensores/Triggers"));
        
        JLabel gpiInfo = new JLabel("Estado de las entradas (Verde = ALTO, Gris = BAJO):");
        gpiPanel.add(gpiInfo);
        
        for (int i = 0; i < 4; i++) {
            JPanel portPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            portPanel.add(gpiIndicators[i]);
            portPanel.add(gpiLabels[i]);
            gpiPanel.add(portPanel);
        }
        
        panel.add(gpoPanel);
        panel.add(gpiPanel);
        
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
        
        // ==================== Sección Filtro de Duplicados ====================
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
        
        // Separador
        gbc.gridy = 3; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        
        // ==================== Sección Instaladores de Servicio ====================
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
        
        // Separador
        gbc.gridy = 7; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        
        // ==================== Sección Configuración ====================
        gbc.gridy = 8; gbc.fill = GridBagConstraints.NONE;
        JLabel configTitle = new JLabel("Configuración");
        configTitle.setFont(configTitle.getFont().deriveFont(Font.BOLD, 14f));
        panel.add(configTitle, gbc);
        
        gbc.gridy = 9; gbc.gridwidth = 1;
        panel.add(saveConfigButton, gbc);
        
        gbc.gridx = 1; gbc.gridwidth = 2;
        JLabel saveInfo = new JLabel("Guarda la configuración actual en config.json");
        panel.add(saveInfo, gbc);
        
        // Espacio vacío para expandir
        gbc.gridx = 0; gbc.gridy = 10; gbc.gridwidth = 3;
        gbc.weighty = 1.0;
        panel.add(new JLabel(), gbc);
        
        return panel;
    }
    
    /**
     * Crea la barra de estado inferior.
     * 
     * @return Panel de barra de estado
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, Color.GRAY),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)
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
        // Botón conectar
        connectButton.addActionListener(e -> connect());
        
        // Botón desconectar
        disconnectButton.addActionListener(e -> disconnect());
        
        // Botón limpiar tags
        clearTagsButton.addActionListener(e -> clearTags());
        
        // Botón probar API
        testApiButton.addActionListener(e -> testApiConnection());
        
        // Botones GPO
        for (int i = 0; i < 4; i++) {
            final int port = i + 1;
            gpoButtons[i].addActionListener(e -> toggleGpo(port));
        }
        
        // Filtro de duplicados
        duplicateFilterCheck.addActionListener(e -> {
            duplicateFilter.setEnabled(duplicateFilterCheck.isSelected());
            config.setDuplicateFilterEnabled(duplicateFilterCheck.isSelected());
        });
        
        duplicateExpirationSpinner.addChangeListener(e -> {
            int seconds = (Integer) duplicateExpirationSpinner.getValue();
            duplicateFilter.setExpirationSeconds(seconds);
            config.setDuplicateFilterExpiration(seconds);
        });
        
        // Generadores de servicio
        generateWindowsServiceButton.addActionListener(e -> generateWindowsService());
        generateUbuntuServiceButton.addActionListener(e -> generateUbuntuService());
        
        // Guardar configuración
        saveConfigButton.addActionListener(e -> saveConfiguration());
        
        // Cierre de ventana
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
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
    }
    
    // ==================== Acciones ====================
    
    /**
     * Conecta al lector RFID.
     */
    private void connect() {
        saveUIToConfig();
        
        setStatus("Conectando a " + config.getReaderIP() + "...");
        activityIndicator.setIndeterminate(true);
        connectButton.setEnabled(false);
        
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                connection = new LLRPConnection(config);
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
     * Llamado cuando la conexión es exitosa.
     */
    private void onConnected() {
        connectionIndicator.setBackground(Color.GREEN);
        connectionStatusLabel.setText("Conectado");
        connectButton.setEnabled(false);
        disconnectButton.setEnabled(true);
        setStatus("Conectado a " + config.getReaderIP());
        statistics.recordConnection();
        
        // Inicializar controlador GPIO
        gpioController = new GPIOController(connection);
        
        // Iniciar cliente API si está configurado
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
        connectionIndicator.setBackground(Color.RED);
        connectionStatusLabel.setText("Error: " + error);
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        setStatus("Error de conexión: " + error);
        statistics.recordError(error);
        
        JOptionPane.showMessageDialog(this,
            "Error al conectar: " + error,
            "Error de Conexión",
            JOptionPane.ERROR_MESSAGE);
    }
    
    /**
     * Llamado cuando se desconecta.
     */
    private void onDisconnected() {
        connectionIndicator.setBackground(Color.RED);
        connectionStatusLabel.setText("Desconectado");
        connectButton.setEnabled(true);
        disconnectButton.setEnabled(false);
        setStatus("Desconectado");
        statistics.recordDisconnection();
        
        if (gpioController != null) {
            gpioController.close();
            gpioController = null;
        }
        
        if (apiClient != null) {
            apiClient.stop();
        }
    }
    
    /**
     * Limpia la tabla de tags.
     */
    private void clearTags() {
        tagCache.clear();
        tagTableModel.setRowCount(0);
        statistics.resetTagStatistics();
        updateStatsLabels();
    }
    
    /**
     * Prueba la conexión con la API.
     */
    private void testApiConnection() {
        String endpoint = apiEndpointField.getText().trim();
        String apiKey = new String(apiKeyField.getPassword());
        
        if (endpoint.isEmpty()) {
            apiStatusLabel.setText("Endpoint vacío");
            apiStatusLabel.setForeground(Color.RED);
            return;
        }
        
        apiStatusLabel.setText("Probando...");
        apiStatusLabel.setForeground(Color.BLUE);
        
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                APIClient testClient = new APIClient(endpoint, apiKey);
                return testClient.testConnection();
            }
            
            @Override
            protected void done() {
                try {
                    if (get()) {
                        apiStatusLabel.setText("Conexión exitosa");
                        apiStatusLabel.setForeground(new Color(0, 128, 0));
                    } else {
                        apiStatusLabel.setText("Conexión fallida");
                        apiStatusLabel.setForeground(Color.RED);
                    }
                } catch (Exception e) {
                    apiStatusLabel.setText("Error: " + e.getMessage());
                    apiStatusLabel.setForeground(Color.RED);
                }
            }
        }.execute();
    }
    
    /**
     * Inicia el cliente de API.
     */
    private void startApiClient() {
        if (apiClient != null) {
            apiClient.stop();
        }
        
        String endpoint = apiEndpointField.getText().trim();
        String key = new String(apiKeyField.getPassword());
        
        if (!endpoint.isEmpty()) {
            apiClient = new APIClient(endpoint, key);
            apiClient.setSendResultHandler(result -> {
                SwingUtilities.invokeLater(() -> {
                    if (result.success) {
                        statistics.recordApiSuccess(result.tagCount, result.responseTimeMs);
                    } else {
                        statistics.recordApiFailure(result.tagCount);
                    }
                });
            });
            apiClient.start();
        }
    }
    
    /**
     * Alterna el estado de un puerto GPO.
     * 
     * @param port Número de puerto (1-4)
     */
    private void toggleGpo(int port) {
        if (gpioController == null) {
            JOptionPane.showMessageDialog(this,
                "Primero debe conectar al lector",
                "No Conectado",
                JOptionPane.WARNING_MESSAGE);
            gpoButtons[port - 1].setSelected(false);
            return;
        }
        
        boolean newState = gpoButtons[port - 1].isSelected();
        
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return gpioController.setGpoState(port, newState);
            }
            
            @Override
            protected void done() {
                try {
                    if (!get()) {
                        gpoButtons[port - 1].setSelected(!newState);
                        setStatus("Error al cambiar GPO " + port);
                    }
                } catch (Exception e) {
                    gpoButtons[port - 1].setSelected(!newState);
                    setStatus("Error: " + e.getMessage());
                }
            }
        }.execute();
    }
    
    /**
     * Agrega una etiqueta a la tabla de monitoreo.
     * 
     * @param tag Datos de la etiqueta
     */
    public void addTag(TagData tag) {
        if (tag == null) return;
        
        // Filtrar duplicados si está habilitado
        if (!duplicateFilter.shouldProcess(tag.getEpc())) {
            // Actualizar estadísticas de filtrado en la UI
            SwingUtilities.invokeLater(() -> {
                duplicateStatsLabel.setText(String.format(
                    "Filtrados: %d | Procesados: %d",
                    duplicateFilter.getDuplicatesFiltered(),
                    duplicateFilter.getUniqueTagsProcessed()));
            });
            return;
        }
        
        statistics.recordTagRead(tag);
        
        String epc = tag.getEpc();
        TagData existing = tagCache.get(epc);
        
        if (existing == null) {
            statistics.recordUniqueTag();
            tagCache.put(epc, tag);
        } else {
            existing.merge(tag);
        }
        
        SwingUtilities.invokeLater(() -> {
            updateTagTable();
            updateStatsLabels();
        });
        
        // Enviar a API si está habilitado
        if (apiClient != null && apiClient.isRunning()) {
            apiClient.enqueue(tag);
        }
    }
    
    /**
     * Actualiza la tabla de tags.
     */
    private void updateTagTable() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        
        tagTableModel.setRowCount(0);
        
        tagCache.values().stream()
            .sorted((a, b) -> Long.compare(b.getLastSeen(), a.getLastSeen()))
            .limit(maxTagsInTable)
            .forEach(tag -> {
                tagTableModel.addRow(new Object[]{
                    tag.getEpc(),
                    String.format("%.1f", tag.getRssi()),
                    tag.getAntennaPort(),
                    tag.getReadCount(),
                    sdf.format(new Date(tag.getLastSeen()))
                });
            });
        
        if (autoScrollCheck.isSelected() && tagTable.getRowCount() > 0) {
            tagTable.scrollRectToVisible(tagTable.getCellRect(0, 0, true));
        }
    }
    
    /**
     * Actualiza las etiquetas de estadísticas.
     */
    private void updateStatsLabels() {
        tagsReadLabel.setText(String.valueOf(statistics.getTagsRead()));
        uniqueTagsLabel.setText(String.valueOf(statistics.getUniqueTags()));
        tpsLabel.setText(String.format("%.1f", statistics.getTagsPerSecond()));
        
        statsLabel.setText(String.format("Tags: %d | Únicos: %d | TPS: %.1f | API: %d/%d",
            statistics.getTagsRead(),
            statistics.getUniqueTags(),
            statistics.getTagsPerSecond(),
            statistics.getApiSuccessCount(),
            statistics.getApiFailureCount()));
    }
    
    /**
     * Establece el mensaje de estado.
     * 
     * @param message Mensaje de estado
     */
    private void setStatus(String message) {
        statusLabel.setText(message);
    }
    
    /**
     * Inicia el temporizador de actualización.
     */
    private void startUpdateTimer() {
        updateExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RFIDMainWindow-Updater");
            t.setDaemon(true);
            return t;
        });
        
        updateExecutor.scheduleWithFixedDelay(() -> {
            if (running) {
                SwingUtilities.invokeLater(() -> {
                    updateStatsLabels();
                    updateGpiIndicators();
                });
            }
        }, MONITOR_REFRESH_INTERVAL, MONITOR_REFRESH_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Actualiza los indicadores GPI.
     */
    private void updateGpiIndicators() {
        if (gpioController != null && connection != null && connection.isConnected()) {
            for (int i = 0; i < 4; i++) {
                boolean state = gpioController.getCachedInputState(i + 1);
                gpiIndicators[i].setBackground(state ? Color.GREEN : Color.GRAY);
                gpiLabels[i].setText("GPI " + (i + 1) + ": " + (state ? "ALTO" : "BAJO"));
            }
        }
    }
    
    /**
     * Cierra la aplicación limpiamente.
     */
    public void shutdown() {
        running = false;
        
        if (updateExecutor != null) {
            updateExecutor.shutdown();
        }
        
        disconnect();
        
        if (apiClient != null) {
            apiClient.close();
        }
    }
    
    // ==================== Getters ====================
    
    public RFIDConfig getConfig() {
        saveUIToConfig();
        return config;
    }
    
    public ReaderStatistics getStatistics() {
        return statistics;
    }
    
    public LLRPConnection getConnection() {
        return connection;
    }
    
    public GPIOController getGpioController() {
        return gpioController;
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
                sb.append("[ -f config.json ] && cp config.json $INSTALL_DIR/\n\n");
                
                sb.append("# Crear servicio systemd\n");
                sb.append("cat > /etc/systemd/system/rfid-zebra.service << EOF\n");
                sb.append("[Unit]\n");
                sb.append("Description=RFID Zebra FX7500 Service\n");
                sb.append("After=network.target\n\n");
                sb.append("[Service]\n");
                sb.append("Type=simple\n");
                sb.append("User=root\n");
                sb.append("WorkingDirectory=$INSTALL_DIR\n");
                sb.append("ExecStart=/usr/bin/java -Xmx256m -jar rfid-zebra.jar --headless\n");
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
            config.saveToFile("config.json");
            JOptionPane.showMessageDialog(this,
                "Configuración guardada en config.json",
                "Guardado",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                "Error al guardar: " + e.getMessage(),
                "Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }
}
