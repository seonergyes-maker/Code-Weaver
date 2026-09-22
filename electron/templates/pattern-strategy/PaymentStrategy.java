/**
 * Interfaz de estrategia de pago
 */
public interface PaymentStrategy {
    void pay(double amount);
    String getDescription();
}