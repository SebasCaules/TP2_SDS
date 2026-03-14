package Animation;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class OutputParser {

    public static SimulationData parse(Path filePath) throws IOException {
        List<Frame> frames = new ArrayList<>();

        try (BufferedReader br = Files.newBufferedReader(filePath)) {
            String line = nextNonEmptyLine(br);
            if (line == null) {
                throw new IOException("Archivo vacio: " + filePath);
            }
            int n = Integer.parseInt(line.trim());

            line = nextNonEmptyLine(br);
            if (line == null) {
                throw new IOException("Falta la linea de metadata en: " + filePath);
            }
            String[] meta = line.trim().split("\\s+");
            if (meta.length < 3) {
                throw new IOException("Metadata invalida (esperado: L eta escenario): " + line);
            }
            double l = Double.parseDouble(meta[0]);
            double eta = Double.parseDouble(meta[1]);
            String scenario = meta[2];

            while (true) {
                String timeLine = nextNonEmptyLine(br);
                if (timeLine == null) {
                    break;
                }

                int time = Integer.parseInt(timeLine.trim());
                ParticleState[] particles = new ParticleState[n];

                for (int i = 0; i < n; i++) {
                    String particleLine = nextNonEmptyLine(br);
                    if (particleLine == null) {
                        throw new IOException("Frame incompleto en t=" + time + " para " + filePath);
                    }

                    String[] parts = particleLine.trim().split("\\s+");
                    if (parts.length < 5) {
                        throw new IOException("Linea de particula invalida en t=" + time + ": " + particleLine);
                    }

                    double x = Double.parseDouble(parts[0]);
                    double y = Double.parseDouble(parts[1]);
                    double vx = Double.parseDouble(parts[2]);
                    double vy = Double.parseDouble(parts[3]);
                    boolean leader = Integer.parseInt(parts[4]) == 1;
                    particles[i] = new ParticleState(x, y, vx, vy, leader);
                }

                frames.add(new Frame(time, particles));
            }

            if (frames.isEmpty()) {
                throw new IOException("No se encontraron frames en: " + filePath);
            }

            return new SimulationData(n, l, eta, scenario, frames);
        }
    }

    private static String nextNonEmptyLine(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine()) != null) {
            if (!line.trim().isEmpty()) {
                return line;
            }
        }
        return null;
    }
}

