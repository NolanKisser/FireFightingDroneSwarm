import javax.swing.*;
import java.awt.*;
import java.util.Date;
import java.text.SimpleDateFormat;

/**
 * DroneSwarmMonitor class represents User Interface for the Firefighting Drone Swarm.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */

public class DroneSwarmMonitor extends JFrame {
    private JTextArea eventLog;
    private SimpleDateFormat timeFormat;
    private ZoneMap mapPanel;
    private JPanel mapContainer;
    private JLabel droneStateLabel;
    private JLabel activeFiresLabel;

    public DroneSwarmMonitor() {
        super("SYSC 3303A: Firefighting Drone Swarm Monitor");
        this.timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        setupUI();
    }

    private void setupUI() {
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(1200, 720);
        this.setLayout(new BorderLayout(5, 5));

        //Load CSV button
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        droneStateLabel = new JLabel("Drone State: IDLE");
        activeFiresLabel = new JLabel("Active Fires: 0");
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(droneStateLabel);
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(activeFiresLabel);
        this.add(controlPanel, BorderLayout.NORTH);

        //Zone Map
        mapContainer = new JPanel(new BorderLayout());
        mapContainer.setBorder(BorderFactory.createTitledBorder("Zone Map"));
        mapPanel = new ZoneMap();
        mapContainer.add(mapPanel, BorderLayout.CENTER);
        this.add(mapContainer, BorderLayout.CENTER);

        //Event Console
        eventLog = new JTextArea(12, 50);
        eventLog.setEditable(false);
        eventLog.setBackground(new Color(30, 30, 30));
        eventLog.setForeground(Color.CYAN);
        eventLog.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(eventLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder(null, "Communication Log",
                0, 0, null, Color.GRAY));
        this.add(scrollPane, BorderLayout.SOUTH);
        this.setVisible(true);
    }

    /**
     * Updates the GUI log whenever a message is passed between subsystems.
     */
    public void addLog(String subsystem, String action) {
        String timestamp = timeFormat.format(new Date());
        String logEntry = String.format("[%s] %s -> %s\n", timestamp, subsystem, action);

        // Ensure UI updates are handled on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            eventLog.append(logEntry);
            eventLog.setCaretPosition(eventLog.getDocument().getLength());
        });
    }

    /**
     * Updates the current drone state in the GUI.
     * @param state the new drone state
     */
    public void setDroneState(String state) {
        SwingUtilities.invokeLater(() -> droneStateLabel.setText("Drone State: " + state));
    }

    /**
     * Updates the active fire count in the GUI.
     * @param count number of active fire incidents
     */
    public void setActiveFires(int count) {
        SwingUtilities.invokeLater(() -> activeFiresLabel.setText("Active Fires: " + count));
    }
}
