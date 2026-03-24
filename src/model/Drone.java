package model;

import subsystems.Scheduler;

public class Drone {

    /**
     * Possible operating states of a drone
     */
    public enum DroneState {
        IDLE,
        EN_ROUTE,
        EXTINGUISHING,
        RETURNING,
        REFILLING,
        FAULTED
    }

    private final int droneID;
    private final Scheduler scheduler;

    private DroneState state;
    private FireEvent event;

    // current drone location
    private double currentX = 0.0;
    private double currentY = 0.0;
    private double currentAgent = 100.0;

    // parameters obtained in iteration 0
    private static final double CRUISE_SPEED_LOADED = 10.0;
    private static final double CRUISE_SPEED_UNLOADED = 15.0;
    private static final double DROP_RATE = 2.0;
    private static final double NOZZLE_DOORS = 0.5;

    private static final double LOW_VOLUME = 10.0;
    private static final double MODERATE_VOLUME = 20.0;
    private static final double HIGH_VOLUME = 30.0;

    public Drone(int droneID, Scheduler scheduler) {
        this.droneID = droneID;
        this.scheduler = scheduler;
        this.state = DroneState.IDLE;
    }

    /**
     * Return the drone's current operating state
     * @return the current drone state
     */
    public DroneState getDroneState() {
        return state;
    }

    /**
     * Transitions the drone to a new operating state
     * @param newState the new drone state
     */
    public void transitionTo(DroneState newState) {
        this.state = newState;
    }

    public FireEvent getEvent() {
        return event;
    }

    public void setEvent(FireEvent event) {
        this.event = event;
    }

    public void clearEvent() {
        this.event = null;
    }

    public int getDroneID() {
        return droneID;
    }

    /**
     * Return the drone's current X coordinate
     * @return current x coordinate
     */
    public double getCurrentX() {
        return currentX;
    }

    /**
     * Return the drone's current Y coordinate
     * @return current y coordinate
     */
    public double getCurrentY() {
        return currentY;
    }

    public double getCurrentAgent() {
        return currentAgent;
    }

    public double getCruiseSpeedLoaded() {
        return CRUISE_SPEED_LOADED;
    }

    public double getCruiseSpeedUnloaded() {
        return CRUISE_SPEED_UNLOADED;
    }

    public double getDropRate() {
        return DROP_RATE;
    }

    public double getNozzleDoorsTime() {
        return NOZZLE_DOORS;
    }

    public boolean hasEnoughAgentFor(FireEvent event) {
        return currentAgent >= getRequiredVolume(event);
    }

    /**
     * Returns the volume required based on the severity of the fire
     * @param event the fire being serviced
     * @return the required volume
     */
    public double getRequiredVolume(FireEvent event) {
        return switch (event.getSeverity()) {
            case Low -> LOW_VOLUME;
            case Moderate -> MODERATE_VOLUME;
            case High -> HIGH_VOLUME;
        };
    }

    /**
     * Returns the enroute travel time for the given fire event
     * @param event the fire event
     * @return the travel time in seconds
     */
    public double getEnRoute(FireEvent event) {
        Zone zone = scheduler.getZones().get(event.getZoneID());

        double dx = zone.getCenterX() - currentX;
        double dy = zone.getCenterY() - currentY;

        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance / CRUISE_SPEED_LOADED;
    }

    /**
     * Return the return to base travel time for the given fire event
     * @param event the fire event
     * @return the return time in seconds
     */
    public double getReturn() {
        double dx = 0.0 - currentX;
        double dy = 0.0 - currentY;

        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance / CRUISE_SPEED_UNLOADED;
    }

    public void useAgent(double amount) {
        currentAgent -= amount;
        if (currentAgent < 0) currentAgent = 0;
    }

    public void refillAgent() {
        currentAgent = 100.0;
    }

    /**
     * Move drone step-by-step towards the target, visually updating 1 block (100 units) at a time
     * @param targetX the x coordinate of the destination
     * @param targetY the y coordinate of the destination
     * @param speed the speed of the drone
     */
    public void moveToTargetStepByStep(double targetX, double targetY, double speed, Runnable statusCallback)
            throws InterruptedException {

        double dx = targetX - currentX;
        double dy = targetY - currentY;

        double distance = Math.sqrt(dx * dx + dy * dy);
        int steps = 10;

        double stepX = dx / steps;
        double stepY = dy / steps;

        for (int i = 0; i < steps; i++) {
            currentX += stepX;
            currentY += stepY;

            if (statusCallback != null) {
                statusCallback.run();
            }

            Thread.sleep((long) ((distance / speed) * 10 / steps));
        }

        // snap to exact target (avoid floating errors)
        currentX = targetX;
        currentY = targetY;
    }
}