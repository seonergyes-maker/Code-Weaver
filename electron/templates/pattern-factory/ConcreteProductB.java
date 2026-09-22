/**
 * Implementación concreta del producto B
 */
public class ConcreteProductB implements Product {
    
    @Override
    public void use() {
        System.out.println("Usando Producto B");
    }
    
    @Override
    public String getDescription() {
        return "Soy el Producto B";
    }
}