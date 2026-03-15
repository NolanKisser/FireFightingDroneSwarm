import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Scheduler class communicates and synchronizes the FireIncidentSubsystem and the DroneSubsystem.
 * Updated to use an active switch state machine.
 * * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 */
public class Scheduler implements Runnable {

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
        public double agentRemaining;
        public FaultType currentFault;
        public FireEvent currentMission;

        public DroneStatus(int id) {
            this.droneID = id;
            this.currentX = 0.0;
            this.currentY = 0.0;
            this.agentRemaining = 100.0;
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
    private int activeDroneCount = 0; // Tracks how many drones are currently active

    private final Map<Integer, Zone> zones = new HashMap<>();
    private final DroneSwarmMonitor monitor;


    public Scheduler(String zoneFilePath) {
        this(zoneFilePath, null);
    }

    public Scheduler(String zoneFilePath, DroneSwarmMonitor monitor) {
        this.monitor = monitor;
        loadZonesCSV(zoneFilePath);
    }

    private void transitionTo(State newState) {
        this.currentState = newState;
        updateMonitorCounts();
        System.out.println("[Scheduler] Transitioned to state: " + newState);
    }

    /**
     * Active state machine loop managing the Scheduler's states.
     */
    @Override
    public void run() {
        boolean running = true;
        while (running) {
            synchronized (this) {
                try {
                    switch (currentState) {
                        case WAITING:
                            // Wait until an event is added or the simulation is completely done
                            while (incompleteEvents.isEmpty() && !allEventsDone) {
                                wait();
                            }

                            if (allEventsDone && incompleteEvents.isEmpty() && activeDroneCount == 0) {
                                running = false; // Simulation is finished
                            } else if (!incompleteEvents.isEmpty()) {
                                transitionTo(State.EVENT_QUEUED);
                            }
                            break;

                        case EVENT_QUEUED:
                            // We have events in the queue. Notify drones so they can pick them up.
                            notifyAll();

                            // Wait until a drone takes an event or the queue empties
                            while (!incompleteEvents.isEmpty() && activeDroneCount == 0) {
                                wait();
                            }

                            if (activeDroneCount > 0) {
                                transitionTo(State.DRONE_ACTIVE);
                            } else if (incompleteEvents.isEmpty()) {
                                transitionTo(State.WAITING);
                            }
                            break;

                        case DRONE_ACTIVE:
                            // Wait while drones are actively working on events
                            while (activeDroneCount > 0) {
                                wait();
                            }

                            // Once all active drones return, check if more events are pending
                            if (!incompleteEvents.isEmpty()) {
                                transitionTo(State.EVENT_QUEUED);
                            } else {
                                transitionTo(State.WAITING);
                            }
                            break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    running = false;
                }
            }
        }
    }

    public synchronized void registerDrone(int droneID) {
        droneStatuses.putIfAbsent(droneID, new DroneStatus(droneID));
    }

    /**
     * Adds a new fire event from the CSV file to the priority queue
     * @param fireEvent event to add
     */
    public synchronized void newFireEvent(FireEvent fireEvent) {
        incompleteEvents.add(fireEvent);
        updateMonitorCounts();
        notifyAll(); // Wake up the scheduler state machine
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

        FireEvent nextEvent = incompleteEvents.poll();
        activeDroneCount++; // A drone has picked up an event
        notifyAll(); // Wake up the scheduler state machine to process transition
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
                activeDroneCount--;
                notifyAll();
            }
        }
    }

    /**
     * Notification from DroneSubsystem that it has reached the target zone.
     * @param droneID ID of the drone
     * @param fireEvent The event being serviced
     */
    public synchronized void droneArrivedAtZone(int droneID, FireEvent fireEvent) {
        System.out.println("[Scheduler] Notification: Drone " + droneID + " arrived at Zone " + fireEvent.getZoneID());
    }

    public synchronized void droneReturnToBase(int droneID){
        System.out.println("[Scheduler] Notification: Drone " + droneID + " returned to base.");
        DroneStatus status = droneStatuses.get(droneID);
        if(status != null) {
            status.currentMission = null;
            status.agentRemaining = 100.0;
        }
        activeDroneCount--; // Drone is no longer actively working on a mission
        notifyAll(); // Wake up the scheduler state machine to evaluate transitions
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
        updateMonitorCounts();
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

    public synchronized int getActiveFireCount() {
        return incompleteEvents.size();
    }

    public void notifyDroneTransition(DroneStates state) {
        if (monitor != null) {
            monitor.setDroneState(state.getState());
            monitor.setActiveFires(getActiveFireCount());
        }
    }

    private void updateMonitorCounts() {
        if (monitor != null) {
            monitor.setActiveFires(getActiveFireCount());
        }
    }

    public State getCurrentState() {
        return currentState;
    }

    public static void main(String[] args) {

    }
}