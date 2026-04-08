package metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MetricsTracker collects and prints performance metrics for the firefighting drone simulation.
 * @author Jordan Grewal, Nolan Kisser, Celina Yang
 * @version April 5, 2026
 */
public class MetricsTracker {

    // counters to track number of fires detected and number of fires extinguished
    private final AtomicInteger totalFireEvents = new AtomicInteger(0);
    private final AtomicInteger totalExtinguishedFires = new AtomicInteger(0);

    // start and end time of simulation
    private long simulationStartTime = -1;
    private long simulationEndTime = -1;

    // storing timestamps for each zone
    private final Map<Integer, Long> fireStartTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> fireResponseTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> fireExtinguishTimes = new ConcurrentHashMap<>();

    // computing averages
    private long totalResponseTime = 0;
    private long totalExtinguishTime = 0;
    private int responseTimeCount = 0;
    private int extinguishTimeCount = 0;

    // stores metrics for each drone
    private final Map<Integer, DroneMetrics> droneMetrics = new ConcurrentHashMap<>();

    /**
     * tracks metrics for a single drone
     */
    public static class DroneMetrics {
        private final int droneId;
        private double totalDistanceTravelled = 0.0;

        // time tracking
        private long totalFlightTime = 0;
        private long totalIdleTime = 0;

        private long lastStateChangeTime;
        private String currentState;

        // position for distance calculation
        private double lastX = 0.0;
        private double lastY = 0.0;

        /**
         * creates a new DroneMetrics instance for a specific drone given the drone ID.
         * @param droneId id of the drone
         */
        public DroneMetrics(int droneId) {
            this.droneId = droneId;
            this.lastStateChangeTime = System.currentTimeMillis();
            this.currentState = "IDLE";
        }

        /**
         * updates the drone's state and records the time spent in the previous state.
         * @param newState the new state of the drone
         */
        public void updateState(String newState) {
            long now = System.currentTimeMillis();
            long duration = now - lastStateChangeTime;

            // adding time based on previous state
            if ("IDLE".equalsIgnoreCase(currentState)) {
                totalIdleTime += duration;
            } else if ("EN_ROUTE".equalsIgnoreCase(currentState)
                    || "RETURNING".equalsIgnoreCase(currentState)
                    || "EXTINGUISHING".equalsIgnoreCase(currentState)) {
                totalFlightTime += duration;
            }

            // update state and timestamp
            currentState = newState;
            lastStateChangeTime = now;
        }

        /**
         * updates drone position and calculates distance travelled
         * @param newX new x coordinate
         * @param newY new y coordinate
         */
        public void updateLocation(double newX, double newY) {
            double dx = newX - lastX;
            double dy = newY - lastY;
            totalDistanceTravelled += Math.sqrt(dx * dx + dy * dy);

            lastX = newX;
            lastY = newY;
        }

        /**
         * finalize timing calculations by updating current state
         */
        public void finalizeTime() {
            updateState(currentState);
        }

        /**
         * @return total distance travelled
         */
        public double getTotalDistanceTravelled() {
            return totalDistanceTravelled;
        }

        /**
         * @return total flight time
         */
        public double getTotalFlightTimeSeconds() {
            return totalFlightTime / 1000.0;
        }

        /**
         * @return total idle time
         */
        public double getTotalIdleTimeSeconds() {
            return totalIdleTime / 1000.0;
        }

        /**
         * formatted metrics to display individual drone information
         * @return formatted metrics
         */
        @Override
        public String toString() {
            return String.format(
                    "Drone %d: Distance=%.2f m, Flight Time=%.2f s, Idle Time=%.2f s",
                    droneId,
                    totalDistanceTravelled,
                    getTotalFlightTimeSeconds(),
                    getTotalIdleTimeSeconds()
            );
        }
    }

    /**
     * marks the start of the simulation
     */
    public void markSimulationStart() {
        if (simulationStartTime == -1) {
            simulationStartTime = System.currentTimeMillis();
        }
    }

    /**
     * marks the end of the simulation
     */
    public void markSimulationEnd() {
        simulationEndTime = System.currentTimeMillis();
    }

    /**
     * register drone for tracking
     * @param droneId id of the drone
     */
    public void registerDrone(int droneId) {
        droneMetrics.computeIfAbsent(droneId, DroneMetrics::new);
    }

