/**
 * Estrategia de pago con tarjeta de crédito
 */
public class CreditCardPayment implements PaymentStrategy {
    
    private String cardNumber;
    private String name;
    
    public CreditCardPayment(String cardNumber, String name) {
        this.cardNumber = cardNumber;
        this.name = name;
    }
    
    @Override
    public void pay(double amount) {
        System.out.printf("Pagando $%.2f con tarjeta %s%n", 
            amount, cardNumber.substring(cardNumber.length() - 4));
    }
    
    @Override
    public String getDescription() {
        return "Tarjeta de crédito: " + name;
    }
}