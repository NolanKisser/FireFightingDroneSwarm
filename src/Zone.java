/**
 * Zone class represents the different zones within the simulation. Each zone is defined with two
 * coordinates, the start point (x1,y1) and the end point (x2,y2). The center point of the zone is
 * computed for the drone's to service a fire within the designated zone.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class Zone {
    private final int zoneID;
    private final double x1, y1, x2, y2;

    /**
     * Constructor for Zone
     * @param zoneID unique ID for the zone
     * @param x1 x coordinate of start point
     * @param y1 y coordinate of start point
     * @param x2 x coordinate of end point
     * @param y2 y coordinate of end point
     */
    public Zone(int zoneID, double x1, double y1, double x2, double y2) {
        this.zoneID = zoneID;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    /**
     * Get the x coordinate of the center of the zone
     * @return x coordinate
     */
    public double getCenterX() {
        return (x1 + x2) / 2;
    }

    /**
     * Get the y coordinate of the center of the zone
     * @return y coordinate
     */
    public double getCenterY() {
        return (y1 + y2) / 2;
    }

    /**
     * Returns a string representation of the zone
     * @return string describing the zone boundaries
     */
    @Override
    public String toString() {
        return "Zone {" + zoneID + ", start: " + x1 + "," + y1 + ", end: " + x2 + "," + y2 + "}";
    }
}
