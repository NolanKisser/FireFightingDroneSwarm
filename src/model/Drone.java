package model;

/**
 * Represents the physical state and capabilities of a firefighting drone.
 */
public class Drone {

    public enum DroneState {
        IDLE,
        EN_ROUTE,
        EXTINGUISHING,
        EN_ROUTE_NEXT_MISSION,
        RETURNING,
        REFILLING,
        FAULTED
    }

    private final int id;
    private DroneState state;
    private double x;
    private double y;
    private double agentLevel;
    private FireEvent currentMission;

    // Physical Capabilities
    public static final double CRUISE_SPEED_LOADED = 10.0;
    public static final double CRUISE_SPEED_UNLOADED = 15.0;
    public static final double NOZZLE_DOORS = 0.5;
    public static final double DROP_RATE = 2.0;

    // Mission Requirements
    public static final double LOW_VOLUME = 10.0;
    public static final double MODERATE_VOLUME = 20.0;
    public static final double HIGH_VOLUME = 30.0;

    public Drone(int id) {
        this.id = id;
        this.state = DroneState.IDLE;
        this.x = 0.0;
        this.y = 0.0;
        this.agentLevel = 100.0;
        this.currentMission = null;
    }

    // --- Getters & Setters ---
    public int getId() { return id; }

    public DroneState getState() { return state; }
    public void setState(DroneState state) { this.state = state; }

    public double getX() { return x; }
    public double getY() { return y; }
    public void setLocation(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getAgentLevel() { return agentLevel; }
    public void setAgentLevel(double agentLevel) { this.agentLevel = agentLevel; }
    public void consumeAgent(double amount) {
        this.agentLevel = Math.max(0, this.agentLevel - amount);
    }

    public FireEvent getCurrentMission() { return currentMission; }
    public void setCurrentMission(FireEvent currentMission) { this.currentMission = currentMission; }

    // --- Physical Computations ---

    public double getRequiredVolume(FireEvent event) {
        if (event == null) return 0;
        return switch (event.getSeverity()) {
            case Low -> LOW_VOLUME;
            case Moderate -> MODERATE_VOLUME;
            case High -> HIGH_VOLUME;
        };
    }

    public double distanceTo(double targetX, double targetY) {
        return Math.sqrt(Math.pow(targetX - x, 2) + Math.pow(targetY - y, 2));
    }

    public double computeTravelTime(double targetX, double targetY, boolean isLoaded) {
        double distance = distanceTo(targetX, targetY);
        double speed = isLoaded ? CRUISE_SPEED_LOADED : CRUISE_SPEED_UNLOADED;
        return distance / speed;
    }
}