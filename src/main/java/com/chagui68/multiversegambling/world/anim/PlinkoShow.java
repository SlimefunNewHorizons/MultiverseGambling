package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

/**
 * Plinko on the pavilion as a wall of displays.
 *
 * <p>The pyramid stands up in front of the audience: a dark panel in a golden frame, a
 * grid of pegs that gets wider as it comes down, a shelf of buckets coloured from the
 * rich edges to the poor middle with their multipliers written under them, and a ball
 * that really falls, hopping over every peg it hits. The peg lights up as the ball
 * bounces off it. Every piece is a display entity, so the ground of the arena is never
 * touched, the board is crisp at any distance and the fall is smooth.</p>
 *
 * <p>The bounces come from the same provably fair rolls that decide the bucket, so the
 * ball takes the real path and stops on the bucket the game pays for.</p>
 */
public final class PlinkoShow extends ArenaShow {

    /** How far in front of the wall the ball hangs, so it never clips the pegs. */
    private static final float BALL_DEPTH = 0.42f;
    /** How high the ball hops over a peg, as a share of the row height. */
    private static final double HOP = 0.55;

    private static final Material PANEL = Material.BLACK_CONCRETE;
    private static final Material PEG = Material.LIGHT_GRAY_CONCRETE;
    private static final Material LIT_PEG = Material.GLOWSTONE;
    private static final Material CROWN_PEG = Material.GOLD_BLOCK;
    private static final Material WON_BUCKET = Material.LIME_CONCRETE;

    private final PlinkoBoard board;
    private final int[] path;
    private final int bucket;
    private double[] multipliers;
    private final List<BlockDisplay> buckets = new ArrayList<>();
    private final List<List<BlockDisplay>> pegs = new ArrayList<>();
    private final List<BlockDisplay> bulbs = new ArrayList<>();
    private Location anchor;
    private ItemDisplay ball;
    private int lastLevel = -1;
    private BlockDisplay litPeg;
    private boolean landed;

