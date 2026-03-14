package Animation;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class AnimationPanel extends JPanel {
    private static final int PANEL_SIZE = 760;
    private static final int MARGIN = 30;

    private final SimulationData data;
    private int frameIndex;

    public AnimationPanel(SimulationData data) {
        this.data = data;
        this.frameIndex = 0;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(PANEL_SIZE, PANEL_SIZE));
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public int getFrameCount() {
        return data.frames.size();
    }

    public Frame getCurrentFrame() {
        return data.frames.get(frameIndex);
    }

    public void setFrameIndex(int frameIndex) {
        int frameCount = getFrameCount();
        if (frameCount == 0) {
            this.frameIndex = 0;
        } else {
            int normalized = frameIndex % frameCount;
            if (normalized < 0) {
                normalized += frameCount;
            }
            this.frameIndex = normalized;
        }
        repaint();
    }

    public void nextFrame() {
        setFrameIndex(frameIndex + 1);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int drawSize = Math.min(w, h) - 2 * MARGIN;
        int originX = (w - drawSize) / 2;
        int originY = (h - drawSize) / 2;
        double scale = drawSize / data.sideLength;

        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(2f));
        g2.drawRect(originX, originY, drawSize, drawSize);

        Frame frame = getCurrentFrame();
        for (ParticleState p : frame.particles) {
            int px = originX + (int) Math.round(p.x * scale);
            int py = originY + drawSize - (int) Math.round(p.y * scale);

            int radius = p.leader ? 6 : 4;
            g2.setColor(p.leader ? new Color(200, 40, 40) : new Color(40, 90, 210));
            g2.fillOval(px - radius, py - radius, 2 * radius, 2 * radius);

            g2.setColor(p.leader ? new Color(120, 20, 20) : new Color(20, 60, 150));
            int vxPix = (int) Math.round(p.vx * scale * 8.0);
            int vyPix = (int) Math.round(-p.vy * scale * 8.0);
            g2.drawLine(px, py, px + vxPix, py + vyPix);
        }

        g2.dispose();
    }
}

