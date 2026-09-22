import java.util.ArrayList;
import java.util.List;

/**
 * Sujeto observable que notifica a sus observadores
 */
public class Subject {
    
    private List<Observer> observers = new ArrayList<>();
    private String state;
    
    public void attach(Observer observer) {
        observers.add(observer);
    }
    
    public void detach(Observer observer) {
        observers.remove(observer);
    }
    
    public void setState(String state) {
        this.state = state;
        notifyAllObservers();
    }
    
    public String getState() {
        return state;
    }
    
    private void notifyAllObservers() {
        for (Observer observer : observers) {
            observer.update(state);
        }
    }
}