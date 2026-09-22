import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;

/**
 * Estilos modernos para la interfaz grafica
 * Tema claro profesional
 */
public class ModernUIStyle {
    
    // Colores del tema claro
    public static final Color BACKGROUND_DARK = new Color(255, 255, 255);      // Blanco
    public static final Color BACKGROUND_MEDIUM = new Color(245, 245, 245);    // Gris muy claro
    public static final Color BACKGROUND_LIGHT = new Color(255, 255, 255);     // Blanco
    public static final Color TEXT_PRIMARY = new Color(33, 33, 33);            // Negro/gris oscuro
    public static final Color TEXT_SECONDARY = new Color(100, 100, 100);       // Gris medio
    public static final Color ACCENT_BLUE = new Color(0, 102, 204);            // Azul
    public static final Color ACCENT_GREEN = new Color(40, 150, 69);           // Verde
    public static final Color ACCENT_RED = new Color(200, 50, 60);             // Rojo
    public static final Color ACCENT_ORANGE = new Color(230, 130, 0);          // Naranja
    public static final Color BORDER_COLOR = new Color(200, 200, 200);         // Gris claro
    
    /** Aplica estilo a un JFrame */
    public static void styleFrame(JFrame frame) {
        frame.getContentPane().setBackground(BACKGROUND_DARK);
    }
    
    /** Aplica estilo a un JPanel */
    public static void stylePanel(JPanel panel) {
        panel.setBackground(BACKGROUND_DARK);
        panel.setForeground(TEXT_PRIMARY);
    }
    
    /** Aplica estilo a un JLabel */
    public static void styleLabel(JLabel label) {
        label.setForeground(TEXT_PRIMARY);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
    }
    
    /** Aplica estilo a un JLabel de titulo */
    public static void styleTitleLabel(JLabel label) {
        label.setForeground(TEXT_PRIMARY);
        label.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }
    
    /** Aplica estilo a un JTextField */
    public static void styleTextField(JTextField field) {
        field.setBackground(Color.WHITE);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        field.setFont(new Font("Consolas", Font.PLAIN, 12));
    }
    
    /** Aplica estilo a un JButton primario */
    public static void styleButton(JButton button) {
        button.setBackground(ACCENT_BLUE);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(120, 32));
    }
    
    /** Aplica estilo a un JButton de exito (verde) */
    public static void styleSuccessButton(JButton button) {
        styleButton(button);
        button.setBackground(ACCENT_GREEN);
    }
    
    /** Aplica estilo a un JButton de peligro (rojo) */
    public static void styleDangerButton(JButton button) {
        styleButton(button);
        button.setBackground(ACCENT_RED);
    }
    
    /** Aplica estilo a un JButton secundario */
    public static void styleSecondaryButton(JButton button) {
        styleButton(button);
        button.setBackground(BACKGROUND_MEDIUM);
        button.setForeground(TEXT_PRIMARY);
        button.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        button.setBorderPainted(true);
    }
    
    /** Aplica estilo a un JCheckBox */
    public static void styleCheckBox(JCheckBox checkBox) {
        checkBox.setBackground(BACKGROUND_DARK);
        checkBox.setForeground(TEXT_PRIMARY);
        checkBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        checkBox.setFocusPainted(false);
    }
    
    /** Aplica estilo a un JSpinner */
    public static void styleSpinner(JSpinner spinner) {
        spinner.setBackground(Color.WHITE);
        spinner.setForeground(TEXT_PRIMARY);
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            JTextField tf = ((JSpinner.DefaultEditor) editor).getTextField();
            tf.setBackground(Color.WHITE);
            tf.setForeground(TEXT_PRIMARY);
            tf.setCaretColor(TEXT_PRIMARY);
        }
    }
    
    /** Aplica estilo a un JTable */
    public static void styleTable(JTable table) {
        table.setBackground(Color.WHITE);
        table.setForeground(TEXT_PRIMARY);
        table.setGridColor(BORDER_COLOR);
        table.setSelectionBackground(ACCENT_BLUE);
        table.setSelectionForeground(Color.WHITE);
        table.setFont(new Font("Consolas", Font.PLAIN, 12));
        table.setRowHeight(28);
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(1, 1));
        
        // Estilo del header
        JTableHeader header = table.getTableHeader();
        header.setBackground(BACKGROUND_MEDIUM);
        header.setForeground(TEXT_PRIMARY);
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
    }
    
    /** Aplica estilo a un JScrollPane */
    public static void styleScrollPane(JScrollPane scrollPane) {
        scrollPane.setBackground(BACKGROUND_DARK);
        scrollPane.getViewport().setBackground(Color.WHITE);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
    }
    
    /** Aplica estilo a un JTabbedPane */
    public static void styleTabbedPane(JTabbedPane tabbedPane) {
        tabbedPane.setBackground(BACKGROUND_DARK);
        tabbedPane.setForeground(TEXT_PRIMARY);
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));
    }
    
    /** Aplica estilo a un JTextArea */
    public static void styleTextArea(JTextArea textArea) {
        textArea.setBackground(Color.WHITE);
        textArea.setForeground(TEXT_PRIMARY);
        textArea.setCaretColor(TEXT_PRIMARY);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        textArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    }
    
    /** Crea un panel con borde titulado */
    public static JPanel createTitledPanel(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(BACKGROUND_DARK);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                TEXT_PRIMARY
            ),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        return panel;
    }
    
    /** Crea un panel tipo tarjeta */
    public static JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(BACKGROUND_MEDIUM);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        return panel;
    }
    
    /** Crea una barra de estado */
    public static JLabel createStatusBar() {
        JLabel label = new JLabel("Listo");
        label.setOpaque(true);
        label.setBackground(BACKGROUND_MEDIUM);
        label.setForeground(TEXT_SECONDARY);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        return label;
    }
}
