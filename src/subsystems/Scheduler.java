package subsystems;

import model.*;
import ui.*;
import metrics.MetricsTracker;

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
 * @author Jordan Grewal, Nolan Kisser, Celina Yang
 * @version April 5, 2026
 */
public class Scheduler implements Runnable {

    /**
     * Operating state of the scheduler
     */
    public enum State {
        WAITING,
        EVENT_QUEUED,
        DRONE_ACTIVE
    }

    /**
     * Possibly fault conditions a drone may report
     */
    public enum FaultType {
        NONE,
        COMMUNICATION_LOST,
        NOZZLE_JAMMED,
        STUCK_IN_FLIGHT
    }

    /**
     * Stores status and information for each drone
     */
    public static class DroneStatus {
        public int droneID;
        public double currentX;
        public double currentY;
        public double agentRemaining;
        public FaultType currentFault;
        public FireEvent currentMission;
        public long expectedArrivalTime = 0; // Timer threshold for STUCK_IN_FLIGHT detection

        public InetAddress address;
        public int port;

        public boolean waitingForEvent;

        /**
         * Constructs a new DroneStatus with default values
         * @param id unique ID of the drone
         */
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

    // metrics
    private final MetricsTracker metrics = new MetricsTracker();

    /**
     * Constructs a Scheduler with provided zone CSV file path
     * @param zoneFilePath path to CSV file containing zones
     */
    public Scheduler(String zoneFilePath) {
        this(zoneFilePath, null);
    }

    /**
     * Constructs a Scheduler with provided zone CSV file path and monitor for UI updates
     * @param zoneFilePath path for CSV file containing zones
     * @param monitor monitor for simulation
     */
    public Scheduler(String zoneFilePath, DroneSwarmMonitor monitor) {
        this.monitor = monitor;
        loadZonesCSV(zoneFilePath);
    }

    /**
     * Transitions the scheduler to a new state and updates the monitor
     * @param newState the new state to enter
     */
    private void transitionTo(State newState) {
        this.currentState = newState;
        updateMonitorCounts();
        System.out.println("[Scheduler] Transitioned to state: " + newState);
    }


    /**
     * Starts the scheduler as separate process
     * @param args command line arguments
     */
    public static void main(String[] args) {
        String zonesFilePath = "Final_zone_file_w26.csv";
        DroneSwarmMonitor monitor = new DroneSwarmMonitor();
        Scheduler scheduler = new Scheduler(zonesFilePath, monitor);
        scheduler.run();
    }

