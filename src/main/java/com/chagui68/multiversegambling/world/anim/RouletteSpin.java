package com.chagui68.multiversegambling.world.anim;

/**
 * Where the rotor of the roulette and its ball are at any moment of a spin.
 *
 * <p>The wheel lies flat on the ground like a real one: a wooden bowl with the ball track
 * round its rim, a sloped lower track with the deflectors, and the rotor in the middle
 * with its number ring, its pockets and the turret. As at a real table the rotor turns
 * slowly anticlockwise seen from above while the ball is thrown hard the other way: it
 * laps the ball track several times, loses speed, runs down the lower track, bounces
 * over a few pockets and comes to rest in the winning one, which it then rides until the
 * rotor stops.</p>
 *
 * <p>Angles are measured round the vertical axis, anticlockwise seen from above, with
 * {@code 0} at the far side of the wheel ({@code -z}, away from the audience). Progress
 * goes from {@code 0} at the first frame to {@code 1} at the last. Free of Bukkit on
 * purpose: tests check that the ball always ends in the pocket the game pays for.</p>
 */
public final class RouletteSpin {

    /** Radius of the ball track, where the ball is thrown. */
    public static final double TRACK = 5.3;
    /** Radius at which the ball leaves the lower track and drops on the rotor. */
    public static final double RIM = 4.3;
    /** Radius of the middle of the pockets. */
    public static final double POCKET = 3.32;
    /** Share of the spin the ball rides the ball track. */
    public static final double ON_TRACK = 0.55;
    /** Share of the spin after which the ball has dropped on the rotor. */
    public static final double DROPPED = 0.66;
    /** Share of the spin after which the ball rests in its pocket. */
    public static final double LANDED = 0.78;
    /** Share of the spin the rotor takes to stop. */
    public static final double ROTOR_STOP = 0.95;
    /**
     * Fastest turn per frame of the ball. The client moves a display in a straight line
     * between two frames, so any faster and the ball would visibly cut across the bowl.
     */
    static final double BALL_STEP = 0.45;
    /** Average turn per frame of the rotor: slow and heavy, like a real one. */
    static final double ROTOR_SPEED = 0.055;
    /** Share of its way the ball keeps its full speed before it brakes. */
    private static final double CRUISE = 0.55;

    private final double rotorStart;
    private final double rotorArc;
    private final double ballStart;
    private final double ballArc;
    private final double pocket;

    /**
     * @param frames     frames the spin lasts
     * @param rotorStart where the rotor sets off
     * @param ballStart  where the ball is thrown
     * @param pocket     angle of the middle of the winning pocket on the rotor, the small
     *                   jitter inside the pocket included
     */
    public RouletteSpin(int frames, double rotorStart, double ballStart, double pocket) {
        int length = Math.max(1, frames);
        this.rotorStart = rotorStart;
        this.pocket = pocket;
        this.rotorArc = ROTOR_SPEED * ROTOR_STOP * length;
        // The ball cruises before it brakes: its top speed is the whole way over the
        // cruise plus half the braking.
        double ballRoom = BALL_STEP * (CRUISE + (1 - CRUISE) / 2) * LANDED * length;
        double landing = rotor(LANDED) + pocket;
        double laps = Math.max(0, Math.floor(ballRoom / WheelMath.TAU) - 1);
        this.ballStart = ballStart;
        this.ballArc = -(WheelMath.TAU * laps + WheelMath.normalize(ballStart - landing));
    }

    /** Angle of the rotor. */
    public double rotor(double progress) {
        return rotorStart + rotorArc * WheelMath.easeQuad(progress / ROTOR_STOP);
    }

    /** Angle of the ball. */
    public double ball(double progress) {
        if (progress >= LANDED) {
            // In its pocket the ball rides the rotor. Counted on from where it landed, so
            // the angle runs on without a jump of a whole turn.
            return ballStart + ballArc + rotor(progress) - rotor(LANDED);
        }
        double u = progress / LANDED;
        return ballStart + ballArc * cruise(u) + jitter(progress);
    }

    /** Distance of the ball from the axle. */
    public double ballRadius(double progress) {
        if (progress <= ON_TRACK) {
            return TRACK;
        }
        if (progress <= DROPPED) {
            return TRACK + (RIM - TRACK) * smooth((progress - ON_TRACK) / (DROPPED - ON_TRACK));
        }
        if (progress <= LANDED) {
            return RIM + (POCKET - RIM) * smooth((progress - DROPPED) / (LANDED - DROPPED));
        }
        return POCKET;
    }

    /**
     * How high the ball jumps over the surface: one knock on a deflector on the way
     * down, then a few hops over the frets, each lower than the one before.
     */
    public double hop(double progress) {
        if (progress > ON_TRACK + 0.05 && progress < DROPPED) {
            double u = (progress - ON_TRACK - 0.05) / (DROPPED - ON_TRACK - 0.05);
            return 0.3 * Math.sin(Math.PI * u);
        }
        if (progress >= DROPPED && progress < LANDED) {
            double u = (progress - DROPPED) / (LANDED - DROPPED);
            return Math.abs(Math.sin(3 * Math.PI * u)) * 0.45 * (1 - u) * (1 - u);
        }
        return 0.0;
    }

    /** True once the ball rests in its pocket. */
    public boolean landed(double progress) {
        return progress >= LANDED;
    }

    /** The ball is knocked a little sideways by every hop over a fret. */
    private static double jitter(double progress) {
        if (progress < DROPPED || progress >= LANDED) {
            return 0.0;
        }
        double u = (progress - DROPPED) / (LANDED - DROPPED);
        return 0.09 * Math.sin(3 * Math.PI * u) * (1 - u);
    }

    /** Constant speed for the first part of the way, then braking to a stop. */
    static double cruise(double u) {
        double t = Math.max(0, Math.min(1, u));
        double k = CRUISE;
        double total = k + (1 - k) / 2;
        if (t <= k) {
            return t / total;
        }
        double v = (t - k) / (1 - k);
        return (k + (1 - k) / 2 * (1 - (1 - v) * (1 - v))) / total;
    }

    private static double smooth(double u) {
        double t = Math.max(0, Math.min(1, u));
        return t * t * (3 - 2 * t);
    }
}
