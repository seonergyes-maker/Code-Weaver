/**
 * Modelo - Representa los datos y la lógica de negocio
 */
public class Model {
    
    private String data;
    
    public String getData() {
        return data;
    }
    
    public void setData(String data) {
        this.data = data;
    }
    
    public String processData() {
        return data != null ? data.toUpperCase() : "";
    }
}