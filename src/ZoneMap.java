import javax.swing.*;
import java.awt.*;

/**
 * ZoneMap class represents Zones for the Firefighting Drone Swarm.
 * @author Jordan Grewal, Ozan Kaya, Nolan Kisser, Celina Yang
 * @version January 31, 2026
 */

public class ZoneMap extends JPanel {
    private static final int[][] DUMMY_ZONES = {
            {1, 0, 0, 1000, 700},
            {2, 1000, 0, 2000, 700},
            {3, 0, 700, 1000, 1500},
            {4, 1000, 700, 2000, 1500}
    };

    public ZoneMap() {
        this.setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        //Fixed World Bounds as per assumptions
        final int WORLD_WIDTH = 3000;  // 3000 meters
        final int WORLD_HEIGHT = 1500; // 1500 meters
        final int GRID_SIZE = 100;     // 100 meters per box
        final int ZONE_WIDTH = 2000;   // 2000 meters zone area

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
            if (i > WORLD_WIDTH - (GRID_SIZE * 6) && i < WORLD_WIDTH) {
                continue;
            }
            int x = offsetX + (int) (i * fitScale);
            g2.drawLine(x, offsetY, x, offsetY + (int) (WORLD_HEIGHT * fitScale));
        }
        for (int j = 0; j <= WORLD_HEIGHT; j += GRID_SIZE) {
            int y = offsetY + (int) (j * fitScale);
            g2.drawLine(offsetX, y, offsetX + (int) (WORLD_WIDTH * fitScale), y);
        }

        // Zone area border (2000m x 1500m)
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2));
        g2.drawRect(
                offsetX,
                offsetY,
                (int) (ZONE_WIDTH * fitScale),
                (int) (WORLD_HEIGHT * fitScale)
        );

        // Legend aligned to grid units
        int legendCellSize = (int) (GRID_SIZE * fitScale);
        int legendStartX = offsetX + (int) ((WORLD_WIDTH - (GRID_SIZE * 7)) * fitScale);
        int legendStartY = offsetY;
        int legendFontSize = Math.max(10, (int) (legendCellSize * 0.40));
        g2.setFont(new Font("SansSerif", Font.PLAIN, legendFontSize));
        g2.setColor(Color.BLACK);

        int legendY = legendStartY;
        int labelTextPad = Math.max(4, (int) (legendCellSize * 0.08));
        int descTextPad = Math.max(6, (int) (legendCellSize * 0.12));
        int descColX = offsetX + (int) ((WORLD_WIDTH - (GRID_SIZE * 6)) * fitScale);
        int descX = descColX + descTextPad;
        int descTextY = legendY + legendCellSize - labelTextPad;

        g2.setColor(new Color(220, 240, 220));
        g2.fillRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.drawString("Z(n)", legendStartX + labelTextPad, descTextY);
        g2.drawString("Zone Label", descX, descTextY);
        legendY += legendCellSize;

        descTextY = legendY + legendCellSize - labelTextPad;
        g2.setColor(new Color(220, 40, 40));
        g2.fillRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.drawString("Active fire", descX, descTextY);
        legendY += legendCellSize;

        descTextY = legendY + legendCellSize - labelTextPad;
        g2.setColor(new Color(90, 170, 70));
        g2.fillRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.drawString("Extinguished fire", descX, descTextY);
        legendY += legendCellSize;

        descTextY = legendY + legendCellSize - labelTextPad;
        g2.setColor(new Color(240, 200, 60));
        g2.fillRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.drawString("D(n)", legendStartX + labelTextPad, descTextY);
        g2.drawString("Drone outbound", descX, descTextY);
        legendY += legendCellSize;

        descTextY = legendY + legendCellSize - labelTextPad;
        g2.setColor(new Color(110, 160, 60));
        g2.fillRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.drawString("D(n)", legendStartX + labelTextPad, descTextY);
        g2.drawString("Drone Extinguishing fire", descX, descTextY);
        legendY += legendCellSize;

        descTextY = legendY + legendCellSize - labelTextPad;
        g2.setColor(new Color(190, 110, 200));
        g2.fillRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.setColor(Color.BLACK);
        g2.drawRect(legendStartX, legendY, legendCellSize, legendCellSize);
        g2.drawString("D(3)", legendStartX + labelTextPad, descTextY);
        g2.drawString("Drone Returning", descX, descTextY);

        //Draw Zones from dummy data
        for (int[] z : DUMMY_ZONES) {
            int x = offsetX + (int) (z[1] * fitScale);
            int y = offsetY + (int) (z[2] * fitScale);
            int w = (int) ((z[3] - z[1]) * fitScale);
            int h = (int) ((z[4] - z[2]) * fitScale);

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
            String label = "Z(" + z[0] + ")";
            FontMetrics fm = g2.getFontMetrics();
            int textX = x + (labelBoxW - fm.stringWidth(label)) / 2;
            int textY = y + (labelBoxH + fm.getAscent() - fm.getDescent()) / 2;
            g2.drawString(label, textX, textY);
        }
    }
}
