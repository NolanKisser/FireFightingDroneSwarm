/**
 * DroneSubsystem class represents a single firefighter drone.
 * The firefighter drone repeats the next incomplete FireEvent from the Scheduler and simulates
 * extinguishing the fire then reports back to the Scheduler of completion.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version February 14, 2026
 */
public class DroneSubsystem implements Runnable {


    public enum State {
        IDLE,
        EN_ROUTE,
        EXTINGUISHING,
        RETURNING,
        REFILLING,
        FAULTED
    }
    private final Scheduler scheduler;
    private final int droneID;
    private State state;
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

    /**
     * Constructor for DroneSubsystem
     * @param scheduler the shared scheduler
     * @param droneID unique ID for the drone
     */
    public DroneSubsystem(Scheduler scheduler, int droneID) {
        this.scheduler = scheduler;
        this.droneID = droneID;
        this.state = State.IDLE;
        // Register this drone with the scheduler's tracking system
        this.scheduler.registerDrone(droneID);
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
        double loadVolume = switch (event.getSeverity()) {
            case Low -> LOW_VOLUME;
            case Moderate -> MODERATE_VOLUME;
            case High -> HIGH_VOLUME;
        };

        double dropTime = loadVolume / DROP_RATE;
        double nozzleTime = NOZZLE_DOORS + NOZZLE_DOORS;
        return dropTime + nozzleTime;
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

    public void toZoneCenter(FireEvent event) {
        Zone zone = scheduler.getZones().get(event.getZoneID());
        currentX = zone.getCenterX();
        currentY = zone.getCenterY();
    }

    public void toBase() {
        currentX = 0.0;
        currentY = 0.0;
    }

    /**
     * The drone blocks until there is an event available. When the scheduler indicates there are
     * no more events, the drone will complete execution.
     */
    @Override
    public void run() {
        boolean running = true;
        while(running) {
            // simulating travel, extinguish, and return time
            try {
                switch (state) {
                    case IDLE:
                        event = scheduler.getNextFireEvent();
                        if (event == null) {
                            running = false; // Simulation complete
                        } else {
                            // Check if drone has enough agent to take on a mission
                            if (currentAgent < LOW_VOLUME) {
                                System.out.printf("[Drone %d] Insufficient agent (%.1f%%). Must refill before accepting mission.\n", droneID, currentAgent);
                                // Requeue the event since we can't handle it
                                scheduler.newFireEvent(event);
                                event = null;
                                state = State.RETURNING;
                            } else {
                                state = State.EN_ROUTE;
                            }
                        }
                        break;

                    case EN_ROUTE:
                        double travelTime = computeEnRoute(event);
                        System.out.printf("[Drone %d] En route to Zone %d. Expected travel time: %.1f seconds\n", droneID, event.getZoneID(), travelTime);

                        Thread.sleep((long) (travelTime * 10)); // Scaled for simulation speed
                        moveToZoneCenter(event);

                        // Push status update and notify arrival
                        scheduler.updateDroneStatus(droneID, currentX, currentY, currentAgent);
                        scheduler.droneArrivedAtZone(droneID, event);
                        state = State.EXTINGUISHING;
                        break;

                    case EXTINGUISHING:
                        // Determine how much agent is required based on severity
                        double requiredVolume = switch (event.getSeverity()) {
                            case Low -> LOW_VOLUME;
                            case Moderate -> MODERATE_VOLUME;
                            case High -> HIGH_VOLUME;
                        };

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
                        scheduler.updateDroneStatus(droneID, currentX, currentY, currentAgent);

                        // Physical Simulation: Close doors
                        System.out.printf("[Drone %d] Closing nozzle doors... (%.1fs)\n", droneID, NOZZLE_DOORS);
                        Thread.sleep((long) (NOZZLE_DOORS * 10));

                        // Check if the drone successfully extinguished the fire
                        if (volumeToDrop >= requiredVolume) {
                            System.out.printf("[Drone %d] Successfully extinguished fire in Zone %d!\n", droneID, event.getZoneID());
                            scheduler.completeFireEvent(event);
                        } else {
                            System.out.printf("[Drone %d] Ran out of agent! Fire in Zone %d not fully extinguished.\n", droneID, event.getZoneID());
                            // Re-queue the event so another drone can finish the job
                            scheduler.newFireEvent(event);
                        }

                        // Always return to base after a drop (can be optimized in future iterations to chain nearby fires)
                        state = State.RETURNING;
                        event = null;
                        break;

                    case RETURNING:
                        double returnTime = computeReturn(event);
                        System.out.printf("[Drone %d] Returning to base. Expected return time: %.1f seconds\n", droneID, returnTime);

                        Thread.sleep((long) (returnTime * 10)); // Scaled
                        moveToBase();
                        scheduler.updateDroneStatus(droneID, currentX, currentY, currentAgent);

                        state = State.REFILLING;
                        break;

                    case REFILLING:
                        System.out.printf("[Drone %d] Refilling agent at base...\n", droneID);
                        Thread.sleep(1500); // Simulate refill time

                        currentAgent = 100.0; // Agent replenished
                        scheduler.droneReturnToBase(droneID); // Notify scheduler we are ready
                        scheduler.updateDroneStatus(droneID, currentX, currentY, currentAgent);

                        state = State.IDLE;
                        break;

                    case FAULTED:
                        // The drone sits inactive. The scheduler will have re-queued its mission.
                        Thread.sleep(5000);
                        // In a real application, you might transition to an attempt to recover or restart
                        running = false; // End the thread for this faulted drone
                        break;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                running = false;
            }
        }
    }
}
