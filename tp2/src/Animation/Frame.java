package Animation;

public class Frame {
    public final int time;
    public final ParticleState[] particles;

    public Frame(int time, ParticleState[] particles) {
        this.time = time;
        this.particles = particles;
    }
}

