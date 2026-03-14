package Animation;

import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
            if (!dragging[0]) {
                animationPanel.nextFrame();
                slider.setValue(animationPanel.getFrameIndex());
                updateStatus(status, animationPanel, data, sourcePath);
            }
        });
        timer.start();

        JPanel container = new JPanel(new BorderLayout());
        container.add(status, BorderLayout.NORTH);
        container.add(animationPanel, BorderLayout.CENTER);
        container.add(slider, BorderLayout.SOUTH);

        frame.setContentPane(container);
        frame.pack();
        frame.setLocationRelativeTo(null);
        updateStatus(status, animationPanel, data, sourcePath);
        frame.setVisible(true);
    }

    private static void updateStatus(JLabel status, AnimationPanel panel, SimulationData data, Path sourcePath) {
        Frame current = panel.getCurrentFrame();
        status.setText(String.format(
                "Archivo: %s | Escenario: %s | eta=%.2f | Frame %d/%d | t=%d",
                sourcePath.getFileName(),
                data.scenario,
                data.eta,
                panel.getFrameIndex() + 1,
                panel.getFrameCount(),
                current.time
        ));
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
                System.err.println("Error al abrir animacion: " + e.getMessage());
                System.err.println("Uso: java -cp tp2/src Animation.AnimationLauncher output/off_lattice_A_eta0.00.txt");
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                System.err.print(sw);
            }
        });
    }
}

