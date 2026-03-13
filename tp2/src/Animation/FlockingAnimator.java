package Animation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.IOException;
import java.util.List;

/**
 * FlockingAnimator
 * ----------------
 * Reads a Vicsek flocking simulation output file and renders an interactive
 * animation using Java Swing / Java2D.
 *
 * Features:
 *  - Each particle is drawn as an arrow whose direction = velocity direction.
 *  - Arrow color encodes the velocity angle (full HSB hue wheel, −π → π → H 0→1).
 *  - The leader particle is drawn larger and outlined in white.
 *  - Playback controls: Play/Pause, Step Forward, Step Backward, Speed slider.
 *  - Live polarisation (va) display.
 *
 * Usage:
 *   java FlockingAnimator <output_file.txt> [fps]
 *   fps defaults to 10.
 */
public class FlockingAnimator extends JPanel {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------
    private static final int WINDOW_W    = 800;
    private static final int WINDOW_H    = 800;
    private static final int PANEL_SIZE  = 700;   // square simulation area in pixels
    private static final int MARGIN      = 50;    // pixels around the simulation square
    private static final double ARROW_LEN_FRAC = 0.018; // arrow length as fraction of PANEL_SIZE
    private static final double LEADER_SCALE   = 2.2;   // leader arrow scale multiplier

    // -----------------------------------------------------------------------
    // State
    // -----------------------------------------------------------------------
    private final SimulationData data;
    private int frameIndex = 0;
    private boolean playing = true;

    private Timer timer;
    private JLabel infoLabel;
    private JSlider speedSlider;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public FlockingAnimator(SimulationData data, int fps) {
        this.data = data;
        setPreferredSize(new Dimension(PANEL_SIZE, PANEL_SIZE));
        setBackground(Color.BLACK);

        // Animation timer
        timer = new Timer(1000 / fps, e -> {
            if (playing) {
                frameIndex = (frameIndex + 1) % data.frames.size();
                repaint();
                updateInfo();
            }
        });
        timer.start();
    }

    // -----------------------------------------------------------------------
    // Painting
    // -----------------------------------------------------------------------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        SimulationData.Frame frame = data.frames.get(frameIndex);
        double L = data.L;
        double arrowLen = PANEL_SIZE * ARROW_LEN_FRAC;

