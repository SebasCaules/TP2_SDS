package Animation;

import java.util.List;

public class SimulationData {
    public final int particleCount;
    public final double sideLength;
    public final double eta;
    public final String scenario;
    public final List<Frame> frames;

    public SimulationData(int particleCount, double sideLength, double eta, String scenario, List<Frame> frames) {
        this.particleCount = particleCount;
        this.sideLength = sideLength;
        this.eta = eta;
        this.scenario = scenario;
        this.frames = frames;
    }
}

