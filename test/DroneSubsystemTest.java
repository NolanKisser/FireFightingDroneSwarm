import model.*;
import subsystems.*;
import ui.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Unit tests for the DroneSubsystem class.
 * Tests drone behaviour, event processing, and integration with the Scheduler.
 *
 * @author Jordan Grewal, Nolan Kisser, Celina Yang
 * @version February 14, 2026
 */
public class DroneSubsystemTest {

    private Scheduler scheduler;
    private String testZoneFilePath;

    @BeforeEach
    public void setup() throws IOException, InterruptedException {
        // Create a temporary zone file for testing
        testZoneFilePath = "test/test_zones.csv";
        createTestZoneFile(testZoneFilePath);
        scheduler = new Scheduler(testZoneFilePath);
        Thread udpThread = new Thread(() -> scheduler.startUDPServer());
        udpThread.setDaemon(true);
        udpThread.start();
        // Wait for UDP server to be ready
        Thread.sleep(500);
    }

    /**
     * Helper method to create a test zone file
     */
    private void createTestZoneFile(String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("1,(0;0),(700;600)\n");
            writer.write("2,(0;600),(650;1500)\n");
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        cleanupTestFiles();
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * Helper method to clean up test files
     */
    private void cleanupTestFiles() {
        File file = new File(testZoneFilePath);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    @DisplayName("Test DroneSubsystem creation with valid parameters")
    public void testDroneSubsystemCreation() {
        assertNotNull(new DroneSubsystem(scheduler, 1));
    }

    @Test
    @DisplayName("Test DroneSubsustem with multiple drone IDs")
    public void testMultipleDroneIDs() {
        DroneSubsystem drone1 = new DroneSubsystem(scheduler, 1);
        DroneSubsystem drone2 = new DroneSubsystem(scheduler, 2);
        DroneSubsystem drone3 = new DroneSubsystem(scheduler, 3);

        assertNotNull(drone1);
        assertNotNull(drone2);
        assertNotNull(drone3);
    }

    @Test
    @DisplayName("Test DroneSubsystem processes single event")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testDroneProcessesSingleEvent() throws InterruptedException {
        FireEvent event = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.NONE);

        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        Thread droneThread = new Thread(drone);

        scheduler.newFireEvent(event);
        scheduler.updateAllEventsDone();

        droneThread.start();
        waitUntil(() -> drone.getState() == Drone.DroneState.IDLE, 8000);

        // Drone should return to IDLE after processing event
        assertEquals(Drone.DroneState.IDLE, drone.getState());
    }

    @Test
    @DisplayName("Test DroneSubsystem processes multiple events sequentially")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    public void testDroneProcessesMultipleEvents() throws InterruptedException {
        FireEvent event1 = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.NONE);
        FireEvent event2 = new FireEvent("14:10:00", 2, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.Moderate, FireEvent.FaultType.NONE);

        scheduler.newFireEvent(event1);
        scheduler.newFireEvent(event2);
        scheduler.updateAllEventsDone();

        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        Thread droneThread = new Thread(drone);
        droneThread.start();
        
        // Wait for drone to process both events and return to idle
        waitUntil(() -> drone.getState() == Drone.DroneState.IDLE, 18000);
        assertEquals(Drone.DroneState.IDLE, drone.getState());
    }

    @Test
    @DisplayName("Test multiple drones can be created")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    public void testMultipleDrones() throws InterruptedException {
        FireEvent event1 = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.NONE);
        FireEvent event2 = new FireEvent("14:10:00", 2, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.Moderate, FireEvent.FaultType.NONE);

        scheduler.newFireEvent(event1);
        scheduler.newFireEvent(event2);
        scheduler.updateAllEventsDone();

        DroneSubsystem drone1 = new DroneSubsystem(scheduler, 1);
        DroneSubsystem drone2 = new DroneSubsystem(scheduler, 2);
        Thread drone1Thread = new Thread(drone1);
        Thread drone2Thread = new Thread(drone2);

        drone1Thread.start();
        drone2Thread.start();

        // Wait for both drones to process their events
        waitUntil(() -> drone1.getState() == Drone.DroneState.IDLE, 18000);
        waitUntil(() -> drone2.getState() == Drone.DroneState.IDLE, 18000);
        