        for (SimulationData.Particle p : frame.particles) {
            // Map simulation coords → pixel coords
            double px = (p.x / L) * PANEL_SIZE;
            double py = PANEL_SIZE - (p.y / L) * PANEL_SIZE; // flip Y

            // Colour by angle: angle ∈ [-π, π] → hue ∈ [0, 1]
            float hue = (float) ((p.angle + Math.PI) / (2 * Math.PI));
            Color arrowColor = Color.getHSBColor(hue, 1.0f, 1.0f);

            double scale = p.isLeader ? LEADER_SCALE : 1.0;
            drawArrow(g2, px, py, p.angle, arrowLen * scale, arrowColor, p.isLeader);
        }
    }

    /**
     * Draw a velocity arrow centred at (cx, cy) pointing in direction {@code angle}.
     * The arrow origin is the particle position; the tip points in the direction of motion.
     */
    private void drawArrow(Graphics2D g2, double cx, double cy,
                           double angle, double len, Color color, boolean leader) {
        double cosA = Math.cos(angle);
        double sinA = -Math.sin(angle); // screen Y is inverted

        double x1 = cx - cosA * len * 0.5;
        double y1 = cy - sinA * len * 0.5;
        double x2 = cx + cosA * len * 0.5;
        double y2 = cy + sinA * len * 0.5;

        // Shaft
        g2.setColor(color);
        g2.setStroke(new BasicStroke(leader ? 2.5f : 1.2f));
        g2.draw(new Line2D.Double(x1, y1, x2, y2));

        // Arrowhead
        double headLen  = len * 0.4;
        double headAngle = Math.PI / 7;
        double ax1 = x2 - headLen * Math.cos(angle - headAngle) * Math.signum(Math.cos(angle) + 1e-9);
        double ay1 = y2 + headLen * Math.sin(angle - headAngle) * Math.signum(Math.cos(angle) + 1e-9);
        double ax2 = x2 - headLen * Math.cos(angle + headAngle) * Math.signum(Math.cos(angle) + 1e-9);
        double ay2 = y2 + headLen * Math.sin(angle + headAngle) * Math.signum(Math.cos(angle) + 1e-9);

        // Simpler arrowhead via rotation
        double[] tip = rotatePoint(len * 0.38, 0, angle);
        double[] left  = rotatePoint(len * 0.0, -len * 0.18, angle);
        double[] right = rotatePoint(len * 0.0,  len * 0.18, angle);

        int[] xs = { (int)(x2), (int)(x2 - tip[0] + left[0]),  (int)(x2 - tip[0] + right[0]) };
        int[] ys = { (int)(y2), (int)(y2 + tip[1] - left[1]),  (int)(y2 + tip[1] - right[1]) };

        g2.fillPolygon(xs, ys, 3);

        // Leader: white outline circle
        if (leader) {
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.5f));
            int r = (int)(len * 0.55);
            g2.drawOval((int)(cx - r / 2.0), (int)(cy - r / 2.0), r, r);
        }
    }

    /** Rotate point (x,y) by angle around origin; returns new (x', y'). */
    private double[] rotatePoint(double x, double y, double angle) {
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new double[]{ x * cos - y * sin, x * sin + y * cos };
    }

    // -----------------------------------------------------------------------
    // Controls & Window
    // -----------------------------------------------------------------------
    private void updateInfo() {
        if (infoLabel == null) return;
        SimulationData.Frame f = data.frames.get(frameIndex);
        infoLabel.setText(String.format(
                "  t = %d  |  frame %d / %d  |  va = %.4f  |  η = %.3f",
                f.time, frameIndex + 1, data.frames.size(), f.polarization, data.eta));
    }

    /** Build the full JFrame with the animation panel and controls. */
    public static JFrame buildWindow(SimulationData data, int fps) {
        JFrame frame = new JFrame("Flocking Animator  —  Vicsek Model");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        FlockingAnimator animator = new FlockingAnimator(data, fps);

        // --- Info bar ---
        JLabel info = new JLabel("  Loading…");
        info.setFont(new Font("Monospaced", Font.PLAIN, 13));
        info.setForeground(Color.WHITE);
        info.setBackground(new Color(30, 30, 30));
        info.setOpaque(true);
        animator.infoLabel = info;

        // --- Control bar ---
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        controls.setBackground(new Color(45, 45, 45));

        JButton playPause = new JButton("⏸ Pause");
        styleButton(playPause);
        playPause.addActionListener(e -> {
            animator.playing = !animator.playing;
            playPause.setText(animator.playing ? "⏸ Pause" : "▶ Play");
        });

        JButton stepBack = new JButton("◀ Step");
        styleButton(stepBack);
        stepBack.addActionListener(e -> {
            animator.playing = false;
            playPause.setText("▶ Play");
            animator.frameIndex = Math.max(0, animator.frameIndex - 1);
            animator.repaint();
            animator.updateInfo();
        });

        JButton stepFwd = new JButton("Step ▶");
        styleButton(stepFwd);
        stepFwd.addActionListener(e -> {
            animator.playing = false;
            playPause.setText("▶ Play");
            animator.frameIndex = Math.min(data.frames.size() - 1, animator.frameIndex + 1);
            animator.repaint();
            animator.updateInfo();
        });

        // Speed slider (fps)
        JLabel speedLabel = new JLabel("Speed:");
        speedLabel.setForeground(Color.WHITE);
        JSlider speed = new JSlider(1, 60, fps);
        speed.setBackground(new Color(45, 45, 45));
        speed.setForeground(Color.WHITE);
        speed.addChangeListener(e -> animator.timer.setDelay(1000 / speed.getValue()));
        animator.speedSlider = speed;

        // Frame scrubber
        JLabel scrubLabel = new JLabel("Frame:");
        scrubLabel.setForeground(Color.WHITE);
        JSlider scrubber = new JSlider(0, data.frames.size() - 1, 0);
        scrubber.setPreferredSize(new Dimension(200, 20));
        scrubber.setBackground(new Color(45, 45, 45));
        scrubber.addChangeListener(e -> {
            if (scrubber.getValueIsAdjusting()) {
                animator.playing = false;
                playPause.setText("▶ Play");
                animator.frameIndex = scrubber.getValue();
                animator.repaint();
                animator.updateInfo();
            }
        });
        // Keep scrubber in sync during playback
        animator.timer.addActionListener(e -> scrubber.setValue(animator.frameIndex));

        controls.add(stepBack);
        controls.add(playPause);
        controls.add(stepFwd);
        controls.add(Box.createHorizontalStrut(10));
        controls.add(speedLabel);
        controls.add(speed);
        controls.add(Box.createHorizontalStrut(10));
        controls.add(scrubLabel);
        controls.add(scrubber);

        // --- Assemble ---

        frame.add(info, BorderLayout.NORTH);
        frame.add(animator, BorderLayout.CENTER);
        frame.add(controls, BorderLayout.SOUTH);

        frame.pack();
        frame.setLocationRelativeTo(null);
        animator.updateInfo();
        return frame;
    }

    private static void styleButton(JButton b) {
        b.setBackground(new Color(70, 70, 70));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
    }

    // -----------------------------------------------------------------------
    // Main
    // -----------------------------------------------------------------------
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java FlockingAnimator <output_file.txt> [fps]");
            System.exit(1);
        }
        String filepath = args[0];
        int fps = args.length >= 2 ? Integer.parseInt(args[1]) : 10;

        SwingUtilities.invokeLater(() -> {
            try {
                SimulationData data = SimulationParser.parse(filepath);
                JFrame window = buildWindow(data, fps);
                window.setVisible(true);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(null,
                        "Error reading file:\n" + e.getMessage(),
                        "Parse Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
