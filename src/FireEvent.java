public class FireEvent {
    public enum Type {
        FIRE_DETECTED,
        DRONE_REQUEST
    }

    public enum Severity {
        LOW,
        MODERATE,
        HIGH
    }

    private final String time;
    private final int zoneID;
    private final Type type;
    private final Severity severity;

    /**
     * Constructor for FireEvent
     * @param time
     * @param zoneID
     * @param type
     * @param severity
     */
    public FireEvent(String time, int zoneID, Type type, Severity severity) {
        this.time = time;
        this.zoneID = zoneID;
        this.type = type;
        this.severity = severity;
    }

    public String getTime() {
        return time;
    }
    public int getZoneID() {
        return zoneID;
    }
    public Type getType() {
        return type;
    }
    public Severity getSeverity() {
        return severity;
    }
    public String toString() {
        return "Fire Event {" + time + ", " + zoneID + ", " + type + ", " + severity + "}";
    }

}
