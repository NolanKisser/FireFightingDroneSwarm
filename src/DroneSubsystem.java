import java.io.BufferedReader;
import java.io.FileReader;

/**
 * DroneSubsystem class represents a single firefighter drone.
 * The firefighter drone repeats the next incomplete FireEvent from the Scheduler and simulates
 * extinguishing the fire then reports back to the Scheduler of completion.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class DroneSubsystem implements Runnable {

    private final Scheduler scheduler;
    private final int droneID;

    /**
     * Constructor for DroneSubsystem
     * @param scheduler the shared scheduler
     * @param droneID unique ID for the drone
     */
    public DroneSubsystem(Scheduler scheduler, int droneID) {
        this.scheduler = scheduler;
        this.droneID = droneID;
    }

    /**
     * The drone blocks until there is an event available. When the scheduler indicates there are
     * no more events, the drone will complete execution.
     */
    @Override
    public void run() {
        while(true) {
            FireEvent event = scheduler.getNextFireEvent();

            // there are no more events to process
            if (event == null) {
                break;
            }
            System.out.println("DroneID: " + droneID + " extinguishing event: " + event);

            // simulating travel, extinguish, and return time
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            scheduler.completeFireEvent(event);
            System.out.println("DroneID: " + droneID + " extinguished event: " + event);
        }
    }
}
