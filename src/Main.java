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
        String csvFilePath = "event_file.csv";
        String zonesFilePath = "zone_file.csv";
        DroneSwarmMonitor monitor =  new DroneSwarmMonitor();
        Scheduler scheduler = new Scheduler(zonesFilePath);

        // create subsystem threads for fire incident and drone
        Thread fireincidentsubsystem = new Thread(new FireIncidentSubsystem(scheduler, csvFilePath));
        Thread dronesubsystem = new Thread(new DroneSubsystem(scheduler, 1));
        fireincidentsubsystem.start();
        dronesubsystem.start();
        monitor.addLog("SYSTEM", "Started");

    }
}
