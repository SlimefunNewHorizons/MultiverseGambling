package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.engine.RouletteTable;
import com.chagui68.multiversegambling.engine.RouletteTable.Bet;

import java.util.ArrayList;
import java.util.List;

/**
 * Geometry of the betting cloth of the roulette table: every field a chip can be put on.
 *
 * <p>The cloth lies on the ground to the right of the wheel, as at a real table: the zero
 * next to the wheel, the numbers in three rows of twelve, the "2 to 1" columns at the far
 * end, then the dozens and the even-money bets along the side where the players stand.
 * The colour roulette only has three big fields: green, red and black.</p>
 *
 * <p>Offsets are in blocks in the local frame of the stage, {@code x} to the right of the
 * audience and {@code z} towards it; every field is a rectangle given by its middle and
 * its size. Free of Bukkit on purpose, so the cloth is checked by tests.</p>
 */
public final class RouletteLayout {

    /** One place on the cloth and the bet it stands for. */
    public record Field(Bet bet, int selection, float x, float z, float width, float depth) {
    }

    /** Width of a number cell. */
    static final float CELL = 0.8f;
    /** Depth of a row of numbers. */
    static final float ROW = 1.0f;
    /** Depth of the dozens and of the even-money row. */
    static final float BAND = 0.7f;
    /** Left edge of the cloth, a little to the right of the wheel. */
    static final float LEFT = 0.9f;
    /** Width of the zero (or zeros) at the head of the cloth. */
    static final float ZERO = 0.9f;
    /** Far edge of the rows of numbers. */
    static final float TOP = -2.2f;

    private RouletteLayout() {
    }

    /** The full cloth of the classic roulette, with the double zero when there is one. */
    public static List<Field> numbers(boolean doubleZero) {
        List<Field> fields = new ArrayList<>();
        float numbersLeft = LEFT + ZERO;
        float height = 3 * ROW;
        if (doubleZero) {
            fields.add(new Field(Bet.NUMBER, RouletteTable.DOUBLE_ZERO, LEFT + ZERO / 2, TOP + height / 4,
                    ZERO, height / 2));
            fields.add(new Field(Bet.NUMBER, 0, LEFT + ZERO / 2, TOP + height * 3 / 4, ZERO, height / 2));
        } else {
            fields.add(new Field(Bet.NUMBER, 0, LEFT + ZERO / 2, TOP + height / 2, ZERO, height));
        }
        for (int number = 1; number <= 36; number++) {
            int column = (number - 1) / 3;
            // 3, 6, 9... sit on the far row; 1, 4, 7... on the row next to the players.
            int row = 2 - (number - 1) % 3;
            fields.add(new Field(Bet.NUMBER, number, numbersLeft + column * CELL + CELL / 2,
                    TOP + row * ROW + ROW / 2, CELL, ROW));
        }
        for (int row = 0; row < 3; row++) {
            fields.add(new Field(Bet.COLUMN, 2 - row, numbersLeft + 12 * CELL + CELL / 2, TOP + row * ROW + ROW / 2,
                    CELL, ROW));
        }
        float dozensZ = TOP + height + BAND / 2;
        for (int dozen = 0; dozen < 3; dozen++) {
            fields.add(new Field(Bet.DOZEN, dozen, numbersLeft + (4 * dozen + 2) * CELL, dozensZ, 4 * CELL, BAND));
        }
        Bet[] outside = {Bet.HALF, Bet.PARITY, Bet.COLOR, Bet.COLOR, Bet.PARITY, Bet.HALF};
        int[] selection = {0, 0, 0, 1, 1, 1};
        float outsideZ = dozensZ + BAND;
        for (int i = 0; i < outside.length; i++) {
            fields.add(new Field(outside[i], selection[i], numbersLeft + (2 * i + 1) * CELL, outsideZ, 2 * CELL, BAND));
        }
        return fields;
    }

    /** The three fields of the colour roulette: green (the zero), red and black. */
    public static List<Field> colours() {
        return List.of(
                new Field(Bet.NUMBER, 0, 2.1f, 0.0f, 2.2f, 3.2f),
                new Field(Bet.COLOR, 0, 5.35f, 0.0f, 3.9f, 3.2f),
                new Field(Bet.COLOR, 1, 9.45f, 0.0f, 3.9f, 3.2f));
    }

    /** The field a bet is put on, or {@code null} when the cloth has none for it. */
    public static Field fieldOf(List<Field> fields, Bet bet, int selection) {
        for (Field field : fields) {
            if (field.bet() == bet && field.selection() == selection) {
                return field;
            }
        }
        return null;
    }

    /** Smallest {@code x} any field reaches. */
    static float minX(List<Field> fields) {
        float min = Float.MAX_VALUE;
        for (Field field : fields) {
            min = Math.min(min, field.x() - field.width() / 2);
        }
        return min;
    }

    static float maxX(List<Field> fields) {
        float max = -Float.MAX_VALUE;
        for (Field field : fields) {
            max = Math.max(max, field.x() + field.width() / 2);
        }
        return max;
    }

    static float minZ(List<Field> fields) {
        float min = Float.MAX_VALUE;
        for (Field field : fields) {
            min = Math.min(min, field.z() - field.depth() / 2);
        }
        return min;
    }

    static float maxZ(List<Field> fields) {
        float max = -Float.MAX_VALUE;
        for (Field field : fields) {
            max = Math.max(max, field.z() + field.depth() / 2);
        }
        return max;
    }
}