    /**
     * record when a new fire event enters the system and is detected in a zone
     * @param zoneId zone ID where fire occurred
     */
    public void recordFireStart(int zoneId) {
        markSimulationStart();

        if (!fireStartTimes.containsKey(zoneId)) {
            fireStartTimes.put(zoneId, System.currentTimeMillis());
            totalFireEvents.incrementAndGet();
        }
    }

    /**
     * records when a drone is assigned to a fire event and computes response time
     * @param zoneId zone ID where fire occurred
     * @param droneId id of the drone
     */
    public void recordDroneAssignment(int zoneId, int droneId) {
        registerDrone(droneId);

        Long startTime = fireStartTimes.get(zoneId);
        if (startTime != null && !fireResponseTimes.containsKey(zoneId)) {
            long responseTime = System.currentTimeMillis() - startTime;
            fireResponseTimes.put(zoneId, responseTime);
            totalResponseTime += responseTime;
            responseTimeCount++;
        }
    }

    /**
     * records when a fire is fully extinguished
     * @param zoneId zone ID where fire occurred
     */
    public void recordFireExtinguished(int zoneId) {
        Long startTime = fireStartTimes.get(zoneId);
        if (startTime != null) {
            long extinguishTime = System.currentTimeMillis() - startTime;
            fireExtinguishTimes.put(zoneId, extinguishTime);
            totalExtinguishTime += extinguishTime;
            extinguishTimeCount++;
            totalExtinguishedFires.incrementAndGet();
        }
    }

    /**
     * records drones state change
     * @param droneId id of the drone
     * @param newState new state of drone
     */
    public void recordDroneStateChange(int droneId, String newState) {
        registerDrone(droneId);
        droneMetrics.get(droneId).updateState(newState);
    }

    /**
     * records drone location update
     * @param droneId id of the drone
     * @param x x coordinate
     * @param y y coordinate
     */
    public void recordDroneLocation(int droneId, double x, double y) {
        registerDrone(droneId);
        droneMetrics.get(droneId).updateLocation(x, y);
    }

    /**
     * finalize all drone timers before printing
     */
    public void finalizeMetrics() {
        for (DroneMetrics metrics : droneMetrics.values()) {
            metrics.finalizeTime();
        }
    }

    /**
     * @return average response time
     */
    public double getAverageResponseTime() {
        return responseTimeCount > 0 ? (double) totalResponseTime / responseTimeCount : 0;
    }

    /**
     * @return average extinguish time
     */
    public double getAverageExtinguishTime() {
        return extinguishTimeCount > 0 ? (double) totalExtinguishTime / extinguishTimeCount : 0;
    }

    /**
     * @return total simulation time
     */
    public double getTotalSimulationTime() {
        if (simulationStartTime == -1 || simulationEndTime == -1) {
            return 0;
        }
        return (simulationEndTime - simulationStartTime);
    }

    /**
     * retrieves metrics for a specific drone
     * @param droneId id of the drone
     * @return DroneMetrics object
     */
    public DroneMetrics getDroneMetrics(int droneId) {
        return droneMetrics.get(droneId);
    }

    /**
     * @return total number of fire events
     */
    public int getTotalFireEvents() {
        return totalFireEvents.get();
    }


    /**
     * @return total number of extinguished fires
     */
    public int getTotalExtinguishedFires() {
        return totalExtinguishedFires.get();
    }

    /**
     * print simulation performance summary
     */
    public void printSummary() {

        // total summary
        System.out.println("\n=== PERFORMANCE METRICS SUMMARY ===");
        System.out.println("Total Zones With Fire Events: " + totalFireEvents.get());
        System.out.println("Total Extinguished Fires: " + totalExtinguishedFires.get());
        System.out.printf("Average Response Time: %.2f seconds%n", getAverageResponseTime() / 1000.0);
        System.out.printf("Average Extinguish Time: %.2f seconds%n", getAverageExtinguishTime() / 1000.0);
        System.out.printf("Total Time to Extinguish All Fires: %.2f seconds%n", getTotalSimulationTime() / 1000.0);

        // individual drone summary
        System.out.println("\n--- Individual Drone Metrics ---");
        for (DroneMetrics metrics : droneMetrics.values()) {
            System.out.println(metrics);
        }
        System.out.println("=================================\n");
    }
}