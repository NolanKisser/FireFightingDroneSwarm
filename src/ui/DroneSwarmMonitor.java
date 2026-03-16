package ui;

import subsystems.*;


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
    private JLabel activeFiresLabel;
    private JTable droneStatusTable;
    private javax.swing.table.DefaultTableModel droneTableModel;

    public DroneSwarmMonitor() {
        super("SYSC 3303A: Firefighting Drone Swarm Monitor");
        this.timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
        setupUI();
    }

    private void setupUI() {
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(1200, 720);
        this.setLayout(new BorderLayout(5, 5));

        // Top Panel for active fires and Drone Table
        JPanel topPanel = new JPanel(new BorderLayout());
        
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        activeFiresLabel = new JLabel("Active Fires: 0");
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(activeFiresLabel);
        
        JButton launchButton = new JButton("Launch Simulation");
        launchButton.addActionListener(e -> {
            Thread fireincidentsubsystem = new Thread(new FireIncidentSubsystem("event_file.csv"));
            fireincidentsubsystem.start();
            launchButton.setEnabled(false);
        });
        controlPanel.add(Box.createHorizontalStrut(20));
        controlPanel.add(launchButton);

        topPanel.add(controlPanel, BorderLayout.NORTH);

        // Drone Status Table
        String[] columns = {"Drone ID", "State", "Zone", "Severity", "Agent %"};
        droneTableModel = new javax.swing.table.DefaultTableModel(columns, 0);
        droneStatusTable = new JTable(droneTableModel);
        JScrollPane tableScrollPane = new JScrollPane(droneStatusTable);
        tableScrollPane.setPreferredSize(new Dimension(this.getWidth(), 100));
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Drone Status"));
        topPanel.add(tableScrollPane, BorderLayout.CENTER);
        
        this.add(topPanel, BorderLayout.NORTH);

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
     * Updates the current drone state in the GUI and on the map.
     * @param droneID the ID of the drone
     * @param state the new drone state
     * @param zone "N/A" or zone number
     * @param severity "N/A" or severity
     * @param agentRemaining agent remaining percentage
     * @param x drone x position
     * @param y drone y position
     */
    public void updateDroneStatus(int droneID, String state, String zone, String severity, double agentRemaining, double x, double y) {
        SwingUtilities.invokeLater(() -> {
            boolean found = false;
            for (int i = 0; i < droneTableModel.getRowCount(); i++) {
                if ((int) droneTableModel.getValueAt(i, 0) == droneID) {
                    droneTableModel.setValueAt(state, i, 1);
                    droneTableModel.setValueAt(zone, i, 2);
                    droneTableModel.setValueAt(severity, i, 3);
                    droneTableModel.setValueAt(String.format("%.1f", agentRemaining), i, 4);
                    found = true;
                    break;
                }
            }
            if (!found) {
                droneTableModel.addRow(new Object[]{droneID, state, zone, severity, String.format("%.1f", agentRemaining)});
            }
            mapPanel.updateDrone(droneID, x, y, state);
        });
    }

    /**
     * Updates the active fire count in the GUI.
     * @param count number of active fire incidents
     */
    public void setActiveFires(int count) {
        SwingUtilities.invokeLater(() -> activeFiresLabel.setText("Active Fires: " + count));
    }

    public void addActiveFire(int zoneID) {
        mapPanel.addActiveFire(zoneID);
    }
    
    public void removeActiveFire(int zoneID) {
        mapPanel.removeActiveFire(zoneID);
    }

    public void addExtinguishedFire(int zoneID) {
        mapPanel.addExtinguishedFire(zoneID);
    }
}