        assertEquals(Drone.DroneState.IDLE, drone1.getState());
        assertEquals(Drone.DroneState.IDLE, drone2.getState());
    }

    @Test
    @DisplayName("Test DroneSubsystem with different event severity levels")
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testDroneWithDifferentSeverityEvents() throws InterruptedException {
        FireEvent eventLow = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.NONE);
        FireEvent eventMod = new FireEvent("14:05:00", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Moderate, FireEvent.FaultType.NONE);
        FireEvent eventHigh = new FireEvent("14:10:00", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High, FireEvent.FaultType.NONE);

        scheduler.newFireEvent(eventLow);
        scheduler.newFireEvent(eventMod);
        scheduler.newFireEvent(eventHigh);
        scheduler.updateAllEventsDone();

        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        Thread droneThread = new Thread(drone);
        droneThread.start();
        
        // Wait for drone to process all three events
        waitUntil(() -> drone.getState() == Drone.DroneState.IDLE, 23000);
        assertEquals(Drone.DroneState.IDLE, drone.getState());
    }

    @Test
    @DisplayName("Test Drone for computing travel time from base to zone center with loaded speed")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testComputeEnrouteFromBase() {
        Drone drone = new Drone(1);
        Zone zone1 = new Zone(1, 0, 0, 700, 600);
        
        double expected = Math.sqrt(350 * 350 + 300 * 300) / Drone.CRUISE_SPEED_LOADED;
        double actual = drone.computeTravelTime(zone1.getCenterX(), zone1.getCenterY(), true);
        assertEquals(expected, actual, 0.01);
    }

    @Test
    @DisplayName("Test Drone computes required volume for different event severity levels")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testComputeExtinguishAllSeverities() {
        Drone drone = new Drone(1);
        FireEvent eventLow = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, 
                FireEvent.Severity.Low, FireEvent.FaultType.NONE);
        FireEvent eventMod = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, 
                FireEvent.Severity.Moderate, FireEvent.FaultType.NONE);
        FireEvent eventHigh = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, 
                FireEvent.Severity.High, FireEvent.FaultType.NONE);

        assertEquals(Drone.LOW_VOLUME, drone.getRequiredVolume(eventLow), 0.01);
        assertEquals(Drone.MODERATE_VOLUME, drone.getRequiredVolume(eventMod), 0.01);
        assertEquals(Drone.HIGH_VOLUME, drone.getRequiredVolume(eventHigh), 0.01);
    }

    @Test
    @DisplayName("Test Drone location after moving to zone center")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testMoveToCenter() {
        Zone zone2 = new Zone(2, 0, 600, 650, 1500);

        Drone drone = new Drone(1);
        drone.setLocation(zone2.getCenterX(), zone2.getCenterY());

        assertEquals(325.0, drone.getX(), 0.01);
        assertEquals(1050.0, drone.getY(), 0.01);
    }

    @Test
    @DisplayName("Test Drone return travel time from zone center to base with unloaded speed")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testReturnToBaseSpeed() {
        Drone drone = new Drone(1);
        Zone zone1 = new Zone(1, 0, 0, 700, 600);
        
        drone.setLocation(zone1.getCenterX(), zone1.getCenterY());
        double expected = Math.sqrt(350 * 350 + 300 * 300) / Drone.CRUISE_SPEED_UNLOADED;

        double actual = drone.computeTravelTime(0, 0, false);
        assertEquals(expected, actual, 0.01);
    }

    @Test
    @DisplayName("Test Drone resets location to base")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testResetBaseLocation() {
        Drone drone = new Drone(1);
        Zone zone1 = new Zone(1, 0, 0, 700, 600);

        drone.setLocation(zone1.getCenterX(), zone1.getCenterY());
        drone.setLocation(0, 0);

        assertEquals(0.0, drone.getX(), 0.01);
        assertEquals(0.0, drone.getY(), 0.01);
    }

    @Test
    @DisplayName("Test Drone distance calculation")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testDistanceCalculation() {
        Drone drone = new Drone(1);
        Zone zone1 = new Zone(1, 0, 0, 700, 600);
        
        double expected = Math.sqrt(350 * 350 + 300 * 300);
        double actual = drone.distanceTo(zone1.getCenterX(), zone1.getCenterY());
        assertEquals(expected, actual, 0.01);
    }

    @Test
    @DisplayName("Test Drone state transitions through initialization")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testDroneInitialState() {
        Drone drone = new Drone(1);
        
        assertEquals(Drone.DroneState.IDLE, drone.getState());
        assertEquals(0.0, drone.getX(), 0.01);
        assertEquals(0.0, drone.getY(), 0.01);
        assertEquals(100.0, drone.getAgentLevel(), 0.01);
    }

    @Test
    @DisplayName("Test Drone agent consumption")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testAgentConsumption() {
        Drone drone = new Drone(1);
        
        drone.consumeAgent(25.0);
        assertEquals(75.0, drone.getAgentLevel(), 0.01);
        
        drone.consumeAgent(100.0);
        assertEquals(0.0, drone.getAgentLevel(), 0.01);
    }

    @Test
    @DisplayName("Test Drone mission assignment")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testMissionAssignment() {
        Drone drone = new Drone(1);
        FireEvent event = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.NONE);

        drone.setCurrentMission(event);
        assertNotNull(drone.getCurrentMission());
        assertEquals(event, drone.getCurrentMission());
    }

    @Test
    @DisplayName("Test STUCK_IN_FLIGHT soft fault allows recovery to IDLE")
    @Timeout(value = 35, unit = TimeUnit.SECONDS)
    public void testTimingBasedStuckInFlightDetection() throws InterruptedException {
        FireEvent stuckEvent = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.STUCK_IN_FLIGHT);
        scheduler.newFireEvent(stuckEvent);
        scheduler.updateAllEventsDone();

        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        Thread t = new Thread(drone);
        t.start();

        waitUntil(() -> drone.getState() == Drone.DroneState.FAULTED, 8000);
        assertEquals(Drone.DroneState.FAULTED, drone.getState());
        
        // SOFT FAULT: Drone recovers through handleFaultRecovery(), returns to IDLE briefly,
        // Wait for thread to finish
        t.join(30000);
        assertFalse(t.isAlive(), "Drone thread should complete after soft fault recovery and requeue processing");
    }

    @Test
    @DisplayName("Test NOZZLE_JAMMED hard fault causes permanent shutdown (NOT recovery)")
    @Timeout(value = 20, unit = TimeUnit.SECONDS)
    public void testNozzleJammedFaultDetection() throws InterruptedException {
        FireEvent nozzleEvent = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.NOZZLE_JAMMED);
        scheduler.newFireEvent(nozzleEvent);
        scheduler.updateAllEventsDone();

        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        Thread t = new Thread(drone);
        t.start();
        
        // Drone should detect NOZZLE_JAMMED fault during extinguishing phase
        waitUntil(() -> drone.getState() == Drone.DroneState.FAULTED, 10000);
        assertEquals(Drone.DroneState.FAULTED, drone.getState());
        
        // HARD FAULT: Drone should NOT recover to IDLE
        Thread.sleep(2000);
        
        // Verify drone remains FAULTED (hard fault - permanent)
        assertEquals(Drone.DroneState.FAULTED, drone.getState());
    }

    @Test
    @DisplayName("Test COMMUNICATION_LOST soft fault allows recovery to IDLE")
    @Timeout(value = 35, unit = TimeUnit.SECONDS)
    public void testCommunicationLostFaultDetection() throws InterruptedException {
        FireEvent commEvent = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.COMMUNICATION_LOST);
        scheduler.newFireEvent(commEvent);
        scheduler.updateAllEventsDone();

        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        Thread t = new Thread(drone);
        t.start();
        
        // Drone should detect COMMUNICATION_LOST via timing or packet verification
        waitUntil(() -> drone.getState() == Drone.DroneState.FAULTED, 15000);
        assertEquals(Drone.DroneState.FAULTED, drone.getState());
        
        // SOFT FAULT: Drone recovers through handleFaultRecovery(), returns to IDLE briefly,
        t.join(30000);
        assertFalse(t.isAlive(), "Drone thread should complete after soft fault recovery and requeue processing");
    }

    @Test
    @DisplayName("Test soft fault recovery cycle with complete state transitions")
    @Timeout(value = 35, unit = TimeUnit.SECONDS)
    public void testFaultRecoveryAndRequeue() throws InterruptedException {
        // Event that will trigger STUCK_IN_FLIGHT (soft fault)
        FireEvent stuckEvent = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.STUCK_IN_FLIGHT);
        scheduler.newFireEvent(stuckEvent);
        scheduler.updateAllEventsDone();

        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        Thread t = new Thread(drone);
        t.start();
        
        // Wait for fault to be detected
        waitUntil(() -> drone.getState() == Drone.DroneState.FAULTED, 10000);
        assertEquals(Drone.DroneState.FAULTED, drone.getState());
        
        // Wait for recovery and requeue processing to complete
        // The thread should finish successfully after processing the requeued event
        t.join(30000);
        assertFalse(t.isAlive(), "Drone thread should complete after soft fault recovery and requeue event processing");
    }

    @Test
    @DisplayName("Test normal mission completes without fault when FaultType is NONE")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    public void testNormalMissionWithoutFault() throws InterruptedException {
        FireEvent normalEvent = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low, FireEvent.FaultType.NONE);
        scheduler.newFireEvent(normalEvent);
        scheduler.updateAllEventsDone();

        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        Thread t = new Thread(drone);
        t.start();

        t.join(25000);
        assertFalse(t.isAlive(), "Drone thread should complete normal mission successfully");
    }


    /**
     * helper function
     * @param condition
     * @param timeout
     */
    private static void waitUntil(BooleanSupplier condition, long timeout) {
        long start = System.currentTimeMillis();
        while(System.currentTimeMillis() - start < timeout) {
            if(condition.get()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * helper function
     */
    private interface BooleanSupplier {
        boolean get();
    }
}
