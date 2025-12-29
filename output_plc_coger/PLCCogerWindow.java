import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * PLC COGER - Ventana Principal
 * Monitoreo de API JSON y Control de PLC via Modbus TCP
 * Con soporte para System Tray y inicio automatico
 */
public class PLCCogerWindow extends JFrame implements 
    CogerAPIClient.APIListener, CogerModbusClient.PLCListener {
    
    // Configuracion
    private PLCCogerConfig config;
    private LogManager logManager;
    
    // Clientes
    private CogerAPIClient apiClient;
    private CogerModbusClient modbusClient;
    
    // Estado
    private boolean apiPolling = false;
    private boolean plcConnected = false;
    private CogerAPIClient.ItemData currentProcessingItem = null;
    
    // System Tray
    private TrayIcon trayIcon;
    private boolean isMinimizedToTray = false;
    
    // ============ UI - Pestana Monitoreo ============
    private JTable itemsTable;
    private DefaultTableModel tableModel;
    private JLabel monitorStatusLabel;
    private JLabel itemCountLabel;
    private JButton refreshButton;
    
    // ============ UI - Pestana API ============
    private JTextField apiEndpointField;
    private JTextField apiTokenField;
    private JTextField apiIPField;
    private JTextField apiTrabajoField;
    private JSpinner apiIntervalSpinner;
    private JButton apiStartButton;
    private JButton apiStopButton;
    private JLabel apiStatusLabel;
    
    // ============ UI - Pestana PLC ============
    private JCheckBox plcEnabledCheck;
    private JTextField plcIPField;
    private JSpinner plcPortSpinner;
    private JSpinner plcUnitIdSpinner;
    private JSpinner plcCoilSpinner;
    private JSpinner plcIntervalSpinner;
    private JSpinner hrTipoSpinner;
    private JSpinner hrAnchoSpinner;
    private JSpinner hrLargoSpinner;
    private JSpinner hrControlSpinner;
    private JButton plcConnectButton;
    private JButton plcDisconnectButton;
    private JLabel plcStatusLabel;
    private JLabel coilStatusLabel;
    
    // ============ UI - Pestana Opciones ============
    private JCheckBox apiAutoStartCheck;
    private JCheckBox plcAutoStartCheck;
    private JCheckBox startMinimizedCheck;
    private JCheckBox startWithWindowsCheck;
    private JButton startAllButton;
    private JButton stopAllButton;
    private JButton installServiceButton;
    
    // ============ UI - Barra de estado ============
    private JLabel statusBar;
    
    /** Constructor */
    public PLCCogerWindow() {
        super("PLC COGER - Monitoreo API y Control PLC");
        
        config = new PLCCogerConfig();
        logManager = LogManager.getInstance();
        apiClient = new CogerAPIClient();
        modbusClient = new CogerModbusClient();
        
        apiClient.setListener(this);
        modbusClient.setListener(this);
        
        initUI();
        initSystemTray();
        loadConfigToUI();
        
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1000, 750);
        setLocationRelativeTo(null);
        
        // Manejar cierre de ventana
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (SystemTray.isSupported()) {
                    minimizeToTray();
                } else {
                    exitApplication();
                }
            }
            
            @Override
            public void windowIconified(WindowEvent e) {
                if (SystemTray.isSupported()) {
                    minimizeToTray();
                }
            }
        });
        
        // Auto-inicio si esta configurado
        SwingUtilities.invokeLater(() -> {
            if (config.isApiAutoStart()) {
                startAPIPolling();
            }
            if (config.isPlcAutoStart() && config.isPlcEnabled()) {
                connectPLC();
            }
            if (config.isStartMinimized()) {
                minimizeToTray();
            }
        });
    }
    
    /** Inicializa el System Tray */
    private void initSystemTray() {
        if (!SystemTray.isSupported()) {
            System.out.println("[TRAY] System Tray no soportado");
            return;
        }
        
        try {
            SystemTray tray = SystemTray.getSystemTray();
            
            // Crear icono (cuadrado azul simple)
            Image image = createTrayImage();
            
            // Menu popup
            PopupMenu popup = new PopupMenu();
            
            MenuItem showItem = new MenuItem("Mostrar");
            showItem.addActionListener(e -> restoreFromTray());
            
            MenuItem startAllItem = new MenuItem("Iniciar Todo");
            startAllItem.addActionListener(e -> startAll());
            
            MenuItem stopAllItem = new MenuItem("Detener Todo");
            stopAllItem.addActionListener(e -> stopAll());
            
            MenuItem exitItem = new MenuItem("Salir");
            exitItem.addActionListener(e -> exitApplication());
            
            popup.add(showItem);
            popup.addSeparator();
            popup.add(startAllItem);
            popup.add(stopAllItem);
            popup.addSeparator();
            popup.add(exitItem);
            
            trayIcon = new TrayIcon(image, "PLC COGER", popup);
            trayIcon.setImageAutoSize(true);
            
            // Doble click para restaurar
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        restoreFromTray();
                    }
                }
            });
            
            tray.add(trayIcon);
            System.out.println("[TRAY] System Tray inicializado");
            
        } catch (Exception e) {
            System.err.println("[TRAY] Error inicializando System Tray: " + e.getMessage());
        }
    }
    
    /** Crea imagen para el tray icon */
    private Image createTrayImage() {
        int size = 16;
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
            size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0, 102, 204));
        g.fillRect(0, 0, size, size);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("P", 4, 12);
        g.dispose();
        return image;
    }
    
    /** Minimiza a la bandeja del sistema */
    private void minimizeToTray() {
        if (trayIcon != null) {
            setVisible(false);
            isMinimizedToTray = true;
            trayIcon.displayMessage("PLC COGER", 
                "La aplicacion sigue ejecutandose en segundo plano", 
                TrayIcon.MessageType.INFO);
        }
    }
    
    /** Restaura desde la bandeja del sistema */
    private void restoreFromTray() {
        setVisible(true);
        setState(Frame.NORMAL);
        toFront();
        isMinimizedToTray = false;
    }
    
    /** Sale de la aplicacion */
    private void exitApplication() {
        saveConfigFromUI();
        config.save();
        
        stopAll();
        
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        
        logManager.shutdown();
        apiClient.shutdown();
        modbusClient.shutdown();
        
        System.exit(0);
    }
    
    /** Inicia todo (API y PLC) */
    private void startAll() {
        if (!apiPolling) {
            startAPIPolling();
        }
        if (!plcConnected && config.isPlcEnabled()) {
            connectPLC();
        }
        setStatus("Todos los servicios iniciados");
    }
    
    /** Detiene todo (API y PLC) */
    private void stopAll() {
        if (apiPolling) {
            stopAPIPolling();
        }
        if (plcConnected) {
            disconnectPLC();
        }
        setStatus("Todos los servicios detenidos");
    }
    
    /** Inicializa la interfaz grafica */
    private void initUI() {
        ModernUIStyle.styleFrame(this);
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        ModernUIStyle.stylePanel(mainPanel);
        
        // Header
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Pestanas
        JTabbedPane tabbedPane = new JTabbedPane();
        ModernUIStyle.styleTabbedPane(tabbedPane);
        
        tabbedPane.addTab("Monitoreo", createMonitoringTab());
        tabbedPane.addTab("API", createAPITab());
        tabbedPane.addTab("PLC", createPLCTab());
        tabbedPane.addTab("Opciones", createOptionsTab());
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        
        // Barra de estado
        statusBar = ModernUIStyle.createStatusBar();
        mainPanel.add(statusBar, BorderLayout.SOUTH);
        
        setContentPane(mainPanel);
    }
    
    /** Crea el panel de cabecera */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(ModernUIStyle.BACKGROUND_MEDIUM);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        
        JLabel titleLabel = new JLabel("PLC COGER");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(ModernUIStyle.ACCENT_BLUE);
        
        JLabel subtitleLabel = new JLabel("Monitoreo API y Control PLC via Modbus TCP");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(ModernUIStyle.TEXT_SECONDARY);
        
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.add(titleLabel);
        leftPanel.add(subtitleLabel);
        
        panel.add(leftPanel, BorderLayout.WEST);
        
        return panel;
    }
    
    /** Crea la pestana de Monitoreo */
    private JPanel createMonitoringTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        ModernUIStyle.stylePanel(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Panel de controles superior
        JPanel controlPanel = ModernUIStyle.createCardPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 5));
        
        itemCountLabel = new JLabel("Items: 0");
        ModernUIStyle.styleTitleLabel(itemCountLabel);
        
        monitorStatusLabel = new JLabel("Esperando datos...");
        ModernUIStyle.styleLabel(monitorStatusLabel);
        
        refreshButton = new JButton("Actualizar");
        ModernUIStyle.styleButton(refreshButton);
        refreshButton.addActionListener(e -> {
            if (apiPolling) {
                setStatus("Datos actualizandose automaticamente");
            } else {
                setStatus("Inicia el polling de API para obtener datos");
            }
        });
        
        controlPanel.add(itemCountLabel);
        controlPanel.add(Box.createHorizontalStrut(30));
        controlPanel.add(monitorStatusLabel);
        controlPanel.add(Box.createHorizontalStrut(30));
        controlPanel.add(refreshButton);
        
        panel.add(controlPanel, BorderLayout.NORTH);
        
        // Tabla de items
        String[] columns = {"Orden", "Tag", "Codigo", "Descripcion", "Tipo", "Desc. Tipo", "Ancho", "Largo", "Unidad", "Variante"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        itemsTable = new JTable(tableModel);
        ModernUIStyle.styleTable(itemsTable);
        
        // Ajustar anchos de columnas
        itemsTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        itemsTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        itemsTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(250);
        itemsTable.getColumnModel().getColumn(4).setPreferredWidth(40);
        itemsTable.getColumnModel().getColumn(5).setPreferredWidth(120);
        itemsTable.getColumnModel().getColumn(6).setPreferredWidth(50);
        itemsTable.getColumnModel().getColumn(7).setPreferredWidth(50);
        itemsTable.getColumnModel().getColumn(8).setPreferredWidth(50);
        itemsTable.getColumnModel().getColumn(9).setPreferredWidth(50);
        
        JScrollPane scrollPane = new JScrollPane(itemsTable);
        ModernUIStyle.styleScrollPane(scrollPane);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Panel de leyenda inferior
        JPanel legendPanel = ModernUIStyle.createCardPanel();
        legendPanel.setLayout(new GridLayout(2, 1, 5, 5));
        
        JLabel legend1 = new JLabel("Esta tabla muestra los items obtenidos de la API en tiempo real");
        JLabel legend2 = new JLabel("El primer item de la lista se enviara al PLC cuando el coil enabler cambie a ON");
        ModernUIStyle.styleLabel(legend1);
        ModernUIStyle.styleLabel(legend2);
        legend1.setForeground(ModernUIStyle.TEXT_SECONDARY);
        legend2.setForeground(ModernUIStyle.TEXT_SECONDARY);
        
        legendPanel.add(legend1);
        legendPanel.add(legend2);
        
        panel.add(legendPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    /** Crea la pestana de configuracion API */
    private JPanel createAPITab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        ModernUIStyle.stylePanel(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Panel de configuracion
        JPanel configPanel = ModernUIStyle.createTitledPanel("Configuracion de API");
        configPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // Endpoint
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel endpointLabel = new JLabel("Endpoint:");
        ModernUIStyle.styleLabel(endpointLabel);
        configPanel.add(endpointLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        apiEndpointField = new JTextField(30);
        ModernUIStyle.styleTextField(apiEndpointField);
        configPanel.add(apiEndpointField, gbc);
        
        row++;
        
        // Token
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel tokenLabel = new JLabel("Token:");
        ModernUIStyle.styleLabel(tokenLabel);
        configPanel.add(tokenLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        apiTokenField = new JTextField(30);
        ModernUIStyle.styleTextField(apiTokenField);
        configPanel.add(apiTokenField, gbc);
        
        row++;
        
        // IP
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel ipLabel = new JLabel("IP:");
        ModernUIStyle.styleLabel(ipLabel);
        configPanel.add(ipLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        apiIPField = new JTextField(15);
        ModernUIStyle.styleTextField(apiIPField);
        configPanel.add(apiIPField, gbc);
        
        row++;
        
        // Trabajo
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel trabajoLabel = new JLabel("Trabajo:");
        ModernUIStyle.styleLabel(trabajoLabel);
        configPanel.add(trabajoLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        apiTrabajoField = new JTextField(20);
        ModernUIStyle.styleTextField(apiTrabajoField);
        configPanel.add(apiTrabajoField, gbc);
        
        row++;
        
        // Intervalo de polling
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel intervalLabel = new JLabel("Intervalo polling (ms):");
        ModernUIStyle.styleLabel(intervalLabel);
        configPanel.add(intervalLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        apiIntervalSpinner = new JSpinner(new SpinnerNumberModel(1000, 100, 60000, 100));
        ModernUIStyle.styleSpinner(apiIntervalSpinner);
        configPanel.add(apiIntervalSpinner, gbc);
        
        row++;
        
        // Botones
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        buttonPanel.setOpaque(false);
        
        apiStartButton = new JButton("Iniciar Polling");
        ModernUIStyle.styleSuccessButton(apiStartButton);
        apiStartButton.addActionListener(e -> startAPIPolling());
        
        apiStopButton = new JButton("Detener");
        ModernUIStyle.styleDangerButton(apiStopButton);
        apiStopButton.setEnabled(false);
        apiStopButton.addActionListener(e -> stopAPIPolling());
        
        buttonPanel.add(apiStartButton);
        buttonPanel.add(apiStopButton);
        
        configPanel.add(buttonPanel, gbc);
        
        row++;
        
        // Estado
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        apiStatusLabel = new JLabel("Polling detenido");
        ModernUIStyle.styleTitleLabel(apiStatusLabel);
        configPanel.add(apiStatusLabel, gbc);
        
        panel.add(configPanel, BorderLayout.NORTH);
        
        // Panel de informacion
        JPanel infoPanel = ModernUIStyle.createTitledPanel("Informacion de la API");
        infoPanel.setLayout(new GridLayout(3, 1, 5, 5));
        
        JLabel info1 = new JLabel("POST: {endpoint}/rfid_lecturas/apilado");
        JLabel info2 = new JLabel("DELETE: {endpoint}/rfid_lecturas/baja_apilado/{token}/{ip}/{trabajo}/{tag}/{numero}");
        JLabel info3 = new JLabel("La API se consulta automaticamente al intervalo configurado");
        
        ModernUIStyle.styleLabel(info1);
        ModernUIStyle.styleLabel(info2);
        ModernUIStyle.styleLabel(info3);
        
        info1.setForeground(ModernUIStyle.ACCENT_GREEN);
        info2.setForeground(ModernUIStyle.ACCENT_RED);
        info3.setForeground(ModernUIStyle.TEXT_SECONDARY);
        
        infoPanel.add(info1);
        infoPanel.add(info2);
        infoPanel.add(info3);
        
        panel.add(infoPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /** Crea la pestana de configuracion PLC */
    private JPanel createPLCTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        ModernUIStyle.stylePanel(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Panel superior con conexion
        JPanel connectionPanel = ModernUIStyle.createTitledPanel("Conexion Modbus TCP");
        connectionPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        int row = 0;
        
        // Checkbox habilitar
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        plcEnabledCheck = new JCheckBox("Habilitar comunicacion con PLC", false);
        ModernUIStyle.styleCheckBox(plcEnabledCheck);
        plcEnabledCheck.addActionListener(e -> updatePLCFieldsEnabled());
        connectionPanel.add(plcEnabledCheck, gbc);
        
        row++;
        gbc.gridwidth = 1;
        
        // IP
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel ipLabel = new JLabel("IP del PLC:");
        ModernUIStyle.styleLabel(ipLabel);
        connectionPanel.add(ipLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        plcIPField = new JTextField(15);
        ModernUIStyle.styleTextField(plcIPField);
        connectionPanel.add(plcIPField, gbc);
        
        row++;
        
        // Puerto
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel portLabel = new JLabel("Puerto:");
        ModernUIStyle.styleLabel(portLabel);
        connectionPanel.add(portLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        plcPortSpinner = new JSpinner(new SpinnerNumberModel(502, 1, 65535, 1));
        ModernUIStyle.styleSpinner(plcPortSpinner);
        connectionPanel.add(plcPortSpinner, gbc);
        
        row++;
        
        // Unit ID
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel unitLabel = new JLabel("Unit ID:");
        ModernUIStyle.styleLabel(unitLabel);
        connectionPanel.add(unitLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        plcUnitIdSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 255, 1));
        ModernUIStyle.styleSpinner(plcUnitIdSpinner);
        connectionPanel.add(plcUnitIdSpinner, gbc);
        
        row++;
        
        // Coil enabler
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel coilLabel = new JLabel("Coil Enabler:");
        ModernUIStyle.styleLabel(coilLabel);
        connectionPanel.add(coilLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        plcCoilSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 65535, 1));
        ModernUIStyle.styleSpinner(plcCoilSpinner);
        connectionPanel.add(plcCoilSpinner, gbc);
        
        row++;
        
        // Intervalo polling
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel intervalLabel = new JLabel("Intervalo polling (ms):");
        ModernUIStyle.styleLabel(intervalLabel);
        connectionPanel.add(intervalLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        plcIntervalSpinner = new JSpinner(new SpinnerNumberModel(500, 50, 10000, 50));
        ModernUIStyle.styleSpinner(plcIntervalSpinner);
        connectionPanel.add(plcIntervalSpinner, gbc);
        
        row++;
        
        // Botones conexion
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2;
        JPanel connButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        connButtonPanel.setOpaque(false);
        
        plcConnectButton = new JButton("Conectar");
        ModernUIStyle.styleSuccessButton(plcConnectButton);
        plcConnectButton.addActionListener(e -> connectPLC());
        
        plcDisconnectButton = new JButton("Desconectar");
        ModernUIStyle.styleDangerButton(plcDisconnectButton);
        plcDisconnectButton.setEnabled(false);
        plcDisconnectButton.addActionListener(e -> disconnectPLC());
        
        connButtonPanel.add(plcConnectButton);
        connButtonPanel.add(plcDisconnectButton);
        
        connectionPanel.add(connButtonPanel, gbc);
        
        row++;
        
        // Estado PLC
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 1;
        plcStatusLabel = new JLabel("Desconectado");
        ModernUIStyle.styleTitleLabel(plcStatusLabel);
        connectionPanel.add(plcStatusLabel, gbc);
        
        gbc.gridx = 1;
        coilStatusLabel = new JLabel("Coil: --");
        ModernUIStyle.styleLabel(coilStatusLabel);
        connectionPanel.add(coilStatusLabel, gbc);
        
        // Panel de Holding Registers
        JPanel hrPanel = ModernUIStyle.createTitledPanel("Holding Registers");
        hrPanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        row = 0;
        
        // HR Tipo Embalaje
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel hrTipoLabel = new JLabel("HR Tipo Embalaje:");
        ModernUIStyle.styleLabel(hrTipoLabel);
        hrPanel.add(hrTipoLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        hrTipoSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 65535, 1));
        ModernUIStyle.styleSpinner(hrTipoSpinner);
        hrPanel.add(hrTipoSpinner, gbc);
        
        row++;
        
        // HR Ancho
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel hrAnchoLabel = new JLabel("HR Ancho:");
        ModernUIStyle.styleLabel(hrAnchoLabel);
        hrPanel.add(hrAnchoLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        hrAnchoSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 65535, 1));
        ModernUIStyle.styleSpinner(hrAnchoSpinner);
        hrPanel.add(hrAnchoSpinner, gbc);
        
        row++;
        
        // HR Largo
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel hrLargoLabel = new JLabel("HR Largo:");
        ModernUIStyle.styleLabel(hrLargoLabel);
        hrPanel.add(hrLargoLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        hrLargoSpinner = new JSpinner(new SpinnerNumberModel(2, 0, 65535, 1));
        ModernUIStyle.styleSpinner(hrLargoSpinner);
        hrPanel.add(hrLargoSpinner, gbc);
        
        row++;
        
        // HR Control
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        JLabel hrControlLabel = new JLabel("HR Control:");
        ModernUIStyle.styleLabel(hrControlLabel);
        hrPanel.add(hrControlLabel, gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        hrControlSpinner = new JSpinner(new SpinnerNumberModel(3, 0, 65535, 1));
        ModernUIStyle.styleSpinner(hrControlSpinner);
        hrPanel.add(hrControlSpinner, gbc);
        
        // Combinar paneles
        JPanel topPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        topPanel.setOpaque(false);
        topPanel.add(connectionPanel);
        topPanel.add(hrPanel);
        
        panel.add(topPanel, BorderLayout.NORTH);
        
        // Panel de leyenda
        JPanel legendPanel = ModernUIStyle.createTitledPanel("Flujo de Operacion");
        legendPanel.setLayout(new GridLayout(4, 1, 5, 5));
        
        JLabel l1 = new JLabel("1. La aplicacion consulta la API periodicamente para obtener la lista de items");
        JLabel l2 = new JLabel("2. Cuando el Coil Enabler cambia de 0 a 1: Se escriben datos al PLC");
        JLabel l3 = new JLabel("3. Cuando el Coil cambia de 1 a 0: Se llama a la API de baja");
        JLabel l4 = new JLabel("Logs guardados en carpeta 'logs/' (tags_YYYYMMDD.log y errores_YYYYMMDD.log)");
        
        ModernUIStyle.styleLabel(l1);
        ModernUIStyle.styleLabel(l2);
        ModernUIStyle.styleLabel(l3);
        ModernUIStyle.styleLabel(l4);
        
        l1.setForeground(ModernUIStyle.TEXT_SECONDARY);
        l2.setForeground(ModernUIStyle.ACCENT_GREEN);
        l3.setForeground(ModernUIStyle.ACCENT_RED);
        l4.setForeground(ModernUIStyle.ACCENT_BLUE);
        
        legendPanel.add(l1);
        legendPanel.add(l2);
        legendPanel.add(l3);
        legendPanel.add(l4);
        
        panel.add(legendPanel, BorderLayout.CENTER);
        
        updatePLCFieldsEnabled();
        
        return panel;
    }
    
    /** Crea la pestana de Opciones */
    private JPanel createOptionsTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        ModernUIStyle.stylePanel(panel);
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Panel de inicio automatico
        JPanel autoStartPanel = ModernUIStyle.createTitledPanel("Inicio Automatico");
        autoStartPanel.setLayout(new GridLayout(4, 1, 10, 10));
        
        apiAutoStartCheck = new JCheckBox("Iniciar polling de API automaticamente");
        ModernUIStyle.styleCheckBox(apiAutoStartCheck);
        
        plcAutoStartCheck = new JCheckBox("Conectar al PLC automaticamente");
        ModernUIStyle.styleCheckBox(plcAutoStartCheck);
        
        startMinimizedCheck = new JCheckBox("Iniciar minimizado en bandeja del sistema");
        ModernUIStyle.styleCheckBox(startMinimizedCheck);
        
        startWithWindowsCheck = new JCheckBox("Iniciar con Windows (requiere instalacion como servicio)");
        ModernUIStyle.styleCheckBox(startWithWindowsCheck);
        
        autoStartPanel.add(apiAutoStartCheck);
        autoStartPanel.add(plcAutoStartCheck);
        autoStartPanel.add(startMinimizedCheck);
        autoStartPanel.add(startWithWindowsCheck);
        
        // Panel de control
        JPanel controlPanel = ModernUIStyle.createTitledPanel("Control de Servicios");
        controlPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 10));
        
        startAllButton = new JButton("Iniciar Todo");
        ModernUIStyle.styleSuccessButton(startAllButton);
        startAllButton.setPreferredSize(new Dimension(150, 40));
        startAllButton.addActionListener(e -> startAll());
        
        stopAllButton = new JButton("Detener Todo");
        ModernUIStyle.styleDangerButton(stopAllButton);
        stopAllButton.setPreferredSize(new Dimension(150, 40));
        stopAllButton.addActionListener(e -> stopAll());
        
        controlPanel.add(startAllButton);
        controlPanel.add(stopAllButton);
        
        // Panel de servicio Windows
        JPanel servicePanel = ModernUIStyle.createTitledPanel("Servicio de Windows");
        servicePanel.setLayout(new BorderLayout(10, 10));
        
        JTextArea serviceInfo = new JTextArea();
        serviceInfo.setText(
            "Para instalar como servicio de Windows:\n\n" +
            "1. Ejecutar como Administrador\n" +
            "2. Usar NSSM (Non-Sucking Service Manager) o similar\n" +
            "3. Comando: nssm install PLCCoger java -jar PLCCoger.jar\n\n" +
            "El servicio se ejecutara en segundo plano y aparecera en la bandeja del sistema.\n" +
            "Los logs se guardan en la carpeta 'logs/'."
        );
        serviceInfo.setEditable(false);
        serviceInfo.setLineWrap(true);
        serviceInfo.setWrapStyleWord(true);
        ModernUIStyle.styleTextArea(serviceInfo);
        
        installServiceButton = new JButton("Generar Script de Instalacion");
        ModernUIStyle.styleButton(installServiceButton);
        installServiceButton.addActionListener(e -> generateServiceScript());
        
        servicePanel.add(new JScrollPane(serviceInfo), BorderLayout.CENTER);
        servicePanel.add(installServiceButton, BorderLayout.SOUTH);
        
        // Panel de configuracion
        JPanel configPanel = ModernUIStyle.createTitledPanel("Configuracion");
        configPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 15, 10));
        
        JButton saveConfigButton = new JButton("Guardar Configuracion");
        ModernUIStyle.styleButton(saveConfigButton);
        saveConfigButton.addActionListener(e -> {
            saveConfigFromUI();
            config.save();
            setStatus("Configuracion guardada en config.ini");
        });
        
        JButton openLogsButton = new JButton("Abrir Carpeta Logs");
        ModernUIStyle.styleSecondaryButton(openLogsButton);
        openLogsButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new java.io.File("logs"));
            } catch (Exception ex) {
                setStatus("No se pudo abrir la carpeta de logs");
            }
        });
        
        configPanel.add(saveConfigButton);
        configPanel.add(openLogsButton);
        
        // Organizar paneles
        JPanel topPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        topPanel.setOpaque(false);
        topPanel.add(autoStartPanel);
        topPanel.add(controlPanel);
        
        JPanel bottomPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        bottomPanel.setOpaque(false);
        bottomPanel.add(servicePanel);
        bottomPanel.add(configPanel);
        
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 15, 15));
        centerPanel.setOpaque(false);
        centerPanel.add(topPanel);
        centerPanel.add(bottomPanel);
        
        panel.add(centerPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    /** Genera script de instalacion de servicio */
    private void generateServiceScript() {
        try {
            String jarPath = new java.io.File("PLCCoger.jar").getAbsolutePath();
            String workDir = new java.io.File(".").getAbsolutePath();
            
            StringBuilder bat = new StringBuilder();
            bat.append("@echo off\n");
            bat.append("echo Instalando PLC COGER como servicio de Windows...\n");
            bat.append("echo.\n");
            bat.append("echo Requiere NSSM (https://nssm.cc/download)\n");
            bat.append("echo.\n");
            bat.append("nssm install PLCCoger java\n");
            bat.append("nssm set PLCCoger AppParameters -jar \"").append(jarPath).append("\"\n");
            bat.append("nssm set PLCCoger AppDirectory \"").append(workDir).append("\"\n");
            bat.append("nssm set PLCCoger DisplayName \"PLC COGER Service\"\n");
            bat.append("nssm set PLCCoger Description \"Monitoreo API y Control PLC via Modbus TCP\"\n");
            bat.append("nssm set PLCCoger Start SERVICE_AUTO_START\n");
            bat.append("echo.\n");
            bat.append("echo Servicio instalado. Iniciando...\n");
            bat.append("nssm start PLCCoger\n");
            bat.append("echo.\n");
            bat.append("pause\n");
            
            java.io.PrintWriter writer = new java.io.PrintWriter("install_service.bat");
            writer.print(bat.toString());
            writer.close();
            
            setStatus("Script generado: install_service.bat - Ejecutar como Administrador");
            JOptionPane.showMessageDialog(this,
                "Script generado: install_service.bat\n\n" +
                "Para instalar el servicio:\n" +
                "1. Descargar NSSM de https://nssm.cc/download\n" +
                "2. Ejecutar install_service.bat como Administrador",
                "Script Generado",
                JOptionPane.INFORMATION_MESSAGE);
                
        } catch (Exception e) {
            logManager.logError("OPCIONES", "Error generando script", e);
            setStatus("Error generando script de servicio");
        }
    }
    
    /** Actualiza estado de campos PLC */
    private void updatePLCFieldsEnabled() {
        boolean enabled = plcEnabledCheck.isSelected() && !plcConnected;
        plcIPField.setEnabled(enabled);
        plcPortSpinner.setEnabled(enabled);
        plcUnitIdSpinner.setEnabled(enabled);
        plcCoilSpinner.setEnabled(enabled);
        plcIntervalSpinner.setEnabled(enabled);
        hrTipoSpinner.setEnabled(enabled);
        hrAnchoSpinner.setEnabled(enabled);
        hrLargoSpinner.setEnabled(enabled);
        hrControlSpinner.setEnabled(enabled);
        plcConnectButton.setEnabled(plcEnabledCheck.isSelected() && !plcConnected);
        plcDisconnectButton.setEnabled(plcConnected);
    }
    
    /** Carga la configuracion a la UI */
    private void loadConfigToUI() {
        // API
        apiEndpointField.setText(config.getApiEndpoint());
        apiTokenField.setText(config.getApiToken());
        apiIPField.setText(config.getApiIP());
        apiTrabajoField.setText(config.getApiTrabajo());
        apiIntervalSpinner.setValue(config.getApiPollingInterval());
        
        // PLC
        plcEnabledCheck.setSelected(config.isPlcEnabled());
        plcIPField.setText(config.getPlcIP());
        plcPortSpinner.setValue(config.getPlcPort());
        plcUnitIdSpinner.setValue(config.getPlcUnitId());
        plcCoilSpinner.setValue(config.getPlcEnablerCoil());
        plcIntervalSpinner.setValue(config.getPlcPollingInterval());
        
        // HRs
        hrTipoSpinner.setValue(config.getHrTipoEmbalaje());
        hrAnchoSpinner.setValue(config.getHrAncho());
        hrLargoSpinner.setValue(config.getHrLargo());
        hrControlSpinner.setValue(config.getHrControl());
        
        // Opciones
        apiAutoStartCheck.setSelected(config.isApiAutoStart());
        plcAutoStartCheck.setSelected(config.isPlcAutoStart());
        startMinimizedCheck.setSelected(config.isStartMinimized());
        startWithWindowsCheck.setSelected(config.isStartWithWindows());
        
        updatePLCFieldsEnabled();
    }
    
    /** Guarda la UI a la configuracion */
    private void saveConfigFromUI() {
        // API
        config.setApiEndpoint(apiEndpointField.getText().trim());
        config.setApiToken(apiTokenField.getText().trim());
        config.setApiIP(apiIPField.getText().trim());
        config.setApiTrabajo(apiTrabajoField.getText().trim());
        config.setApiPollingInterval((Integer) apiIntervalSpinner.getValue());
        
        // PLC
        config.setPlcEnabled(plcEnabledCheck.isSelected());
        config.setPlcIP(plcIPField.getText().trim());
        config.setPlcPort((Integer) plcPortSpinner.getValue());
        config.setPlcUnitId((Integer) plcUnitIdSpinner.getValue());
        config.setPlcEnablerCoil((Integer) plcCoilSpinner.getValue());
        config.setPlcPollingInterval((Integer) plcIntervalSpinner.getValue());
        
        // HRs
        config.setHrTipoEmbalaje((Integer) hrTipoSpinner.getValue());
        config.setHrAncho((Integer) hrAnchoSpinner.getValue());
        config.setHrLargo((Integer) hrLargoSpinner.getValue());
        config.setHrControl((Integer) hrControlSpinner.getValue());
        
        // Opciones
        config.setApiAutoStart(apiAutoStartCheck.isSelected());
        config.setPlcAutoStart(plcAutoStartCheck.isSelected());
        config.setStartMinimized(startMinimizedCheck.isSelected());
        config.setStartWithWindows(startWithWindowsCheck.isSelected());
    }
    
    /** Inicia el polling de la API */
    private void startAPIPolling() {
        saveConfigFromUI();
        
        apiClient.setEndpoint(config.getApiEndpoint());
        apiClient.setToken(config.getApiToken());
        apiClient.setIP(config.getApiIP());
        apiClient.setTrabajo(config.getApiTrabajo());
        apiClient.setPollingInterval(config.getApiPollingInterval());
        
        apiClient.startPolling();
        apiPolling = true;
        
        apiStartButton.setEnabled(false);
        apiStopButton.setEnabled(true);
        apiStatusLabel.setText("[ON] Polling activo");
        monitorStatusLabel.setText("[ON] Recibiendo datos...");
        
        logManager.logInfo("API", "Polling iniciado cada " + config.getApiPollingInterval() + "ms");
        setStatus("Polling de API iniciado cada " + config.getApiPollingInterval() + "ms");
    }
    
    /** Detiene el polling de la API */
    private void stopAPIPolling() {
        apiClient.stopPolling();
        apiPolling = false;
        
        apiStartButton.setEnabled(true);
        apiStopButton.setEnabled(false);
        apiStatusLabel.setText("Polling detenido");
        monitorStatusLabel.setText("Polling detenido");
        
        logManager.logInfo("API", "Polling detenido");
        setStatus("Polling de API detenido");
    }
    
    /** Conecta al PLC */
    private void connectPLC() {
        saveConfigFromUI();
        
        modbusClient.setHost(config.getPlcIP());
        modbusClient.setPort(config.getPlcPort());
        modbusClient.setUnitId(config.getPlcUnitId());
        
        if (modbusClient.connect()) {
            plcConnected = true;
            modbusClient.startPolling(config.getPlcEnablerCoil(), config.getPlcPollingInterval());
            
            plcStatusLabel.setText("[ON] Conectado");
            updatePLCFieldsEnabled();
            logManager.logInfo("PLC", "Conectado a " + config.getPlcIP() + ":" + config.getPlcPort());
            setStatus("Conectado al PLC " + config.getPlcIP() + ":" + config.getPlcPort());
        }
    }
    
    /** Desconecta del PLC */
    private void disconnectPLC() {
        modbusClient.disconnect();
        plcConnected = false;
        currentProcessingItem = null;
        
        plcStatusLabel.setText("Desconectado");
        coilStatusLabel.setText("Coil: --");
        updatePLCFieldsEnabled();
        logManager.logInfo("PLC", "Desconectado");
        setStatus("Desconectado del PLC");
    }
    
    /** Actualiza la barra de estado */
    private void setStatus(String text) {
        statusBar.setText(text);
    }
    
    // ============ APIListener ============
    
    @Override
    public void onItemsReceived(List<CogerAPIClient.ItemData> items) {
        tableModel.setRowCount(0);
        
        for (CogerAPIClient.ItemData item : items) {
            tableModel.addRow(new Object[] {
                item.orden,
                item.tag,
                item.codigo,
                item.descripcion,
                item.tipoEmbalaje,
                item.descTipoEmbalaje,
                item.ancho,
                item.largo,
                item.unidadMedida,
                item.variante
            });
        }
        
        itemCountLabel.setText("Items: " + items.size());
        
        if (!items.isEmpty()) {
            monitorStatusLabel.setText("[ON] " + items.size() + " item(s) en cola");
        } else {
            monitorStatusLabel.setText("[!] Sin items pendientes");
        }
    }
    
    @Override
    public void onError(String message) {
        monitorStatusLabel.setText("[ERR] Error: " + message);
        logManager.logError("API", message);
        setStatus("Error API: " + message);
    }
    
    @Override
    public void onBajaSuccess(String tag) {
        if (currentProcessingItem != null) {
            logManager.logTagBaja(tag, currentProcessingItem.codigo, currentProcessingItem.orden);
        }
        setStatus("OK - Baja exitosa del tag: " + tag);
        currentProcessingItem = null;
    }
    
    @Override
    public void onBajaError(String tag, String error) {
        logManager.logError("API", "Error en baja del tag " + tag + ": " + error);
        setStatus("ERROR - En baja del tag " + tag + ": " + error);
    }
    
    // ============ PLCListener ============
    
    @Override
    public void onCoilChanged(boolean value) {
        SwingUtilities.invokeLater(() -> {
            coilStatusLabel.setText("Coil: " + (value ? "ON (1)" : "OFF (0)"));
            coilStatusLabel.setForeground(value ? ModernUIStyle.ACCENT_GREEN : ModernUIStyle.TEXT_PRIMARY);
            
            if (value) {
                CogerAPIClient.ItemData item = apiClient.getFirstItem();
                
                if (item != null) {
                    currentProcessingItem = item;
                    setStatus("Escribiendo al PLC: " + item.codigo + " (ancho=" + item.ancho + ", largo=" + item.largo + ")");
                    
                    modbusClient.writeProductData(
                        config.getHrTipoEmbalaje(),
                        config.getHrAncho(),
                        config.getHrLargo(),
                        config.getHrControl(),
                        item.tipoEmbalaje,
                        item.ancho,
                        item.largo
                    );
                    
                    logManager.logTagEnviado(item.tag, item.codigo, item.ancho, item.largo, item.tipoEmbalaje);
                } else {
                    logManager.logError("PLC", "Coil ON pero no hay items en la cola");
                    setStatus("Coil ON pero no hay items en la cola");
                }
            } else {
                if (currentProcessingItem != null) {
                    setStatus("Realizando baja del item: " + currentProcessingItem.tag);
                    apiClient.realizarBaja(currentProcessingItem.tag, currentProcessingItem.orden);
                }
            }
        });
    }
    
    @Override
    public void onWriteSuccess() {
        SwingUtilities.invokeLater(() -> {
            setStatus("OK - Datos escritos al PLC correctamente");
        });
    }
    
    @Override
    public void onConnected() {
        SwingUtilities.invokeLater(() -> {
            plcStatusLabel.setText("[ON] Conectado");
        });
    }
    
    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            plcStatusLabel.setText("Desconectado");
            plcConnected = false;
            updatePLCFieldsEnabled();
        });
    }
}
