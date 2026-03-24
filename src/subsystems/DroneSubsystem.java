package subsystems;

import model.*;

import java.io.IOException;
import java.net.*;

/**
 * DroneSubsystem class represents a single firefighter drone process.
 * It communicates with the Scheduler through UDP and delegates drone-specific
 * state/data behavior to the Drone class.
 *
 * @author Jordan Grewal, Nolan Kisser, Celina Yang
 * @version March 23, 2026
 */
public class DroneSubsystem implements Runnable {

    private final Scheduler scheduler;
    private final Drone drone;

    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendReceiveSocket;

    private static final int SCHEDULER_PORT = 6000;
    private static final String SCHEDULER_HOST = "localhost";
    private static final long REFILL_TIME_MS = 1500;

    private boolean running = true;

    /**
     * Constructs a new drone subsystem
     * @param scheduler the scheduler coordinating the simulation
     * @param droneID the unique ID of this drone
     */
    public DroneSubsystem(Scheduler scheduler, int droneID) {
        this.scheduler = scheduler;
        this.drone = new Drone(droneID, scheduler);

        try {
            sendReceiveSocket = new DatagramSocket();
        } catch (SocketException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Starts a drone subsystem thread as an individual process
     * @param args command line arguments
     */
    public static void main(String[] args) {
        int id = 1;
        if (args.length > 0) {
            try {
                id = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Invalid Drone ID provided. Using default ID 1.");
            }
        }

        Scheduler scheduler = new Scheduler("zone_file.csv");
        DroneSubsystem droneSubsystem = new DroneSubsystem(scheduler, id);
        Thread droneThread = new Thread(droneSubsystem);
        droneThread.start();
    }

    /**
     * Sends a message to the scheduler and waits for the reply
     * @param message the message to send
     * @return the scheduler's reply message
     */
    private String sendAndReceive(String message) {
        sendOnly(message);
        return receiveOnly();
    }

    /**
     * Send a UDP message to the scheduler
     * @param message the message to send
     */
    private void sendOnly(String message) {
        byte[] bytes = message.getBytes();

        try {
            sendPacket = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(SCHEDULER_HOST), SCHEDULER_PORT);
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("[Drone " + drone.getDroneID() + "] sent message to scheduler");
        System.out.println("[Drone " + drone.getDroneID() + "] message sent: " + message);
    }

    /**
     * Receives a UDP reply from the scheduler
     * @return the received message as a string
     */
    private String receiveOnly() {
        receivePacket = new DatagramPacket(new byte[1024], 1024);

        try {
            sendReceiveSocket.receive(receivePacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String message = new String(receivePacket.getData(), 0, receivePacket.getLength());

        System.out.println("[Drone " + drone.getDroneID() + "] received message from scheduler");
        System.out.println("[Drone " + drone.getDroneID() + "] message received: " + message);
        return message;
    }

    /**
     * Sends the drone's current status to the scheduler
     */
    private void sendStatusUpdate() {
        sendOnly("STATUS_UPDATE," + drone.getDroneID() + "," + drone.getDroneState() + "," + drone.getCurrentX() + "," + drone.getCurrentY() + "," + drone.getCurrentAgent());
    }

    /**
     * Handles the drone's behaviour based on its current state
     * @throws InterruptedException if the thread is interrupted during simulation
     */
    private synchronized void handleEvent() throws InterruptedException {
        switch (drone.getDroneState()) {

            case IDLE:
                String message = sendAndReceive("DRONE_READY," + drone.getDroneID());
                String[] parts = message.split(",");

                if (parts[0].equals("ASSIGN_EVENT")) {
                    System.out.println("RECEIVE ASSIGN");

                    String time = parts[1];
                    int zoneID = Integer.parseInt(parts[2]);
                    FireEvent.Severity severity = FireEvent.Severity.valueOf(parts[3]);

                    FireEvent event = new FireEvent(time, zoneID, FireEvent.Type.FIRE_DETECTED, severity);
                    drone.setEvent(event);

                    if (!drone.hasEnoughAgentFor(event)) {
                        System.out.printf("[Drone %d] Insufficient agent (%.1f%%). Must refill before accepting mission.\n", drone.getDroneID(), drone.getCurrentAgent());
                        drone.clearEvent();
                        drone.transitionTo(Drone.DroneState.RETURNING);
                    } else {
                        drone.transitionTo(Drone.DroneState.EN_ROUTE);
                    }

                } else if (parts[0].equals("ALL_EVENTS_COMPLETE")) {
                    System.out.println("ALL EVENTS COMPLETE");
                    running = false;
                }
                break;

            case EN_ROUTE:
                FireEvent enRouteEvent = drone.getEvent();
                double travelTime = drone.getEnRoute(enRouteEvent);
                System.out.printf("[Drone %d] En route to Zone %d. Expected travel time: %.1f seconds\n", drone.getDroneID(), enRouteEvent.getZoneID(), travelTime);
                Zone target = scheduler.getZones().get(enRouteEvent.getZoneID());
                drone.moveToTargetStepByStep(target.getCenterX(), target.getCenterY(), drone.getCruiseSpeedLoaded(), this::sendStatusUpdate);
                sendOnly("DRONE_ARRIVE_TO_ZONE," + drone.getDroneID() + "," + enRouteEvent.getTime() + "," + enRouteEvent.getZoneID() + "," + enRouteEvent.getSeverity());
                drone.transitionTo(Drone.DroneState.EXTINGUISHING);
                break;

            case EXTINGUISHING:
                FireEvent extinguishEvent = drone.getEvent();

                double requiredVolume = drone.getRequiredVolume(extinguishEvent);
                double volumeToDrop = Math.min(requiredVolume, drone.getCurrentAgent());
                double dropTime = volumeToDrop / drone.getDropRate();

                System.out.printf("[Drone %d] Opening nozzle doors... (%.1fs)\n", drone.getDroneID(), drone.getNozzleDoorsTime());
                Thread.sleep((long) (drone.getNozzleDoorsTime() * 10));

                System.out.printf("[Drone %d] Dropping %.1f%% agent on Zone %d... (%.1fs)\n", drone.getDroneID(), volumeToDrop, extinguishEvent.getZoneID(), dropTime);
                Thread.sleep((long) (dropTime * 10));

                drone.useAgent(volumeToDrop);
                sendStatusUpdate();

                System.out.printf("[Drone %d] Closing nozzle doors... (%.1fs)\n", drone.getDroneID(), drone.getNozzleDoorsTime());
                Thread.sleep((long) (drone.getNozzleDoorsTime() * 10));

                if (volumeToDrop >= requiredVolume) {
                    System.out.printf("[Drone %d] Successfully extinguished fire in Zone %d!\n", drone.getDroneID(), extinguishEvent.getZoneID());
                    sendOnly("DRONE_COMPLETE_EVENT," + drone.getDroneID() + "," + extinguishEvent.getTime() + "," + extinguishEvent.getZoneID() + "," + extinguishEvent.getSeverity());
                } else {
                    System.out.printf("[Drone %d] Ran out of agent! Fire in Zone %d not fully extinguished.\n", drone.getDroneID(), extinguishEvent.getZoneID());
                    sendOnly("REQUEUE_EVENT," + drone.getDroneID() + "," + extinguishEvent.getTime() + "," + extinguishEvent.getZoneID() + "," + extinguishEvent.getSeverity());
                }

                drone.transitionTo(Drone.DroneState.RETURNING);
                drone.clearEvent();
                break;

            case RETURNING:
                double returnTime = drone.getReturn();

                System.out.printf("[Drone %d] Returning to base. Expected return time: %.1f seconds\n", drone.getDroneID(), returnTime);

                drone.moveToTargetStepByStep(0.0, 0.0, drone.getCruiseSpeedUnloaded(),this::sendStatusUpdate);

                drone.transitionTo(Drone.DroneState.REFILLING);
                break;

            case REFILLING:
                System.out.printf("[Drone %d] Refilling agent at base...\n", drone.getDroneID());
                Thread.sleep(REFILL_TIME_MS);

                drone.refillAgent();
                sendStatusUpdate();

                String reply = sendAndReceive("DRONE_RETURN_TO_BASE," + drone.getDroneID());

                if (reply.startsWith("ALL_EVENTS_COMPLETE")) {
                    System.out.println("[Drone " + drone.getDroneID() + "] All events complete. Shutting down.");
                    running = false;
                } else {
                    drone.transitionTo(Drone.DroneState.IDLE);
                }
                break;

            case FAULTED:
                Thread.sleep(5000);
                break;
        }
    }

    /**
     * The drone blocks until there is an event available. When the scheduler indicates there are
     * no more events, the drone will complete execution.
     */
    @Override
    public void run() {
        sendAndReceive("REGISTER_DRONE," + drone.getDroneID());

        while (running) {
            try {
                handleEvent();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}