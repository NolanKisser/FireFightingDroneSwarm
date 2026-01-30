package core;

import gui.DroneSwarmGUI;

public class Main {
    public static void main(String[] args) {
        String csvFilePath = "event_file.csv";
        String zonesFilePath = "zone_file.csv";
        DroneSwarmGUI gui =  new DroneSwarmGUI();
        Scheduler scheduler = new Scheduler(zonesFilePath, gui);
        gui.setMap(scheduler);

        Thread fireincidentsubsystem = new Thread(new FireIncidentSubsystem(scheduler, csvFilePath));
        Thread dronesubsystem = new Thread(new DroneSubsystem(scheduler, 1));
        fireincidentsubsystem.start();
        dronesubsystem.start();
        gui.addLog("SYSTEM", "Started");



    }
}
