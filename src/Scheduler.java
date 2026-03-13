import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
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

        public InetAddress address;
        public int port;

        public DroneStatus(int id) {
            this.droneID = id;
            this.currentX = 0.0;
            this.currentY = 0.0;
            this.agentRemaining = 100.0; // Assume 100% capacity at start
            this.currentFault = FaultType.NONE;
            this.currentMission = null;

            this.address = null;
            this.port = 0;
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
    private final DroneSwarmMonitor monitor;

    // UDP
    public int schedulerPort = 6000;
    private DatagramSocket socket;

    public static void main(String[] args) {
        String zonesFilePath = "zone_file.csv";
        DroneSwarmMonitor monitor = new DroneSwarmMonitor();
        Scheduler scheduler = new Scheduler(zonesFilePath, monitor);
        scheduler.startUDPServer();
    }


    public void startUDPServer() {
        try {
            socket = new DatagramSocket(schedulerPort);
            monitor.addLog("Scheduler", "UDP Server listening on port " + schedulerPort);
            System.out.println("UDP Server listening on port " +  schedulerPort);
            while(true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                monitor.addLog("Scheduler", "Received: " + message);

                handleUDPMessage(message, packet.getAddress(), packet.getPort());


            }

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private synchronized void handleUDPMessage(String message, InetAddress address, int port) {
        try {
            String[] messageParts = message.split(",");
            switch (messageParts[0]) {
                case "REGISTER_DRONE":
                    // REGISTER_DRONE,droneID,address,port
                    int droneID = Integer.parseInt(messageParts[1]);
                    registerDrone(droneID, address, port);
                    sendUDPMessage("REGISTERED_DRONE," + droneID, address, port);
                    break;
                case "FIRE_DETECTED":
                    // FIRE_DETECTED,time,zoneID,severity
                    String fireTime =  messageParts[1];
                    int fireZoneID = Integer.parseInt(messageParts[2]);
                    FireEvent.Severity fireSeverity = FireEvent.Severity.valueOf(messageParts[3]);

                    FireEvent newEvent = new FireEvent(fireTime, fireZoneID, FireEvent.Type.FIRE_DETECTED, fireSeverity);
                    newFireEvent(newEvent);
                    break;
                case "ALL_EVENTS_DONE":
                    // ALL_EVENTS_DONE
                    updateAllEventsDone();
                    sendUDPMessage("ALL FIRES EXTINGUISHED,", address, port);
                    break;
                case "STATUS_UPDATE":
                    // STATUS_UPDATE,droneId,state,x,y,agent
                    int statusDroneID = Integer.parseInt(messageParts[1]);
                    String statusDroneState =  messageParts[2];
                    double statusX = Double.parseDouble(messageParts[3]);
                    double statusY = Double.parseDouble(messageParts[4]);
                    double statusAgent = Double.parseDouble(messageParts[5]);

                    updateDroneStatus(statusDroneID, statusX, statusY, statusAgent);
                    break;
                case "DRONE_ARRIVE_TO_ZONE":
                    // DRONE_ARRIVED,droneID,time,zoneID,severity
                    break;
                case "DRONE_RETURN_TO_BASE":
                    // DRONE_RETURN_TO_BASE,droneID
                    break;
                case "DRONE_READY":
                    // DRONE_READY,droneID
                    droneID = Integer.parseInt(messageParts[1]);

                    if(incompleteEvents.isEmpty()) {
                        if(allEventsDone) {
                            sendUDPMessage("ALL_EVENTS_COMPLETE,", address, port);
                        } else {
                            sendUDPMessage("NO_EVENTS_AVAILABLE,", address, port);
                            Thread.sleep(2500);
                        }
                        currentState = State.WAITING;
                    } else {
                        FireEvent event = incompleteEvents.poll();

                        DroneStatus status = droneStatuses.get(droneID);
                        if(status != null) {
                            status.currentMission = event;
                        }

                        String newMessage = "ASSIGN_EVENT," + event.getTime() + "," + event.getZoneID() + "," + event.getSeverity();

                        sendUDPMessage(newMessage, address, port);
                        currentState = State.DRONE_ACTIVE;
                    }

                    break;
                case "DRONE_COMPLETE_EVENT":
                    // DRONE_COMPLETE_EVENT,droneID,time,zoneID,severity
                    droneID = Integer.parseInt(messageParts[1]);
                    break;
                case "REQUEUE_EVENT":
                    // REQUEUE_EVENT,droneID,time,zoneID,severity
                    break;




            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendUDPMessage(String message, InetAddress address, int port) {
        try {
            byte[] data =  message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);

            monitor.addLog("Scheduler", "Sent: " + message);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Scheduler(String zoneFilePath) {
        this(zoneFilePath, null);
    }

    public Scheduler(String zoneFilePath, DroneSwarmMonitor monitor) {
        this.monitor = monitor;
        loadZonesCSV(zoneFilePath);
    }


    /**
     * Registers a drone in the scheduler's tracking system.
     */
    public synchronized void registerDrone(int droneID, InetAddress address, int port) {
        droneStatuses.putIfAbsent(droneID, new DroneStatus(droneID));

        DroneStatus status = droneStatuses.get(droneID);
        status.address = address;
        status.port = port;

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
        updateMonitorCounts();
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

    public void notifyDroneTransition(DroneSubsystem.DroneState state) {
        if (monitor != null) {
            monitor.setDroneState(state.name());
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



}
