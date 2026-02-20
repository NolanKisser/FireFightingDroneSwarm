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

    public enum FaultType {
        NONE,
        COMMUNICATION_LOST,
        NOZZLE_FAILURE,
        STUCK_IN_FLIGHT
    }

    // Tracks the internal state of each drone
    public static class DroneStatus {
        public int droneID;
        public double currentX;
        public double currentY;
        public double agentRemaining; // Remaining firefighting agent
        public FaultType currentFault;
        public FireEvent currentMission;

        public DroneStatus(int id) {
            this.droneID = id;
            this.currentX = 0.0;
            this.currentY = 0.0;
            this.agentRemaining = 100.0; // Assume 100% capacity at start
            this.currentFault = FaultType.NONE;
            this.currentMission = null;
        }
    }

    private State currentState = State.WAITING;

    // fire events to be completed
    private final Queue<FireEvent> incompleteEvents = new LinkedList<>();
    // completed fire events
    private final Queue<FireEvent> completeEvents = new LinkedList<>();

    // Track statuses of all drones
    private final Map<Integer, DroneStatus> droneStatuses = new HashMap<>();
    private boolean allEventsDone = false;

    private final Map<Integer, Zone> zones = new HashMap<>();


    public Scheduler(String zoneFilePath) {
        loadZonesCSV(zoneFilePath);
    }
    /**
     * Registers a drone in the scheduler's tracking system.
     */
    public synchronized void registerDrone(int droneID) {
        droneStatuses.putIfAbsent(droneID, new DroneStatus(droneID));
    }

    /**
     * Adds a new fire event from the CSV file to the priority queue
     * @param fireEvent event to add
     */
    public synchronized void newFireEvent(FireEvent fireEvent) {
        incompleteEvents.add(fireEvent);
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
        FireEvent nextEvent = incompleteEvents.poll();

        return nextEvent;
    }

    /**
     * Updates the status of a specific drone.
     */
    public synchronized void updateDroneStatus(int droneID, double x, double y, double agentRemaining) {
        DroneStatus status = droneStatuses.get(droneID);
        if (status != null) {
            status.currentX = x;
            status.currentY = y;
            status.agentRemaining = agentRemaining;
            System.out.printf("[Scheduler] Drone %d Status Update - Loc: (%.1f, %.1f), Agent: %.1f%%\n",
                    droneID, x, y, agentRemaining);
        }
    }

    /**
     * Handles faults reported by a drone or detected via communication timeouts.
     */
    public synchronized void reportFault(int droneID, FaultType fault) {
        DroneStatus status = droneStatuses.get(droneID);
        if (status != null) {
            status.currentFault = fault;
            System.err.println("[Scheduler] FAULT DETECTED for Drone " + droneID + ": " + fault);

            // If the drone was on a mission, requeue the mission so it isn't ignored
            if (status.currentMission != null) {
                System.out.println("[Scheduler] Re-queuing event from failed Drone " + droneID);
                incompleteEvents.add(status.currentMission);
                status.currentMission = null;
                notifyAll(); // Wake up other available drones to take this event
            }
        }
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
        DroneStatus status = droneStatuses.get(droneID);
        if(status != null) {
            status.currentMission = null;
            status.agentRemaining = 100.0;
        }

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
        return completeEvents.poll();
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




}
