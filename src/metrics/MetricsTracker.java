package metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MetricsTracker collects and prints Iteration 5 performance metrics
 * for the firefighting drone swarm system.
 */
public class MetricsTracker {

    // Counters
    private final AtomicInteger totalFireEvents = new AtomicInteger(0);
    private final AtomicInteger totalExtinguishedFires = new AtomicInteger(0);

    // Overall timing
    private long simulationStartTime = -1;
    private long simulationEndTime = -1;

    // Fire timing by zone
    private final Map<Integer, Long> fireStartTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> fireResponseTimes = new ConcurrentHashMap<>();
    private final Map<Integer, Long> fireExtinguishTimes = new ConcurrentHashMap<>();

    // Totals for averages
    private long totalResponseTime = 0;
    private long totalExtinguishTime = 0;
    private int responseTimeCount = 0;
    private int extinguishTimeCount = 0;

    // Drone metrics
    private final Map<Integer, DroneMetrics> droneMetrics = new ConcurrentHashMap<>();

    /**
     * Tracks metrics for one drone.
     */
    public static class DroneMetrics {
        private final int droneId;
        private double totalDistanceTravelled = 0.0;
        private long totalFlightTime = 0;
        private long totalIdleTime = 0;
        private long lastStateChangeTime;
        private String currentState;

        private double lastX = 0.0;
        private double lastY = 0.0;

        public DroneMetrics(int droneId) {
            this.droneId = droneId;
            this.lastStateChangeTime = System.currentTimeMillis();
            this.currentState = "IDLE";
        }

        public void updateState(String newState) {
            long now = System.currentTimeMillis();
            long duration = now - lastStateChangeTime;

            if ("IDLE".equalsIgnoreCase(currentState)) {
                totalIdleTime += duration;
            } else if ("EN_ROUTE".equalsIgnoreCase(currentState)
                    || "RETURNING".equalsIgnoreCase(currentState)
                    || "EXTINGUISHING".equalsIgnoreCase(currentState)) {
                totalFlightTime += duration;
            }

            currentState = newState;
            lastStateChangeTime = now;
        }

        public void updateLocation(double newX, double newY) {
            double dx = newX - lastX;
            double dy = newY - lastY;
            totalDistanceTravelled += Math.sqrt(dx * dx + dy * dy);

            lastX = newX;
            lastY = newY;
        }

        public void finalizeTime() {
            updateState(currentState);
        }

        public double getTotalDistanceTravelled() {
            return totalDistanceTravelled;
        }

        public double getTotalFlightTimeSeconds() {
            return totalFlightTime / 1000.0;
        }

        public double getTotalIdleTimeSeconds() {
            return totalIdleTime / 1000.0;
        }

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
     * Call once when simulation begins.
     */
    public void markSimulationStart() {
        if (simulationStartTime == -1) {
            simulationStartTime = System.currentTimeMillis();
        }
    }

    /**
     * Call once when simulation ends.
     */
    public void markSimulationEnd() {
        simulationEndTime = System.currentTimeMillis();
    }

    /**
     * Register a drone so its metrics are tracked.
     */
    public void registerDrone(int droneId) {
        droneMetrics.computeIfAbsent(droneId, DroneMetrics::new);
    }

    /**
     * Record when a new fire event enters the system.
     */
    public void recordFireStart(int zoneId) {
        markSimulationStart();

        if (!fireStartTimes.containsKey(zoneId)) {
            fireStartTimes.put(zoneId, System.currentTimeMillis());
            totalFireEvents.incrementAndGet();
        }
    }

    /**
     * Record when a drone is first assigned to a fire.
     * This gives response time from detection to dispatch.
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
     * Record when a fire is fully extinguished.
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
     * Record a drone state change.
     */
    public void recordDroneStateChange(int droneId, String newState) {
        registerDrone(droneId);
        droneMetrics.get(droneId).updateState(newState);
    }

    /**
     * Record a drone location update.
     */
    public void recordDroneLocation(int droneId, double x, double y) {
        registerDrone(droneId);
        droneMetrics.get(droneId).updateLocation(x, y);
    }

    /**
     * Finalize all drone timers before printing.
     */
    public void finalizeMetrics() {
        for (DroneMetrics metrics : droneMetrics.values()) {
            metrics.finalizeTime();
        }
    }

    public double getAverageResponseTime() {
        return responseTimeCount > 0 ? (double) totalResponseTime / responseTimeCount : 0;
    }

    public double getAverageExtinguishTime() {
        return extinguishTimeCount > 0 ? (double) totalExtinguishTime / extinguishTimeCount : 0;
    }

    public double getTotalSimulationTime() {
        if (simulationStartTime == -1 || simulationEndTime == -1) {
            return 0;
        }
        return (simulationEndTime - simulationStartTime);
    }

    /**
     * Print Iteration 5 metrics summary.
     */
    public void printSummary() {
        System.out.println("\n=== PERFORMANCE METRICS SUMMARY ===");
        System.out.println("Total Zones With Fire Events: " + totalFireEvents.get());
        System.out.println("Total Extinguished Fires: " + totalExtinguishedFires.get());
        System.out.printf("Average Response Time: %.2f seconds%n", getAverageResponseTime() / 1000.0);
        System.out.printf("Average Extinguish Time: %.2f seconds%n", getAverageExtinguishTime() / 1000.0);
        System.out.printf("Total Time to Extinguish All Fires: %.2f seconds%n", getTotalSimulationTime() / 1000.0);

        System.out.println("\n--- Individual Drone Metrics ---");
        for (DroneMetrics metrics : droneMetrics.values()) {
            System.out.println(metrics);
        }
        System.out.println("=================================\n");
    }
}