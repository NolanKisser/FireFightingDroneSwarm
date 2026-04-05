import metrics.MetricsTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

public class MetricsTrackerTest {
    private MetricsTracker tracker;

    @BeforeEach
    public void setup() {
        tracker = new MetricsTracker();
    }

    @Test
    @DisplayName("Test distance calculation uses Pythagorean theorem correctly")
    public void testDroneDistanceCalculation() {
        tracker.registerDrone(1);

        // Initial position
        tracker.recordDroneLocation(1, 0, 0);

        // Move 3 units X, 4 units Y (distance should be 5)
        tracker.recordDroneLocation(1, 3, 4);

        // Move 0 units X, 5 units Y (distance should add 5, total 10)
        tracker.recordDroneLocation(1, 3, 9);

        MetricsTracker.DroneMetrics metrics = tracker.getDroneMetrics(1);
        assertNotNull(metrics);
        assertEquals(10.0, metrics.getTotalDistanceTravelled(), 0.01);
    }

    @Test
    @DisplayName("Test averages return 0 safely when no events have occurred")
    public void testAverageTimeWithNoEventsReturnsZero() {
        assertEquals(0.0, tracker.getAverageResponseTime(), 0.01);
        assertEquals(0.0, tracker.getAverageExtinguishTime(), 0.01);
    }

    @Test
    @DisplayName("Test total simulation time with start and end markers")
    public void testSimulationTimeCalculation() throws InterruptedException {
        tracker.markSimulationStart();
        Thread.sleep(50); // Simulate time passing
        tracker.markSimulationEnd();

        assertTrue(tracker.getTotalSimulationTime() >= 50.0);
    }

    @Test
    @DisplayName("Test fire counting logic increments properly")
    public void testFireCountingLogic() {
        // Record 2 fires starting
        tracker.recordFireStart(1); // Zone 1
        tracker.recordFireStart(2); // Zone 2

        assertEquals(2, tracker.getTotalFireEvents());
        assertEquals(0, tracker.getTotalExtinguishedFires());

        // Extinguish 1 fire
        tracker.recordFireExtinguished(1);

        assertEquals(2, tracker.getTotalFireEvents());
        assertEquals(1, tracker.getTotalExtinguishedFires());
    }

    @Test
    @DisplayName("Test state changes properly categorize flight vs idle time")
    public void testFlightTimeVsIdleTimeAccumulation() throws InterruptedException {
        tracker.registerDrone(2);

        // Drone is IDLE by default. Let it sit for ~50ms
        Thread.sleep(50);

        // Change to EN_ROUTE (This logs the idle time and starts flight time)
        tracker.recordDroneStateChange(2, "EN_ROUTE");
        Thread.sleep(100);

        // Change back to IDLE (This logs the flight time)
        tracker.recordDroneStateChange(2, "IDLE");
        tracker.finalizeMetrics(); // Close out the final timer

        MetricsTracker.DroneMetrics metrics = tracker.getDroneMetrics(2);

        assertTrue(metrics.getTotalIdleTimeSeconds() > 0.04);
        assertTrue(metrics.getTotalFlightTimeSeconds() > 0.09);
    }
}