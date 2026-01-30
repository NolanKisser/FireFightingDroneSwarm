import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Zone class.
 * Tests zone creation, coordinate getters, and center calculations.
 *
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
class ZoneTest {

    @Test
    @DisplayName("Test Zone creation with positive coordinates")
    void testZoneCreation() {
        Zone zone = new Zone(1, 0, 0, 700, 600);

        assertEquals(1, zone.getZoneID());
        assertEquals(0, zone.getX1());
        assertEquals(0, zone.getY1());
        assertEquals(700, zone.getX2());
        assertEquals(600, zone.getY2());
    }

    @Test
    @DisplayName("Test Zone center calculation")
    public void testZoneCenterCalculation() {
        Zone zone = new Zone(1, 0, 0, 700, 600);

        assertEquals(350.0, zone.getCenterX(), 0.001);
        assertEquals(300.0, zone.getCenterY(), 0.001);
    }

    @Test
    @DisplayName("Test Zone with non-zero starting coordinates")
    public void testZoneWithNonZeroStart() {
        Zone zone = new Zone(2, 0, 600, 650, 1500);

        assertEquals(2, zone.getZoneID());
        assertEquals(0, zone.getX1());
        assertEquals(600, zone.getY1());
        assertEquals(650, zone.getX2());
        assertEquals(1500, zone.getY2());

        assertEquals(325.0, zone.getCenterX(), 0.001);
        assertEquals(1050.0, zone.getCenterY(), 0.001);
    }

    @Test
    @DisplayName("Test Zone toString method")
    public void testToString() {
        Zone zone = new Zone(1, 0, 0, 700, 600);
        String expected = "Zone {1, start: 0.0,0.0, end: 700.0,600.0}";

        assertEquals(expected, zone.toString());
    }

    @Test
    @DisplayName("Test Zone with decimal coordinates")
    public void testZoneWithDecimalCoordinates() {
        Zone zone = new Zone(3, 100.5, 200.5, 500.5, 400.5);

        assertEquals(100.5, zone.getX1(), 0.001);
        assertEquals(200.5, zone.getY1(), 0.001);
        assertEquals(500.5, zone.getX2(), 0.001);
        assertEquals(400.5, zone.getY2(), 0.001);
        assertEquals(300.5, zone.getCenterX(), 0.001);
        assertEquals(300.5, zone.getCenterY(), 0.001);
    }

    @Test
    @DisplayName("Test Zone with same start and end coordinates")
    public void testZoneWithSameCoordinates() {
        Zone zone = new Zone(4, 100, 100, 100, 100);

        assertEquals(100.0, zone.getCenterX(), 0.001);
        assertEquals(100.0, zone.getCenterY(), 0.001);
    }

    @Test
    @DisplayName("Test multiple Zone objects are independent")
    public void testZoneIndependence() {
        Zone zone1 = new Zone(1, 0, 0, 700, 600);
        Zone zone2 = new Zone(2, 0, 600, 650, 1500);

        assertNotEquals(zone1.getZoneID(), zone2.getZoneID());
        assertNotEquals(zone1.getCenterX(), zone2.getCenterX());
        assertNotEquals(zone1.getCenterY(), zone2.getCenterY());
    }
}