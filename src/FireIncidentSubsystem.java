import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * FireIncidentSubsystem class reads the fire events from the given CSV event file and sends to
 * the Scheduler.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class FireIncidentSubsystem implements Runnable {

    // private final Scheduler scheduler;
    private final String filePath;

    private DatagramPacket sendPacket, receivePacket;
    private DatagramSocket sendReceiveSocket;
    int SCHEDULER_PORT = 6000;
    String SCHEDULER_HOST = "localhost";


    /**
     * Constructor for FireIncidentSubsystem
     * @param filePath the path to the CSV event file
     */
    public FireIncidentSubsystem(String filePath) {
        // this.scheduler = scheduler;
        this.filePath = filePath;

        try {
            sendReceiveSocket = new DatagramSocket();
        } catch (IOException e) {
            e.printStackTrace();
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

                Thread.sleep((int)(Math.random() * 5000));

                String time = row[0].trim();
                int zoneID = Integer.parseInt(row[1].trim());
                FireEvent.Type type = FireEvent.Type.valueOf(row[2].trim());
                FireEvent.Severity severity = FireEvent.Severity.valueOf(row[3].trim());

                FireEvent event = new FireEvent(time, zoneID, type, severity);
                sendFireEvent(event);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        sendOnly();
    }

    private void sendFireEvent(FireEvent event) {
        String message = "FIRE_DETECTED," + event.getTime() + "," + event.getZoneID() + "," + event.getSeverity();
        byte[] bytes = message.getBytes();

        try {
            sendPacket = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(SCHEDULER_HOST), SCHEDULER_PORT);
            sendReceiveSocket.send(sendPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("[New Fire Incident]: sent message to scheduler");
        System.out.println("[New Fire Incident] message sent: " + message);
    }

    private void sendOnly() {
        byte[] bytes = "ALL_EVENTS_DONE".getBytes();

        try {
            System.out.println("SENDING");
            sendPacket = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(SCHEDULER_HOST), SCHEDULER_PORT);
            sendReceiveSocket.send(sendPacket);
            System.out.println("SENT");
        } catch (IOException e) {
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

        // scheduler.updateAllEventsDone();


    }

    public static void main(String[] args) {
        String csvFilePath = "event_file.csv";
        Thread fireincidentsubsystem = new Thread(new FireIncidentSubsystem(csvFilePath));
        fireincidentsubsystem.start();

    }
}
