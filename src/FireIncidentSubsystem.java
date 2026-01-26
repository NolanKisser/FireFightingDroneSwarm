import java.io.BufferedReader;
import java.io.FileReader;

public class FireIncidentSubsystem implements Runnable {

    private final Scheduler scheduler;
    private final String filePath;

    /**
     * Constructor
     * @param scheduler
     * @param filePath
     */
    public FireIncidentSubsystem(Scheduler scheduler, String filePath) {
        this.scheduler = scheduler;
        this.filePath = filePath;
    }

    /**
     * Loading CSV file
     * @param filePath
     */
    private void loadCSV(String filePath) {
        String line = "";

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
    }

    @Override
    public void run() {
        loadCSV(filePath);
        System.out.println("Finished reading CSV file");

    }
}