    /**
     * @param rolls  uniform roll per bounce, in the order the ball meets them
     * @param bucket bucket the ball has to stop in
     * @param ticks  frames the drop lasts
     */
    public PlinkoShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                      double[] rolls, int bucket, int ticks) {
        super(plugin, stage, ticks);
        this.board = new PlinkoBoard(Math.max(1, rolls.length));
        this.path = PlinkoBoard.path(rolls);
        this.bucket = Math.max(0, Math.min(rolls.length, bucket));
    }

    /**
     * Multiplier paid by every bucket, written under the shelf.
     */
    public PlinkoShow multipliers(double[] multipliers) {
        this.multipliers = multipliers == null ? null : multipliers.clone();
        return this;
    }

    /**
     * The wall this show draws, for tests and for whoever reads the geometry next.
     */
    PlinkoBoard board() {
        return board;
    }

    /**
     * Bucket the ball ends in.
     */
    public int bucket() {
        return bucket;
    }

    @Override
    protected void onStart() {
        anchor = local(0, 0, 0);
        buildWall();
        // The ball faces the camera, so it is moved by teleporting it: an offset in its
        // transformation would be turned with the camera and fling it off the wall.
        ball = item(ballSpot(0), Material.SNOWBALL, Props.scaled(ballSize()));
        ball.setBillboard(Display.Billboard.CENTER);
        ball.setTeleportDuration(1);
        ball.setGlowColorOverride(Color.WHITE);
        ball.setGlowing(true);
        playSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.2f);
    }

    @Override
    protected void onFrame(int elapsed, int duration) {
        double level = Math.min(board.rows(), progress(elapsed) * board.rows());
        ball.teleport(ballSpot(level));
        int whole = (int) Math.floor(level);
        if (whole != lastLevel && whole < board.levels()) {
            lastLevel = whole;
            hitPeg(whole);
        }
    }

    @Override
    protected void onSettle() {
        land();
    }

    private void land() {
        if (landed) {
            return;
        }
        landed = true;
        ball.setTeleportDuration(2);
        ball.teleport(ballSpot(board.rows()));
        if (bucket < buckets.size()) {
            BlockDisplay plate = buckets.get(bucket);
            if (plate.isValid()) {
                plate.setBlock(WON_BUCKET.createBlockData());
                plate.setGlowColorOverride(Color.LIME);
                plate.setGlowing(true);
                Props.animate(plate, Props.box(bucketCentre(bucket), shelfSize(1.25f)), 4);
            }
        }
        Vector3f spot = bucketCentre(bucket);
        boolean rich = multipliers != null && bucket < multipliers.length && multipliers[bucket] >= 1.0;
        if (rich) {
            celebrate(spot.x, spot.y + 0.5, spot.z + 0.4);
        } else {
            particles(Particle.SMOKE, spot.x, spot.y + 0.3, spot.z + 0.4, 10, 0.2, 0.02);
        }
        playSound(rich ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_BASS, 0.9f, rich ? 1.3f : 0.8f);
    }

    @Override
    protected void onAnimate(int age) {
        if (age % 3 != 0) {
            return;
        }
        int phase = age / 3;
        for (int i = 0; i < bulbs.size(); i++) {
            BlockDisplay bulb = bulbs.get(i);
            if (bulb.isValid()) {
                boolean lit = landed ? phase % 2 == 0 : (i + phase) % 3 == 0;
                bulb.setBlock((lit ? Material.GLOWSTONE : Material.GOLD_BLOCK).createBlockData());
            }
        }
    }

    // ------------------------------------------------------------------ the wall

    private void buildWall() {
        double half = board.halfWidth() + 0.3;
        float height = (float) (board.height() + 0.6);
        float bottom = (float) (board.bucketY() - 0.9);
        float middle = (bottom + height) / 2;
        float tall = height - bottom;
        // A dark panel behind everything, in a golden frame, on a stone plinth.
        block(anchor, PANEL, Props.box(new Vector3f(0, middle, -0.08f), new Vector3f((float) half * 2, tall, 0.1f)));
        block(anchor, Material.POLISHED_BLACKSTONE_BRICKS,
                Props.box(new Vector3f(0, bottom / 2, 0), new Vector3f((float) half * 2 + 0.6f, bottom, 1.2f)));
        for (int side : new int[]{-1, 1}) {
            block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f((float) (side * (half + 0.1)), middle, -0.05f),
                    new Vector3f(0.2f, tall, 0.25f)));
        }
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, height + 0.05f, -0.05f),
                new Vector3f((float) half * 2 + 0.4f, 0.2f, 0.25f)));
        int lights = (int) Math.round(half * 2 / 0.6);
        for (int i = 0; i <= lights; i++) {
            float x = (float) (-half + i * (half * 2 / lights));
            bulbs.add(block(anchor, Material.GLOWSTONE,
                    Props.box(new Vector3f(x, height + 0.3f, 0.0f), new Vector3f(0.2f, 0.2f, 0.12f))));
        }
        text(local(0, height + 0.55, 0), Text.c("&6&lPLINKO"), Props.scaled(2.2f), Display.Billboard.FIXED);

        float pegSize = (float) Math.min(0.32, board.column() * 0.55);
        for (int level = 0; level < board.levels(); level++) {
            List<BlockDisplay> row = new ArrayList<>();
            for (int index = 0; index < board.pegs(level); index++) {
                boolean apex = level == 0;
                float size = apex ? pegSize * 1.2f : pegSize;
                row.add(block(anchor, apex ? CROWN_PEG : PEG, Props.box(
                        new Vector3f((float) board.pegX(level, index), (float) board.levelY(level), 0.05f),
                        new Vector3f(size, size, size))));
            }
            pegs.add(row);
        }
        for (int slot = 0; slot <= board.rows(); slot++) {
            buckets.add(block(anchor, bucketColour(slot), Props.box(bucketCentre(slot), shelfSize(1.0f))));
            if (multipliers != null && slot < multipliers.length) {
                Vector3f under = bucketCentre(slot);
                float scale = (float) Math.max(0.35, Math.min(0.8, board.column() * 1.15));
                text(local(under.x, under.y - 0.55, 0.2), Text.c(labelColour(multipliers[slot])
                        + Text.number(multipliers[slot]) + "x"), Props.scaled(scale), Display.Billboard.FIXED);
            }
        }
    }

    /**
     * The ball hits the peg of that level: it lights up for a moment.
     */
    private void hitPeg(int level) {
        if (level < 0 || level >= pegs.size()) {
            return;
        }
        int column = path[level];
        int index = (column + level) / 2;
        List<BlockDisplay> row = pegs.get(level);
        if (index < 0 || index >= row.size()) {
            return;
        }
        if (litPeg != null && litPeg.isValid()) {
            litPeg.setBlock((pegs.get(0).contains(litPeg) ? CROWN_PEG : PEG).createBlockData());
        }
        litPeg = row.get(index);
        litPeg.setBlock(LIT_PEG.createBlockData());
        playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.45f, (float) (0.8 + level / (double) Math.max(1, board.rows())));
    }

    // ------------------------------------------------------------------ the ball

    /** Where the ball is after {@code level} bounces. */
    private Location ballSpot(double level) {
        double fraction = level - Math.floor(level);
        // Between two rows the ball hops up off the peg before it drops again.
        double hop = level >= board.rows() ? 0 : Math.sin(fraction * Math.PI) * HOP * board.row();
        return local(board.ballX(path, level), board.ballY(level) + hop + 0.12, BALL_DEPTH);
    }

    private float ballSize() {
        return (float) Math.max(0.35, Math.min(0.55, board.column() * 0.9));
    }

    private Vector3f bucketCentre(int slot) {
        return new Vector3f((float) board.bucketX(slot), (float) board.bucketY(), 0.15f);
    }

    private Vector3f shelfSize(float grow) {
        return new Vector3f((float) board.column() * 1.85f, 0.3f * grow, 0.55f);
    }

    /**
     * Bucket colours from the rich edges to the poor middle.
     */
    private Material bucketColour(int slot) {
        double distance = Math.abs(slot - board.rows() / 2.0) / Math.max(1.0, board.rows() / 2.0);
        if (distance > 0.85) {
            return Material.GOLD_BLOCK;
        }
        if (distance > 0.6) {
            return Material.ORANGE_CONCRETE;
        }
        if (distance > 0.3) {
            return Material.YELLOW_CONCRETE;
        }
        return Material.GRAY_CONCRETE;
    }

    private static String labelColour(double multiplier) {
        if (multiplier >= 5) {
            return "&6&l";
        }
        return multiplier >= 1.0 ? "&e" : "&7";
    }
}
