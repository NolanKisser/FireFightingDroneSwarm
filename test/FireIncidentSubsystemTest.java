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
 * Unit tests for the FireIncidentSubsystem class.
 * Tests CSV file reading, event submission to scheduler, and integration.
 *
 * In order to avoid race conditions for parsing tests, Java reflection is used
 * to call the private loadCSV() method directly. This allows to test correct
 * parsing of CSV files and event submission to the scheduler, without competing
 * with a drone for the events.
 *
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class FireIncidentSubsystemTest {

    private Scheduler scheduler;
    private String testZoneFilePath;
    private String testEventFilePath;

    @BeforeEach
    public void setUp() throws IOException {
        testZoneFilePath = "test_fire_zones.csv";
        testEventFilePath = "test_events.csv";

        createTestZoneFile(testZoneFilePath);
        scheduler = new Scheduler(testZoneFilePath);
    }

    @AfterEach
    public void tearDown() {
        deleteTestFile(testZoneFilePath);
        deleteTestFile(testEventFilePath);
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

    /**
     * Helper method to create a test event file
     */
    private void createTestEventFile(String filePath, String... events) throws IOException {
        try (FileWriter writer = new FileWriter(filePath)) {
            for (String event : events) {
                writer.write(event + "\n");
            }
        }
    }

    /**
     * Helper method to delete temporary test files
     */
    private void deleteTestFile(String filePath) {
        File file = new File(filePath);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * Helper method to load CSV without running full subsystem
     */
    private void loadCSVOnly(FireIncidentSubsystem subsystem, String filePath) throws Exception {
        java.lang.reflect.Method loadCSV = FireIncidentSubsystem.class.getDeclaredMethod("loadCSV", String.class);
        loadCSV.setAccessible(true);
        loadCSV.invoke(subsystem, filePath);
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem creation with valid parameters")
    public void testFireIncidentSubsystemCreation() throws IOException {
        createTestEventFile(testEventFilePath, "14:03:15,1,FIRE_DETECTED,Low");
        assertDoesNotThrow(() -> new FireIncidentSubsystem(scheduler, testEventFilePath));
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem reads and submits single event from CSV")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testReadSingleEvent() throws Exception {
        createTestEventFile(testEventFilePath, "14:03:15,1,FIRE_DETECTED,Low");

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(scheduler, testEventFilePath);
        loadCSVOnly(fireSubsystem, testEventFilePath);

        // Now retrieve and verify the event
        FireEvent event = scheduler.getNextFireEvent();
        assertNotNull(event, "Event should not be null");
        assertEquals("14:03:15", event.getTime());
        assertEquals(1, event.getZoneID());
        assertEquals(FireEvent.Type.FIRE_DETECTED, event.getType());
        assertEquals(FireEvent.Severity.Low, event.getSeverity());
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem reads multiple events from CSV in order")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testReadMultipleEvents() throws Exception {
        createTestEventFile(testEventFilePath,
                "14:03:15,1,FIRE_DETECTED,Low",
                "14:10:00,2,DRONE_REQUEST,Moderate",
                "14:15:30,3,FIRE_DETECTED,High"
        );

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(scheduler, testEventFilePath);
        loadCSVOnly(fireSubsystem, testEventFilePath);

        // Verify events are in correct order
        FireEvent event1 = scheduler.getNextFireEvent();
        assertNotNull(event1);
        assertEquals("14:03:15", event1.getTime());
        assertEquals(1, event1.getZoneID());
        assertEquals(FireEvent.Type.FIRE_DETECTED, event1.getType());
        assertEquals(FireEvent.Severity.Low, event1.getSeverity());

        FireEvent event2 = scheduler.getNextFireEvent();
        assertNotNull(event2);
        assertEquals("14:10:00", event2.getTime());
        assertEquals(2, event2.getZoneID());
        assertEquals(FireEvent.Type.DRONE_REQUEST, event2.getType());
        assertEquals(FireEvent.Severity.Moderate, event2.getSeverity());

        FireEvent event3 = scheduler.getNextFireEvent();
        assertNotNull(event3);
        assertEquals("14:15:30", event3.getTime());
        assertEquals(3, event3.getZoneID());
        assertEquals(FireEvent.Type.FIRE_DETECTED, event3.getType());
        assertEquals(FireEvent.Severity.High, event3.getSeverity());
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem parses FIRE_DETECTED event type correctly")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testParseFireDetectedType() throws Exception {
        createTestEventFile(testEventFilePath, "14:03:15,1,FIRE_DETECTED,High");

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(scheduler, testEventFilePath);
        loadCSVOnly(fireSubsystem, testEventFilePath);

        FireEvent event = scheduler.getNextFireEvent();
        assertNotNull(event);
        assertEquals(FireEvent.Type.FIRE_DETECTED, event.getType());
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem parses DRONE_REQUEST event type correctly")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testParseDroneRequestType() throws Exception {
        createTestEventFile(testEventFilePath, "14:10:00,2,DRONE_REQUEST,Moderate");

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(scheduler, testEventFilePath);
        loadCSVOnly(fireSubsystem, testEventFilePath);

        FireEvent event = scheduler.getNextFireEvent();
        assertNotNull(event);
        assertEquals(FireEvent.Type.DRONE_REQUEST, event.getType());
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem parses Low severity correctly")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testParseLowSeverity() throws Exception {
        createTestEventFile(testEventFilePath, "14:03:15,1,FIRE_DETECTED,Low");

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(scheduler, testEventFilePath);
        loadCSVOnly(fireSubsystem, testEventFilePath);

        FireEvent event = scheduler.getNextFireEvent();
        assertNotNull(event);
        assertEquals(FireEvent.Severity.Low, event.getSeverity());
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem parses Moderate severity correctly")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testParseModerateSeverity() throws Exception {
        createTestEventFile(testEventFilePath, "14:10:00,2,DRONE_REQUEST,Moderate");

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(scheduler, testEventFilePath);
        loadCSVOnly(fireSubsystem, testEventFilePath);

        FireEvent event = scheduler.getNextFireEvent();
        assertNotNull(event);
        assertEquals(FireEvent.Severity.Moderate, event.getSeverity());
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem parses High severity correctly")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testParseHighSeverity() throws Exception {
        createTestEventFile(testEventFilePath, "14:15:30,3,FIRE_DETECTED,High");

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(scheduler, testEventFilePath);
        loadCSVOnly(fireSubsystem, testEventFilePath);

        FireEvent event = scheduler.getNextFireEvent();
        assertNotNull(event);
        assertEquals(FireEvent.Severity.High, event.getSeverity());
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem handles CSV with whitespace")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testHandleWhitespace() throws Exception {
        createTestEventFile(testEventFilePath, "14:03:15 , 1 , FIRE_DETECTED , Low");

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(scheduler, testEventFilePath);
        loadCSVOnly(fireSubsystem, testEventFilePath);

        FireEvent event = scheduler.getNextFireEvent();
        assertNotNull(event);
        assertEquals(1, event.getZoneID());
        assertEquals(FireEvent.Severity.Low, event.getSeverity());
    }

    @Test
    @DisplayName("Test complete FireIncidentSubsystem run with drone processing")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    public void testCompleteSystemRun() throws IOException, InterruptedException {
        createTestEventFile(testEventFilePath, "14:03:15,1,FIRE_DETECTED,Low");

        // This test runs the complete system to verify everything works together
        Thread droneThread = new Thread(new DroneSubsystem(scheduler, 1));
        Thread fireThread = new Thread(new FireIncidentSubsystem(scheduler, testEventFilePath));

        fireThread.start();
        droneThread.start();

        fireThread.join();
        droneThread.join();

        // If both threads complete without hanging, the test passes
        assertFalse(fireThread.isAlive());
        assertFalse(droneThread.isAlive());
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem with empty CSV file")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testEmptyCSVFile() throws IOException, InterruptedException {
        createTestEventFile(testEventFilePath); // Empty file

        Thread fireThread = new Thread(new FireIncidentSubsystem(scheduler, testEventFilePath));
        fireThread.start();

        Thread.sleep(100);

        // Should return null since no events and all done
        FireEvent event = scheduler.getNextFireEvent();
        assertNull(event);

        fireThread.join();
    }

    @Test
    @DisplayName("Test FireIncidentSubsystem with events for different zones")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testEventsFromDifferentZones() throws Exception {
        createTestEventFile(testEventFilePath,
                "14:03:15,1,FIRE_DETECTED,Low",
                "14:10:00,2,FIRE_DETECTED,Moderate",
                "14:15:30,3,FIRE_DETECTED,High"
        );

        FireIncidentSubsystem fireSubsystem = new FireIncidentSubsystem(scheduler, testEventFilePath);
        loadCSVOnly(fireSubsystem, testEventFilePath);

        FireEvent event1 = scheduler.getNextFireEvent();
        FireEvent event2 = scheduler.getNextFireEvent();
        FireEvent event3 = scheduler.getNextFireEvent();

        assertNotNull(event1);
        assertNotNull(event2);
        assertNotNull(event3);

        // Verify they're from different zones
        assertNotEquals(event1.getZoneID(), event2.getZoneID());
        assertNotEquals(event2.getZoneID(), event3.getZoneID());
        assertNotEquals(event1.getZoneID(), event3.getZoneID());
    }
}
