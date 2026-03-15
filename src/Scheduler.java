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

        public InetAddress address;
        public int port;

        public boolean waitingForEvent;

        public DroneStatus(int id) {
            this.droneID = id;
            this.currentX = 0.0;
            this.currentY = 0.0;
            this.agentRemaining = 100.0;
            this.currentFault = FaultType.NONE;
            this.currentMission = null;

            this.address = null;
            this.port = 0;

            this.waitingForEvent = false;
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

    // UDP
    public int schedulerPort = 6000;
    private DatagramSocket socket;
    private boolean udpRunning = true;


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


    public static void main(String[] args) {
        String zonesFilePath = "zone_file.csv";
        DroneSwarmMonitor monitor = new DroneSwarmMonitor();
        Scheduler scheduler = new Scheduler(zonesFilePath, monitor);
        scheduler.run();
    }


    public void startUDPServer() {
        try {
            socket = new DatagramSocket(schedulerPort);
            monitor.addLog("Scheduler", "UDP Server listening on port " + schedulerPort);
            System.out.println("UDP Server listening on port " +  schedulerPort);
            while(udpRunning) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                monitor.addLog("Scheduler", "Received: " + message);

                handleUDPMessage(message, packet.getAddress(), packet.getPort());


            }

        } catch (SocketException e) {
            if(udpRunning) {
                e.printStackTrace();
            } else {
                System.out.println("[Scheduler] UDP Server stopped");
            }
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
                    System.out.println("[Scheduler] All events done");
                    updateAllEventsDone();
                    System.out.println("[Scheduler] FireIncidentSubsystem reported all events done");
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
                    droneID = Integer.parseInt(messageParts[1]);

                    String arriveTime = messageParts[2];
                    int arriveZoneID = Integer.parseInt(messageParts[3]);
                    FireEvent.Severity arriveSeverity = FireEvent.Severity.valueOf(messageParts[4]);

                    FireEvent arrivedEvent = new FireEvent(
                            arriveTime,
                            arriveZoneID,
                            FireEvent.Type.FIRE_DETECTED,
                            arriveSeverity
                    );

                    droneArrivedAtZone(droneID, arrivedEvent);
                    break;
                case "DRONE_RETURN_TO_BASE":
                    // DRONE_RETURN_TO_BASE,droneID
                    droneID = Integer.parseInt(messageParts[1]);
                    boolean finished = droneReturnToBase(droneID);

                    if (finished) {
                        sendUDPMessage("ALL_EVENTS_COMPLETE,", address, port);
                    } else {
                        sendUDPMessage("RETURN_CONFIRMED,", address, port);
                    }
                    break;
                case "DRONE_READY":
                    // DRONE_READY,droneID
                    droneID = Integer.parseInt(messageParts[1]);

                    System.out.println("DRONE READY ---------> " + allEventsDone );

                    DroneStatus readyStatus = droneStatuses.get(droneID);
                    if (readyStatus != null) {
                        readyStatus.address = address;
                        readyStatus.port = port;
                    }

                    // FIX: Check if there are events in the queue FIRST
                    if (!incompleteEvents.isEmpty()) {
                        FireEvent event = incompleteEvents.poll();
                        if (readyStatus != null) {
                            readyStatus.currentMission = event;
                            readyStatus.waitingForEvent = false;
                        }
                        activeDroneCount++;

                        String newMessage = "ASSIGN_EVENT," +
                                event.getTime() + "," +
                                event.getZoneID() + "," +
                                event.getSeverity();

                        sendUDPMessage(newMessage, address, port);
                        System.out.println("[Scheduler] Assigned event to drone " + droneID);
                        notifyAll(); // Wake up the scheduler state machine thread

                    } else if (!allEventsDone) {
                        // Queue is empty, but producer is still working
                        if (readyStatus != null) {
                            readyStatus.waitingForEvent = true;
                        }
                        System.out.println("[Scheduler] Drone " + droneID + " is waiting for an event.");

                    } else {
                        // Queue is empty AND producer is done
                        sendUDPMessage("ALL_EVENTS_COMPLETE,", address, port);
                    }
                    break;
                case "DRONE_COMPLETE_EVENT":
                    // DRONE_COMPLETE_EVENT,droneID,time,zoneID,severity
                    droneID = Integer.parseInt(messageParts[1]);

                    String completeTime = messageParts[2];
                    int completeZoneID = Integer.parseInt(messageParts[3]);
                    FireEvent.Severity completeSeverity = FireEvent.Severity.valueOf(messageParts[4]);

                    FireEvent completedEvent = new FireEvent(
                            completeTime,
                            completeZoneID,
                            FireEvent.Type.FIRE_DETECTED,
                            completeSeverity
                    );

                    completeFireEvent(completedEvent);
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



    /**
     * Active state machine loop managing the Scheduler's states.
     */
    @Override
    public void run() {
        boolean running = true;

        // Start UDP server in separate thread
        new Thread(this::startUDPServer).start();

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
                                udpRunning = false;
                                if (socket != null && !socket.isClosed()) {
                                    socket.close();
                                }
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
        updateMonitorCounts();
        notifyAll();

        for (DroneStatus status : droneStatuses.values()) {
            if (status.waitingForEvent && status.currentMission == null
                    && status.address != null) {

                FireEvent event = incompleteEvents.poll();
                if (event == null) {
                    return;
                }

                status.currentMission = event;
                status.waitingForEvent = false;
                activeDroneCount++;

                String message = "ASSIGN_EVENT," +
                        event.getTime() + "," +
                        event.getZoneID() + "," +
                        event.getSeverity();

                sendUDPMessage(message, status.address, status.port);
                System.out.println("[Scheduler] Assigned queued event to waiting drone " + status.droneID);
                break;
            }
        }
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

        if(incompleteEvents.isEmpty()) {
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

    public synchronized boolean droneReturnToBase(int droneID){
        System.out.println("[Scheduler] Notification: Drone " + droneID + " returned to base.");
        DroneStatus status = droneStatuses.get(droneID);
        if (status != null) {
            status.currentMission = null;
            status.agentRemaining = 100.0;
            status.waitingForEvent = false;
        }

        if (activeDroneCount > 0) {
            activeDroneCount--;
        }

        notifyAll();

        return allEventsDone && incompleteEvents.isEmpty() && activeDroneCount == 0;
        // activeDroneCount--; // Drone is no longer actively working on a mission
        // notifyAll(); // Wake up the scheduler state machine to evaluate transitions
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