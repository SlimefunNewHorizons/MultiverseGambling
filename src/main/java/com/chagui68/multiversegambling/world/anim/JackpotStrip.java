package com.chagui68.multiversegambling.world.anim;

/**
 * The strip of the jackpot: one cell per player, as wide as their share of the pot,
 * repeated end to end and scrolled under a fixed marker until it stops on the winner.
 *
 * <p>Cells live on a loop of length {@link #length()}: a cell that leaves the window on
 * the left comes back on the right, so the strip can scroll as long as it likes with a
 * fixed number of pieces. The loop is always longer than the window plus a margin, so the
 * jump back is never seen. Offsets are in blocks along the strip, {@code 0} under the
 * marker. Free of Bukkit on purpose: tests check that the strip stops on the winner.</p>
 */
public final class JackpotStrip {

    /** Width of the window the strip is seen through. */
    public static final float WINDOW = 12.0f;
    /** Narrowest a cell is drawn, so even the smallest stake has room for a head. */
    public static final float MIN_CELL = 0.9f;
    /** Room left on both sides of the window so a cell never wraps in sight. */
    private static final float MARGIN = 1.8f;

    private final int players;
    private final float[] widths;
    private final float[] centres;
    private final float cycle;
    private final float length;
    private final double travel;

    /**
     * @param stakes  what each player put in, in the order of their cells
     * @param winner  index of the player the strip stops on
     * @param inside  where the marker ends inside the winning cell, from -1 (left edge)
     *                to 1 (right edge); kept away from the edges
     * @param laps    whole loops the strip runs before it stops
     */
    public JackpotStrip(double[] stakes, int winner, double inside, int laps) {
        players = Math.max(1, stakes.length);
        double total = 0;
        for (double stake : stakes) {
            total += Math.max(0, stake);
        }
        float[] cell = new float[players];
        float sum = 0;
        for (int i = 0; i < players; i++) {
            double share = total <= 0 || i >= stakes.length ? 1.0 / players : Math.max(0, stakes[i]) / total;
            cell[i] = Math.max(MIN_CELL, (float) (WINDOW * share));
            sum += cell[i];
        }
        cycle = sum;
        int cycles = Math.max(1, (int) Math.ceil((WINDOW + 2 * MARGIN) / cycle));
        length = cycles * cycle;
        widths = new float[players * cycles];
        centres = new float[players * cycles];
        float left = 0;
        for (int c = 0; c < widths.length; c++) {
            widths[c] = cell[c % players];
            centres[c] = left + widths[c] / 2;
            left += widths[c];
        }
        int target = Math.floorMod(winner, players);
        double offset = Math.max(-0.7, Math.min(0.7, inside)) * widths[target] / 2;
        // The winning cell must end with its middle at -offset, so the marker (0) sits
        // offset away from that middle, inside the cell.
        travel = laps * (double) length + floorMod(centres[target] + offset - length / 2.0, length);
    }

    /** Number of cells drawn: every player once per loop. */
    public int cells() {
        return widths.length;
    }

    /** Player a cell belongs to. */
    public int ownerOf(int cell) {
        return cell % players;
    }

    public float widthOf(int cell) {
        return widths[cell];
    }

    /** Length of the whole loop. */
    public float length() {
        return length;
    }

    /** How far the strip has scrolled at that progress: fast at first, creeping at the end. */
    public double scroll(double progress) {
        return travel * WheelMath.ease(progress);
    }

    /** Offset of the middle of a cell from the marker, once the strip scrolled that far. */
    public double x(int cell, double scroll) {
        return floorMod(centres[cell] - scroll, length) - length / 2.0;
    }

    /** Player whose cell is under the marker, or -1 between cells. */
    public int underMarker(double scroll) {
        for (int cell = 0; cell < widths.length; cell++) {
            if (Math.abs(x(cell, scroll)) <= widths[cell] / 2) {
                return ownerOf(cell);
            }
        }
        return -1;
    }

    private static double floorMod(double value, double modulus) {
        double wrapped = value % modulus;
        return wrapped < 0 ? wrapped + modulus : wrapped;
    }
}