    /**
     * Starts the UDP server used to receive messages from drones and fire incident subsystem
     */
    public void startUDPServer() {
        try {
            socket = new DatagramSocket(schedulerPort);
            if (monitor != null){
                monitor.addLog("Scheduler", "UDP Server listening on port " + schedulerPort);
            }
            System.out.println("UDP Server listening on port " +  schedulerPort);

            new Thread(() -> {
                while(udpRunning) {
                    long now = System.currentTimeMillis();
                    synchronized(this) {
                        for (DroneStatus status : droneStatuses.values()) {
                            if (status.currentMission != null && status.expectedArrivalTime > 0 && now > status.expectedArrivalTime) {
                                System.err.println("[" + java.time.LocalTime.now() + "] [Scheduler] TIMER EXPIRED! Drone " + status.droneID + " hasn't arrived. Assuming STUCK_IN_FLIGHT.");
                                reportFault(status.droneID, FaultType.STUCK_IN_FLIGHT);
                                status.expectedArrivalTime = 0; // stop timer
                                if (monitor != null) monitor.updateDroneStatus(status.droneID, "FAULT: STUCK", "N/A", "N/A", status.agentRemaining, status.currentFault.toString(), status.currentX, status.currentY);
                            }
                        }
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException e) {}
                }
            }).start();

            while(udpRunning) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String message = new String(packet.getData(), 0, packet.getLength());
                if (monitor != null){
                    monitor.addLog("Scheduler", "Received: " + message);
                }

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

    /**
     * Handles incoming UDP message and routes it to the appropriate scheduler behaviour based on message type
     * @param message the received message contents
     * @param address the ip address
     * @param port    the UDP port
     */
    private synchronized void handleUDPMessage(String message, InetAddress address, int port) {
        try {
            String[] messageParts = message.split(",");
            switch (messageParts[0].trim()) {
                case "REGISTER_DRONE":
                    int droneID = Integer.parseInt(messageParts[1].trim());
                    registerDrone(droneID, address, port);
                    sendUDPMessage("REGISTERED_DRONE," + droneID, address, port);
                    break;
                case "FIRE_DETECTED":
                    try {
                        String fireTime =  messageParts[1].trim();
                        int fireZoneID = Integer.parseInt(messageParts[2].trim());
                        FireEvent.Severity fireSeverity = FireEvent.Severity.valueOf(messageParts[3].trim());
                        FireEvent.FaultType fault = messageParts.length > 4 ? FireEvent.FaultType.valueOf(messageParts[4].trim()) : FireEvent.FaultType.NONE;

                        FireEvent newEvent = new FireEvent(fireTime, fireZoneID, FireEvent.Type.FIRE_DETECTED, fireSeverity, fault);
                        newFireEvent(newEvent);
                    } catch (Exception e) {
                        System.err.println("[" + java.time.LocalTime.now() + "] [Scheduler] Packet Error: Dropped corrupted or malformed message.");
                        e.printStackTrace(); // Show us exactly why it failed!
                    }
                    break;
                case "ALL_EVENTS_DONE":
                    System.out.println("[Scheduler] All events done");
                    updateAllEventsDone();
                    System.out.println("[Scheduler] FireIncidentSubsystem reported all events done");
                    break;
                case "STATUS_UPDATE":
                    int statusDroneID = Integer.parseInt(messageParts[1].trim());
                    String statusDroneState =  messageParts[2].trim();
                    double statusX = Double.parseDouble(messageParts[3].trim());
                    double statusY = Double.parseDouble(messageParts[4].trim());
                    double statusAgent = Double.parseDouble(messageParts[5].trim());

                    updateDroneStatus(statusDroneID, statusX, statusY, statusAgent);

                    // track movement metrics
                    metrics.recordDroneLocation(statusDroneID, statusX, statusY);

                    // track time spent in states
                    metrics.recordDroneStateChange(statusDroneID, statusDroneState);

                    DroneStatus statusPtr = droneStatuses.get(statusDroneID);
                    if (monitor != null && statusPtr != null) {
                        String zoneStr = statusPtr.currentMission != null ? String.valueOf(statusPtr.currentMission.getZoneID()) : "N/A";
                        String sevStr = statusPtr.currentMission != null ? statusPtr.currentMission.getSeverity().toString() : "N/A";
                        String faultStr = statusPtr.currentFault != null ? statusPtr.currentFault.toString() : "NONE";

                        monitor.updateDroneStatus(statusDroneID, statusDroneState, zoneStr, sevStr, statusAgent, faultStr, statusX, statusY);
                    }
                    if (monitor != null && statusDroneState.equals("IDLE")) {
                        String faultStr = statusPtr != null && statusPtr.currentFault != null ? statusPtr.currentFault.toString() : "NONE";

                        monitor.updateDroneStatus(statusDroneID, statusDroneState, "N/A", "N/A", statusAgent, faultStr, statusX, statusY);
                    }
                    break;
                case "DRONE_ARRIVE_TO_ZONE":
                    droneID = Integer.parseInt(messageParts[1].trim());

                    if (droneStatuses.containsKey(droneID)) {
                        droneStatuses.get(droneID).expectedArrivalTime = 0;
                    }

                    String arriveTime = messageParts[2].trim();
                    int arriveZoneID = Integer.parseInt(messageParts[3].trim());
                    FireEvent.Severity arriveSeverity = FireEvent.Severity.valueOf(messageParts[4].trim());

                    FireEvent arrivedEvent = new FireEvent(
                            arriveTime, arriveZoneID, FireEvent.Type.FIRE_DETECTED, arriveSeverity, FireEvent.FaultType.NONE
                    );
                    droneArrivedAtZone(droneID, arrivedEvent);
                    break;
                case "DRONE_RETURN_TO_BASE":
                    droneID = Integer.parseInt(messageParts[1].trim());
                    boolean finished = droneReturnToBase(droneID);

                    if (finished) {
                        sendUDPMessage("ALL_EVENTS_COMPLETE,", address, port);
                    } else {
                        sendUDPMessage("RETURN_CONFIRMED,", address, port);
                    }
                    break;
                case "DRONE_READY":
                    droneID = Integer.parseInt(messageParts[1].trim());
                    DroneStatus readyStatus = droneStatuses.get(droneID);
                    if (readyStatus != null) {
                        readyStatus.address = address;
                        readyStatus.port = port;
                    }

                    if (!incompleteEvents.isEmpty()) {
                        FireEvent event = incompleteEvents.poll();
                        if (readyStatus != null) {
                            readyStatus.currentMission = event;
                            readyStatus.waitingForEvent = false;

                            Zone z = zones.get(event.getZoneID());
                            double distance = Math.sqrt(Math.pow(z.getCenterX() - readyStatus.currentX, 2) + Math.pow(z.getCenterY() - readyStatus.currentY, 2));
                            long expectedTravelMillis = (long) ((distance / 10.0) * 10);

                            readyStatus.expectedArrivalTime = System.currentTimeMillis() + expectedTravelMillis + 3000;
                        }
                        activeDroneCount++;

                        // metrics tracker
                        metrics.recordDroneAssignment(event.getZoneID(), droneID);
                        metrics.recordDroneStateChange(droneID, "EN_ROUTE");

                        String newMessage = "ASSIGN_EVENT," + event.getTime() + "," + event.getZoneID() + "," + event.getSeverity() + "," + event.getFaultType();
                        sendUDPMessage(newMessage, address, port);
                        System.out.println("[Scheduler] Assigned event to drone " + droneID);
                        notifyAll();
                    } else if (!allEventsDone) {
                        if (readyStatus != null) readyStatus.waitingForEvent = true;
                    } else {
                        sendUDPMessage("ALL_EVENTS_COMPLETE,", address, port);
                    }
                    break;
                case "DRONE_COMPLETE_EVENT":
                    droneID = Integer.parseInt(messageParts[1].trim());
                    String completeTime = messageParts[2].trim();
                    int completeZoneID = Integer.parseInt(messageParts[3].trim());
                    FireEvent.Severity completeSeverity = FireEvent.Severity.valueOf(messageParts[4].trim());

                    FireEvent completedEvent = new FireEvent(
                            completeTime, completeZoneID, FireEvent.Type.FIRE_DETECTED, completeSeverity, FireEvent.FaultType.NONE
                    );
                    completeFireEvent(completedEvent);
                    break;
                case "HARD_FAULT":
                    droneID = Integer.parseInt(messageParts[1].trim());
                    FaultType fType = FaultType.valueOf(messageParts[2].trim());
                    reportFault(droneID, fType);
                    break;
                case "REQUEST_NEXT_MISSION":
                    droneID = Integer.parseInt(messageParts[1].trim());
                    double agentRemaining = Double.parseDouble(messageParts[2].trim());
                    
                    if (!incompleteEvents.isEmpty()) {
                        DroneStatus requestingDrone = droneStatuses.get(droneID);
                        FireEvent nextEvent = incompleteEvents.peek();
                        Zone nextZone = zones.get(nextEvent.getZoneID());
                        
                        if (requestingDrone != null && nextZone != null) {
                            // Use Drone helper methods for calculations
                            Drone tempDrone = new Drone(droneID);
                            tempDrone.setLocation(requestingDrone.currentX, requestingDrone.currentY);
                            
                            double distToZone = tempDrone.distanceTo(nextZone.getCenterX(), nextZone.getCenterY());
                            
                            // Get required volume for the next mission
                            double requiredVolume = tempDrone.getRequiredVolume(nextEvent);
                            
                            // Add buffer (25%) to ensure safe return to base
                            double minimumAgentForContinuation = requiredVolume * 1.25;
                            
                            // Check if drone has enough agent to handle next mission and return to base
                            if (agentRemaining >= minimumAgentForContinuation) {
                                // Attempt to assign the next mission to this drone
                                FireEvent assignedEvent = incompleteEvents.poll();
                                
                                // Verify that another drone hasn't taken this mission (safety check)
                                if (assignedEvent != null && assignedEvent.getZoneID() == nextEvent.getZoneID()) {
                                    requestingDrone.currentMission = assignedEvent;
                                    requestingDrone.waitingForEvent = false;
                                    activeDroneCount++;
                                    
                                    // Set timeout timer for new assignment
                                    long expectedTravelMillis = (long) ((distToZone / Drone.CRUISE_SPEED_LOADED) * 10);
                                    requestingDrone.expectedArrivalTime = System.currentTimeMillis() + expectedTravelMillis + 3000;
                                    
                                    String assignMessage = "ASSIGN_EVENT," + assignedEvent.getTime() + "," + 
                                                          assignedEvent.getZoneID() + "," + assignedEvent.getSeverity() + "," + 
                                                          assignedEvent.getFaultType();
                                    sendUDPMessage(assignMessage, address, port);
                                    System.out.println("[Scheduler] Drone " + droneID + " approved to continue to next zone (agent: " + agentRemaining + "%, required: " + minimumAgentForContinuation + "%)");
                                } else {
                                    // Mission was taken by another drone - return to base
                                    sendUDPMessage("RETURN_TO_BASE,", address, port);
                                    System.out.println("[Scheduler] Drone " + droneID + " requested mission but it was taken by another drone. Sending to base.");
                                }
                            } else {
                                // Insufficient agent - drone must return to base
                                sendUDPMessage("RETURN_TO_BASE,", address, port);
                                System.out.println("[Scheduler] Drone " + droneID + " has insufficient agent (" + agentRemaining + "%) for next mission. Sending to base.");
                            }
                        } else {
                            sendUDPMessage("RETURN_TO_BASE,", address, port);
                        }
                    } else {
                        // No more events in queue - return to base
                        sendUDPMessage("RETURN_TO_BASE,", address, port);
                    }
                    break;
                case "REQUEUE_EVENT":
                    droneID = Integer.parseInt(messageParts[1].trim());
                    String requeueTime = messageParts[2].trim();
                    int requeueZoneID = Integer.parseInt(messageParts[3].trim());
                    FireEvent.Severity requeueSeverity = FireEvent.Severity.valueOf(messageParts[4].trim());

                    System.out.println("[Scheduler] Drone " + droneID + " ran out of agent. Re-queuing Zone " + requeueZoneID);

                    // Create a clean event strictly enforcing FaultType.NONE
                    FireEvent requeuedEvent = new FireEvent(
                            requeueTime,
                            requeueZoneID,
                            FireEvent.Type.FIRE_DETECTED,
                            requeueSeverity,
                            FireEvent.FaultType.NONE
                    );

                    incompleteEvents.add(requeuedEvent);
                    notifyAll();
                    assignPendingEvents(); // Instantly hand off to an idle drone
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Sends a UDP message to the specified address and port
     * @param message the message to send
     * @param address the ip address
     * @param port    the UDP port
     */
    private void sendUDPMessage(String message, InetAddress address, int port) {
        try {
            if (socket == null || socket.isClosed()) return;

            byte[] data =  message.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
            socket.send(packet);

            if (monitor != null){
                monitor.addLog("Scheduler", "Sent: " + message);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper method to assign any pending events in the queue to drones
     * that are currently stuck waiting for an assignment.
     */
    private synchronized void assignPendingEvents() {
        for (DroneStatus status : droneStatuses.values()) {
            if (status.waitingForEvent && status.currentMission == null && status.address != null) {

                FireEvent event = incompleteEvents.poll();
                if (event == null) {
                    break; // No more events in the queue
                }

                status.currentMission = event;
                status.waitingForEvent = false;
                activeDroneCount++;

                // Set the timeout timer for this newly assigned drone
                Zone z = zones.get(event.getZoneID());
                double distance = Math.sqrt(Math.pow(z.getCenterX() - status.currentX, 2) + Math.pow(z.getCenterY() - status.currentY, 2));
                long expectedTravelMillis = (long) ((distance / 10.0) * 10);
                status.expectedArrivalTime = System.currentTimeMillis() + expectedTravelMillis + 3000;

                String message = "ASSIGN_EVENT," +
                        event.getTime() + "," +
                        event.getZoneID() + "," +
                        event.getSeverity() + "," +
                        event.getFaultType();

                sendUDPMessage(message, status.address, status.port);
                System.out.println("[Scheduler] Assigned RE-QUEUED event to waiting drone " + status.droneID);
            }
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
                                for (DroneStatus status : droneStatuses.values()) {
                                    if (status.address != null) {
                                        sendUDPMessage("ALL_EVENTS_COMPLETE,", status.address, status.port);
                                    }
                                }
                                try { Thread.sleep(200); } catch (InterruptedException e) {}

                                // final metrics
                                metrics.finalizeMetrics();
                                metrics.markSimulationEnd();
                                metrics.printSummary();

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

    /**
     * Registers a drone with the scheduler or updates its network information if known
     * @param droneID the id of the drone
     * @param address the ip address of the drone
     * @param port    the UDP port used by the drone
     */
    public synchronized void registerDrone(int droneID, InetAddress address, int port) {
        droneStatuses.putIfAbsent(droneID, new DroneStatus(droneID));

        DroneStatus status = droneStatuses.get(droneID);
        status.address = address;
        status.port = port;

        metrics.registerDrone(droneID);

    }

    /**
     * Adds a new fire event from the CSV file to the priority queue
     * @param fireEvent event to add
     */
    public synchronized void newFireEvent(FireEvent fireEvent) {
        // metrics tracker for fire detected
        metrics.recordFireStart(fireEvent.getZoneID());

        if (monitor != null) {
            monitor.addActiveFire(fireEvent.getZoneID());
        }
        incompleteEvents.add(fireEvent);
        updateMonitorCounts();
        notifyAll();
        assignPendingEvents();
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

    /**
     * Records a fault for a drone and re-queues its mission if needed
     * @param droneID the id of the drone that faulted
     * @param fault   the fault types that occurred
     */
    public synchronized void reportFault(int droneID, FaultType fault) {
        DroneStatus status = droneStatuses.get(droneID);
        if (status != null) {
            status.currentFault = fault;
            System.err.println("[Scheduler] FAULT DETECTED for Drone " + droneID + ": " + fault);

            if (monitor != null) {
                monitor.updateDroneStatus(droneID, "FAULT: " + fault, "N/A", "N/A", status.agentRemaining, fault.toString(), status.currentX, status.currentY);
                monitor.addFaultLog("Scheduler", droneID, fault.toString(), "Fault detected and drone marked offline");
                monitor.setDroneOffline(droneID, status.currentMission.getZoneID());
            }

            // If the drone was on a mission, requeue the mission so it isn't ignored
            if (status.currentMission != null) {
                System.out.println("[Scheduler] Re-queuing event from failed Drone " + droneID);

                FireEvent cleanEvent = new FireEvent(
                        status.currentMission.getTime(),
                        status.currentMission.getZoneID(),
                        status.currentMission.getType(),
                        status.currentMission.getSeverity(),
                        FireEvent.FaultType.NONE
                );
                incompleteEvents.add(cleanEvent);
                status.currentMission = null;
                activeDroneCount--;
                notifyAll();
                assignPendingEvents();
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

        // drone return to idle tracker
        metrics.recordDroneStateChange(droneID, "IDLE");

        if (status != null) {
            status.currentMission = null;
            status.agentRemaining = 100.0;
            status.waitingForEvent = false;
            status.currentFault = FaultType.NONE;
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

        // fire extinguished metrics tracker
        metrics.recordFireExtinguished(fireEvent.getZoneID());

        if (monitor != null) {
            monitor.removeActiveFire(fireEvent.getZoneID());
            monitor.addExtinguishedFire(fireEvent.getZoneID());
        }
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

    public void notifyDroneTransition(Drone.DroneState state) {
        if (monitor != null) {
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

    /**
     * Forcefully shuts down the UDP server and closes the socket.
     * Crucial for freeing up the port between JUnit tests.
     */
    public void shutdown() {
        this.udpRunning = false;
        this.allEventsDone = true;
        if (this.socket != null && !this.socket.isClosed()) {
            this.socket.close();
        }
        synchronized (this) {
            notifyAll(); // Wake up any threads stuck waiting for events
        }
    }
}