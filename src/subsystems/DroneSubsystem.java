package subsystems;

import model.*;
import java.io.IOException;
import java.net.*;
import java.time.LocalTime;

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

        Scheduler scheduler = new Scheduler("zone_file.csv");
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
            e.printStackTrace();
        }
        return new String(receivePacket.getData(), 0, receivePacket.getLength()).trim();
    }

    private void moveToTargetStepByStep(double targetX, double targetY, double speed, boolean willGetStuck, boolean willLoseComms) throws InterruptedException {
        double stepDistance = 100.0;
        int stepsTaken = 0;

        while (drone.getX() != targetX || drone.getY() != targetY) {

            // SIMULATE MID-FLIGHT FAILURE: Freeze after 2 steps
            if (willGetStuck && stepsTaken == 2) {
                System.err.printf("[%s] [Drone %d] FAULT: Stuck mid-flight at (%.1f, %.1f)! Commencing radio silence.\n",
                        ts(), drone.getId(), drone.getX(), drone.getY());
                drone.setState(Drone.DroneState.FAULTED);
                return; // Abort movement entirely
            }

            if (willLoseComms && stepsTaken == 5) {
                System.err.printf("[%s] [Drone %d] HARD FAULT: Communication Lost! Shutting down drone operations.\n", ts(), drone.getId());
                sendOnly("HARD_FAULT," + drone.getId() + ",COMMUNICATION_LOST");
                drone.setState(Drone.DroneState.FAULTED);
                return; // Abort movement entirely
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

            stepsTaken++;
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

                System.out.printf("[%s] [Drone %d] En route to Zone %d. Travel time: %.1fs\n",
                        ts(), drone.getId(), currentEvent.getZoneID(), travelTime);

                // Pass the fault check into the movement method
                boolean willGetStuck = (currentEvent.getFaultType() == FireEvent.FaultType.STUCK_IN_FLIGHT);
                boolean willLoseComms = (currentEvent.getFaultType() == FireEvent.FaultType.COMMUNICATION_LOST);

                System.out.println("DEBUG fault type = " + currentEvent.getFaultType());
                System.out.println("DEBUG willGetStuck = " + willGetStuck);

                moveToTargetStepByStep(target.getCenterX(), target.getCenterY(), Drone.CRUISE_SPEED_LOADED, willGetStuck, willLoseComms);

                // Check if the drone died during transit. If so, abort the rest of EN_ROUTE
                if (drone.getState() == Drone.DroneState.FAULTED) {
                    return;
                }

                sendOnly("DRONE_ARRIVE_TO_ZONE," + drone.getId() + "," + currentEvent.getTime() + "," +
                        currentEvent.getZoneID() + "," + currentEvent.getSeverity());
                drone.setState(Drone.DroneState.EXTINGUISHING);
                break;

            case EXTINGUISHING:
                FireEvent ev = drone.getCurrentMission();
                if (ev.getFaultType() == FireEvent.FaultType.NOZZLE_JAMMED) {
                    System.err.printf("[%s] [Drone %d] HARD FAULT: Nozzle doors jammed! Shutting down.\n", ts(), drone.getId());
                    sendOnly("HARD_FAULT," + drone.getId() + ",NOZZLE_JAMMED");
                    drone.setState(Drone.DroneState.FAULTED);
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
                } else {
                    System.out.printf("[%s] [Drone %d] Ran out of agent! Fire in Zone %d not fully extinguished.\n", ts(), drone.getId(), ev.getZoneID());
                    sendOnly("REQUEUE_EVENT," + drone.getId() + "," + ev.getTime() + "," + ev.getZoneID() + "," + ev.getSeverity());
                }

                drone.setState(Drone.DroneState.RETURNING);
                drone.setCurrentMission(null);
                break;

            case RETURNING:
                double returnTime = drone.computeTravelTime(0, 0, false);
                System.out.printf("[%s] [Drone %d] Returning to base. Expected return time: %.1f seconds\n", ts(), drone.getId(), returnTime);

                moveToTargetStepByStep(0, 0, Drone.CRUISE_SPEED_UNLOADED, false, false);
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
                Thread.sleep(5000);
                break;
        }
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
}