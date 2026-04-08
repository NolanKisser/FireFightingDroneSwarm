package ui;

import model.*;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ZoneMap class represents Zones for the Firefighting Drone Swarm.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */

public class ZoneMap extends JPanel {
    private final List<Zone> zones = new ArrayList<>();

    public void loadZonesCSV(String zoneFilePath) {
        zones.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(zoneFilePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] row = line.split(",");
                if (row.length < 3) continue;
                int zoneID = Integer.parseInt(row[0].trim());
                String[] startCoords = row[1].replace("(", "").replace(")", "").split(";");
                int x1 = Integer.parseInt(startCoords[0].trim());
                int y1 = Integer.parseInt(startCoords[1].trim());
                String[] endCoords = row[2].replace("(", "").replace(")", "").split(";");
                int x2 = Integer.parseInt(endCoords[0].trim());
                int y2 = Integer.parseInt(endCoords[1].trim());
                zones.add(new Zone(zoneID, x1, y1, x2, y2));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        repaint();
    }

    private static class DroneRenderInfo {
        double x, y;
        String state;
        String fault;
    }
    
    private final Map<Integer, DroneRenderInfo> drones = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> activeFires = new ConcurrentHashMap<>();
    private final java.util.Set<Integer> extinguishedFires = ConcurrentHashMap.newKeySet();

    public void addActiveFire(int zoneID) {
        // Increment the count of fires for this zone
        activeFires.put(zoneID, activeFires.getOrDefault(zoneID, 0) + 1);
        extinguishedFires.remove(zoneID);
        repaint();
    }

    public void removeActiveFire(int zoneID) {
        // Decrement the active fire count safely
        int count = activeFires.getOrDefault(zoneID, 0);
        if (count > 0) {
            activeFires.put(zoneID, count - 1);
        }
        repaint();
    }

    public void addExtinguishedFire(int zoneID) {
        // Decrement the active fire count
        int count = activeFires.getOrDefault(zoneID, 0);
        if (count > 0) {
            activeFires.put(zoneID, count - 1);
        }

        // ONLY mark the zone as extinguished (green) if there are 0 active fires left
        if (activeFires.getOrDefault(zoneID, 0) == 0) {
            extinguishedFires.add(zoneID);
        }
        repaint();
    }

    public void updateDrone(int id, double x, double y, String state, String fault) {
        DroneRenderInfo info = drones.computeIfAbsent(id, k -> new DroneRenderInfo());
        info.x = x;
        info.y = y;
        info.state = state;
        info.fault = fault;
        repaint();
    }

