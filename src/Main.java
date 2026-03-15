import java.net.InetAddress;

/**
 * Main class for the Firefighting Drone Swarm simulation
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class Main {
    /**
     * Firefighting Drone Swarm simulation
     * @param args command line arguments
     */
    public static void main(String[] args) {
        try {
            String csvFilePath = "event_file.csv";
            String zonesFilePath = "zone_file.csv";
            DroneSwarmMonitor monitor =  new DroneSwarmMonitor();
            Scheduler scheduler = new Scheduler(zonesFilePath, monitor);
    
            // create subsystem threads for fire incident and drone
            Thread schedulerThread = new Thread(scheduler, "Scheduler-Thread");
            schedulerThread.start();
    
            Thread fireincidentsubsystem = new Thread(new FireIncidentSubsystem("localhost", 5000, csvFilePath));
            
            InetAddress schedulerAddress = InetAddress.getByName("localhost");
            Thread dronesubsystem = new Thread(new DroneSubsystem(schedulerAddress, 5000, 1));
            
            fireincidentsubsystem.start();
            dronesubsystem.start();
            monitor.addLog("SYSTEM", "Started");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
