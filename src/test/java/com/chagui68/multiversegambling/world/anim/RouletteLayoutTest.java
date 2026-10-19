package com.chagui68.multiversegambling.world.anim;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chagui68.multiversegambling.engine.RouletteTable;
import com.chagui68.multiversegambling.engine.RouletteTable.Bet;
import com.chagui68.multiversegambling.world.CasinoLayout;

import java.util.List;

import org.junit.jupiter.api.Test;

class RouletteLayoutTest {

    @Test
    void everyBetOfTheClassicRouletteHasAField() {
        for (boolean doubleZero : new boolean[]{false, true}) {
            List<RouletteLayout.Field> cloth = RouletteLayout.numbers(doubleZero);
            for (int pocket : (doubleZero ? RouletteTable.american() : RouletteTable.european()).pockets()) {
                assertNotNull(RouletteLayout.fieldOf(cloth, Bet.NUMBER, pocket), "number " + pocket);
            }
            for (Bet bet : new Bet[]{Bet.COLOR, Bet.PARITY, Bet.HALF}) {
                for (int selection = 0; selection < 2; selection++) {
                    assertNotNull(RouletteLayout.fieldOf(cloth, bet, selection), bet + " " + selection);
                }
            }
            for (int selection = 0; selection < 3; selection++) {
                assertNotNull(RouletteLayout.fieldOf(cloth, Bet.DOZEN, selection));
                assertNotNull(RouletteLayout.fieldOf(cloth, Bet.COLUMN, selection));
            }
        }
    }

    @Test
    void eachColumnFieldSitsAtTheEndOfTheRowItPays() {
        List<RouletteLayout.Field> cloth = RouletteLayout.numbers(false);
        for (int selection = 0; selection < 3; selection++) {
            RouletteLayout.Field column = RouletteLayout.fieldOf(cloth, Bet.COLUMN, selection);
            for (int number = 1; number <= 36; number++) {
                RouletteLayout.Field cell = RouletteLayout.fieldOf(cloth, Bet.NUMBER, number);
                boolean sameRow = Math.abs(cell.z() - column.z()) < 1e-4;
                boolean pays = RouletteTable.payoutOf(Bet.COLUMN, selection, number) > 0;
                assertTrue(sameRow == pays, "column " + selection + " and number " + number);
            }
        }
    }

    @Test
    void noTwoFieldsOverlapAndTheClothStaysOnTheStage() {
        for (List<RouletteLayout.Field> cloth : List.of(RouletteLayout.numbers(false), RouletteLayout.numbers(true),
                RouletteLayout.colours())) {
            for (int i = 0; i < cloth.size(); i++) {
                for (int j = i + 1; j < cloth.size(); j++) {
                    assertFalse(overlap(cloth.get(i), cloth.get(j)), cloth.get(i) + " / " + cloth.get(j));
                }
            }
            float corner = (float) Math.hypot(RouletteLayout.maxX(cloth), RouletteLayout.maxZ(cloth));
            assertTrue(corner < CasinoLayout.STAGE_RADIUS, "the cloth leaves the stage: " + corner);
            assertTrue(RouletteLayout.minX(cloth) > 0.3f, "the cloth runs into the wheel");
        }
    }

    private static boolean overlap(RouletteLayout.Field a, RouletteLayout.Field b) {
        float epsilon = 1e-3f;
        return Math.abs(a.x() - b.x()) * 2 < a.width() + b.width() - epsilon
                && Math.abs(a.z() - b.z()) * 2 < a.depth() + b.depth() - epsilon;
    }
}
