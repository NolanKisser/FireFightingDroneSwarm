import javax.lang.model.util.SimpleElementVisitor14;
import java.io.IOException;
import java.net.*;

/**
 * DroneSubsystem class represents a single firefighter drone.
 * The firefighter drone repeats the next incomplete FireEvent from the Scheduler and simulates
 * extinguishing the fire then reports back to the Scheduler of completion.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version February 14, 2026
 */
public class DroneSubsystem implements Runnable {


    public enum DroneState {
        IDLE,
        EN_ROUTE,
        EXTINGUISHING,
        RETURNING,
        REFILLING,
        FAULTED
    }
    private final Scheduler scheduler;
    private final int droneID;
    private DroneState state;
    private FireEvent event;

    // current drone location
    private double currentX = 0.0;
    private double currentY = 0.0;
    private double currentAgent = 100.0; // 100% capacity

    // parameters obtained in iteration 0
    private static final double CRUISE_SPEED_LOADED = 10.0;
    private static final double CRUISE_SPEED_UNLOADED = 15.0;
    private static final double NOZZLE_DOORS = 0.5;
    private static final double DROP_RATE = 2.0;

    private static final double LOW_VOLUME = 10.0;
    private static final double MODERATE_VOLUME = 20.0;
    private static final double HIGH_VOLUME = 30.0;

    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendReceiveSocket;
    int SCHEDULER_PORT = 6000;
    String SCHEDULER_HOST = "localhost";

    private boolean running = true;

    public DroneSubsystem(Scheduler scheduler, int droneID) {
        this.scheduler = scheduler;
        this.droneID = droneID;
        transitionTo(DroneState.IDLE);

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
        DroneSubsystem drone = new DroneSubsystem(scheduler, id);
        Thread droneThread = new Thread(drone);
        droneThread.start();
    }

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

