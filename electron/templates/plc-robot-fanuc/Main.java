import javax.swing.*;

/**
 * PLC ROBOT FANUC v1.0.2 by Daemon4 - Aplicación de Monitoreo API y Control PLC
 * Punto de entrada principal
 */
public class Main {
    
    public static void main(String[] args) {
        // Configurar Look and Feel del sistema
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // Iniciar aplicación en el Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            try {
                PLCRobotFanucWindow window = new PLCRobotFanucWindow();
                window.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, 
                    "Error al iniciar la aplicación: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
