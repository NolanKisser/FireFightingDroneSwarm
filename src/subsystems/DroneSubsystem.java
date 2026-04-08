package subsystems;

import model.*;
import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.time.Instant;

/**
 * DroneSubsystem class handles the network communication and thread execution
 * for a specific Drone object.
 */
public class DroneSubsystem implements Runnable {

    private final Scheduler scheduler;
    private final Drone drone; // Uses our new data model!

    private boolean running = true;

    // Networking
    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendReceiveSocket;
    private final int SCHEDULER_PORT = 6000;
    private final String SCHEDULER_HOST = "localhost";
    
    // Timing for fault detection
    private Instant travelStartTime;
    private long expectedTravelTimeSeconds;
    
    // Hard fault tracking
    private String lastFaultType = null;


    public DroneSubsystem(Scheduler scheduler, int droneID) {
        this.scheduler = scheduler;
        this.drone = new Drone(droneID);

        try {
            sendReceiveSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        int id = 1;
        if (args.length > 0) {
            try {
                id = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid Drone ID provided. Using default ID 1.");
            }
        }

        Scheduler scheduler = new Scheduler("Final_zone_file_w26.csv");
        DroneSubsystem subsystem = new DroneSubsystem(scheduler, id);
        Thread droneThread = new Thread(subsystem);
        droneThread.start();
    }

    private String ts() { return LocalTime.now().toString(); }

    private String sendAndReceive(String message) {
        sendOnly(message);
        return receiveOnly();
    }

    private void sendOnly(String message) {
        byte[] bytes = message.getBytes();
        try {
            sendPacket = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(SCHEDULER_HOST), SCHEDULER_PORT);
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String receiveOnly() {
        receivePacket = new DatagramPacket(new byte[1024], 1024);
        try {
            sendReceiveSocket.receive(receivePacket);
        } catch (IOException e) {
            System.err.printf("[%s] [Drone %d] COMMUNICATION ERROR: Failed to receive packet.\n", ts(), drone.getId());
            e.printStackTrace();
            return ""; // Return empty string to signal error
        }
        return new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
    }

    private void moveToTargetStepByStep(double targetX, double targetY, double speed) throws InterruptedException {
        double stepDistance = 100.0;

        while (drone.getX() != targetX || drone.getY() != targetY) {

            long elapsedSeconds = Instant.now().getEpochSecond() - travelStartTime.getEpochSecond();
            
            // STUCK_IN_FLIGHT: Check if travel time exceeds expected time
            if (elapsedSeconds > expectedTravelTimeSeconds * 1.5) {
                System.err.printf("[%s] [Drone %d] FAULT: Stuck mid-flight at (%.1f, %.1f)! Travel time exceeded limit (%.0fs > %.0fs).\n",
                        ts(), drone.getId(), drone.getX(), drone.getY(), (double)elapsedSeconds, expectedTravelTimeSeconds);
                drone.setState(Drone.DroneState.FAULTED);
                return; // Abort movement
            }

            double diffX = targetX - drone.getX();
            double diffY = targetY - drone.getY();

            if (Math.abs(diffX) > 0) {
                double step = Math.min(stepDistance, Math.abs(diffX));
                drone.setLocation(drone.getX() + (Math.signum(diffX) * step), drone.getY());
                Thread.sleep((long) ((step / speed) * 10));
            } else if (Math.abs(diffY) > 0) {
                double step = Math.min(stepDistance, Math.abs(diffY));
                drone.setLocation(drone.getX(), drone.getY() + (Math.signum(diffY) * step));
                Thread.sleep((long) ((step / speed) * 10));
            }

            sendOnly("STATUS_UPDATE," + drone.getId() + "," + drone.getState() + "," +
                    drone.getX() + "," + drone.getY() + "," + drone.getAgentLevel());
        }
    }

    private synchronized void handleEvent() throws InterruptedException {
        switch (drone.getState()) {
            case IDLE:
                String message = sendAndReceive("DRONE_READY," + drone.getId());
                String[] parts = message.split(",");

                if (parts[0].trim().equals("ASSIGN_EVENT")) {
                    String time = parts[1].trim();
                    int zoneID = Integer.parseInt(parts[2].trim());
                    FireEvent.Severity severity = FireEvent.Severity.valueOf(parts[3].trim());
                    FireEvent.FaultType faultType = parts.length > 4 ? FireEvent.FaultType.valueOf(parts[4].trim()) : FireEvent.FaultType.NONE;

                    FireEvent event = new FireEvent(time, zoneID, FireEvent.Type.FIRE_DETECTED, severity, faultType);
                    drone.setCurrentMission(event);

                    System.out.printf("[%s] [Drone %d] Dispatched to Zone %d\n", ts(), drone.getId(), zoneID);

                    if (drone.getAgentLevel() < Drone.LOW_VOLUME) {
                        System.out.printf("[%s] [Drone %d] Insufficient agent. Must refill.\n", ts(), drone.getId());
                        drone.setCurrentMission(null);
                        drone.setState(Drone.DroneState.RETURNING);
                    } else {
                        drone.setState(Drone.DroneState.EN_ROUTE);
                    }
                } else if(parts[0].equals("ALL_EVENTS_COMPLETE")) {
                    running = false;
                }
                break;

            case EN_ROUTE:
                FireEvent currentEvent = drone.getCurrentMission();
                Zone target = scheduler.getZones().get(currentEvent.getZoneID());
                double travelTime = drone.computeTravelTime(target.getCenterX(), target.getCenterY(), true);

                System.out.printf("[%s] [Drone %d] En route to Zone %d. Expected travel time: %.1fs\n",
                        ts(), drone.getId(), currentEvent.getZoneID(), travelTime);

                // START TIMER for fault detection
                travelStartTime = Instant.now();
                expectedTravelTimeSeconds = (long) Math.ceil(travelTime);

                moveToTargetStepByStep(target.getCenterX(), target.getCenterY(), Drone.CRUISE_SPEED_LOADED);

                // Check if the drone died during transit.
                if (drone.getState() == Drone.DroneState.FAULTED) {
                    String faultType = currentEvent.getFaultType() == FireEvent.FaultType.STUCK_IN_FLIGHT ? "STUCK_IN_FLIGHT" : "UNKNOWN";
                    reportFault(faultType, currentEvent);
                    return; // Return to let handleEvent process FAULTED state
                }

                // CHECK FOR STUCK_IN_FLIGHT FAULT
                if (currentEvent.getFaultType() == FireEvent.FaultType.STUCK_IN_FLIGHT) {
                    System.err.printf("[%s] [Drone %d] FAULT DETECTED: STUCK_IN_FLIGHT during travel!\n", ts(), drone.getId());
                    reportFault("STUCK_IN_FLIGHT", currentEvent);
                    return;
                }

                // CHECK FOR COMMUNICATION_LOST FAULT
                if (currentEvent.getFaultType() == FireEvent.FaultType.COMMUNICATION_LOST) {
                    System.err.printf("[%s] [Drone %d] FAULT DETECTED: COMMUNICATION_LOST during travel!\n", ts(), drone.getId());
                    reportFault("COMMUNICATION_LOST", currentEvent);
                    return;
                }

                sendOnly("DRONE_ARRIVE_TO_ZONE," + drone.getId() + "," + currentEvent.getTime() + "," +
                        currentEvent.getZoneID() + "," + currentEvent.getSeverity());
                drone.setState(Drone.DroneState.EXTINGUISHING);
                break;

            case EN_ROUTE_NEXT_MISSION:
                FireEvent missionEvent = drone.getCurrentMission();
                Zone missionTarget = scheduler.getZones().get(missionEvent.getZoneID());
                double missionTravelTime = drone.computeTravelTime(missionTarget.getCenterX(), missionTarget.getCenterY(), true);

                System.out.printf("[%s] [Drone %d] Continuing en route to next Zone %d. Expected travel time: %.1fs\n",
                        ts(), drone.getId(), missionEvent.getZoneID(), missionTravelTime);

                // START TIMER for fault detection
                travelStartTime = Instant.now();
                expectedTravelTimeSeconds = (long) Math.ceil(missionTravelTime);

                moveToTargetStepByStep(missionTarget.getCenterX(), missionTarget.getCenterY(), Drone.CRUISE_SPEED_LOADED);

                // Check if the drone died during transit.
                if (drone.getState() == Drone.DroneState.FAULTED) {
                    String faultType = missionEvent.getFaultType() == FireEvent.FaultType.STUCK_IN_FLIGHT ? "STUCK_IN_FLIGHT" : "UNKNOWN";
                    reportFault(faultType, missionEvent);
                    return; // Return to let handleEvent process FAULTED state
                }

                // CHECK FOR STUCK_IN_FLIGHT FAULT
                if (missionEvent.getFaultType() == FireEvent.FaultType.STUCK_IN_FLIGHT) {
                    System.err.printf("[%s] [Drone %d] FAULT DETECTED: STUCK_IN_FLIGHT during travel!\n", ts(), drone.getId());
                    reportFault("STUCK_IN_FLIGHT", missionEvent);
                    return;
                }

                // CHECK FOR COMMUNICATION_LOST FAULT
                if (missionEvent.getFaultType() == FireEvent.FaultType.COMMUNICATION_LOST) {
                    System.err.printf("[%s] [Drone %d] FAULT DETECTED: COMMUNICATION_LOST during travel!\n", ts(), drone.getId());
                    reportFault("COMMUNICATION_LOST", missionEvent);
                    return;
                }

                sendOnly("DRONE_ARRIVE_TO_ZONE," + drone.getId() + "," + missionEvent.getTime() + "," +
                        missionEvent.getZoneID() + "," + missionEvent.getSeverity());
                drone.setState(Drone.DroneState.EXTINGUISHING);
                break;

            case EXTINGUISHING:
                FireEvent ev = drone.getCurrentMission();
                // NOZZLE_JAMMED FAULT
                if (ev.getFaultType() == FireEvent.FaultType.NOZZLE_JAMMED) {
                    System.err.printf("[%s] [Drone %d] HARD FAULT: Nozzle doors jammed! Cannot proceed with fire suppression at Zone %d.\n", 
                            ts(), drone.getId(), ev.getZoneID());
                    reportFault("NOZZLE_JAMMED", ev);
                    return;
                }

                double requiredVolume = drone.getRequiredVolume(ev);
                double volumeToDrop = Math.min(requiredVolume, drone.getAgentLevel());
                double dropTime = volumeToDrop / Drone.DROP_RATE;

                System.out.printf("[%s] [Drone %d] Opening nozzle doors... (%.1fs)\n", ts(), drone.getId(), Drone.NOZZLE_DOORS);
                Thread.sleep((long) (Drone.NOZZLE_DOORS * 10));

                System.out.printf("[%s] [Drone %d] Dropping %.1f%% agent on Zone %d... (%.1fs)\n",
                        ts(), drone.getId(), volumeToDrop, ev.getZoneID(), dropTime);
                Thread.sleep((long) (dropTime * 10));

                drone.consumeAgent(volumeToDrop);
                sendOnly("STATUS_UPDATE," + drone.getId() + "," + drone.getState() + "," +
                        drone.getX() + "," + drone.getY() + "," + drone.getAgentLevel());

                System.out.printf("[%s] [Drone %d] Closing nozzle doors... (%.1fs)\n", ts(), drone.getId(), Drone.NOZZLE_DOORS);
                Thread.sleep((long) (Drone.NOZZLE_DOORS * 10));

                if (volumeToDrop >= requiredVolume) {
                    System.out.printf("[%s] [Drone %d] Successfully extinguished fire in Zone %d!\n", ts(), drone.getId(), ev.getZoneID());
                    sendOnly("DRONE_COMPLETE_EVENT," + drone.getId() + "," + ev.getTime() + "," + ev.getZoneID() + "," + ev.getSeverity());
                    
                    // Optimization: Check if drone can take another mission
                    String nextMissionResponse = sendAndReceive("REQUEST_NEXT_MISSION," + drone.getId() + "," + drone.getAgentLevel());
                    String[] nextParts = nextMissionResponse.split(",");
                    
                    if (nextParts[0].trim().equals("ASSIGN_EVENT")) {
                        // Drone can handle another mission
                        String nextTime = nextParts[1].trim();
                        int nextZoneID = Integer.parseInt(nextParts[2].trim());
                        FireEvent.Severity nextSeverity = FireEvent.Severity.valueOf(nextParts[3].trim());
                        FireEvent.FaultType nextFaultType = nextParts.length > 4 ? FireEvent.FaultType.valueOf(nextParts[4].trim()) : FireEvent.FaultType.NONE;
                        
                        FireEvent nextEvent = new FireEvent(nextTime, nextZoneID, FireEvent.Type.FIRE_DETECTED, nextSeverity, nextFaultType);
                        drone.setCurrentMission(nextEvent);
                        drone.setState(Drone.DroneState.EN_ROUTE_NEXT_MISSION);
                        
                        System.out.printf("[%s] [Drone %d] Proceeding directly to next Zone %d (remaining agent: %.1f%%)\n", ts(), drone.getId(), nextZoneID, drone.getAgentLevel());
                    } else {
                        // No suitable mission, return to base
                        drone.setState(Drone.DroneState.RETURNING);
                        drone.setCurrentMission(null);
                        System.out.printf("[%s] [Drone %d] No suitable next mission available. Returning to base.\n", ts(), drone.getId());
                    }
                } else {
                    System.out.printf("[%s] [Drone %d] Ran out of agent! Fire in Zone %d not fully extinguished.\n", ts(), drone.getId(), ev.getZoneID());
                    sendOnly("REQUEUE_EVENT," + drone.getId() + "," + ev.getTime() + "," + ev.getZoneID() + "," + ev.getSeverity());
                    drone.setState(Drone.DroneState.RETURNING);
                    drone.setCurrentMission(null);
                }
                break;

            case RETURNING:
                double returnTime = drone.computeTravelTime(0, 0, false);
                System.out.printf("[%s] [Drone %d] Returning to base. Expected return time: %.1f seconds\n", ts(), drone.getId(), returnTime);

                moveToTargetStepByStep(0, 0, Drone.CRUISE_SPEED_UNLOADED);
                drone.setState(Drone.DroneState.REFILLING);
                break;

            case REFILLING:
                System.out.printf("[%s] [Drone %d] Refilling agent at base...\n", ts(), drone.getId());
                Thread.sleep(1500);

                drone.setAgentLevel(100.0);
                sendOnly("STATUS_UPDATE," + drone.getId() + "," + drone.getState() + "," +
                        drone.getX() + "," + drone.getY() + "," + drone.getAgentLevel());

                String reply = sendAndReceive("DRONE_RETURN_TO_BASE," + drone.getId());

                if (reply.startsWith("ALL_EVENTS_COMPLETE")) {
                    System.out.printf("[%s] [Drone %d] All events complete. Shutting down.\n", ts(), drone.getId());
                    running = false;
                } else {
                    drone.setState(Drone.DroneState.IDLE);
                }
                break;

            case FAULTED:
                System.out.printf("[%s] [Drone %d] In FAULTED state. Initiating fault recovery protocol...\n", ts(), drone.getId());
                handleFaultRecovery();
                break;
        }
    }


    /**
     * Reports a fault to the scheduler and initiates recovery procedures
     * @param faultType the type of fault that occurred (e.g., STUCK_IN_FLIGHT, NOZZLE_JAMMED)
     * @param event the fire event associated with the fault
     */
    private void reportFault(String faultType, FireEvent event) {
        this.lastFaultType = faultType; // Track fault type for differentiated handling
        System.err.printf("[%s] [Drone %d] FAULT DETECTED: %s at Zone %d\n", ts(), drone.getId(), faultType, event.getZoneID());
        
        // Send fault report to scheduler
        String faultReport = "HARD_FAULT," + drone.getId() + "," + faultType + "," + event.getZoneID();
        System.out.printf("[%s] [Drone %d] Sending fault report: %s\n", ts(), drone.getId(), faultReport);
        sendOnly(faultReport);
        
        // Set drone to faulted state
        drone.setState(Drone.DroneState.FAULTED);
        drone.setCurrentMission(null);
        
        // Sleep to simulate restart/recovery
        try {
            System.out.printf("[%s] [Drone %d] Initiating recovery sequence...\n", ts(), drone.getId());
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Handles the complete fault recovery protocol
     * Manages state transitions and prepares drone for next mission
     * IMPORTANT: Hard faults (NOZZLE_JAMMED) permanently shutdown the drone
     */
    private void handleFaultRecovery() throws InterruptedException {
        // HARD FAULT: NOZZLE_JAMMED causes PERMANENT shutdown
        if ("NOZZLE_JAMMED".equals(lastFaultType)) {
            System.err.printf("[%s] [Drone %d] *** HARD FAULT DETECTED: NOZZLE_JAMMED ***\n", ts(), drone.getId());
            System.err.printf("[%s] [Drone %d] Nozzle/Bay doors are permanently stuck! Drone is disabled.\n", ts(), drone.getId());
            
            // Send permanent shutdown notification to scheduler
            sendOnly("DRONE_SHUTDOWN," + drone.getId() + ",NOZZLE_JAMMED");
            
            // Permanently terminate this drone's operation
            System.err.printf("[%s] [Drone %d] Drone %d is now PERMANENTLY OFFLINE.\n", ts(), drone.getId(), drone.getId());
            running = false;
            return;
        }
        
        // SOFT FAULTS: STUCK_IN_FLIGHT, COMMUNICATION_LOST - allow recovery
        FireEvent faultedEvent = drone.getCurrentMission();
        
        // Log fault details
        if (faultedEvent != null) {
            System.out.printf("[%s] [Drone %d] Fault occurred during mission at Zone %d (Fault: %s)\n",
                    ts(), drone.getId(), faultedEvent.getZoneID(), faultedEvent.getFaultType());
        }
        
        // Clear mission data and reset
        drone.setCurrentMission(null);
        
        // Return drone to base for inspection
        System.out.printf("[%s] [Drone %d] Returning to base station for inspection and reset...\n", ts(), drone.getId());
        moveToTargetStepByStep(0, 0, Drone.CRUISE_SPEED_UNLOADED);
        
        // Simulate restart/recovery delay
        System.out.printf("[%s] [Drone %d] Drone recovery sequence initiated. Waiting 5 seconds...\n", ts(), drone.getId());
        Thread.sleep(5000);
        
        // Reset agent and transition to IDLE
        drone.setAgentLevel(100.0);
        System.out.printf("[%s] [Drone %d] Recovery complete. Agent recharged. Ready for next mission.\n", ts(), drone.getId());
        System.out.printf("[%s] [Drone %d] Transitioning from FAULTED to IDLE state.\n", ts(), drone.getId());
        
        drone.setState(Drone.DroneState.IDLE);
        
        // Send status update to scheduler
        sendOnly("STATUS_UPDATE," + drone.getId() + "," + Drone.DroneState.IDLE + "," +
                drone.getX() + "," + drone.getY() + "," + drone.getAgentLevel());
    }

    @Override
    public void run() {
        sendAndReceive("REGISTER_DRONE," + drone.getId());
        while(running) {
            try {
                handleEvent();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Drone.DroneState getState() {
        return drone.getState();
    }
    public FireEvent getCurrentMission() { return drone.getCurrentMission(); }
    public double getAgentLevel() { return drone.getAgentLevel(); }
}