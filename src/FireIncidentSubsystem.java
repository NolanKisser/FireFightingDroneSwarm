import java.io.BufferedReader;
import java.io.FileReader;

/**
 * FireIncidentSubsystem class reads the fire events from the given CSV event file and sends to
 * the Scheduler.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class FireIncidentSubsystem implements Runnable {

    private final String schedulerHost;
    private final int schedulerPort;
    private final String filePath;
    private final EventSocket eventSocket;

    /**
     * Constructor for FireIncidentSubsystem
     * @param schedulerHost the host of the scheduler
     * @param schedulerPort the port of the scheduler
     * @param filePath the path to the CSV event file
     */
    public FireIncidentSubsystem(String schedulerHost, int schedulerPort, String filePath) {
        this.schedulerHost = schedulerHost;
        this.schedulerPort = schedulerPort;
        this.filePath = filePath;
        this.eventSocket = new EventSocket();
    }

    private Object sendAndReceive(UDPMessage request) {
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName(schedulerHost);
            eventSocket.send(request, address, schedulerPort);
            UDPMessage response = (UDPMessage) eventSocket.receive();
            return response != null ? response.response : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Loading and reading the CSV file and submits a FireEvent to Scheduler for each event (row)
     * @param filePath the path to the CSV event file
     */
    private void loadCSV(String filePath) {
        String line;

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            while((line = br.readLine()) != null) {
                String[] row = line.split(",");

                String time = row[0].trim();
                int zoneID = Integer.parseInt(row[1].trim());
                FireEvent.Type type = FireEvent.Type.valueOf(row[2].trim());
                FireEvent.Severity severity = FireEvent.Severity.valueOf(row[3].trim());

                FireEvent event = new FireEvent(time, zoneID, type, severity);
                System.out.println("Processing event: Time=" + time + ", Zone=" + zoneID + ", Type=" + type + ", Severity=" + severity);
                sendAndReceive(new UDPMessage(UDPMessage.Command.NEW_FIRE_EVENT, event));
                
                try {
                    Thread.sleep(2000); // 2 second delay between sending events
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * The subsystem signals the scheduler after submitting all events from the CSV file and waits
     * for completion.
     */
    @Override
    public void run() {
        loadCSV(filePath);

        sendAndReceive(new UDPMessage(UDPMessage.Command.UPDATE_ALL_EVENTS_DONE));

        while(true) {
            FireEvent completed = (FireEvent) sendAndReceive(new UDPMessage(UDPMessage.Command.GET_COMPLETED_EVENT));
            if(completed == null) {
                break;
            }
        }

    }
    public static void main(String[] args) {
        String csvFilePath = "event_file.csv";
        System.out.println("Starting FireIncidentSubsystem...");
        
        // Host coordinates and scheduler port
        FireIncidentSubsystem fireincident = new FireIncidentSubsystem("localhost", 5000, csvFilePath);
        fireincident.run();
        
        System.out.println("FireIncidentSubsystem completed.");
    }
}
