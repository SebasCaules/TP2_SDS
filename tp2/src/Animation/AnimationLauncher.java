package Animation;

import javax.imageio.ImageIO;

import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class AnimationLauncher {

    private static final Path[] OUTPUT_DIR_CANDIDATES = new Path[]{
            Paths.get("output"),
            Paths.get("../output"),
            Paths.get("../../output")
    };

    private static Path resolveInputFile(String[] args) {
        if (args.length > 0) {
            return Paths.get(args[0]);
        }

        Path initialDir = findExistingOutputDirectory();
        if (initialDir == null) {
            return null;
        }

        JFileChooser chooser = new JFileChooser(initialDir.toFile());
        chooser.setDialogTitle("Elegi un archivo de simulacion (.txt)");
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos de simulacion (*.txt)", "txt"));
        chooser.setAcceptAllFileFilterUsed(false);

        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            return chooser.getSelectedFile().toPath();
        }

        return null;
    }

    private static Path findExistingOutputDirectory() {
        for (Path dir : OUTPUT_DIR_CANDIDATES) {
            if (Files.isDirectory(dir)) {
                return dir;
            }
        }
        return null;
    }

    private static void createAndShowUI(SimulationData data, Path sourcePath) {
        JFrame frame = new JFrame("Animacion Off-Lattice - " + sourcePath.getFileName());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        AnimationPanel animationPanel = new AnimationPanel(data);
        JLabel status = new JLabel();
        status.setOpaque(true);
        status.setBackground(new Color(245, 245, 245));
        status.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        status.setHorizontalAlignment(SwingConstants.CENTER);

        int maxFrame = Math.max(0, animationPanel.getFrameCount() - 1);
        JSlider slider = new JSlider(0, maxFrame, 0);
        slider.setPaintTicks(false);

        final boolean[] paused = {false};

        final boolean[] dragging = {false};
        slider.addChangeListener(ignored -> {
            if (slider.getValueIsAdjusting()) {
                dragging[0] = true;
                animationPanel.setFrameIndex(slider.getValue());
                updateStatus(status, animationPanel, data, sourcePath);
            } else if (dragging[0]) {
                dragging[0] = false;
                animationPanel.setFrameIndex(slider.getValue());
                updateStatus(status, animationPanel, data, sourcePath);
            }
        });

        Timer timer = new Timer(35, ignored -> {
            if (!dragging[0] && !paused[0]) {
                animationPanel.nextFrame();
                slider.setValue(animationPanel.getFrameIndex());
                updateStatus(status, animationPanel, data, sourcePath);
            }
        });
        timer.start();

        JButton backButton = new JButton("Volver");
        backButton.addActionListener(ignored -> {
            timer.stop();
            frame.dispose();
            openFileSelectionAndLaunch();
        });

        JButton pauseButton = new JButton("Pausar");
        pauseButton.addActionListener(ignored -> {
            paused[0] = !paused[0];
            pauseButton.setText(paused[0] ? "Reanudar" : "Pausar");
            updateStatus(status, animationPanel, data, sourcePath, paused[0]);
        });

        JButton screenshotButton = new JButton("Screenshot");
        screenshotButton.addActionListener(ignored -> {
            try {
                saveScreenshot(frame, animationPanel, sourcePath);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(
                        frame,
                        "No se pudo guardar el screenshot: " + ex.getMessage(),
                        "Screenshot",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        JTextField timeField = new JTextField(8);
        timeField.setToolTipText("Ingresa un t entero");
        JButton goToTimeButton = new JButton("Ir a t");
        goToTimeButton.addActionListener(ignored -> {
            String text = timeField.getText().trim();
            if (text.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Ingresa un valor de t", "Ir a t", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                int targetT = Integer.parseInt(text);
                int targetIndex = findFrameIndexByTime(data, targetT);
                if (targetIndex < 0) {
                    JOptionPane.showMessageDialog(
                            frame,
                            "No existe un frame con t=" + targetT,
                            "Ir a t",
                            JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }

                paused[0] = true;
                pauseButton.setText("Reanudar");
                animationPanel.setFrameIndex(targetIndex);
                slider.setValue(targetIndex);
                updateStatus(status, animationPanel, data, sourcePath, true);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(
                        frame,
                        "t debe ser un entero.",
                        "Ir a t",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(backButton);
        controls.add(pauseButton);
        controls.add(screenshotButton);
        controls.add(new JLabel("t:"));
        controls.add(timeField);
        controls.add(goToTimeButton);

        JPanel header = new JPanel(new BorderLayout());
        header.add(status, BorderLayout.NORTH);
        header.add(controls, BorderLayout.SOUTH);

        JPanel container = new JPanel(new BorderLayout());
        container.add(header, BorderLayout.NORTH);
        container.add(animationPanel, BorderLayout.CENTER);
        container.add(slider, BorderLayout.SOUTH);

        frame.setContentPane(container);
        frame.pack();
        frame.setLocationRelativeTo(null);
        updateStatus(status, animationPanel, data, sourcePath, false);
        frame.setVisible(true);
    }

    private static void updateStatus(JLabel status, AnimationPanel panel, SimulationData data, Path sourcePath) {
        updateStatus(status, panel, data, sourcePath, false);
    }

    private static void updateStatus(
            JLabel status,
            AnimationPanel panel,
            SimulationData data,
            Path sourcePath,
            boolean paused
    ) {
        Frame current = panel.getCurrentFrame();
        status.setText(String.format(
                "Archivo: %s | Escenario: %s | eta=%.2f | Frame %d/%d | t=%d | %s",
                sourcePath.getFileName(),
                data.scenario,
                data.eta,
                panel.getFrameIndex() + 1,
                panel.getFrameCount(),
                current.time,
                paused ? "Pausado" : "Reproduciendo"
        ));
    }

    private static int findFrameIndexByTime(SimulationData data, int t) {
        for (int i = 0; i < data.frames.size(); i++) {
            if (data.frames.get(i).time == t) {
                return i;
            }
        }
        return -1;
    }

    private static void saveScreenshot(JFrame owner, AnimationPanel panel, Path sourcePath) throws IOException {
        Path sourceDir = sourcePath.toAbsolutePath().getParent();
        if (sourceDir == null) {
            sourceDir = Path.of(".").toAbsolutePath();
        }

        JFileChooser chooser = new JFileChooser(sourceDir.toFile());
        chooser.setDialogTitle("Guardar screenshot");
        chooser.setFileFilter(new FileNameExtensionFilter("Imagen PNG (*.png)", "png"));
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setSelectedFile(sourceDir.resolve(buildDefaultScreenshotName(panel, sourcePath)).toFile());

        int result = chooser.showSaveDialog(owner);
        if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }

        Path target = chooser.getSelectedFile().toPath();
        if (!target.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
            target = target.resolveSibling(target.getFileName() + ".png");
        }

        int width = Math.max(1, panel.getWidth());
        int height = Math.max(1, panel.getHeight());
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        panel.paint(g2);
        g2.dispose();

        Path targetParent = target.toAbsolutePath().getParent();
        if (targetParent != null) {
            Files.createDirectories(targetParent);
        }
        ImageIO.write(image, "png", target.toFile());

        JOptionPane.showMessageDialog(
                owner,
                "Screenshot guardado en:\n" + target.toAbsolutePath(),
                "Screenshot",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    private static String buildDefaultScreenshotName(AnimationPanel panel, Path sourcePath) {
        String base = sourcePath.getFileName().toString().replaceAll("\\.txt$", "");
        Frame current = panel.getCurrentFrame();
        return String.format(Locale.US, "%s_frame%04d_t%d.png", base, panel.getFrameIndex(), current.time);
    }

    private static void openFileSelectionAndLaunch() {
        SwingUtilities.invokeLater(() -> {
            try {
                Path file = resolveInputFile(new String[0]);
                if (file == null) {
                    Path outputDir = findExistingOutputDirectory();
                    if (outputDir == null) {
                        JOptionPane.showMessageDialog(
                                null,
                                "No encontre la carpeta output. Ejecuta desde la raiz del proyecto o pasa un archivo por argumento.",
                                "Animacion Off-Lattice",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                    return;
                }

                SimulationData data = OutputParser.parse(file);
                createAndShowUI(data, file);
            } catch (Exception e) {
                showLaunchError(e);
            }
        });
    }

    private static void showLaunchError(Exception e) {
        System.err.println("Error al abrir animacion: " + e.getMessage());
        System.err.println("Uso: java -cp tp2/src Animation.AnimationLauncher output/off_lattice_A_eta0.00.txt");
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        System.err.print(sw);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Path file = resolveInputFile(args);
                if (file == null) {
                    Path outputDir = findExistingOutputDirectory();
                    if (outputDir == null) {
                        JOptionPane.showMessageDialog(
                                null,
                                "No encontre la carpeta output. Ejecuta desde la raiz del proyecto o pasa un archivo por argumento.",
                                "Animacion Off-Lattice",
                                JOptionPane.ERROR_MESSAGE
                        );
                    }
                    return;
                }

                SimulationData data = OutputParser.parse(file);
                createAndShowUI(data, file);
            } catch (Exception e) {
                showLaunchError(e);
            }
        });
    }
}

