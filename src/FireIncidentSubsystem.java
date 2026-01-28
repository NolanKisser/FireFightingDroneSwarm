import java.io.BufferedReader;
import java.io.FileReader;

/**
 * FireIncidentSubsystem class reads the fire events from the given CSV input file and sends to
 * the Scheduler.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */
public class FireIncidentSubsystem implements Runnable {

    private final Scheduler scheduler;
    private final String filePath;

    /**
     * Constructor for FireIncidentSubsystem
     * @param scheduler the shared scheduler
     * @param filePath the path to the CSV input file
     */
    public FireIncidentSubsystem(Scheduler scheduler, String filePath) {
        this.scheduler = scheduler;
        this.filePath = filePath;
    }

    /**
     * Loading and reading the CSV file and submits a FireEvent to Scheduler for each event (row)
     * @param filePath the path to the CSV input file
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
                scheduler.newFireEvent(event);
                System.out.println("Submitted new fire event: " + event);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Finished reading CSV event file");

    }

    /**
     * The subsystem signals the scheduler after submitting all events from the CSV file and waits
     * for completion.
     */
    @Override
    public void run() {
        loadCSV(filePath);

        scheduler.updateAllEventsDone();

        while(true) {
            FireEvent completed = scheduler.getCompletedEvent();
            if(completed == null) {
                break;
            }
        }

    }
}
