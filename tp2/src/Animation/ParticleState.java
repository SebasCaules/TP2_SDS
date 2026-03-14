package Animation;

public class ParticleState {
    public final double x;
    public final double y;
    public final double vx;
    public final double vy;
    public final boolean leader;

    public ParticleState(double x, double y, double vx, double vy, boolean leader) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.leader = leader;
    }
}

