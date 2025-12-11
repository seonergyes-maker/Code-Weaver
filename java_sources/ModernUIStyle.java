import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.*;
import java.awt.*;

/**
 * Clase de utilidades para aplicar un estilo moderno tipo web/dashboard
 * a la interfaz gráfica del sistema RFID.
 * 
 * Paleta de colores industrial:
 * - Fondo oscuro: #1E2028 (gris azulado oscuro)
 * - Panel secundario: #252A34 (gris más claro)
 * - Acento primario: #00B8D9 (cyan/turquesa)
 * - Acento éxito: #36B37E (verde)
 * - Acento advertencia: #FFAB00 (ámbar)
 * - Acento error: #FF5630 (rojo)
 * - Texto principal: #FFFFFF
 * - Texto secundario: #A5ADBA
 */
public class ModernUIStyle {
    
 // Colores principales (tema claro)
 public static final Color BG_DARK   = new Color(244, 245, 247);  // #F4F5F7 fondo general
 public static final Color BG_PANEL  = new Color(255, 255, 255);  // #FFFFFF paneles
 public static final Color BG_CARD   = new Color(250, 251, 252);  // #FAFBFC tarjetas
 public static final Color BG_INPUT  = new Color(235, 236, 240);  // #EBECF0 campos de texto
 public static final Color BG_HOVER  = new Color(223, 225, 230);  // #DFE1E6 hover

 // Acentos (los mismos, funcionan bien sobre fondos claros)
 public static final Color ACCENT_PRIMARY = new Color(0, 184, 217);   // #00B8D9 cyan
 public static final Color ACCENT_SUCCESS = new Color(54, 179, 126);  // #36B37E verde
 public static final Color ACCENT_WARNING = new Color(255, 171, 0);   // #FFAB00 ámbar
 public static final Color ACCENT_ERROR   = new Color(255, 86, 48);   // #FF5630 rojo
 public static final Color ACCENT_INFO    = new Color(101, 84, 192);  // #6554C0 púrpura

 // Texto (letras negras / oscuras)
 public static final Color TEXT_PRIMARY   = new Color(0, 0, 0);       // #000000 texto principal
 public static final Color TEXT_SECONDARY = new Color(11, 11, 11);    // #444B58 gris oscuro
 public static final Color TEXT_MUTED     = new Color(44, 44, 44); // #6B778C gris medio

 // Bordes
 public static final Color BORDER_DEFAULT = new Color(223, 225, 230); // #DFE1E6
 public static final Color BORDER_FOCUS   = ACCENT_PRIMARY;
    
    /**
     * Aplica el tema oscuro moderno a toda la aplicación.
     */
    public static void applyDarkTheme() {
        try {
            // Intentar FlatLaf primero
            Class<?> flatDarkClass = Class.forName("com.formdev.flatlaf.FlatDarkLaf");
            UIManager.setLookAndFeel((LookAndFeel) flatDarkClass.getDeclaredConstructor().newInstance());
        } catch (Exception e) {
            // Aplicar tema manual
            applyManualDarkTheme();
        }
        
        // Aplicar colores personalizados
        UIManager.put("Panel.background", BG_DARK);
        UIManager.put("Panel.foreground", TEXT_PRIMARY);
        UIManager.put("Label.foreground", TEXT_PRIMARY);
        UIManager.put("Button.background", BG_CARD);
        UIManager.put("Button.foreground", TEXT_PRIMARY);
        UIManager.put("Button.select", ACCENT_PRIMARY);
        UIManager.put("TextField.background", BG_INPUT);
        UIManager.put("TextField.foreground", TEXT_PRIMARY);
        UIManager.put("TextField.caretForeground", TEXT_PRIMARY);
        UIManager.put("TextArea.background", BG_INPUT);
        UIManager.put("TextArea.foreground", TEXT_PRIMARY);
        UIManager.put("ComboBox.background", BG_INPUT);
        UIManager.put("ComboBox.foreground", TEXT_PRIMARY);
        UIManager.put("List.background", BG_PANEL);
        UIManager.put("List.foreground", TEXT_PRIMARY);
        UIManager.put("Table.background", BG_PANEL);
        UIManager.put("Table.foreground", TEXT_PRIMARY);
        UIManager.put("Table.gridColor", BORDER_DEFAULT);
        UIManager.put("Table.selectionBackground", ACCENT_PRIMARY);
        UIManager.put("Table.selectionForeground", TEXT_PRIMARY);
        UIManager.put("TableHeader.background", BG_CARD);
        UIManager.put("TableHeader.foreground", TEXT_PRIMARY);
        UIManager.put("ScrollPane.background", BG_DARK);
        UIManager.put("TabbedPane.background", BG_DARK);
        UIManager.put("TabbedPane.foreground", TEXT_PRIMARY);
        UIManager.put("TabbedPane.selected", BG_CARD);
        UIManager.put("CheckBox.background", BG_DARK);
        UIManager.put("CheckBox.foreground", TEXT_PRIMARY);
        UIManager.put("Spinner.background", BG_INPUT);
        UIManager.put("Spinner.foreground", TEXT_PRIMARY);
        UIManager.put("ProgressBar.foreground", ACCENT_PRIMARY);
        UIManager.put("ProgressBar.background", BG_CARD);
        UIManager.put("Slider.background", BG_DARK);
        UIManager.put("Slider.foreground", TEXT_PRIMARY);
        UIManager.put("ToolTip.background", BG_CARD);
        UIManager.put("ToolTip.foreground", TEXT_PRIMARY);
        UIManager.put("OptionPane.background", BG_PANEL);
        UIManager.put("OptionPane.foreground", TEXT_PRIMARY);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
    }
    