        System.out.println("[Drone " + droneID + "] sent message to scheduler");
        System.out.println("[Drone " + droneID + "] message sent: " + message);
    }

    private String receiveOnly() {
        receivePacket = new  DatagramPacket(new byte[1024], 1024);

        try {
            sendReceiveSocket.receive(receivePacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

        System.out.println("[Drone " + droneID + "] received message from scheduler");
        System.out.println("[Drone " + droneID + " ] message received:  " + message);
        return message;
    }

    /**
     * Compute the en route travel time from the drone's current location to the center of zone
     * associated with the given fire event
     * @param event the fire event being serviced
     * @return travel time in seconds
     */
    private double computeEnRoute(FireEvent event) {
        Zone target = scheduler.getZones().get(event.getZoneID());

        double zoneCenterX = target.getCenterX();
        double zoneCenterY = target.getCenterY();

        double distance = distance(currentX, currentY, zoneCenterX, zoneCenterY);
        return distance / CRUISE_SPEED_LOADED;

    }

    /**
     * Compute the extinguish time of an event based on the severity of the fire. It takes into
     * account the nozzle door opening and closing time and dispensing of the water.
     * @param event the fire event being serviced
     * @return extinguish time in seconds
     */
    private double computeExtinguish(FireEvent event) {
        double loadVolume = getRequiredVolume(event);
        double dropTime = loadVolume / DROP_RATE;
        double nozzleTime = NOZZLE_DOORS + NOZZLE_DOORS;
        return dropTime + nozzleTime;
    }

    private double getRequiredVolume(FireEvent event) {
        return switch (event.getSeverity()) {
            case Low -> LOW_VOLUME;
            case Moderate -> MODERATE_VOLUME;
            case High -> HIGH_VOLUME;
        };
    }

    private void transitionTo(DroneState newState) {
        this.state = newState;
//         scheduler.notifyDroneTransition(newState);
    }

    /**
     * Compute the return time from the drone's current location, at the center of the event's
     * zone to the drone's base coordinates (0,0).
     * @param event the fire event being serviced
     * @return the return time in seconds
     */
    private double computeReturn(FireEvent event) {
        double distance = distance(currentX, currentY, 0, 0);
        return distance / CRUISE_SPEED_UNLOADED;

    }

    /**
     * Move drone step-by-step towards the target, visually updating 1 block (100 units) at a time
     * @param targetX the x coordinate of the destination
     * @param targetY the y coordinate of the destination
     * @param speed the speed of the drone
     */
    private void moveToTargetStepByStep(double targetX, double targetY, double speed) throws InterruptedException {
        double stepDistance = 100.0;
        while (currentX != targetX || currentY != targetY) {
            double diffX = targetX - currentX;
            double diffY = targetY - currentY;
            
            // Move strictly block-by-block aligned to axis
            if (Math.abs(diffX) > 0) {
                double step = Math.min(stepDistance, Math.abs(diffX));
                currentX += Math.signum(diffX) * step;
                Thread.sleep((long) ((step / speed) * 10)); // Simulated time delay
            } else if (Math.abs(diffY) > 0) {
                double step = Math.min(stepDistance, Math.abs(diffY));
                currentY += Math.signum(diffY) * step;
                Thread.sleep((long) ((step / speed) * 10)); // Simulated time delay
            }
            
            sendOnly("STATUS_UPDATE," + droneID + "," + state + "," + currentX + "," + currentY + "," + currentAgent);
        }
    }

    /**
     * Compute the distance between the two points
     * @param x1 x coordinate of first point
     * @param y1 y coordinate of first point
     * @param x2 x coordinate of second point
     * @param y2 y coordinate of second point
     * @return the distance between the first point and second point
     */
    private static double distance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    /**
     * Update the drone's current location to the center of the zone associated with the fire event
     * @param event the fire event of the center zone destination
     */
    private void moveToZoneCenter(FireEvent event) {
        Zone target = scheduler.getZones().get(event.getZoneID());
        currentX = target.getCenterX();
        currentY = target.getCenterY();
    }

    /**
     * Update the drone's current location to base coordinates of (0,0)
     */
    private void moveToBase() {
        currentX = 0.0;
        currentY = 0.0;
    }

    public double getCurrentX() {
        return currentX;
    }

    public double getCurrentY() {
        return currentY;
    }

    public double getEnRoute(FireEvent event) {
        return computeEnRoute(event);
    }
    public double getExtinguish(FireEvent event) {
        return computeExtinguish(event);
    }

    public double getReturn(FireEvent event) {
        return computeReturn(event);
    }

    public DroneState getDroneState() {
        return state;
    }

    public void toZoneCenter(FireEvent event) {
        Zone zone = scheduler.getZones().get(event.getZoneID());
        currentX = zone.getCenterX();
        currentY = zone.getCenterY();
    }

    public void toBase() {
        currentX = 0.0;
        currentY = 0.0;
    }

    private synchronized void handleEvent() throws InterruptedException {
        switch (state) {
            case IDLE:
                String message = sendAndReceive("DRONE_READY," + droneID);
                String[] parts =  message.split(",");
                if (parts[0].equals("ASSIGN_EVENT")) {
                    System.out.println("RECEIVE ASSIGN");
                    String time = parts[1];
                    int zoneID = Integer.parseInt(parts[2]);
                    FireEvent.Severity severity = FireEvent.Severity.valueOf(parts[3]);

                    event = new FireEvent(time, zoneID, FireEvent.Type.FIRE_DETECTED, severity);

                    // Check if drone has enough agent to take on a mission
                    if (currentAgent < LOW_VOLUME) {
                        System.out.printf("[Drone %d] Insufficient agent (%.1f%%). Must refill before accepting mission.\n", droneID, currentAgent);
                        // Requeue the event since we can't handle it
//                                 scheduler.newFireEvent(event);
                        event = null;
                        transitionTo(DroneState.RETURNING);
                    } else {
                        transitionTo(DroneState.EN_ROUTE);
                    }
                } else if(parts[0].equals("ALL_EVENTS_COMPLETE")) {
                    System.out.println("ALL EVENTS COMPLETE");
                    running = false;
                }

                break;

            case EN_ROUTE:
                double travelTime = computeEnRoute(event);
                System.out.printf("[Drone %d] En route to Zone %d. Expected travel time: %.1f seconds\n", droneID, event.getZoneID(), travelTime);

                Zone target = scheduler.getZones().get(event.getZoneID());
                moveToTargetStepByStep(target.getCenterX(), target.getCenterY(), CRUISE_SPEED_LOADED);

                // Check arrival and update
                sendOnly("DRONE_ARRIVE_TO_ZONE," + droneID + "," + event.getTime() + "," + event.getZoneID() + "," + event.getSeverity());
//                         scheduler.updateDroneStatus(droneID, currentX, currentY, currentAgent);
//                         scheduler.droneArrivedAtZone(droneID, event);
                transitionTo(DroneState.EXTINGUISHING);
                break;

            case EXTINGUISHING:
                // Determine how much agent is required based on severity
                double requiredVolume = getRequiredVolume(event);

                // Calculate how much we can actually drop based on remaining capacity
                double volumeToDrop = Math.min(requiredVolume, currentAgent);
                double dropTime = volumeToDrop / DROP_RATE;

                // Physical Simulation: Open doors
                System.out.printf("[Drone %d] Opening nozzle doors... (%.1fs)\n", droneID, NOZZLE_DOORS);
                Thread.sleep((long) (NOZZLE_DOORS * 10));

                // Physical Simulation: Drop agent
                System.out.printf("[Drone %d] Dropping %.1f%% agent on Zone %d... (%.1fs)\n", droneID, volumeToDrop, event.getZoneID(), dropTime);
                Thread.sleep((long) (dropTime * 10));

                currentAgent -= volumeToDrop;
                sendOnly("STATUS_UPDATE," + droneID + "," + state + "," + currentX + "," + currentY + "," + currentAgent);
//                         scheduler.updateDroneStatus(droneID, currentX, currentY, currentAgent);

                // Physical Simulation: Close doors
                System.out.printf("[Drone %d] Closing nozzle doors... (%.1fs)\n", droneID, NOZZLE_DOORS);
                Thread.sleep((long) (NOZZLE_DOORS * 10));

                // Check if the drone successfully extinguished the fire
                if (volumeToDrop >= requiredVolume) {
                    System.out.printf("[Drone %d] Successfully extinguished fire in Zone %d!\n", droneID, event.getZoneID());
//                             scheduler.completeFireEvent(event);
                    sendOnly("DRONE_COMPLETE_EVENT," +  droneID + "," + event.getTime() + "," + event.getZoneID() + "," + event.getSeverity());
                } else {
                    System.out.printf("[Drone %d] Ran out of agent! Fire in Zone %d not fully extinguished.\n", droneID, event.getZoneID());
                    // Re-queue the event so another drone can finish the job
//                             scheduler.newFireEvent(event);
                    sendOnly("REQUEUE_EVENT," + droneID + "," + event.getTime() + "," + event.getZoneID() + "," + event.getSeverity());
                }

                // Always return to base after a drop (can be optimized in future iterations to chain nearby fires)
                transitionTo(DroneState.RETURNING);
                event = null;
                break;

            case RETURNING:
                double returnTime = computeReturn(event);
                System.out.printf("[Drone %d] Returning to base. Expected return time: %.1f seconds\n", droneID, returnTime);

                moveToTargetStepByStep(0, 0, CRUISE_SPEED_UNLOADED);
//                         scheduler.updateDroneStatus(droneID, currentX, currentY, currentAgent);
                transitionTo(DroneState.REFILLING);
                break;

            case REFILLING:
                System.out.printf("[Drone %d] Refilling agent at base...\n", droneID);
                Thread.sleep(1500); // Simulate refill time

                currentAgent = 100.0; // Agent replenished
//                         scheduler.droneReturnToBase(droneID); // Notify scheduler we are ready
//                         scheduler.updateDroneStatus(droneID, currentX, currentY, currentAgent);

                sendOnly("STATUS_UPDATE," + droneID + "," + state + "," + currentX + "," + currentY + "," + currentAgent);

                String reply = sendAndReceive("DRONE_RETURN_TO_BASE," + droneID);

                if (reply.startsWith("ALL_EVENTS_COMPLETE")) {
                    System.out.println("[Drone " + droneID + "] All events complete. Shutting down.");
                    running = false;
                } else {
                    transitionTo(DroneState.IDLE);
                }
                break;

            case FAULTED:
                // The drone sits inactive. The scheduler will have re-queued its mission.
                Thread.sleep(5000);
                // In a real application, you might transition to an attempt to recover or restart
                // running = false; // End the thread for this faulted drone
                break;
        }
    }

    /**
     * The drone blocks until there is an event available. When the scheduler indicates there are
     * no more events, the drone will complete execution.
     */
    @Override
    public void run() {

        sendAndReceive("REGISTER_DRONE," + droneID);

        while(running) {
            try {
                handleEvent();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
