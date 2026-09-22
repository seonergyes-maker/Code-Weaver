/**
 * Estrategia de pago con PayPal
 */
public class PayPalPayment implements PaymentStrategy {
    
    private String email;
    
    public PayPalPayment(String email) {
        this.email = email;
    }
    
    @Override
    public void pay(double amount) {
        System.out.printf("Pagando $%.2f via PayPal (%s)%n", amount, email);
    }
    
    @Override
    public String getDescription() {
        return "PayPal: " + email;
    }
}