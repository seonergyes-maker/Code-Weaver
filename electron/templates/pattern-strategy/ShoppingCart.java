import java.util.ArrayList;
import java.util.List;

/**
 * Carrito de compras que usa estrategias de pago
 */
public class ShoppingCart {
    
    private List<Double> items = new ArrayList<>();
    private PaymentStrategy paymentStrategy;
    
    public void addItem(double price) {
        items.add(price);
    }
    
    public void setPaymentStrategy(PaymentStrategy strategy) {
        this.paymentStrategy = strategy;
    }
    
    public double getTotal() {
        return items.stream().mapToDouble(Double::doubleValue).sum();
    }
    
    public void checkout() {
        if (paymentStrategy == null) {
            System.out.println("Error: Selecciona un método de pago");
            return;
        }
        double total = getTotal();
        System.out.println("Método: " + paymentStrategy.getDescription());
        paymentStrategy.pay(total);
        System.out.println("¡Compra completada!");
    }
    
    public static void main(String[] args) {
        ShoppingCart cart = new ShoppingCart();
        cart.addItem(100.00);
        cart.addItem(50.50);
        cart.addItem(25.00);
        
        System.out.println("Total: $" + cart.getTotal());
        
        // Pagar con tarjeta
        cart.setPaymentStrategy(new CreditCardPayment("4111111111111111", "Juan Pérez"));
        cart.checkout();
        
        System.out.println();
        
        // Cambiar a PayPal
        cart.setPaymentStrategy(new PayPalPayment("juan@email.com"));
        cart.checkout();
    }
}