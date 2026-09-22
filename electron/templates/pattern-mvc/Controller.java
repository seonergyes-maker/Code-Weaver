/**
 * Controlador - Maneja la interacción entre Modelo y Vista
 */
public class Controller {
    
    private Model model;
    private View view;
    
    public Controller(Model model, View view) {
        this.model = model;
        this.view = view;
    }
    
    public void setData(String data) {
        if (data == null || data.isEmpty()) {
            view.displayError("Los datos no pueden estar vacíos");
            return;
        }
        model.setData(data);
        view.displaySuccess("Datos actualizados");
    }
    
    public void displayData() {
        String processedData = model.processData();
        view.displayData(processedData);
    }
    
    public static void main(String[] args) {
        Model model = new Model();
        View view = new View();
        Controller controller = new Controller(model, view);
        
        controller.setData("Hola Mundo MVC");
        controller.displayData();
        
        controller.setData("");  // Error
        controller.setData("Nuevo dato");
        controller.displayData();
    }
}