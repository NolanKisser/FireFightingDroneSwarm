/**
 * DroneSubsystem class represents a single firefighter drone.
 * The firefighter drone repeats the next incomplete FireEvent from the Scheduler and simulates
 * extinguishing the fire then reports back to the Scheduler of completion.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class DroneSubsystem implements Runnable {

    private final Scheduler scheduler;
    private final int droneID;

    // current drone location
    private double currentX = 0.0;
    private double currentY = 0.0;

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

    /**
     * The drone blocks until there is an event available. When the scheduler indicates there are
     * no more events, the drone will complete execution.
     */
    @Override
    public void run() {
        while(true) {
            FireEvent event = scheduler.getNextFireEvent();

            // there are no more events to process
            if (event == null) {
                break;
            }

            // simulating travel, extinguish, and return time
            try {
                System.out.println("DroneID: " + droneID + " is enRoute to fire in zone " + event.getZoneID()
                        + ". Expected travel time: " + (int) computeEnRoute(event) + " seconds");
                Thread.sleep(1000);
                moveToZoneCenter(event);
                System.out.println("DroneID: " + droneID + " is extinguishing fire in zone " + event.getZoneID()
                        + ". Expected completion time: " + (int) computeExtinguish(event) + " seconds");
                Thread.sleep(1000);
                System.out.println("DroneID: " + droneID + " is returning from zone " + event.getZoneID()
                        + ". Expected return time: " + (int) computeReturn(event) + " seconds\n");
                Thread.sleep(1000);
                moveToBase();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            scheduler.completeFireEvent(event);
        }
    }
}
