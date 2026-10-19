package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
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
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The raffle as a lottery drum and a podium.
 *
 * <p>A wire drum on two wooden trestles turns with a ball inside for every ticket sold, in
 * the colour of its owner, tumbling at the bottom of the cage. One by one the prizes are
 * drawn, the smallest first: a ball drops out of the drum, rolls down a gold chute, jumps
 * onto its step of the podium (copper for the third prize, iron for the second, gold for
 * the first) and opens into the head of the player who owns it. The order of the winners
 * is drawn by the game before the drum turns; the show only paints it.</p>
 */
public final class RaffleShow extends ArenaShow {

    /** One player in the draw. */
    public record Entry(String name, ItemStack head, Material colour, int tickets) {
    }

    /** Most balls drawn in the drum, whatever the tickets sold. */
    private static final int MAX_BALLS = 30;
    private static final float DRUM_X = -2.7f;
    private static final float DRUM_Y = 3.0f;
    private static final float RADIUS = 1.7f;
    private static final float HALF = 1.4f;
    /** The drum is turned a little towards the audience, so it is seen three quarters on. */
    private static final double TURN = 0.45;
    private static final int BARS = 14;
    private static final double TOP_SPEED = 0.26;
    /** When each draw happens, as a share of the show; fewer prizes use the last ones. */
    private static final double[] DRAWS = {0.30, 0.52, 0.74};
    /** Steps of the podium, first prize first: x, height and material. */
    private static final float[] STEP_X = {3.6f, 2.25f, 4.95f};
    private static final float[] STEP_TOP = {2.0f, 1.4f, 1.0f};
    private static final Material[] STEP = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};

    /** A ball tumbling in the drum. */
    private static final class Ball {
        private final int owner;
        private final ItemDisplay display;
        private final float along;
        private final double phase;
        private final double toss;
        private final double spin;

        private Ball(int owner, ItemDisplay display) {
            this.owner = owner;
            this.display = display;
            this.along = (float) ((Rng.next() * 2 - 1) * (HALF - 0.3));
            this.phase = Rng.next() * WheelMath.TAU;
            this.toss = Rng.next() * WheelMath.TAU;
            this.spin = 0.5 + Rng.next();
        }
    }

    private final List<Entry> entries;
    private final List<Integer> winners;
    private final List<String> shares;
    private String title = "";

    private Location anchor;
    private final Quaternionf frame = Props.yaw(TURN);
    private final Vector3f centre = new Vector3f(DRUM_X, DRUM_Y, 0);
    private final List<BlockDisplay> bars = new ArrayList<>();
    private final List<BlockDisplay> spokes = new ArrayList<>();
    private BlockDisplay crankArm;
    private BlockDisplay crankKnob;
    private final List<Ball> balls = new ArrayList<>();
    private final boolean[] revealed = new boolean[3];
    private double angle;
    private double speed;

    /**
     * @param entries the players in the draw
     * @param winners index of the winner of each prize, first prize first
     * @param shares  words on the podium under each winner, first prize first, such as "70%"
     * @param ticks   frames the draw lasts
     */
    public RaffleShow(MultiverseGamblingPlugin plugin, ArenaStage stage, List<Entry> entries, List<Integer> winners,
                      List<String> shares, int ticks) {
        super(plugin, stage, ticks);
        this.entries = List.copyOf(entries);
        this.winners = List.copyOf(winners.subList(0, Math.min(3, winners.size())));
        this.shares = List.copyOf(shares);
    }

    /** Words over the drum, such as the name of the game and the tickets sold. */
    public RaffleShow title(String text) {
        this.title = text == null ? "" : text;
        return this;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void onStart() {
        if (entries.isEmpty()) {
            return;
        }
        anchor = local(0, 0, 0);
        buildDrum();
        buildPodium();
        fillDrum();
        placeDrum(0);
        if (!title.isBlank()) {
            TextDisplay sign = text(local(DRUM_X, DRUM_Y + RADIUS + 0.9, 0), Text.c(title), Props.scaled(1.2f),
                    Display.Billboard.VERTICAL);
            sign.setBackgroundColor(Props.argb(150, 0, 0, 0));
        }
        playSound(Sound.BLOCK_CHAIN_PLACE, 0.8f, 0.7f);
    }

    private void buildDrum() {
        // The platform, the axle and two trestles.
        block(anchor, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(new Vector3f(DRUM_X, 0.1f, 0), frame,
                new Vector3f(2 * HALF + 1.6f, 0.2f, 3.6f)));
        block(anchor, Material.IRON_BLOCK, Props.box(new Vector3f(centre), frame, new Vector3f(2 * HALF + 1.0f, 0.14f, 0.14f)));
        for (int end : new int[]{-1, 1}) {
            float along = end * (HALF + 0.3f);
            for (int side : new int[]{-1, 1}) {
                double lean = Math.atan2(side * 1.1, DRUM_Y);
                float length = (float) Math.hypot(DRUM_Y, 1.1);
                Vector3f middle = frame.transform(new Vector3f(along, -DRUM_Y / 2, side * 0.55f)).add(centre);
                block(anchor, Material.DARK_OAK_PLANKS, Props.box(middle,
                        new Quaternionf(frame).mul(Props.pitch(-lean)), new Vector3f(0.18f, length, 0.18f)));
            }
            // A gold ring at each end of the cage; round, so it never needs to turn.
            for (int i = 0; i < 20; i++) {
                double at = WheelMath.TAU * i / 20;
                float chord = (float) (2 * RADIUS * Math.sin(Math.PI / 20) * 1.08);
                block(anchor, Material.GOLD_BLOCK, Props.box(onDrum(end * HALF, RADIUS, at),
                        new Quaternionf(frame).mul(Props.pitch(at)), new Vector3f(0.09f, 0.1f, chord)));
            }
            for (int k = 0; k < 2; k++) {
                spokes.add(block(anchor, Material.IRON_BLOCK, spokePose(end, k, 0)));
            }
        }
        for (int i = 0; i < BARS; i++) {
            bars.add(block(anchor, Material.IRON_BLOCK, barPose(i, 0)));
        }
        crankArm = block(anchor, Material.IRON_BLOCK, crankPose(0));
        crankKnob = block(anchor, Material.RED_CONCRETE, knobPose(0));
        // The chute the drawn balls roll down, towards the podium.
        Vector3f top = chuteTop();
        Vector3f bottom = chuteBottom();
        Vector3f run = new Vector3f(bottom).sub(top);
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(top).add(bottom).mul(0.5f),
                Props.roll(Math.atan2(run.y, run.x)), new Vector3f(run.length(), 0.08f, 0.55f)));
    }

    private void buildPodium() {
        for (int place = 0; place < 3; place++) {
            float x = STEP_X[place];
            float top = STEP_TOP[place];
            block(anchor, STEP[place], Props.box(new Vector3f(x, top / 2, 0), new Vector3f(1.3f, top, 1.4f)));
            TextDisplay number = text(anchor, Text.c("&0&l" + (place + 1)), Props.centred(
                    new Vector3f(x, top / 2 - 0.3f, 0.71f), Props.none(), new Vector3f(2.2f, 2.2f, 2.2f)),
                    Display.Billboard.FIXED);
            number.setShadowed(false);
        }
    }

    /** One ball per ticket, at most {@link #MAX_BALLS}, in the colour of its owner. */
    private void fillDrum() {
        int sold = 0;
        for (Entry entry : entries) {
            sold += Math.max(1, entry.tickets());
        }
        double scale = Math.min(1.0, MAX_BALLS / (double) sold);
        for (int owner = 0; owner < entries.size(); owner++) {
            int count = Math.max(1, (int) Math.round(Math.max(1, entries.get(owner).tickets()) * scale));
            for (int n = 0; n < count; n++) {
                ItemDisplay display = item(anchor, entries.get(owner).colour(), Props.centred(new Vector3f(centre), 0.3f));
                balls.add(new Ball(owner, display));
            }
        }
    }

    // ----------------------------------------------------------------- frames

    @Override
    protected void onFrame(int elapsed, int duration) {
        if (anchor == null) {
            return;
        }
        double progress = progress(elapsed);
        double last = DRAWS[DRAWS.length - 1];
        // Speeds up, turns while the prizes are drawn, then winds down.
        double target = progress < 0.12 ? TOP_SPEED * progress / 0.12
                : progress > last + 0.06 ? TOP_SPEED * Math.max(0, (1 - progress) / (1 - last - 0.06)) : TOP_SPEED;
        speed = target;
        angle += speed;
        placeDrum(1);
        if (elapsed % 4 == 0 && speed > 0.05) {
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, (float) (0.6 + speed * 2));
        }
        int prizes = winners.size();
        for (int reveal = 0; reveal < prizes; reveal++) {
            int slot = DRAWS.length - prizes + reveal;
            if (elapsed == (int) (DRAWS[slot] * duration)) {
                // The smallest prize first, the first prize last.
                draw(prizes - 1 - reveal);
            }
        }
    }

    private void placeDrum(int ticks) {
        for (int i = 0; i < bars.size(); i++) {
            Props.animate(bars.get(i), barPose(i, angle), ticks);
        }
        int index = 0;
        for (int end : new int[]{-1, 1}) {
            for (int k = 0; k < 2; k++) {
                Props.animate(spokes.get(index++), spokePose(end, k, angle), ticks);
            }
        }
        Props.animate(crankArm, crankPose(angle), ticks);
        Props.animate(crankKnob, knobPose(angle), ticks);
        // The balls tumble at the bottom of the cage, tossed up by the bars now and then.
        for (Ball ball : balls) {
            double swing = Math.PI + 0.9 * Math.sin(angle * 0.9 + ball.phase);
            float lift = (float) (0.55 * Math.max(0, Math.sin(angle * 1.3 + ball.toss)));
            float reach = RADIUS - 0.3f - lift;
            Quaternionf tumble = new Quaternionf().rotateXYZ((float) (angle * ball.spin), (float) (angle * 0.7),
                    (float) (angle * ball.spin * 0.5));
            Props.animate(ball.display, Props.centred(onDrum(ball.along, reach, swing), tumble,
                    new Vector3f(0.3f, 0.3f, 0.3f)), ticks);
        }
    }

    /**
     * Draws a prize: a ball of the winner drops out of the drum, rolls down the chute and
     * jumps onto the podium, where it opens into their head.
     */
    private void draw(int place) {
        if (place < 0 || place >= winners.size() || revealed[place]) {
            return;
        }
        revealed[place] = true;
        int owner = winners.get(place);
        Ball ball = null;
        for (Ball candidate : balls) {
            if (candidate.owner == owner) {
                ball = candidate;
                break;
            }
        }
        ItemDisplay display;
        if (ball != null) {
            balls.remove(ball);
            display = ball.display;
        } else {
            display = item(anchor, entries.get(owner).colour(), Props.centred(new Vector3f(centre), 0.3f));
        }
        display.setGlowColorOverride(Color.fromRGB(0xFFD700));
        display.setGlowing(true);
        Vector3f exit = onDrum(0, RADIUS + 0.1f, Math.PI);
        Vector3f top = chuteTop().add(0, 0.25f, 0);
        Vector3f bottom = chuteBottom().add(0, 0.25f, 0);
        Vector3f over = new Vector3f(STEP_X[place], STEP_TOP[place] + 1.5f, 0.2f);
        Vector3f rest = new Vector3f(STEP_X[place], STEP_TOP[place] + 0.45f, 0);
        Quaternionf still = Props.none();
        Props.animate(display, Props.centred(exit, still, new Vector3f(0.35f, 0.35f, 0.35f)), 4);
        later(4, () -> Props.animate(display, Props.centred(top, still, new Vector3f(0.35f, 0.35f, 0.35f)), 3));
        later(7, () -> Props.animate(display, Props.centred(bottom, still, new Vector3f(0.35f, 0.35f, 0.35f)), 6));
        later(13, () -> Props.animate(display, Props.centred(over, still, new Vector3f(0.45f, 0.45f, 0.45f)), 6));
        later(19, () -> Props.animate(display, Props.centred(rest, still, new Vector3f(0.8f, 0.8f, 0.8f)), 3));
        later(22, () -> {
            Props.animate(display, Props.centred(rest, still, new Vector3f(0.01f, 0.01f, 0.01f)), 2);
            open(place, owner, rest);
        });
        later(25, () -> discard(display));
        playSound(Sound.BLOCK_DISPENSER_DISPENSE, 0.9f, 1.2f);
        later(7, () -> playSound(Sound.BLOCK_CHAIN_STEP, 0.8f, 1.4f));
    }

    /** The ball opens into the head of its owner, with their name and prize over it. */
    private void open(int place, int owner, Vector3f rest) {
        Entry entry = entries.get(owner);
        ItemDisplay head = item(anchor, entry.head(), Props.centred(rest, 0.01f));
        later(1, () -> Props.animate(head, Props.centred(rest, 1.1f), 6));
        if (place == 0) {
            head.setGlowColorOverride(Color.fromRGB(0xFFD700));
            head.setGlowing(true);
        }
        String share = place < shares.size() ? shares.get(place) : "";
        TextDisplay label = text(local(rest.x, rest.y + 0.9, 0), Text.c((place == 0 ? "&6&l" : "&f&l") + entry.name()
                + (share.isBlank() ? "" : "\n&e" + share)), Props.scaled(0.1f), Display.Billboard.VERTICAL);
        label.setBackgroundColor(Props.argb(150, 0, 0, 0));
        later(1, () -> Props.animate(label, Props.scaled(place == 0 ? 1.3f : 1.0f), 6));
        particles(Particle.FIREWORK, rest.x, rest.y, rest.z, place == 0 ? 40 : 15, 0.4, 0.1);
        if (place == 0) {
            celebrate(rest.x, rest.y + 0.5, rest.z);
        } else {
            playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.0f + place * 0.2f);
        }
    }

    @Override
    protected void onSettle() {
        // Any prize still in the drum is drawn at once.
        for (int place = winners.size() - 1; place >= 0; place--) {
            draw(place);
        }
    }

    @Override
    protected int extraLinger() {
        return 40;
    }

    // ----------------------------------------------------------------- poses

    private Transformation barPose(int index, double turn) {
        double at = turn + WheelMath.TAU * index / BARS;
        return Props.box(onDrum(0, RADIUS, at), new Quaternionf(frame).mul(Props.pitch(at)),
                new Vector3f(2 * HALF, 0.07f, 0.07f));
    }

    private Transformation spokePose(int end, int k, double turn) {
        return Props.box(onDrum(end * (HALF - 0.02f), 0, 0), new Quaternionf(frame).mul(Props.pitch(turn + k * Math.PI / 2)),
                new Vector3f(0.07f, 2 * RADIUS, 0.07f));
    }

    private Transformation crankPose(double turn) {
        return Props.box(onDrum(HALF + 0.55f, 0.35f, turn), new Quaternionf(frame).mul(Props.pitch(turn)),
                new Vector3f(0.08f, 0.7f, 0.08f));
    }

    private Transformation knobPose(double turn) {
        return Props.box(onDrum(HALF + 0.62f, 0.7f, turn), new Quaternionf(frame).mul(Props.pitch(turn)),
                new Vector3f(0.18f, 0.18f, 0.18f));
    }

    private Vector3f chuteTop() {
        return onDrum(0.3f, RADIUS + 0.35f, Math.PI).add(0.3f, 0, 0.6f);
    }

    private static Vector3f chuteBottom() {
        return new Vector3f(1.0f, 0.55f, 1.0f);
    }

    /**
     * Point of the drum: {@code along} its axis, {@code reach} from it, at angle
     * {@code at} round it (0 on top, turning towards the audience).
     */
    private Vector3f onDrum(float along, float reach, double at) {
        Vector3f local = new Vector3f(along, (float) (Math.cos(at) * reach), (float) (Math.sin(at) * reach));
        return frame.transform(local).add(centre);
    }
}
