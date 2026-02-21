import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for the DroneSubsystem class.
 * Tests drone behaviour, event processing, and integration with the Scheduler.
 *
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version February 14, 2026
 */
public class DroneSubsystemTest {

    private Scheduler scheduler;
    private String testZoneFilePath;

    @BeforeEach
    public void setup() throws IOException {
        // Create a temporary zone file for testing
        testZoneFilePath = "test/test_zones.csv";
        createTestZoneFile(testZoneFilePath);
        scheduler = new Scheduler(testZoneFilePath);
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
        assertDoesNotThrow(() -> new DroneSubsystem(scheduler, 1));
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
                FireEvent.Severity.Low);

        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));

        scheduler.newFireEvent(event);
        scheduler.updateAllEventsDone();

        droneThread.start();
        droneThread.join();

        // If thread completes, drone successfully processed the event
        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test DroneSubsystem completes event after processing")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testDroneCompletesEvent() throws InterruptedException {
        FireEvent event = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);

        scheduler.newFireEvent(event);
        scheduler.updateAllEventsDone();

        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));
        droneThread.start();

        // Wait for drone to process...
        Thread.sleep(10000);

        FireEvent completed = scheduler.getCompletedEvent();
        assertNotNull(completed);
        assertEquals(1, completed.getZoneID());

        droneThread.join();
    }

    @Test
    @DisplayName("Test DroneSubsystem processes multiple events sequentially")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testDroneProcessesMultipleEvents() throws InterruptedException {
        FireEvent event1 = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);
        FireEvent event2 = new FireEvent("14:10:00", 2, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.Moderate);

        scheduler.newFireEvent(event1);
        scheduler.newFireEvent(event2);
        scheduler.updateAllEventsDone();

        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));
        droneThread.start();
        droneThread.join();

        // If thread completes, drone successfully processed the events
        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test DroneSubsystem terminates when there are no more events")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testDroneTerminatesWhenNoEvents() throws InterruptedException {
        scheduler.updateAllEventsDone();

        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));
        droneThread.start();
        droneThread.join();

        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test multiple drones can be created")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testMultipleDrones() throws InterruptedException {
        FireEvent event1 = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);
        FireEvent event2 = new FireEvent("14:10:00", 2, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.Moderate);

        scheduler.newFireEvent(event1);
        scheduler.newFireEvent(event2);
        scheduler.updateAllEventsDone();

        Thread drone1Thread = new Thread(new DroneSubsystem(scheduler, 1));
        Thread drone2Thread = new Thread(new DroneSubsystem(scheduler, 2));

        drone1Thread.start();
        drone2Thread.start();

        drone1Thread.join();
        drone2Thread.join();

        assertFalse(drone1Thread.isAlive());
        assertFalse(drone2Thread.isAlive());
    }

    @Test
    @DisplayName("Test DroneSubsystem with different event severity levels")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testDroneWithDifferentSeverityEvents() throws InterruptedException {
        FireEvent eventLow = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);
        FireEvent eventMod = new FireEvent("14:05:00", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Moderate);
        FireEvent eventHigh = new FireEvent("14:10:00", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);

        scheduler.newFireEvent(eventLow);
        scheduler.newFireEvent(eventMod);
        scheduler.newFireEvent(eventHigh);
        scheduler.updateAllEventsDone();

        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));
        droneThread.start();
        droneThread.join();

        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test DroneSubsystem handles events in different zones")
    public void testDroneHandlesDifferentZones() throws InterruptedException {
        FireEvent event1 = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);
        FireEvent event2 = new FireEvent("14:10:00", 2, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);

        scheduler.newFireEvent(event1);
        scheduler.newFireEvent(event2);
        scheduler.updateAllEventsDone();

        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));
        droneThread.start();
        droneThread.join();

        assertFalse(droneThread.isAlive());
    }


    @Test
    @DisplayName("Test DroneSubsystem for enRoute to zone center with loaded speed")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testComputeEnrouteFromBase() {
        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        FireEvent event  = new FireEvent ("14:03:15", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        double expected = Math.sqrt(350 * 350 + 300 * 300) / 10.0;
        double actual = drone.getEnRoute(event);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Test DroneSubsystem for extinguishing time with different event severity levels")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testComputeExtinguishAllSeverities() {
        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        FireEvent eventLow = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);
        FireEvent eventMod = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Moderate);
        FireEvent eventHigh = new FireEvent("14:00:00",  1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.High);

        assertEquals(10.0 / 2.0 + 1.0, drone.getExtinguish(eventLow));
        assertEquals(20.0 / 2.0 + 1.0, drone.getExtinguish(eventMod));
        assertEquals(30.0 / 2.0 + 1.0, drone.getExtinguish(eventHigh));
    }

    @Test
    @DisplayName("Test DroneSubsystem for updating current location when at zone center")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testMoveToCenter() {
        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        FireEvent event = new FireEvent("14:00:00", 2, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        drone.toZoneCenter(event);

        assertEquals(325.0, drone.getCurrentX());
        assertEquals(1050.0, drone.getCurrentY());
    }

    @Test
    @DisplayName("Test DroneSubsystem for return to based with unloaded speed")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testReturnToBaseSpeed() {
        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        FireEvent event = new FireEvent("14:03:15",  1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        drone.toZoneCenter(event);
        double expected = Math.sqrt(350 * 350 + 300 * 300) / 15.0;

        double actual = drone.getReturn(event);
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Test DroneSubsystem for updating current location when at base")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testResetBaseLocation() {
        DroneSubsystem drone = new DroneSubsystem(scheduler, 1);
        FireEvent event = new FireEvent("14:00:00",  1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        drone.toZoneCenter(event);
        drone.toBase();

        assertEquals(0.0, drone.getCurrentX());
        assertEquals(0.0, drone.getCurrentY());
    }
}
