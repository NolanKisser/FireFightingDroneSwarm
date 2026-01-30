import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for the Scheduler class.
 * Tests the core scheduling functionality, event management, and synchronization.
 *
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class SchedulerTest {

    private Scheduler scheduler;
    private String testZoneFilePath;

    @BeforeEach
    public void setUp() throws IOException {
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
            writer.write("3,(700;0),(1400;600)\n");
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
    @DisplayName("Test Scheduler loads zones correctly from CSV file")
    public void testSchedulerLoadsZones() {
        Map<Integer, Zone> zones = scheduler.getZones();

        assertNotNull(zones);
        assertEquals(3, zones.size());
        assertTrue(zones.containsKey(1));
        assertTrue(zones.containsKey(2));
        assertTrue(zones.containsKey(3));
    }

    @Test
    @DisplayName("Test Scheduler loads zone coordinates correctly")
    public void testSchedulerZoneCoordinates() {
        Map<Integer, Zone> zones = scheduler.getZones();

        Zone zone1 = zones.get(1);
        assertEquals(0, zone1.getX1());
        assertEquals(0, zone1.getY1());
        assertEquals(700, zone1.getX2());
        assertEquals(600, zone1.getY2());

        Zone zone2 = zones.get(2);
        assertEquals(0, zone2.getX1());
        assertEquals(600, zone2.getY1());
        assertEquals(650, zone2.getX2());
        assertEquals(1500, zone2.getY2());

        Zone zone3 = zones.get(3);
        assertEquals(700, zone3.getX1());
        assertEquals(0, zone3.getY1());
        assertEquals(1400, zone3.getX2());
        assertEquals(600, zone3.getY2());
    }

    @Test
    @DisplayName("Test adding a new fire event to the scheduler")
    public void testNewFireEvent() {
        FireEvent event = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);

        assertDoesNotThrow(() -> scheduler.newFireEvent(event));
    }

    @Test
    @DisplayName("Test adding multiple fire events to the scheduler")
    public void testMultipleFireEvents() {
        FireEvent event1 = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);
        FireEvent event2 = new FireEvent("14:10:00", 2, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.Moderate);
        FireEvent event3 = new FireEvent("14:15:00", 3, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);

        assertDoesNotThrow(() -> {
            scheduler.newFireEvent(event1);
            scheduler.newFireEvent(event2);
            scheduler.newFireEvent(event3);
        });
    }

    @Test
    @DisplayName("Test getting next fire event from scheduler")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testGetNextFireEvent() throws InterruptedException {
        FireEvent event = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);

        // Add event in a separate thread to avoid blocking
        Thread addThread = new Thread(() -> scheduler.newFireEvent(event));
        addThread.start();
        addThread.join();

        FireEvent retrieved = scheduler.getNextFireEvent();

        assertNotNull(retrieved);
        assertEquals(event.getTime(), retrieved.getTime());
        assertEquals(event.getZoneID(), retrieved.getZoneID());
        assertEquals(event.getType(), retrieved.getType());
        assertEquals(event.getSeverity(), retrieved.getSeverity());
    }

    @Test
    @DisplayName("Test FIFO order of fire events")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testFireEventFIFOOrder() throws InterruptedException {
        FireEvent event1 = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);
        FireEvent event2 = new FireEvent("14:10:00", 2, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.Moderate);
        FireEvent event3 = new FireEvent("14:15:00", 3, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);

        Thread addThread = new Thread(() -> {
            scheduler.newFireEvent(event1);
            scheduler.newFireEvent(event2);
            scheduler.newFireEvent(event3);
        });
        addThread.start();
        addThread.join();

        FireEvent retrieved1 = scheduler.getNextFireEvent();
        FireEvent retrieved2 = scheduler.getNextFireEvent();
        FireEvent retrieved3 = scheduler.getNextFireEvent();

        assertEquals(1, retrieved1.getZoneID());
        assertEquals(2, retrieved2.getZoneID());
        assertEquals(3, retrieved3.getZoneID());
    }

    @Test
    @DisplayName("Test completing a fire event")
    public void testCompleteFireEvent() {
        FireEvent event = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);

        assertDoesNotThrow(() -> scheduler.completeFireEvent(event));
    }

    @Test
    @DisplayName("Test getting completed event")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testGetCompletedEvent() throws InterruptedException {
        FireEvent event = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);

        Thread completeThread = new Thread(() -> scheduler.completeFireEvent(event));
        completeThread.start();
        completeThread.join();

        FireEvent completed = scheduler.getCompletedEvent();

        assertNotNull(completed);
        assertEquals(event.getZoneID(), completed.getZoneID());
    }

    @Test
    @DisplayName("Test updateAllEventsDone signal")
    public void testUpdateAllEventsDone() {
        assertDoesNotThrow(() -> scheduler.updateAllEventsDone());
    }

    @Test
    @DisplayName("Test getNextFireEvent returns null when all events done and queue empty")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testGetNextFireEventReturnsNullWhenDone() throws InterruptedException {
        Thread doneThread = new Thread(() -> scheduler.updateAllEventsDone());
        doneThread.start();
        doneThread.join();

        FireEvent retrieved = scheduler.getNextFireEvent();

        assertNull(retrieved);
    }

    @Test
    @DisplayName("Test getCompletedEvent returns null when simulation complete")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testGetCompletedEventReturnsNullWhenDone() throws InterruptedException {
        Thread doneThread = new Thread(() -> scheduler.updateAllEventsDone());
        doneThread.start();
        doneThread.join();

        FireEvent completed = scheduler.getCompletedEvent();

        assertNull(completed);
    }

    @Test
    @DisplayName("Test scheduler handles producer-consumer pattern correctly")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testProducerConsumerPattern() throws InterruptedException {
        final int NUM_EVENTS = 5;

        // Producer thread
        Thread producer = new Thread(() -> {
            for (int i = 1; i <= NUM_EVENTS; i++) {
                FireEvent event = new FireEvent("14:0" + i + ":00", i,
                        FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);
                scheduler.newFireEvent(event);
            }
            scheduler.updateAllEventsDone();
        });

        // Consumer thread
        Thread consumer = new Thread(() -> {
            int count = 0;
            while (true) {
                FireEvent event = scheduler.getNextFireEvent();
                if (event == null) break;
                count++;
            }
            assertEquals(NUM_EVENTS, count);
        });

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();
    }

    @Test
    @DisplayName("Test scheduler synchronization with multiple events")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testSchedulerSynchronization() throws InterruptedException {
        final int NUM_EVENTS = 10;

        Thread producer = new Thread(() -> {
            for (int i = 0; i < NUM_EVENTS; i++) {
                FireEvent event = new FireEvent("14:00:0" + i, i % 3 + 1,
                        FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);
                scheduler.newFireEvent(event);
            }
        });

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < NUM_EVENTS; i++) {
                FireEvent event = scheduler.getNextFireEvent();
                assertNotNull(event);
            }
        });

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();
    }

    @Test
    @DisplayName("Test completed events FIFO order")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testCompletedEventsFIFOOrder() throws InterruptedException {
        FireEvent event1 = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);
        FireEvent event2 = new FireEvent("14:10:00", 2, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.Moderate);

        Thread completeThread = new Thread(() -> {
            scheduler.completeFireEvent(event1);
            scheduler.completeFireEvent(event2);
        });
        completeThread.start();
        completeThread.join();

        FireEvent completed1 = scheduler.getCompletedEvent();
        FireEvent completed2 = scheduler.getCompletedEvent();

        assertEquals(1, completed1.getZoneID());
        assertEquals(2, completed2.getZoneID());
    }
}