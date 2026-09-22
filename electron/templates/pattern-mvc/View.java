/**
 * Vista - Presenta los datos al usuario
 */
public class View {
    
    public void displayData(String data) {
        System.out.println("=== Vista ===");
        System.out.println("Datos: " + data);
        System.out.println("=============");
    }
    
    public void displayError(String error) {
        System.out.println("[ERROR] " + error);
    }
    
    public void displaySuccess(String message) {
        System.out.println("[OK] " + message);
    }
}