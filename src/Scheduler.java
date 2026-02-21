import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Scheduler class communicates and synchronizes the FireIncidentSubsystem and the DroneSubsystem.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version February 8, 2026
 */
public class Scheduler {

    public enum State {
        WAITING,
        EVENT_QUEUED,
        DRONE_ACTIVE
    }

    private State currentState = State.WAITING;

    // fire events to be completed
    private final Queue<FireEvent> incompleteEvents = new LinkedList<>();
    // completed fire events
    private final Queue<FireEvent> completeEvents = new LinkedList<>();

    private boolean allEventsDone = false;
    private int activeFires = 0;

    private final Map<Integer, Zone> zones = new HashMap<>();
    private final DroneSwarmMonitor monitor;


    public Scheduler(String zoneFilePath) {
        this(zoneFilePath, null);
    }

    public Scheduler(String zoneFilePath, DroneSwarmMonitor monitor) {
        this.monitor = monitor;
        loadZonesCSV(zoneFilePath);
        updateActiveFiresDisplay();
    }
    /**
     * Adds a new fire event from the CSV file to the incomplete events list
     * @param fireEvent event to add
     */
    public synchronized void newFireEvent(FireEvent fireEvent) {
        incompleteEvents.add(fireEvent);
        activeFires++;
        updateActiveFiresDisplay();
        if (currentState == State.WAITING) {
            currentState = State.EVENT_QUEUED;
        }
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
                currentState = State.WAITING;
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if(incompleteEvents.isEmpty() && allEventsDone) {
            return null;
        }

        currentState = State.DRONE_ACTIVE;
        return incompleteEvents.remove();
    }

    /**
     * Notification from DroneSubsystem that it has reached the target zone.
     * @param droneID ID of the drone
     * @param fireEvent The event being serviced
     */
    public synchronized void droneArrivedAtZone(int droneID, FireEvent fireEvent) {
        // in future iterations with multi-drone, we update specific drone status here
        System.out.println("[Scheduler] Notification: Drone " + droneID + " arrived at Zone " + fireEvent.getZoneID());
    }

    public synchronized void droneReturnToBase(int droneID){
        System.out.println("[Scheduler] Notification: Drone " + droneID + " returned to base.");
        if (!incompleteEvents.isEmpty()) {
            currentState = State.EVENT_QUEUED;
        } else {
            currentState = State.WAITING;
        }
    }
    /**
     * Update boolean when all events are complete
     */
    public synchronized void updateAllEventsDone() {
        allEventsDone = true;
        notifyAll();
    }

    /**
     * Completed fire event gets added to the completeEvents list
     * @param fireEvent
     */
    public synchronized void completeFireEvent(FireEvent fireEvent) {
        completeEvents.add(fireEvent);
        if (activeFires > 0) {
            activeFires--;
        }
        updateActiveFiresDisplay();
        notifyAll();
    }

    /**
     * Retrieving completed fire event
     * @return the next completed fire event or null if simulation is complete
     */
    public synchronized FireEvent getCompletedEvent() {
        while(completeEvents.isEmpty() && !(allEventsDone && incompleteEvents.isEmpty())) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if(completeEvents.isEmpty() && allEventsDone && incompleteEvents.isEmpty()) {
            return null;
        }
        return completeEvents.remove();
    }

    /**
     * Zone ID,Zone Start,Zone End
     * 1,(0;0),(700;600)
     * 2,(0;600),(650;1500)
     * @param zoneFilePath
     */
    private void loadZonesCSV(String zoneFilePath) {
        String line;

        try (BufferedReader br = new BufferedReader(new FileReader(zoneFilePath))) {
            while ((line = br.readLine()) != null) {
                String[] row = line.split(",");

                int zoneID = Integer.parseInt(row[0].trim());

                String[] startCoords = row[1].replace("(", "").replace(")", "").split(";");
                int x1 = Integer.parseInt(startCoords[0].trim());
                int y1 = Integer.parseInt(startCoords[1].trim());

                String[] endCoords = row[2].replace("(", "").replace(")", "").split(";");
                int x2 = Integer.parseInt(endCoords[0].trim());
                int y2 = Integer.parseInt(endCoords[1].trim());

                zones.put(zoneID, new Zone(zoneID, x1, y1, x2, y2));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get all zones
     * @return the zones of all possible fire incidents
     */
    public Map<Integer, Zone> getZones() {
        return zones;
    }

    /**
     * Peek at the next fire event without removing it.
     * @return the next fire event or null if none are queued
     */
    public synchronized FireEvent peekNextFireEvent() {
        return incompleteEvents.peek();
    }

    /**
     * Update the GUI with the drone's current state.
     * @param newState current drone state
     */
    public synchronized void updateDroneState(DroneSubsystem.DroneState newState) {
        updateDroneStateDisplay(newState);
    }

    /**
     * Update the GUI with the number of active fires.
     */
    private void updateActiveFiresDisplay() {
        if (monitor != null) {
            monitor.setActiveFires(activeFires);
        }
    }

    /**
     * Update the GUI with the drone's current state.
     * @param state
     */
    private void updateDroneStateDisplay(DroneSubsystem.DroneState state) {
        if (monitor != null && state != null) {
            monitor.setDroneState(formatDroneState(state));
        }
    }

    private static String formatDroneState(DroneSubsystem.DroneState state) {
        return state.name().replace('_', ' ');
    }


}
