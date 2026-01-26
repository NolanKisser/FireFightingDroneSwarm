public class Main {
    public static void main(String[] args) {
        String filePath = "input.csv";

        Scheduler scheduler = new Scheduler();

        Thread fireincidentsubsystem = new Thread(new FireIncidentSubsystem(scheduler, filePath));
        Thread dronesubsystem1 = new Thread(new DroneSubsystem(scheduler, 1));
        Thread dronesubsystem2 = new Thread(new DroneSubsystem(scheduler, 2));
        Thread dronesubsystem3 = new Thread(new DroneSubsystem(scheduler, 3));

        fireincidentsubsystem.start();
        dronesubsystem1.start();
        dronesubsystem2.start();


    }
}