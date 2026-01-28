import java.util.*;

/**
 * Scheduler class communicates and synchronizes the FireIncidentSubsystem and the DroneSubsystem.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class Scheduler {

    // fire events to be completed
    private final Queue<FireEvent> incompleteEvents = new LinkedList<>();
    // completed fire events
    private final Queue<FireEvent> completeEvents = new LinkedList<>();

    private boolean allEventsDone = false;

    /**
     * Adds a new fire event from the CSV file to the incomplete events list
     * @param fireEvent event to add
     */
    public synchronized void newFireEvent(FireEvent fireEvent) {
        incompleteEvents.add(fireEvent);
        notifyAll();
    }

    /**
     * Retrieve the next fire event for a firefighter drone to extinguish
     * If there are no fire events available, drone thread is blocked until an event is submitted
     * or until all events are complete
     */
    public synchronized FireEvent getNextFireEvent() {

        while(incompleteEvents.isEmpty() && !allEventsDone) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(incompleteEvents.isEmpty() && allEventsDone) {
            return null;
        }

        return incompleteEvents.remove();
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
