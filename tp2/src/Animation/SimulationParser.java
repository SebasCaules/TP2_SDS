package Animation;

import java.io.*;
import java.util.*;

/**
 * Parses the simulation output text file produced by the Vicsek flocking simulation.
 *
 * Expected file format
 * --------------------
 * Line 1 (header):   L <value> ETA <value> RHO <value> N <value>
 *
 * Then, for each time step:
 *   t <time>
 *   <x> <y> <angle> <speed> <isLeader>
 *   <x> <y> <angle> <speed> <isLeader>
 *   ...  (N lines, one per particle)
 *
 * <isLeader> is 1 for the leader particle, 0 otherwise.
 * Angles are in radians.
 *
 * Example:
 * --------
 *   L 10.0 ETA 0.5 RHO 4.0 N 400
 *   t 0
 *   1.234 5.678 1.047 0.3 0
 *   3.100 2.900 -2.094 0.3 1
 *   ...
 */
public class SimulationParser {

    /**
     * Parse a simulation output file and return a {@link SimulationData} object.
     *
     * @param filepath path to the output .txt file
     * @return parsed simulation data
     * @throws IOException if the file cannot be read or has an unexpected format
     */
    public static SimulationData parse(String filepath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filepath))) {

            // --- Header ---
            String headerLine = reader.readLine();
            if (headerLine == null) throw new IOException("Empty file: " + filepath);

            double L   = parseHeaderValue(headerLine, "L");
            double eta = parseHeaderValue(headerLine, "ETA");
            double rho = parseHeaderValue(headerLine, "RHO");
            int    N   = (int) parseHeaderValue(headerLine, "N");

            // --- Frames ---
            List<SimulationData.Frame> frames = new ArrayList<>();
            String line;
            int currentTime = -1;
            List<SimulationData.Particle> currentParticles = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("t ")) {
                    // Save previous frame
                    if (currentParticles != null) {
                        frames.add(new SimulationData.Frame(currentTime, currentParticles));
                    }
                    currentTime = Integer.parseInt(line.substring(2).trim());
                    currentParticles = new ArrayList<>(N);
                } else {
                    // Particle line
                    if (currentParticles == null) {
                        throw new IOException("Particle data found before any time-step header.");
                    }
                    SimulationData.Particle p = parseParticle(line);
                    currentParticles.add(p);
                }
            }

            // Save last frame
            if (currentParticles != null && !currentParticles.isEmpty()) {
                frames.add(new SimulationData.Frame(currentTime, currentParticles));
            }

            if (frames.isEmpty()) throw new IOException("No frames found in file: " + filepath);

            System.out.printf("Parsed %d frames | L=%.1f eta=%.3f rho=%.1f N=%d%n",
                    frames.size(), L, eta, rho, N);
            return new SimulationData(L, eta, rho, N, frames);
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Extract a numeric value from a header like "L 10.0 ETA 0.5 RHO 4.0 N 400".
     * Looks for the token right after the given key (case-insensitive).
     */
    private static double parseHeaderValue(String header, String key) throws IOException {
        String[] tokens = header.trim().split("\\s+");
        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].equalsIgnoreCase(key)) {
                try {
                    return Double.parseDouble(tokens[i + 1]);
                } catch (NumberFormatException e) {
                    throw new IOException("Cannot parse value for key '" + key + "' in header: " + header);
                }
            }
        }
        throw new IOException("Key '" + key + "' not found in header: " + header);
    }

    /**
     * Parse a particle line: "x y angle speed isLeader"
     * isLeader = 1 → true, 0 → false
     */
    private static SimulationData.Particle parseParticle(String line) throws IOException {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 5) {
            throw new IOException("Invalid particle line (expected 5 fields): " + line);
        }
        try {
            double x        = Double.parseDouble(parts[0]);
            double y        = Double.parseDouble(parts[1]);
            double angle    = Double.parseDouble(parts[2]);
            double speed    = Double.parseDouble(parts[3]);
            boolean isLeader = parts[4].equals("1");
            return new SimulationData.Particle(x, y, angle, speed, isLeader);
        } catch (NumberFormatException e) {
            throw new IOException("Cannot parse numbers in particle line: " + line, e);
        }
    }

    // -----------------------------------------------------------------------
    // Quick smoke test
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java SimulationParser <output_file.txt>");
            System.exit(1);
        }
        SimulationData data = parse(args[0]);
        System.out.println("First frame: " + data.frames.get(0));
        System.out.println("Last  frame: " + data.frames.get(data.frames.size() - 1));
    }
}
