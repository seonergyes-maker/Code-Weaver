/**
 * Factory para crear productos
 */
public class ProductFactory {
    
    public enum ProductType {
        TYPE_A, TYPE_B
    }
    
    public static Product createProduct(ProductType type) {
        switch (type) {
            case TYPE_A:
                return new ConcreteProductA();
            case TYPE_B:
                return new ConcreteProductB();
            default:
                throw new IllegalArgumentException("Tipo de producto desconocido");
        }
    }
    
    public static void main(String[] args) {
        Product productA = ProductFactory.createProduct(ProductType.TYPE_A);
        Product productB = ProductFactory.createProduct(ProductType.TYPE_B);
        
        productA.use();
        productB.use();
        
        System.out.println(productA.getDescription());
        System.out.println(productB.getDescription());
    }
}