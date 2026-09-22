/**
 * Implementación concreta del observador
 */
public class ConcreteObserver implements Observer {
    
    private String name;
    
    public ConcreteObserver(String name) {
        this.name = name;
    }
    
    @Override
    public void update(String message) {
        System.out.println(name + " recibió: " + message);
    }
    
    public static void main(String[] args) {
        Subject subject = new Subject();
        
        Observer obs1 = new ConcreteObserver("Observador 1");
        Observer obs2 = new ConcreteObserver("Observador 2");
        Observer obs3 = new ConcreteObserver("Observador 3");
        
        subject.attach(obs1);
        subject.attach(obs2);
        subject.attach(obs3);
        
        subject.setState("Primer cambio");
        subject.setState("Segundo cambio");
        
        subject.detach(obs2);
        subject.setState("Tercer cambio (sin obs2)");
    }
}