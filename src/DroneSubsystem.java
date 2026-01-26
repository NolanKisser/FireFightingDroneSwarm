import java.io.BufferedReader;
import java.io.FileReader;

public class DroneSubsystem implements Runnable {

    private final Scheduler scheduler;
    private final int droneID;

    /**
     * Constructor
     * @param scheduler
     * @param droneID
     */
    public DroneSubsystem(Scheduler scheduler, int droneID) {
        this.scheduler = scheduler;
        this.droneID = droneID;
    }

    @Override
    public void run() {
        while(true) {
            FireEvent event = scheduler.getNextFireEvent();
            if (event == null) {
                break;
            }
            System.out.println("DroneID: " + droneID + " extinguishing event: " + event);

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
