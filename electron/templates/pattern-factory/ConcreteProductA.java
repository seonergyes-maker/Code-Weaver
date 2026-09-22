/**
 * Implementación concreta del producto A
 */
public class ConcreteProductA implements Product {
    
    @Override
    public void use() {
        System.out.println("Usando Producto A");
    }
    
    @Override
    public String getDescription() {
        return "Soy el Producto A";
    }
}