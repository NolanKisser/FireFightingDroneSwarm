package model;

/**
 * Represents the physical state and capabilities of a firefighting drone.
 *  * @author Jordan Grewal, Nolan Kisser, Celina Yang
 *  * @version April 5, 2026
 */
public class Drone {

    /**
     * represents the possible states of a drone during simulation
     */
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

    // physical capabilities of the drone (iteration 0 values)
    public static final double CRUISE_SPEED_LOADED = 10.0;
    public static final double CRUISE_SPEED_UNLOADED = 15.0;
    public static final double NOZZLE_DOORS = 0.5;
    public static final double DROP_RATE = 2.0;

    // simulation volumes
    public static final double LOW_VOLUME = 10.0;
    public static final double MODERATE_VOLUME = 20.0;
    public static final double HIGH_VOLUME = 30.0;

    /**
     * constructs a new drone with default starting values
     * @param id drone ID
     */
    public Drone(int id) {
        this.id = id;
        this.state = DroneState.IDLE;
        this.x = 0.0;
        this.y = 0.0;
        this.agentLevel = 100.0;
        this.currentMission = null;
    }

    /**
     * @return drone ID
     */
    public int getId() { return id; }

    /**
     * @return current drone state
     */
    public DroneState getState() { return state; }

    /**
     * update drone's current state
     * @param state new drone state
     */
    public void setState(DroneState state) { this.state = state; }

    /**
     * @return x coordinate of drone
     */
    public double getX() { return x; }

    /**
     * @return y coordinate of drone
     */
    public double getY() { return y; }

    /**
     * update drone's location
     * @param x new x coordinate
     * @param y new y coordinate
     */
    public void setLocation(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * @return current agent level
     */
    public double getAgentLevel() { return agentLevel; }

    /**
     * setting the drone's agent level
     * @param agentLevel new agent level
     */
    public void setAgentLevel(double agentLevel) { this.agentLevel = agentLevel; }

    /**
     * reduce the drone's available amount of agent
     * @param amount extinguishing agent to consume
     */
    public void consumeAgent(double amount) {
        this.agentLevel = Math.max(0, this.agentLevel - amount);
    }

    /**
     * @return current fire event
     */
    public FireEvent getCurrentMission() { return currentMission; }

    /**
     * assign fire event to the drone
     * @param currentMission fire event assigned to the drone
     */
    public void setCurrentMission(FireEvent currentMission) { this.currentMission = currentMission; }

    /**
     * calculate agent volume required to extinguish fire based on severity
     * @param event fire event
     * @return required volume of agent
     */
    public double getRequiredVolume(FireEvent event) {
        if (event == null) return 0;
        return switch (event.getSeverity()) {
            case Low -> LOW_VOLUME;
            case Moderate -> MODERATE_VOLUME;
            case High -> HIGH_VOLUME;
        };
    }

    /**
     * calculate distance from the drone's current position to target position
     * @param targetX target x coordinate
     * @param targetY target y coordinate
     * @return distance to target
     */
    public double distanceTo(double targetX, double targetY) {
        return Math.sqrt(Math.pow(targetX - x, 2) + Math.pow(targetY - y, 2));
    }

    /**
     * calculate travel time from drone's current position to target position
     * @param targetX target x coordinate
     * @param targetY target y coordinate
     * @param isLoaded true if the drone is carrying agent, otherwise false
     * @return travel time in seconds
     */
    public double computeTravelTime(double targetX, double targetY, boolean isLoaded) {
        double distance = distanceTo(targetX, targetY);
        double speed = isLoaded ? CRUISE_SPEED_LOADED : CRUISE_SPEED_UNLOADED;
        return distance / speed;
    }
}