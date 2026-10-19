package com.chagui68.multiversegambling.world.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class RouletteSpinTest {

    private static final double EPSILON = 1.0e-6;

    @Test
    void theBallDropsIntoThePocketItIsCarriedIn() {
        Random random = new Random(7);
        for (int run = 0; run < 200; run++) {
            int frames = 60 + random.nextInt(120);
            double pocket = random.nextDouble() * WheelMath.TAU;
            RouletteSpin spin = new RouletteSpin(frames, random.nextDouble() * 10, random.nextDouble() * 10, pocket);
            assertEquals(0.0, turn(spin.ball(1), spin.rotor(1) + pocket), 1.0e-5, "the ball misses its pocket");
            double before = spin.ball(RouletteSpin.LANDED - 1.0e-9);
            double after = spin.ball(RouletteSpin.LANDED);
            assertEquals(0.0, turn(before, after), 1.0e-5, "the ball jumps when it lands, run " + run);
        }
    }

    @Test
    void neitherTheRotorNorTheBallTurnTooFarInOneFrame() {
        for (int frames : new int[]{60, 100, 140, 300}) {
            RouletteSpin spin = new RouletteSpin(frames, 1.0, 2.0, 0.5);
            for (int frame = 1; frame <= frames; frame++) {
                double now = frame / (double) frames;
                double then = (frame - 1) / (double) frames;
                assertTrue(turn(spin.rotor(now), spin.rotor(then)) <= RouletteSpin.BALL_STEP + EPSILON,
                        "rotor step at frame " + frame + " of " + frames);
                assertTrue(turn(spin.ball(now), spin.ball(then)) <= RouletteSpin.BALL_STEP + EPSILON,
                        "ball step at frame " + frame + " of " + frames);
            }
        }
    }

    @Test
    void theBallLapsTheTrackSeveralTimesAgainstTheRotor() {
        RouletteSpin spin = new RouletteSpin(100, 0, 0, 0);
        double ballTravel = spin.ball(0) - spin.ball(RouletteSpin.LANDED);
        double rotorTravel = spin.rotor(RouletteSpin.LANDED) - spin.rotor(0);
        assertTrue(ballTravel > 2 * WheelMath.TAU, "the ball laps the track at least twice");
        assertTrue(rotorTravel > 0, "the rotor turns the other way");
        assertTrue(ballTravel > 3 * rotorTravel, "the ball is much faster than the rotor");
    }

    @Test
    void theBallStaysInsideTheBowlAndEndsInThePockets() {
        RouletteSpin spin = new RouletteSpin(100, 0, 0, 0);
        for (int frame = 0; frame <= 100; frame++) {
            double radius = spin.ballRadius(frame / 100.0);
            assertTrue(radius >= RouletteSpin.POCKET - EPSILON && radius <= RouletteSpin.TRACK + EPSILON);
            assertTrue(spin.hop(frame / 100.0) >= 0);
        }
        assertEquals(RouletteSpin.TRACK, spin.ballRadius(0), EPSILON);
        assertEquals(RouletteSpin.POCKET, spin.ballRadius(1), EPSILON);
        assertTrue(spin.landed(1));
    }

    /** Shortest turn between two angles, whatever whole turns lie between them. */
    private static double turn(double a, double b) {
        double d = WheelMath.normalize(a - b);
        return Math.min(d, WheelMath.TAU - d);
    }
}
