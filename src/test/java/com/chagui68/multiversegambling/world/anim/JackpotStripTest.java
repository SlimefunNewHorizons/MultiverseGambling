package com.chagui68.multiversegambling.world.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class JackpotStripTest {

    @Test
    void theStripAlwaysStopsOnTheWinner() {
        Random random = new Random(3);
        for (int run = 0; run < 500; run++) {
            int players = 1 + random.nextInt(24);
            double[] stakes = new double[players];
            for (int i = 0; i < players; i++) {
                stakes[i] = 1 + random.nextInt(1000);
            }
            int winner = random.nextInt(players);
            JackpotStrip strip = new JackpotStrip(stakes, winner, random.nextDouble() * 2 - 1, 2);
            assertEquals(winner, strip.underMarker(strip.scroll(1.0)), "run " + run);
        }
    }

    @Test
    void theLoopIsAlwaysLongerThanTheWindowSoNoCellWrapsInSight() {
        for (int players = 1; players <= 24; players++) {
            double[] stakes = new double[players];
            java.util.Arrays.fill(stakes, 10);
            JackpotStrip strip = new JackpotStrip(stakes, 0, 0, 2);
            assertTrue(strip.length() >= JackpotStrip.WINDOW + 3, players + " players");
        }
    }

    @Test
    void aBiggerStakeGetsAWiderCell() {
        JackpotStrip strip = new JackpotStrip(new double[]{900, 100}, 0, 0, 2);
        assertTrue(strip.widthOf(0) > strip.widthOf(1));
        assertTrue(strip.widthOf(1) >= JackpotStrip.MIN_CELL);
    }
}
