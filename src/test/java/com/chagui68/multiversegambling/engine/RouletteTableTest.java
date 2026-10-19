package com.chagui68.multiversegambling.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.chagui68.multiversegambling.engine.RouletteTable.Bet;
import com.chagui68.multiversegambling.engine.RouletteTable.Color;

class RouletteTableTest {

    private final RouletteTable european = RouletteTable.european();
    private final RouletteTable american = RouletteTable.american();

    @Test
    void theEuropeanWheelHasTheRightPockets() {
        assertEquals(37, european.pocketCount());
        assertEquals(38, american.pocketCount());
        assertTrue(american.pockets().contains(RouletteTable.DOUBLE_ZERO));
        assertFalse(european.pockets().contains(RouletteTable.DOUBLE_ZERO));
    }

    @Test
    void theRealWheelOrderHoldsEveryPocketOnce() {
        for (RouletteTable table : new RouletteTable[]{european, american}) {
            assertEquals(new java.util.HashSet<>(table.pockets()), new java.util.HashSet<>(table.wheelOrder()));
            assertEquals(table.pocketCount(), table.wheelOrder().size());
            assertEquals(0, table.wheelOrder().get(0));
        }
    }

    @Test
    void theColoursCoverTheWholeWheelWithoutOverlapping() {
        int reds = 0;
        int blacks = 0;
        int greens = 0;
        for (int pocket : european.pockets()) {
            switch (RouletteTable.colorOf(pocket)) {
                case RED -> reds++;
                case BLACK -> blacks++;
                case GREEN -> greens++;
            }
        }
        assertEquals(18, reds);
        assertEquals(18, blacks);
        assertEquals(1, greens);
        assertEquals(37, reds + blacks + greens);
    }

    @Test
    void theHouseEdgeIsTheExpectedOne() {
        // 2.70% on the European wheel and 5.26% on the American one: the classic numbers.
        assertEquals(1.0 - 36.0 / 37.0, european.houseEdge(Bet.COLOR), 1e-9);
        assertEquals(1.0 - 36.0 / 38.0, american.houseEdge(Bet.COLOR), 1e-9);
        assertEquals(0.027027027, european.houseEdge(Bet.NUMBER), 1e-9);
    }

    @Test
    void everyRouletteBetHasTheSameReturn() {
        // This is the property that defines roulette: the edge does not depend on the
        // bet. If somebody "improves" a payout, this test spots it right away.
        for (Bet bet : Bet.values()) {
            assertEquals(36.0 / 37.0, european.rtp(bet), 1e-9, "European, bet " + bet);
            assertEquals(36.0 / 38.0, american.rtp(bet), 1e-9, "American, bet " + bet);
        }
    }

    @Test
    void theComputedReturnMatchesTheRealRollOverTheWholeWheel() {
        for (Bet bet : Bet.values()) {
            int selections = switch (bet) {
                case NUMBER -> 37;
                case COLOR -> 2;
                case PARITY, HALF -> 2;
                case DOZEN, COLUMN -> 3;
            };
            for (int selection = 0; selection < selections; selection++) {
                double returned = 0;
                for (int result : european.pockets()) {
                    returned += RouletteTable.payoutOf(bet, selection, result);
                }
                double measured = returned / european.pocketCount();
                assertEquals(european.rtp(bet), measured, 1e-9,
                        "The bet " + bet + " with selection " + selection + " does not add up");
            }
        }
    }

    @Test
    void greenOnlyPaysOnTheStraightBetOnZero() {
        assertEquals(36.0, RouletteTable.payoutOf(Bet.NUMBER, 0, 0));
        assertEquals(Color.GREEN, RouletteTable.colorOf(0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.NUMBER, 0, 1));
    }

    @Test
    void zeroNeverPaysOutsideBets() {
        assertEquals(0.0, RouletteTable.payoutOf(Bet.COLOR, 1, 0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.PARITY, 0, 0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.HALF, 0, 0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.DOZEN, 0, 0));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.COLUMN, 0, 0));
        // On the American wheel the 00 does not pay either.
        assertEquals(0.0, RouletteTable.payoutOf(Bet.COLOR, 1, RouletteTable.DOUBLE_ZERO));
    }

    @Test
    void columnsAndDozensHitTheRightNumbers() {
        // Column 1: 1, 4, 7 ... 34
        assertEquals(3.0, RouletteTable.payoutOf(Bet.COLUMN, 0, 1));
        assertEquals(3.0, RouletteTable.payoutOf(Bet.COLUMN, 0, 34));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.COLUMN, 0, 2));
        // Column 3: 3, 6 ... 36
        assertEquals(3.0, RouletteTable.payoutOf(Bet.COLUMN, 2, 36));
        // Dozens
        assertEquals(3.0, RouletteTable.payoutOf(Bet.DOZEN, 0, 12));
        assertEquals(0.0, RouletteTable.payoutOf(Bet.DOZEN, 0, 13));
        assertEquals(3.0, RouletteTable.payoutOf(Bet.DOZEN, 2, 36));
    }

    @Test
    void theRollResultIsAlwaysAValidPocket() {
        for (int i = 0; i < 5_000; i++) {
            int result = european.spin();
            assertTrue(result >= 0 && result <= 36);
        }
        for (int i = 0; i < 5_000; i++) {
            int result = american.spin();
            assertTrue(result >= 0 && result <= RouletteTable.DOUBLE_ZERO);
        }
    }
}
