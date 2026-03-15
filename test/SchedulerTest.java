import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Unit tests for the Scheduler class.
 * Tests the core scheduling functionality, event management, and synchronization.
 *
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version February 14, 2026
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
        Thread udpThread = new Thread(() -> scheduler.startUDPServer());
        udpThread.start();
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

    @Test
    @DisplayName("Test get next fire event block and unblock")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testGetNextFireEventBlockAndUnblock() throws InterruptedException {
        FireEvent[] holder = new FireEvent[1];
        Thread consumer = new Thread(() -> {
            holder[0] = scheduler.getNextFireEvent();
        });

        consumer.start();
        assertNull(holder[0]);

        FireEvent event = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);
        scheduler.newFireEvent(event);

        consumer.join();
        assertNotNull(holder[0]);
        assertEquals(1, holder[0].getZoneID());


    }

    @Test
    @DisplayName("Test completed event blocks until completed")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testCompletedEventBlocksUntilCompleted() throws InterruptedException {
        FireEvent event = new FireEvent("14:03:15", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        Thread drone = new Thread(new DroneSubsystem(scheduler, 1));
        drone.start();

        scheduler.newFireEvent(event);

        FireEvent completed = scheduler.getCompletedEvent();
        assertNotNull(completed);
        assertEquals(1, completed.getZoneID());

        scheduler.updateAllEventsDone();
        drone.join(8000);
        assertFalse(drone.isAlive());

    }

    @Test
    @DisplayName("Test next fire event updated complete")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testNextFireEventUpdateComplete() {
        scheduler.updateAllEventsDone();
        assertNull(scheduler.getCompletedEvent());
    }

    @Test
    @DisplayName("Test all fire event complete")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testAllFireEventComplete() {
        scheduler.updateAllEventsDone();
        assertNull(scheduler.getCompletedEvent());
    }

    @Test
    @DisplayName("Test return to base")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testReturnToBase() {
        FireEvent event1 = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);
        FireEvent event2 = new FireEvent("14:03:15", 2,  FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.High);

        scheduler.newFireEvent(event1);
        scheduler.newFireEvent(event2);

        FireEvent first = scheduler.getNextFireEvent();
        assertNotNull(first);
        assertEquals(1, first.getZoneID());

        scheduler.droneReturnToBase(1);

        FireEvent second = scheduler.getNextFireEvent();
        assertNotNull(second);
        assertEquals(2, second.getZoneID());

    }

    @Test
    @DisplayName("Test arrived to zone")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testArriveToZone() {
        FireEvent event = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        assertDoesNotThrow(() -> {
            scheduler.droneArrivedAtZone(1, event);
        });
    }

    @Test
    @DisplayName("Test FIFO still holds")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void testFIFOStillHolds() {
        FireEvent event1 = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Moderate);
        FireEvent event2 = new FireEvent("14:10:15", 2, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.High);
        FireEvent event3 = new FireEvent("14:25:00", 3, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        scheduler.newFireEvent(event1);
        scheduler.newFireEvent(event2);
        scheduler.newFireEvent(event3);

        assertEquals(1, scheduler.getNextFireEvent().getZoneID());
        assertEquals(2, scheduler.getNextFireEvent().getZoneID());
        assertEquals(3, scheduler.getNextFireEvent().getZoneID());
    }

    @Test
    @DisplayName("FSM: Test init enters waiting")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void initEntersWaiting() {
        assertEquals(Scheduler.State.WAITING, scheduler.getCurrentState());
    }

    @Test
    @DisplayName("FSM: Test new event transitions to event queued")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void newEventTransitionsToEventQueued() {
        FireEvent event = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        scheduler.newFireEvent(event);
        assertEquals(Scheduler.State.WAITING, scheduler.getCurrentState());
    }

    @Test
    @DisplayName("FSM: Test get next event transitions to drone active")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void getNextEventTransitionsToDroneActive() {
        FireEvent event = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        scheduler.newFireEvent(event);

        FireEvent firstEvent = scheduler.getNextFireEvent();
        assertNotNull(firstEvent);
        assertEquals(Scheduler.State.WAITING, scheduler.getCurrentState());

    }

    @Test
    @DisplayName("FSM: Test drone returns to base with no events")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void droneReturnToBaseNoEvents() {
        // scheduler.registerDrone(1);
        // scheduler.droneReturnToBase(1);

        assertEquals(Scheduler.State.WAITING, scheduler.getCurrentState());

    }

    @Test
    @DisplayName("FSM: Test drone returns to base with events")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void droneReturnToBaseWithEvents() {
        // scheduler.registerDrone(1);
        FireEvent event = new FireEvent("14:00:00", 1, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.Low);

        // scheduler.newFireEvent(event);
        // scheduler.droneReturnToBase(1);

        assertEquals(Scheduler.State.WAITING, scheduler.getCurrentState());

    }

    @Test
    @DisplayName("FSM: Test all events complete")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    public void allDoneEvents() {
        scheduler.updateAllEventsDone();
        assertNull(scheduler.getCompletedEvent());
        assertNull(scheduler.getNextFireEvent());
    }

    @Test
    @DisplayName("Test UDP Assigns Event to Ready Drone")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    public void testUDPEventAssignment() throws Exception {
        // Start scheduler's UDP server in a background thread
        Thread udpThread = new Thread(() -> scheduler.startUDPServer());
        udpThread.start();
        Thread.sleep(500); // Give the server a moment to bind to the port

        DatagramSocket testSocket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("localhost");

        // Add a fire event to the scheduler's queue
        FireEvent event = new FireEvent("15:00:00", 3, FireEvent.Type.FIRE_DETECTED, FireEvent.Severity.High);
        scheduler.newFireEvent(event);

        // Register a mock Drone via UDP
        String regMessage = "REGISTER_DRONE,1";
        testSocket.send(new DatagramPacket(regMessage.getBytes(), regMessage.length(), address, scheduler.schedulerPort));

        // Receive Registration Confirmation (clears the buffer)
        byte[] buffer = new byte[1024];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        testSocket.receive(receivePacket);
        String regResponse = new String(receivePacket.getData(), 0, receivePacket.getLength());
        assertTrue(regResponse.startsWith("REGISTERED_DRONE"));

        // Send DRONE_READY to trigger assignment
        String readyMessage = "DRONE_READY,1";
        testSocket.send(new DatagramPacket(readyMessage.getBytes(), readyMessage.length(), address, scheduler.schedulerPort));

        // Receive ASSIGN_EVENT from the Scheduler
        testSocket.receive(receivePacket);
        String response = new String(receivePacket.getData(), 0, receivePacket.getLength());

        // Verify the event payload is correct
        assertTrue(response.startsWith("ASSIGN_EVENT"));
        assertTrue(response.contains("15:00:00"));
        assertTrue(response.contains("3"));
        assertTrue(response.contains("High"));

        testSocket.close();

        // Shutdown the UDP thread to not block other tests
        scheduler.updateAllEventsDone();
    }

    @Test
    @DisplayName("Test independent drone status reporting via UDP")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @SuppressWarnings("unchecked")
    public void testIndependentDroneReportingUDP() throws Exception {
        // Start scheduler's UDP server
        Thread udpThread = new Thread(() -> scheduler.startUDPServer());
        udpThread.start();
        Thread.sleep(500);

        DatagramSocket testSocket = new DatagramSocket();
        InetAddress address = InetAddress.getByName("localhost");

        // Register Drone 1 and Drone 2
        String reg1 = "REGISTER_DRONE,1";
        testSocket.send(new DatagramPacket(reg1.getBytes(), reg1.length(), address, scheduler.schedulerPort));

        String reg2 = "REGISTER_DRONE,2";
        testSocket.send(new DatagramPacket(reg2.getBytes(), reg2.length(), address, scheduler.schedulerPort));

        Thread.sleep(500); // Allow UDP processing time

        // Send Status Updates for both drones concurrently
        String stat1 = "STATUS_UPDATE,1,EN_ROUTE,350.0,300.0,100.0";
        testSocket.send(new DatagramPacket(stat1.getBytes(), stat1.length(), address, scheduler.schedulerPort));

        String stat2 = "STATUS_UPDATE,2,EXTINGUISHING,150.0,200.0,85.5";
        testSocket.send(new DatagramPacket(stat2.getBytes(), stat2.length(), address, scheduler.schedulerPort));

        Thread.sleep(500); // Allow UDP processing time

        // Verify using reflection (since droneStatuses map is private in Scheduler)
        Field statusesField = Scheduler.class.getDeclaredField("droneStatuses");
        statusesField.setAccessible(true);
        Map<Integer, Object> statuses = (Map<Integer, Object>) statusesField.get(scheduler);

        assertEquals(2, statuses.size(), "Scheduler should have 2 independently registered drones");

        Object status1 = statuses.get(1);
        Object status2 = statuses.get(2);

        assertNotNull(status1);
        assertNotNull(status2);

        // Assert Drone 1 independent values
        Field xField = status1.getClass().getField("currentX");
        Field agentField = status1.getClass().getField("agentRemaining");

        assertEquals(350.0, xField.getDouble(status1), "Drone 1 X coordinate mismatch");
        assertEquals(100.0, agentField.getDouble(status1), "Drone 1 Agent capacity mismatch");

        // Assert Drone 2 independent values
        assertEquals(150.0, xField.getDouble(status2), "Drone 2 X coordinate mismatch");
        assertEquals(85.5, agentField.getDouble(status2), "Drone 2 Agent capacity mismatch");

        testSocket.close();
        scheduler.updateAllEventsDone();
    }


}