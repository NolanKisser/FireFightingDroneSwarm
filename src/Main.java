public class Main {
    public static void main(String[] args) {
        String csvFilePath = "event_file.csv";
        String zonesFilePath = "zone_file.csv";
        DroneSwarmMonitor monitor =  new DroneSwarmMonitor();
        Scheduler scheduler = new Scheduler(zonesFilePath);

        Thread fireincidentsubsystem = new Thread(new FireIncidentSubsystem(scheduler, csvFilePath));
        Thread dronesubsystem = new Thread(new DroneSubsystem(scheduler, 1));
        fireincidentsubsystem.start();
        dronesubsystem.start();
        monitor.addLog("SYSTEM", "Started");

    }
}
