import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the FireEvent class.
 * Tests the creation and getter methods of FireEvent objects.
 *
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
class FireEventTest {

    @Test
    @DisplayName("Test FireEvent creation with FIRE_DETECTED and Low severity")
    void testFireEventCreation_FireDetected_LowSeverity() {
        FireEvent event = new FireEvent("14:03:15", 3, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);

        assertEquals("14:03:15", event.getTime());
        assertEquals(3, event.getZoneID());
        assertEquals(FireEvent.Type.FIRE_DETECTED, event.getType());
        assertEquals(FireEvent.Severity.Low, event.getSeverity());
    }

    @Test
    @DisplayName("Test FireEvent creation with DRONE_REQUEST and Moderate severity")
    public void testFireEventCreation_DroneRequest_Moderate() {
        FireEvent event = new FireEvent("14:10:00", 7, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.Moderate);

        assertEquals("14:10:00", event.getTime());
        assertEquals(7, event.getZoneID());
        assertEquals(FireEvent.Type.DRONE_REQUEST, event.getType());
        assertEquals(FireEvent.Severity.Moderate, event.getSeverity());
    }

    @Test
    @DisplayName("Test FireEvent creation with High severity")
    public void testFireEventCreation_High() {
        FireEvent event = new FireEvent("15:30:45", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);

        assertEquals("15:30:45", event.getTime());
        assertEquals(1, event.getZoneID());
        assertEquals(FireEvent.Type.FIRE_DETECTED, event.getType());
        assertEquals(FireEvent.Severity.High, event.getSeverity());
    }

    @Test
    @DisplayName("Test FireEvent toString method")
    public void testToString() {
        FireEvent event = new FireEvent("14:03:15", 3, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.High);
        String expected = "Fire Event {14:03:15, 3, FIRE_DETECTED, High}";

        assertEquals(expected, event.toString());
    }

    @Test
    @DisplayName("Test FireEvent with zone ID 0")
    public void testFireEventWithZoneZero() {
        FireEvent event = new FireEvent("12:00:00", 0, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);

        assertEquals(0, event.getZoneID());
    }

    @Test
    @DisplayName("Test FireEvent with different time formats")
    public void testFireEventWithMilliseconds() {
        FireEvent event = new FireEvent("14:03:15.123", 5, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.Moderate);

        assertEquals("14:03:15.123", event.getTime());
    }

    @Test
    @DisplayName("Test multiple FireEvent objects are independent")
    public void testFireEventIndependence() {
        FireEvent event1 = new FireEvent("10:00:00", 1, FireEvent.Type.FIRE_DETECTED,
                FireEvent.Severity.Low);
        FireEvent event2 = new FireEvent("11:00:00", 2, FireEvent.Type.DRONE_REQUEST,
                FireEvent.Severity.High);

        assertNotEquals(event1.getTime(), event2.getTime());
        assertNotEquals(event1.getZoneID(), event2.getZoneID());
        assertNotEquals(event1.getType(), event2.getType());
        assertNotEquals(event1.getSeverity(), event2.getSeverity());
    }
}