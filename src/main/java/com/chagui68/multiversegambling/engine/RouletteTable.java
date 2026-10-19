package com.chagui68.multiversegambling.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * European roulette (a single 0) or American (0 and 00). It keeps the wheel as a list
 * of pockets so the American 00 breaks no arithmetic.
 */
public final class RouletteTable {

    public enum Color {
        GREEN, RED, BLACK
    }

    /**
     * Bet types and what each one pays when it wins.
     *
     * <p>COLOR only covers red (selection 0) and black (selection 1). Green is not a
     * colour bet: in real roulette the 0 pays on the number itself (NUMBER), which is
     * why this game never offers "bet green at 2x": that would be a scam.</p>
     */
    public enum Bet {
        NUMBER(36.0),
        COLOR(2.0),
        PARITY(2.0),
        HALF(2.0),
        DOZEN(3.0),
        COLUMN(3.0);

        private final double payout;

        Bet(double payout) {
            this.payout = payout;
        }

        /**
         * Multiplier over the stake, already including the return of the capital.
         */
        public double payout() {
            return payout;
        }
    }

    /**
     * Represents "00" on the American wheel.
     */
    public static final int DOUBLE_ZERO = 37;

    private static final Set<Integer> RED = Set.of(
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);

    private final List<Integer> pockets;

    private RouletteTable(List<Integer> pockets) {
        this.pockets = List.copyOf(pockets);
    }

    public static RouletteTable european() {
        List<Integer> pockets = new ArrayList<>(37);
        for (int i = 0; i <= 36; i++) {
            pockets.add(i);
        }
        return new RouletteTable(pockets);
    }

    public static RouletteTable american() {
        List<Integer> pockets = new ArrayList<>(38);
        for (int i = 0; i <= 36; i++) {
            pockets.add(i);
        }
        pockets.add(DOUBLE_ZERO);
        return new RouletteTable(pockets);
    }

    public List<Integer> pockets() {
        return pockets;
    }

    /**
     * The pockets in the order they sit round a real wheel, clockwise from the zero.
     * Only for drawing the wheel: draws and payouts use {@link #pockets()}.
     */
    public List<Integer> wheelOrder() {
        return pockets.size() > 37 ? AMERICAN_WHEEL : EUROPEAN_WHEEL;
    }

    private static final List<Integer> EUROPEAN_WHEEL = List.of(
            0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10,
            5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26);

    private static final List<Integer> AMERICAN_WHEEL = List.of(
            0, 28, 9, 26, 30, 11, 7, 20, 32, 17, 5, 22, 34, 15, 3, 24, 36, 13, 1,
            DOUBLE_ZERO, 27, 10, 25, 29, 12, 8, 19, 31, 18, 6, 21, 33, 16, 4, 23, 35, 14, 2);

    public int pocketCount() {
        return pockets.size();
    }

    /**
     * Number of pockets that make each bet type win.
     */
    private static int winningPockets(Bet bet) {
        return switch (bet) {
            case NUMBER -> 1;
            case COLOR, PARITY, HALF -> 18;
            case DOZEN, COLUMN -> 12;
        };
    }

    /**
     * Theoretical return of a bet. The beauty of roulette is that <em>every</em> bet
     * shares the same return: 36/37 on the European wheel (2.70% edge) and 36/38 on the
     * American one (5.26%). The test checks it bet by bet.
     */
    public double rtp(Bet bet) {
        return bet.payout() * winningPockets(bet) / pocketCount();
    }

    public double houseEdge(Bet bet) {
        return 1.0 - rtp(bet);
    }

    public int spin() {
        return Rng.pick(pockets);
    }

    public static boolean isGreen(int pocket) {
        return pocket == 0 || pocket == DOUBLE_ZERO;
    }

    public static boolean isRed(int pocket) {
        return RED.contains(pocket);
    }

    public static Color colorOf(int pocket) {
        if (isGreen(pocket)) {
            return Color.GREEN;
        }
        return isRed(pocket) ? Color.RED : Color.BLACK;
    }

    public static String label(int pocket) {
        return pocket == DOUBLE_ZERO ? "00" : String.valueOf(pocket);
    }

    /**
     * Decides whether the bet wins. {@code selection} means:
     * <ul>
     *   <li>NUMBER: the number itself (the 0 pays 36 and covers green).</li>
     *   <li>COLOR: 0 = red, 1 = black.</li>
     *   <li>PARITY: 0 = even, 1 = odd.</li>
     *   <li>HALF: 0 = 1-18 (low), 1 = 19-36 (high).</li>
     *   <li>DOZEN: 0 = 1-12, 1 = 13-24, 2 = 25-36.</li>
     *   <li>COLUMN: 0, 1 or 2.</li>
     * </ul>
     *
     * @return the multiplier to pay, or 0 when the bet loses.
     */
    public static double payoutOf(Bet bet, int selection, int result) {
        boolean wins = switch (bet) {
            case NUMBER -> selection == result;
            case COLOR -> !isGreen(result) && (selection == 0 ? isRed(result) : !isRed(result));
            case PARITY -> !isGreen(result) && (result % 2 == 0) == (selection == 0);
            case HALF -> !isGreen(result) && (result <= 18) == (selection == 0);
            case DOZEN -> !isGreen(result) && (result - 1) / 12 == selection;
            case COLUMN -> !isGreen(result) && result % 3 == (selection + 1) % 3;
        };
        return wins ? bet.payout() : 0.0;
    }
}