    public ZoneMap() {
        this.setBackground(Color.WHITE);
        loadZonesCSV("Final_zone_file_w26.csv");
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        //Fixed World Bounds as per assumptions
        final int WORLD_WIDTH = 2500;  // 2500 meters
        final int WORLD_HEIGHT = 2500; // 2500 meters
        final int GRID_SIZE = 100;     // 100 meters per box
        final int ZONE_WIDTH = 2500;   // 2500 meters zone area

        //Calculate scale to fit the panel perfectly without stretching
        double scaleX = (double) getWidth() / WORLD_WIDTH;
        double scaleY = (double) getHeight() / WORLD_HEIGHT;
        double fitScale = Math.min(scaleX, scaleY);

        //Center the fixed grid in the panel
        int offsetX = (int) (getWidth() - (WORLD_WIDTH * fitScale)) / 2;
        int offsetY = (int) (getHeight() - (WORLD_HEIGHT * fitScale)) / 2;

        //Draw the 100m Grid boxes
        g2.setColor(new Color(230, 230, 230));
        for (int i = 0; i <= WORLD_WIDTH; i += GRID_SIZE) {
            // if (i > WORLD_WIDTH - (GRID_SIZE * 6) && i < WORLD_WIDTH) {
            //     continue;
            // }
            int x = offsetX + (int) (i * fitScale);
            g2.drawLine(x, offsetY, x, offsetY + (int) (WORLD_HEIGHT * fitScale));
        }
        for (int j = 0; j <= WORLD_HEIGHT; j += GRID_SIZE) {
            int y = offsetY + (int) (j * fitScale);
            g2.drawLine(offsetX, y, offsetX + (int) (WORLD_WIDTH * fitScale), y);
        }

        // Zone area border (2500m x 2500m)
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(
                offsetX,
                offsetY,
                (int) (ZONE_WIDTH * fitScale),
                (int) (WORLD_HEIGHT * fitScale)
        );

        // Legend aligned to grid units - using 2x2 cells for better visibility
        int legendCellSize = (int) (GRID_SIZE * fitScale);
        int legendIconSize = legendCellSize * 2;  // 2x2 cells for legend icons
        int legendStartX = offsetX + (int) ((ZONE_WIDTH + GRID_SIZE) * fitScale);
        int legendStartY = offsetY;
        int legendFontSize = Math.max(14, (int) (legendCellSize * 0.3));
        g2.setFont(new Font("SansSerif", Font.PLAIN, legendFontSize));
        g2.setColor(Color.BLACK);

        int legendY = legendStartY;
        int labelTextPad = Math.max(4, (int) (legendIconSize * 0.05));
        int descTextPad = legendIconSize + Math.max(10, (int) (legendIconSize * 0.08));
        int descX = legendStartX + descTextPad;
        int descTextY = legendY + legendIconSize - labelTextPad;

        g2.setColor(new Color(220, 240, 220));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("Z(n)", legendStartX, descTextY);
        g2.drawString("Zone Label", descX, descTextY);
        legendY += legendIconSize;

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(255, 0, 0));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("Active fire", descX, descTextY);
        legendY += legendIconSize;

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(90, 170, 70));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("Extinguished fire", descX, descTextY);
        legendY += legendIconSize;

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(0, 0, 255));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("Idle / Refilling", descX, descTextY);
        legendY += legendIconSize;

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(240, 200, 60));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("D(n)", legendStartX, descTextY);
        g2.drawString("Drone outbound", descX, descTextY);
        legendY += legendIconSize;

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(110, 160, 60));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("D(n)", legendStartX, descTextY);
        g2.drawString("Drone Extinguishing fire", descX, descTextY);
        legendY += legendIconSize;

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(190, 110, 200));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("D(n)", legendStartX, descTextY);
        g2.drawString("Drone Returning", descX, descTextY);
        legendY += legendIconSize;

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(170, 171, 164));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("D(n)", legendStartX, descTextY);
        g2.drawString("Drone Offline", descX, descTextY);
        legendY += legendIconSize * 2;

        g2.setFont(g2.getFont().deriveFont(Font.BOLD));
        g2.drawString("Fault Types", legendStartX, legendY - 5);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN));

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(250, 87, 87));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("D(n)", legendStartX, descTextY);
        g2.drawString("Drone nozzle jammed", descX, descTextY);
        legendY += legendIconSize;

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(130, 5, 5));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("D(n)", legendStartX, descTextY);
        g2.drawString("Drone stuck in flight", descX, descTextY);
        legendY += legendIconSize;

        descTextY = legendY + legendIconSize - labelTextPad;
        g2.setColor(new Color(252, 136, 20));
        g2.fillRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendIconSize, legendIconSize);
        g2.drawString("D(n)", legendStartX, descTextY);
        g2.drawString("Drone communication lost", descX, descTextY);

        int offlineDroneX = legendStartX + 250;
        int offlineDroneY = legendStartY + 25 + (legendIconSize * 2);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD));
        g2.drawString("OFFLINE DRONES", offlineDroneX - 10, offlineDroneY - (legendIconSize) + 15);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN));

        //Draw Zones from dummy data
        for (Zone z : zones) {
            int x = offsetX + (int) (z.getX1() * fitScale);
            int y = offsetY + (int) (z.getY1() * fitScale);
            int w = (int) ((z.getX2() - z.getX1()) * fitScale);
            int h = (int) ((z.getY2() - z.getY1()) * fitScale);

            g2.setColor(new Color(150, 100, 200));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(x, y, w, h);

            // Zone Label Box
            int labelBoxSize = (int) (GRID_SIZE * fitScale);
            int labelBoxW = Math.min(labelBoxSize, w);
            int labelBoxH = Math.min(labelBoxSize, h);
            g2.setColor(new Color(220, 240, 220));
            g2.fillRect(x + 1, y + 1, Math.max(0, labelBoxW - 2), Math.max(0, labelBoxH - 2));
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            String label = "Z(" + z.getZoneID() + ")";
            FontMetrics fm = g2.getFontMetrics();
            int textX = x + (labelBoxW - fm.stringWidth(label)) / 2;
            int textY = y + (labelBoxH + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(label, textX, textY);

            // Draw active or extinguished fire at the center of the zone (1 unit block = 1 GRID_SIZE)
            int fireUnitSize = (int) (GRID_SIZE * fitScale);
            int centerX = offsetX + (int) (z.getCenterX() * fitScale);
            int centerY = offsetY + (int) (z.getCenterY() * fitScale);

            if (activeFires.getOrDefault(z.getZoneID(), 0) > 0) {
                g2.setColor(new Color(255, 0, 0)); // Red active fire
                // Center the unit block on the logical center coordinate
                g2.fillRect(centerX - fireUnitSize / 2, centerY - fireUnitSize / 2, fireUnitSize, fireUnitSize);
                g2.setColor(Color.BLACK);
                g2.drawRect(centerX - fireUnitSize / 2, centerY - fireUnitSize / 2, fireUnitSize, fireUnitSize);
            } else if (extinguishedFires.contains(z.getZoneID())) {
                g2.setColor(new Color(90, 170, 70)); // Green extinguished fire
                // Center the unit block on the logical center coordinate
                g2.fillRect(centerX - fireUnitSize / 2, centerY - fireUnitSize / 2, fireUnitSize, fireUnitSize);
                g2.setColor(Color.BLACK);
                g2.drawRect(centerX - fireUnitSize / 2, centerY - fireUnitSize / 2, fireUnitSize, fireUnitSize);
            }
        }

        // Draw Drones
        for (Map.Entry<Integer, DroneRenderInfo> entry : drones.entrySet()) {
            int id = entry.getKey();
            DroneRenderInfo info = entry.getValue();
            
            int droneX = offsetX + (int) (info.x * fitScale);
            int droneY = offsetY + (int) (info.y * fitScale);
            int droneSize = (int) (GRID_SIZE * fitScale); // Render size as 1 strict unit

            if (info.fault != null && !"NONE".equalsIgnoreCase(info.fault)) {
                if("NOZZLE_JAMMED".equalsIgnoreCase(info.fault)) {
                    g2.setColor(new Color(250, 87, 87));
                } else if ("STUCK_IN_FLIGHT".equalsIgnoreCase(info.fault)) {
                    g2.setColor(new Color(130, 5, 5));
                } else if ("COMMUNICATION_LOST".equalsIgnoreCase(info.fault)) {
                    g2.setColor(new Color(252, 136, 20));
                } else {
                    g2.setColor(Color.DARK_GRAY);
                }

            } else if ("EN_ROUTE".equals(info.state)) {
                g2.setColor(new Color(240, 200, 60)); // Yellow
            } else if ("EXTINGUISHING".equals(info.state)) {
                g2.setColor(new Color(110, 160, 60)); // Green
            } else if ("RETURNING".equals(info.state)) {
                g2.setColor(new Color(190, 110, 200)); // Purple
            } else if ("IDLE".equals(info.state) || "REFILLING".equals(info.state)) {
                g2.setColor(new Color(0, 0, 255)); // Base / Default
            } else if ("OFFLINE".equals(info.state)) {
                offlineDroneY = legendStartY + 25 + (legendCellSize * (2 + id));
                droneX = offlineDroneX;
                droneY = offlineDroneY;
                g2.setColor(new Color(170, 171, 164));
            }else {
                g2.setColor(Color.DARK_GRAY); // Faulted or unknown
            }
            
            g2.fillRect(droneX - droneSize/2, droneY - droneSize/2, droneSize, droneSize);
            g2.setColor(Color.BLACK);
            g2.drawRect(droneX - droneSize/2, droneY - droneSize/2, droneSize, droneSize);
            
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(10, (int) (18 * fitScale))));
            String droneLabel = "D(" + id + ")";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(droneLabel, droneX - fm.stringWidth(droneLabel) / 2, droneY + fm.getAscent() / 2 - 2);
        }
    }
}
