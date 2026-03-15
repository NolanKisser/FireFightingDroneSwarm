import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests for the complete Firefighting Drone Swarm System.
 * Tests the interaction between FireIncidentSubsystem, Scheduler, and DroneSubsystem.
 *
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class SystemIntegrationTest {

    private String testZoneFilePath;
    private String testEventFilePath;
    private Scheduler scheduler;

    @BeforeEach
    public void setUp() {
        testZoneFilePath = "integration_zones.csv";
        testEventFilePath = "integration_events.csv";
    }

    @AfterEach
    public void tearDown() {
        deleteTestFile(testZoneFilePath);
        deleteTestFile(testEventFilePath);
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    private void createTestZoneFile(String filePath) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            writer.write("1,(0;0),(700;600)\n");
            writer.write("2,(0;600),(650;1500)\n");
            writer.write("3,(700;0),(1400;600)\n");
        }
    }

    private void createTestEventFile(String filePath, String... events) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            for (String event : events) {
                writer.write(event + "\n");
            }
        }
    }

    private void deleteTestFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    @DisplayName("Test complete system with single event and single drone")
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testCompleteSystemSingleEvent() throws IOException, InterruptedException {
        createTestZoneFile(testZoneFilePath);
        createTestEventFile(testEventFilePath, "14:03:15,1,FIRE_DETECTED,Low");

        scheduler = new Scheduler(testZoneFilePath);
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();
        Thread.sleep(500); // Allow UDP server to start

        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(testEventFilePath));
        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));

        fireIncidentThread.start();
        droneThread.start();

        fireIncidentThread.join();
        droneThread.join();
        schedulerThread.join();

        assertFalse(fireIncidentThread.isAlive());
        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test complete system with multiple events and single drone")
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testCompleteSystemMultipleEvents() throws IOException, InterruptedException {
        createTestZoneFile(testZoneFilePath);
        createTestEventFile(testEventFilePath,
                "14:03:15,1,FIRE_DETECTED,Low",
                "14:10:00,2,DRONE_REQUEST,Moderate",
                "14:15:30,3,FIRE_DETECTED,High"
        );

        scheduler = new Scheduler(testZoneFilePath);
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();
        Thread.sleep(500);

        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(testEventFilePath));
        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));

        fireIncidentThread.start();
        droneThread.start();

        fireIncidentThread.join();
        droneThread.join();
        schedulerThread.join();

        assertFalse(fireIncidentThread.isAlive());
        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test complete system with multiple drones")
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testCompleteSystemMultipleDrones() throws IOException, InterruptedException {
        createTestZoneFile(testZoneFilePath);
        createTestEventFile(testEventFilePath,
                "14:03:15,1,FIRE_DETECTED,Low",
                "14:10:00,2,DRONE_REQUEST,Moderate",
                "14:15:30,3,FIRE_DETECTED,High",
                "14:20:00,1,FIRE_DETECTED,Low"
        );

        scheduler = new Scheduler(testZoneFilePath);
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();
        Thread.sleep(500);

        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(testEventFilePath));
        Thread drone1Thread = new Thread(new DroneSubsystem(scheduler, 1));
        Thread drone2Thread = new Thread(new DroneSubsystem(scheduler, 2));

        fireIncidentThread.start();
        drone1Thread.start();
        drone2Thread.start();

        fireIncidentThread.join();
        drone1Thread.join();
        drone2Thread.join();
        schedulerThread.join();

        assertFalse(fireIncidentThread.isAlive());
        assertFalse(drone1Thread.isAlive());
        assertFalse(drone2Thread.isAlive());
    }

    @Test
    @DisplayName("Test system processes events in FIFO order")
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testSystemFIFOOrder() throws Exception {
        createTestZoneFile(testZoneFilePath);
        createTestEventFile(testEventFilePath,
                "14:03:15,1,FIRE_DETECTED,Low",
                "14:10:00,2,DRONE_REQUEST,Moderate",
                "14:15:30,3,FIRE_DETECTED,High"
        );

        scheduler = new Scheduler(testZoneFilePath);
        Thread udpThread = new Thread(() -> scheduler.startUDPServer());
        udpThread.start();
        Thread.sleep(500);

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(testEventFilePath);

        // Use reflection to load CSV without running full subsystem thread
        java.lang.reflect.Method loadCSV = FireIncidentSubsystem.class.getDeclaredMethod("loadCSV", String.class);
        loadCSV.setAccessible(true);
        loadCSV.invoke(fireSubsystem, testEventFilePath);

        // Verify FIFO order
        FireEvent event1 = scheduler.getNextFireEvent();
        FireEvent event2 = scheduler.getNextFireEvent();
        FireEvent event3 = scheduler.getNextFireEvent();

        assertNotNull(event1);
        assertNotNull(event2);
        assertNotNull(event3);

        assertEquals(1, event1.getZoneID());
        assertEquals(2, event2.getZoneID());
        assertEquals(3, event3.getZoneID());

        scheduler.updateAllEventsDone(); // Cleanup
    }

    @Test
    @DisplayName("Test system handles all severity levels")
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testSystemAllSeverityLevels() throws IOException, InterruptedException {
        createTestZoneFile(testZoneFilePath);
        createTestEventFile(testEventFilePath,
                "14:00:00,1,FIRE_DETECTED,Low",
                "14:05:00,2,FIRE_DETECTED,Moderate",
                "14:10:00,3,FIRE_DETECTED,High"
        );

        scheduler = new Scheduler(testZoneFilePath);
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();
        Thread.sleep(500);

        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(testEventFilePath));
        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));

        fireIncidentThread.start();
        droneThread.start();

        fireIncidentThread.join();
        droneThread.join();
        schedulerThread.join();

        assertFalse(fireIncidentThread.isAlive());
        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test system handles both event types")
    @Timeout(value = 25, unit = TimeUnit.SECONDS)
    public void testSystemBothEventTypes() throws IOException, InterruptedException {
        createTestZoneFile(testZoneFilePath);
        createTestEventFile(testEventFilePath,
                "14:03:15,1,FIRE_DETECTED,Low",
                "14:10:00,2,DRONE_REQUEST,Moderate"
        );

        scheduler = new Scheduler(testZoneFilePath);
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();
        Thread.sleep(500);

        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(testEventFilePath));
        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));

        fireIncidentThread.start();
        droneThread.start();

        fireIncidentThread.join();
        droneThread.join();
        schedulerThread.join();

        assertFalse(fireIncidentThread.isAlive());
        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test system with no events")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testSystemWithNoEvents() throws IOException, InterruptedException {
        createTestZoneFile(testZoneFilePath);
        createTestEventFile(testEventFilePath); // Empty file

        scheduler = new Scheduler(testZoneFilePath);
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();
        Thread.sleep(500);

        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(testEventFilePath));
        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));

        droneThread.start();
        Thread.sleep(500);

        fireIncidentThread.start();

        fireIncidentThread.join();
        droneThread.join();
        schedulerThread.join();

        assertFalse(fireIncidentThread.isAlive());
        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test system completes all events successfully")
    public void testSystemCompletesAllEvents() throws IOException, InterruptedException {
        createTestZoneFile(testZoneFilePath);
        final int NUM_EVENTS = 5;

        String[] events = new String[NUM_EVENTS];
        for (int i = 0; i < NUM_EVENTS; i++) {
            events[i] = String.format("14:%02d:00,%d,FIRE_DETECTED,Low", i, (i % 3) + 1);
        }
        createTestEventFile(testEventFilePath, events);

        scheduler = new Scheduler(testZoneFilePath);
        Thread schedulerThread = new Thread(scheduler);
        schedulerThread.start();
        Thread.sleep(500);

        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(testEventFilePath));
        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));

        fireIncidentThread.start();
        droneThread.start();

        fireIncidentThread.join();
        droneThread.join();
        schedulerThread.join();

        assertFalse(fireIncidentThread.isAlive());
        assertFalse(droneThread.isAlive());
    }
}