    private static void applyManualDarkTheme() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            // Ignorar
        }
    }
    
    /**
     * Crea un panel con estilo de tarjeta moderna.
     */
    public static JPanel createCard() {
        JPanel card = new JPanel();
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(12, BORDER_DEFAULT),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        return card;
    }
    
    /**
     * Crea un panel con estilo de tarjeta con título.
     */
    public static JPanel createTitledCard(String title) {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
            new RoundedBorder(12, BORDER_DEFAULT),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(TEXT_PRIMARY);
        card.add(titleLabel, BorderLayout.NORTH);
        
        return card;
    }
    
    /**
     * Aplica estilo de botón primario (acento cyan).
     */
    public static void stylePrimaryButton(JButton button) {
        button.setBackground(ACCENT_PRIMARY);
        button.setForeground(TEXT_PRIMARY);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(ACCENT_PRIMARY.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(ACCENT_PRIMARY);
            }
        });
    }
    
    /**
     * Aplica estilo de botón secundario.
     */
    public static void styleSecondaryButton(JButton button) {
        button.setBackground(BG_CARD);
        button.setForeground(TEXT_PRIMARY);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_DEFAULT, 1),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setBackground(BG_HOVER);
            }
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setBackground(BG_CARD);
            }
        });
    }
    
    /**
     * Aplica estilo de botón de éxito (verde).
     */
    public static void styleSuccessButton(JButton button) {
        button.setBackground(ACCENT_SUCCESS);
        button.setForeground(TEXT_PRIMARY);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
    }
    
    /**
     * Aplica estilo de botón de peligro (rojo).
     */
    public static void styleDangerButton(JButton button) {
        button.setBackground(ACCENT_ERROR);
        button.setForeground(TEXT_PRIMARY);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
    }
    
    /**
     * Aplica estilo de campo de texto moderno.
     */
    public static void styleTextField(JTextField field) {
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_DEFAULT, 1),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
    }
    
    /**
     * Aplica estilo a una tabla moderna.
     */
    public static void styleTable(JTable table) {
        table.setBackground(BG_PANEL);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(BORDER_DEFAULT);
        table.setSelectionBackground(ACCENT_PRIMARY);
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setRowHeight(35);
        table.setFont(new Font("Consolas", Font.PLAIN, 12));
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        
        // Header
        table.getTableHeader().setBackground(BG_CARD);
        table.getTableHeader().setForeground(TEXT_PRIMARY);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, ACCENT_PRIMARY));
    }
    
    /**
     * Aplica estilo a un JTabbedPane moderno.
     */
    public static void styleTabbedPane(JTabbedPane tabbedPane) {
        tabbedPane.setBackground(BG_DARK);
        tabbedPane.setForeground(TEXT_PRIMARY);
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        // Color de las pestañas
        UIManager.put("TabbedPane.selected", BG_CARD);
        UIManager.put("TabbedPane.contentAreaColor", BG_DARK);
        UIManager.put("TabbedPane.focus", ACCENT_PRIMARY);
    }
    
    /**
     * Crea una etiqueta de título con estilo.
     */
    public static JLabel createTitleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 18));
        label.setForeground(TEXT_PRIMARY);
        return label;
    }
    
    /**
     * Crea una etiqueta de subtítulo.
     */
    public static JLabel createSubtitleLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(TEXT_SECONDARY);
        return label;
    }
    
    /**
     * Crea un indicador de estado LED.
     */
    public static JPanel createStatusIndicator(Color color) {
        JPanel indicator = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Glow effect
                int size = Math.min(getWidth(), getHeight()) - 4;
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;
                
                // Outer glow
                g2d.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
                g2d.fillOval(x - 3, y - 3, size + 6, size + 6);
                
                // Main circle
                g2d.setColor(color);
                g2d.fillOval(x, y, size, size);
                
                // Highlight
                g2d.setColor(new Color(255, 255, 255, 80));
                g2d.fillOval(x + 2, y + 2, size / 3, size / 3);
            }
        };
        indicator.setPreferredSize(new Dimension(24, 24));
        indicator.setOpaque(false);
        return indicator;
    }
    
    /**
     * Crea un chip/badge de estado.
     */
    public static JLabel createStatusChip(String text, Color bgColor) {
        JLabel chip = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(bgColor);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        chip.setOpaque(false);
        chip.setForeground(TEXT_PRIMARY);
        chip.setFont(new Font("Segoe UI", Font.BOLD, 11));
        chip.setHorizontalAlignment(SwingConstants.CENTER);
        chip.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        return chip;
    }
    
    /**
     * Crea una barra de progreso moderna.
     */
    public static JProgressBar createModernProgressBar() {
        JProgressBar progressBar = new JProgressBar();
        progressBar.setBackground(BG_CARD);
        progressBar.setForeground(TEXT_PRIMARY);
        progressBar.setBorderPainted(false);
        progressBar.setStringPainted(false);
        return progressBar;
    }
    
    /**
     * Aplica estilo a un JSlider moderno.
     */
    public static void styleSlider(JSlider slider) {
        slider.setBackground(BG_DARK);
        slider.setForeground(TEXT_PRIMARY);
        slider.setFont(new Font("Segoe UI", Font.PLAIN, 10));
    }
    
    /**
     * Aplica estilo a un JSpinner moderno.
     */
    public static void styleSpinner(JSpinner spinner) {
        spinner.setBackground(BG_INPUT);
        spinner.setForeground(TEXT_PRIMARY);
        spinner.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(BG_INPUT);
            tf.setForeground(TEXT_PRIMARY);
            tf.setCaretColor(TEXT_PRIMARY);
        }
    }
    
    /**
     * Aplica estilo a un JCheckBox moderno.
     */
    public static void styleCheckBox(JCheckBox checkBox) {
        checkBox.setBackground(BG_DARK);
        checkBox.setForeground(TEXT_PRIMARY);
        checkBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        checkBox.setFocusPainted(false);
    }
    
    /**
     * Aplica estilo a un JComboBox moderno.
     */
    public static void styleComboBox(JComboBox<?> comboBox) {
        comboBox.setBackground(BG_INPUT);
        comboBox.setForeground(TEXT_PRIMARY);
        comboBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ((JComponent) comboBox.getRenderer()).setOpaque(true);
    }
    
    /**
     * Crea un separador horizontal estilizado.
     */
    public static JSeparator createSeparator() {
        JSeparator separator = new JSeparator();
        separator.setForeground(BORDER_DEFAULT);
        separator.setBackground(BG_DARK);
        return separator;
    }
    
    /**
     * Borde redondeado personalizado.
     */
    public static class RoundedBorder extends AbstractBorder {
        private int radius;
        private Color color;
        
        public RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }
        
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2d = (Graphics2D) g.create();
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setColor(color);
            g2d.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2d.dispose();
        }
        
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius / 2, radius / 2, radius / 2);
        }
    }
}
