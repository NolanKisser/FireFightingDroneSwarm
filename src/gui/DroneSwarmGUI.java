package gui;

import core.Scheduler;

import javax.swing.*;
import java.awt.*;
import java.util.Date;
import java.text.SimpleDateFormat;

/**
 * SYSC 3303A Firefighting Drone Swarm - Iteration 1 GUI
 * Provides the required structural framework using the Swing library.
 */
public class DroneSwarmGUI extends JFrame {
    private JTextArea eventLog;
    private SimpleDateFormat timeFormat;
    private ZoneMap mapPanel;
    private JPanel mapContainer;

    public DroneSwarmGUI() {
        super("SYSC 3303A: Firefighting Drone Swarm Monitor");
        this.timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        setupUI();
    }

    private void setupUI() {
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(900, 600);
        this.setLayout(new BorderLayout(5, 5));

        // 1. Header with System Information
        JPanel header = new JPanel(new GridLayout(1, 2));
        header.setBorder(BorderFactory.createEtchedBorder());
        header.add(new JLabel("  Subsystems: Active", JLabel.LEFT));
        header.add(new JLabel("Iteration: #1 (Communication)  ", JLabel.RIGHT));
        this.add(header, BorderLayout.NORTH);



        // 2. Center: Map container
        mapContainer = new JPanel(new BorderLayout());
        JPanel mapPlaceholder = new JPanel();
        mapPlaceholder.setBackground(Color.WHITE);
        mapPlaceholder.setBorder(BorderFactory.createTitledBorder("Live Mission Map (Grid View)"));
        mapPlaceholder.add(new JLabel("Grid visualization will be fully implemented in Iteration 2+"));
        mapContainer.add(mapPlaceholder, BorderLayout.CENTER);
        this.add(mapContainer, BorderLayout.CENTER);

        // 3. Bottom: Event Console (Crucial for Iteration 1 testing)
        eventLog = new JTextArea(12, 50);
        eventLog.setEditable(false);
        eventLog.setBackground(new Color(30, 30, 30));
        eventLog.setForeground(Color.CYAN);
        eventLog.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(eventLog);
        scrollPane.setBorder(BorderFactory.createTitledBorder(null, "Subsystem Communication Log",
                0, 0, null, Color.GRAY));
        this.add(scrollPane, BorderLayout.SOUTH);
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton loadCsvBtn = new JButton("Load Incident CSV");
        controlPanel.add(loadCsvBtn);
        this.add(controlPanel, BorderLayout.NORTH);
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
    public void setMap(Scheduler scheduler){
        mapPanel = new ZoneMap(scheduler);
        mapContainer.removeAll();
        mapContainer.add(mapPanel, BorderLayout.CENTER);
        mapContainer.revalidate();
        mapContainer.repaint();
    }

}
