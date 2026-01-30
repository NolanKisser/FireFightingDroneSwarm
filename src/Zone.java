public class Zone {
    private final int zoneID;
    private final double x1, y1, x2, y2;

    public Zone(int zoneID, double x1, double y1, double x2, double y2) {
        this.zoneID = zoneID;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public int getZoneID() {
        return zoneID;
    }

    public double getX1() {
        return x1;
    }

    public double getY1() {
        return y1;
    }

    public double getX2() {
        return x2;
    }

    public double getY2() {
        return y2;
    }

    public double getCenterX() {
        return x1 + x2 / 2;
    }
    public double getCenterY() {
        return y1 + y2 / 2;
    }

    @Override
    public String toString() {
        return "Zone {" + zoneID + ", start: " + x1 + "," + y1 + ", end: " + x2 + "," + y2 + "}";
    }
}
