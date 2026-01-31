/**
 * FireEvent class represents a fire event as a Java object passed between the FireIncidentSubsystem,
 * DroneSubsystem, and Scheduler to avoid passing text strings.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class FireEvent {

    /**
     * The different event types
     */
    public enum Type {
        FIRE_DETECTED,
        DRONE_REQUEST
    }

    /**
     * The different severity of the event
     */
    public enum Severity {
        Low,
        Moderate,
        High
    }

    private final String time;
    private final int zoneID;
    private final Type type;
    private final Severity severity;

    /**
     * Constructor for FireEvent parsed from CSV file
     * @param time the time stamp of the fire event
     * @param zoneID the zone ID where the fire is taking place
     * @param type the event type
     * @param severity the severity of the event
     */
    public FireEvent(String time, int zoneID, Type type, Severity severity) {
        this.time = time;
        this.zoneID = zoneID;
        this.type = type;
        this.severity = severity;
    }

    /**
     * @return the time stamp of the event
     */
    public String getTime() {
        return time;
    }

    /**
     * @return the zone ID of the event
     */
    public int getZoneID() {
        return zoneID;
    }

    /**
     * @return the type of event
     */
    public Type getType() {
        return type;
    }

    /**
     * @return the severity of the event
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * Represents the String for the fire event
     * @return formatted string of the event contents
     */
    @Override
    public String toString() {
        return "Fire Event {" + time + ", " + zoneID + ", " + type + ", " + severity + "}";
    }
}
