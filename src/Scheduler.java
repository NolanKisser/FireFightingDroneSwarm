import java.util.*;

public class Scheduler {

    private final Queue<FireEvent> incompleteEvents = new LinkedList<>();
    private final Queue<FireEvent> completeEvents = new LinkedList<>();


    /**
     * Fire event from the csv file gets added to the incompleteEvents list
     * @param fireEvent
     */
    public synchronized void newFireEvent(FireEvent fireEvent) {
        incompleteEvents.add(fireEvent);
        notifyAll();
    }

    /**
     * Retrieve the next fire event for a firefighter drone to extinguish
     */
    public synchronized void getNextFireEvent() {
        while(incompleteEvents.isEmpty()){
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Completed fire event gets added to the completeEvents list
     * @param fireEvent
     */
    public synchronized void completeFireEvent(FireEvent fireEvent) {
        completeEvents.add(fireEvent);
        notifyAll();
    }



}
