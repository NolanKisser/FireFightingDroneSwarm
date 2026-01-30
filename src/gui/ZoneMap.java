package gui;

import core.Scheduler;
import core.Zone;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class ZoneMap extends JPanel {
    private final Scheduler scheduler;

    public ZoneMap(Scheduler scheduler) {
        this.scheduler = scheduler;
        this.setBackground(Color.WHITE);
        // Requirement: Minimal GUI for early iterations [cite: 182]
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Map<Integer, Zone> zones = scheduler.getZones();
        if (zones.isEmpty()) return;

        // 1. Determine the world size in meters from your data [cite: 166]
        double maxMetersX = 0;
        double maxMetersY = 0;
        for (Zone z : zones.values()) {
            maxMetersX = Math.max(maxMetersX, z.getX2());
            maxMetersY = Math.max(maxMetersY, z.getY2());
        }

        // 2. Calculate how many pixels represent one meter to fit the panel [cite: 14, 185]
        // Taking the minimum ensures we don't stretch the squares into rectangles [cite: 167]
        double pixelsPerMeter = Math.min(getWidth() / maxMetersX, getHeight() / maxMetersY);

        // 3. Center the grid within the white space [cite: 183]
        int offsetX = (int) (getWidth() - (maxMetersX * pixelsPerMeter)) / 2;
        int offsetY = (int) (getHeight() - (maxMetersY * pixelsPerMeter)) / 2;

        // 4. Draw the Background Grid (e.g., every 50 meters) [cite: 159, 167]
        g2.setColor(new Color(235, 235, 235));
        for (int i = 0; i <= maxMetersX; i += 50) {
            int x = offsetX + (int) (i * pixelsPerMeter);
            g2.drawLine(x, offsetY, x, offsetY + (int) (maxMetersY * pixelsPerMeter));
        }
        for (int j = 0; j <= maxMetersY; j += 50) {
            int y = offsetY + (int) (j * pixelsPerMeter);
            g2.drawLine(offsetX, y, offsetX + (int) (maxMetersX * pixelsPerMeter), y);
        }

        // 5. Draw the Zones [cite: 130, 166]
        for (Zone z : zones.values()) {
            int x = offsetX + (int) (z.getX1() * pixelsPerMeter);
            int y = offsetY + (int) (z.getY1() * pixelsPerMeter);
            int w = (int) ((z.getX2() - z.getX1()) * pixelsPerMeter);
            int h = (int) ((z.getY2() - z.getY1()) * pixelsPerMeter);

            // Purple boundary as per spec [cite: 110, 123]
            g2.setColor(new Color(150, 100, 200));
            g2.setStroke(new BasicStroke(2));
            g2.drawRect(x, y, w, h);

            // Zone Label: Light Green background [cite: 186, 189]
            g2.setColor(new Color(220, 240, 220));
            g2.fillRect(x + 1, y + 1, 40, 20);
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, 12));
            g2.drawString("Z(" + z.getZoneID() + ")", x + 5, y + 15);
        }
    }
}