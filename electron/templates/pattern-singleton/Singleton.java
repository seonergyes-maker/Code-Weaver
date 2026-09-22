/**
 * Patrón Singleton - Garantiza una única instancia
 * Thread-safe usando inicialización estática
 */
public class Singleton {
    
    private static final Singleton INSTANCE = new Singleton();
    
    private Singleton() {
        // Constructor privado
    }
    
    public static Singleton getInstance() {
        return INSTANCE;
    }
    
    public void doSomething() {
        System.out.println("Singleton en acción");
    }
    
    public static void main(String[] args) {
        Singleton s1 = Singleton.getInstance();
        Singleton s2 = Singleton.getInstance();
        System.out.println("Misma instancia: " + (s1 == s2));
        s1.doSomething();
    }
}