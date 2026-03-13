package Animation;

import java.util.List;

/**
 * Holds all parsed data from the simulation output file.
 * Each Frame represents one time step.
 */
public class SimulationData {

    public final double L;           // Box side length
    public final double eta;         // Noise parameter
    public final double rho;         // Density
    public final int N;              // Number of particles (including leader)
    public final List<Frame> frames;

    public SimulationData(double L, double eta, double rho, int N, List<Frame> frames) {
        this.L = L;
        this.eta = eta;
        this.rho = rho;
        this.N = N;
        this.frames = frames;
    }

    // -----------------------------------------------------------------------

    /**
     * One time step of the simulation.
     */
    public static class Frame {
        public final int time;
        public final List<Particle> particles;
        public final double polarization; // va = (1/N*v) * |sum vi|

        public Frame(int time, List<Particle> particles) {
            this.time = time;
            this.particles = particles;
            this.polarization = computePolarization(particles);
        }

        private static double computePolarization(List<Particle> particles) {
            if (particles.isEmpty()) return 0.0;
            double sumVx = 0, sumVy = 0;
            double speed = 0;
            int count = 0;
            for (Particle p : particles) {
                sumVx += Math.cos(p.angle);
                sumVy += Math.sin(p.angle);
                speed += p.speed;
                count++;
            }
            double avgSpeed = speed / count;
            if (avgSpeed == 0) return 0.0;
            return Math.sqrt(sumVx * sumVx + sumVy * sumVy) / (count * avgSpeed);
        }

        @Override
        public String toString() {
            return String.format("Frame[t=%d, N=%d, va=%.4f]", time, particles.size(), polarization);
        }
    }

    // -----------------------------------------------------------------------

    /**
     * One particle at one time step.
     */
    public static class Particle {
        public final double x;       // position x
        public final double y;       // position y
        public final double angle;   // velocity angle in radians [-pi, pi]
        public final double speed;   // scalar speed (v)
        public final boolean isLeader;

        public Particle(double x, double y, double angle, double speed, boolean isLeader) {
            this.x = x;
            this.y = y;
            this.angle = angle;
            this.speed = speed;
            this.isLeader = isLeader;
        }

        /** Velocity components */
        public double vx() { return speed * Math.cos(angle); }
        public double vy() { return speed * Math.sin(angle); }

        @Override
        public String toString() {
            return String.format("Particle[x=%.3f y=%.3f angle=%.3f leader=%b]",
                    x, y, angle, isLeader);
        }
    }
}
