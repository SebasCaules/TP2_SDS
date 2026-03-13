import java.io.*;
import java.util.*;

public class Simulation {

    static final double L = 10.0;
    static final double DENSITY = 4.0;
    static final int N = (int) (DENSITY * L * L);
    static final double V = 0.03;
    static final double RC = 1.0;
    static final double DT = 1.0;
    static final int STEPS = 2000;

    //lider circular
    static final double R_CIRCLE = 5.0;
    static final double OMEGA = V / R_CIRCLE;

    //estado
    double[] x, y, theta;
    boolean[] isLeader;

    double eta;
    String scenario;
    int leaderIdx;
    double leaderFixedTheta;
    double circCX, circCY, circPhi;

    //CIM
    int M;
    double cellSize;
    List<Integer>[][] grid;

    Random rng;

    public Simulation(double eta, String scenario, long seed) {
        this.eta = eta;
        this.scenario = scenario;
        this.rng = new Random(seed);

        M = Math.max(1, (int) (L / RC));
        cellSize = L / M;

        x = new double[N];
        y = new double[N];
        theta = new double[N];
        isLeader = new boolean[N];

        for (int i = 0; i < N; i++) {
            x[i] = rng.nextDouble() * L;
            y[i] = rng.nextDouble() * L;
            theta[i] = rng.nextDouble() * 2.0 * Math.PI - Math.PI;
        }

        leaderIdx = -1;
        if (!scenario.equals("A")) {
            leaderIdx = 0;
            isLeader[leaderIdx] = true;

            if (scenario.equals("B")) {
                leaderFixedTheta = rng.nextDouble() * 2.0 * Math.PI - Math.PI;
                theta[leaderIdx] = leaderFixedTheta;
            } else {
                circCX = L / 2.0;
                circCY = L / 2.0;
                circPhi = rng.nextDouble() * 2.0 * Math.PI;
                x[leaderIdx] = wrap(circCX + R_CIRCLE * Math.cos(circPhi));
                y[leaderIdx] = wrap(circCY + R_CIRCLE * Math.sin(circPhi));
                theta[leaderIdx] = circPhi + Math.PI / 2.0;
            }
        }
    }

    double wrap(double val) {
        double r = val % L;
        return r < 0 ? r + L : r;
    }

    //CIM
    @SuppressWarnings("unchecked")
    void buildGrid() {
        grid = new List[M][M];
        for (int i = 0; i < M; i++)
            for (int j = 0; j < M; j++)
                grid[i][j] = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            int cx = Math.min((int) (x[i] / cellSize), M - 1);
            int cy = Math.min((int) (y[i] / cellSize), M - 1);
            grid[cx][cy].add(i);
        }
    }

    List<Integer> getNeighbors(int idx) {
        List<Integer> nb = new ArrayList<>();
        int cx = Math.min((int) (x[idx] / cellSize), M - 1);
        int cy = Math.min((int) (y[idx] / cellSize), M - 1);
        double rcSq = RC * RC;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                int nx = ((cx + dx) % M + M) % M;
                int ny = ((cy + dy) % M + M) % M;
                for (int j : grid[nx][ny]) {
                    double ddx = x[idx] - x[j];
                    double ddy = y[idx] - y[j];
                    if (ddx > L / 2) ddx -= L;
                    else if (ddx < -L / 2) ddx += L;
                    if (ddy > L / 2) ddy -= L;
                    else if (ddy < -L / 2) ddy += L;
                    if (ddx * ddx + ddy * ddy <= rcSq) {
                        nb.add(j);
                    }
                }
            }
        }
        return nb;
    }

    void step() {
        buildGrid();

        double[] newTheta = new double[N];
        double[] newX = new double[N];
        double[] newY = new double[N];

        for (int i = 0; i < N; i++) {
            if (i == leaderIdx) continue;

            List<Integer> nb = getNeighbors(i);
            double sumSin = 0, sumCos = 0;
            for (int j : nb) {
                sumSin += Math.sin(theta[j]);
                sumCos += Math.cos(theta[j]);
            }
            double avgTheta = Math.atan2(sumSin, sumCos);
            double noise = (rng.nextDouble() - 0.5) * eta;
            newTheta[i] = avgTheta + noise;
            newX[i] = wrap(x[i] + V * Math.cos(newTheta[i]) * DT);
            newY[i] = wrap(y[i] + V * Math.sin(newTheta[i]) * DT);
        }

        //lider
        if (leaderIdx >= 0) {
            if (scenario.equals("B")) {
                newTheta[leaderIdx] = leaderFixedTheta;
                newX[leaderIdx] = wrap(x[leaderIdx] + V * Math.cos(leaderFixedTheta) * DT);
                newY[leaderIdx] = wrap(y[leaderIdx] + V * Math.sin(leaderFixedTheta) * DT);
            } else if (scenario.equals("C")) {
                circPhi += OMEGA * DT;
                newX[leaderIdx] = wrap(circCX + R_CIRCLE * Math.cos(circPhi));
                newY[leaderIdx] = wrap(circCY + R_CIRCLE * Math.sin(circPhi));
                newTheta[leaderIdx] = circPhi + Math.PI / 2.0;
            }
        }

        for (int i = 0; i < N; i++) {
            theta[i] = newTheta[i];
            x[i] = newX[i];
            y[i] = newY[i];
        }
    }

    double computeVa() {
        double sx = 0, sy = 0;
        for (int i = 0; i < N; i++) {
            sx += Math.cos(theta[i]);
            sy += Math.sin(theta[i]);
        }
        return Math.sqrt(sx * sx + sy * sy) / N;
    }

    //output
    void writeState(PrintWriter pw, int t) {
        pw.println(t);
        for (int i = 0; i < N; i++) {
            pw.printf(Locale.US, "%.6f %.6f %.6f %.6f %d%n",
                    x[i], y[i],
                    V * Math.cos(theta[i]),
                    V * Math.sin(theta[i]),
                    isLeader[i] ? 1 : 0);
        }
    }

    void run(String outputDir) throws IOException {
        String fname = String.format("%s/off_lattice_%s_eta%.2f.txt", outputDir, scenario, eta);
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(fname)))) {
            pw.println(N);
            pw.printf(Locale.US, "%.4f %.4f %s%n", L, eta, scenario);
            writeState(pw, 0);
            for (int t = 1; t <= STEPS; t++) {
                step();
                writeState(pw, t);
            }
        }
        System.out.printf("  [%s] eta=%.2f  va=%.4f  -> %s%n", scenario, eta, computeVa(), fname);
    }

    public static void main(String[] args) throws IOException {
        String outDir = "output";
        new File(outDir).mkdirs();

        double[] etas = {0.0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0};
        String[] scenarios = {"A", "B", "C"};
        long seed = 42;

        System.out.printf("=== Off-Lattice ===%n");
        System.out.printf("N=%d  L=%.1f  v=%.2f  rc=%.1f  steps=%d  rho=%.1f%n",
                N, L, V, RC, STEPS, DENSITY);
        System.out.println("=========================================");

        for (String sc : scenarios) {
            System.out.println("Escenario " + sc + ":");
            for (double eta : etas) {
                Simulation sim = new Simulation(eta, sc, seed);
                sim.run(outDir);
            }
        }
        System.out.println("\nListo. Archivos en ./" + outDir + "/");
    }
}
