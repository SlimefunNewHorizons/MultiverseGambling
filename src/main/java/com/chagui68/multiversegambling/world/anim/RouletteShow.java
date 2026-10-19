package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.RouletteTable;
import com.chagui68.multiversegambling.engine.RouletteTable.Bet;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A real roulette table, built big and lying flat on the ground of the pavilion: the
 * wheel at the head of the table and the betting cloth beside it, with every player's
 * chip on the field they bet on.
 *
 * <p>The wheel, from the outside in: a wooden bowl with a gold rim and a ring of bulbs,
 * the polished ball track, the lower track with its eight gold deflectors, and the rotor:
 * the coloured number ring, the pockets split by metal frets, the wooden cone and the
 * chrome turret with its cross on top. The rotor turns slowly, the ball is thrown hard the
 * other way round the ball track, laps it several times, runs down, knocks a deflector,
 * hops over a few frets and drops into its pocket (see {@link RouletteSpin}). The cloth is
 * the one of a real table ({@link RouletteLayout}); when the ball stops, the fields that
 * win light up, the winning chips rise and the losing ones are swept away.</p>
 *
 * <p>Pocket, colours and bets come from the game, already decided by the provably fair
 * generator: the table only paints them. Only the pieces whose turn can be seen move: the
 * tiles, frets and numbers of the rotor and the turret. The bowl, the cone and the gold
 * rings are round, so they stand still and cost nothing per frame.</p>
 */
public final class RouletteShow extends ArenaShow {

    /** How the players look down at the table. */
    public static final float WATCH_PITCH = 22.0f;

    /** Middle of the wheel, at the head of the table on the left of the cloth. */
    private static final float WHEEL_X = -6.5f;
    /** Outer radius of the wooden bowl. */
    private static final float BOWL = 6.2f;
    /** Pieces a round static ring is drawn with. */
    private static final int RING_PIECES = 48;
    /** Wedges a round slab of the cone is drawn with. */
    private static final int DISC_PIECES = 20;
    private static final int BULBS = 32;
    private static final float BALL = 0.42f;

    // Radii and heights of the surface, outside in: ball track, lower track, number ring, pockets.
    // The bowl is kept low, almost flush with the ground, so a player standing at the rim
    // looks down into it instead of at its wall.
    private static final float TRACK_OUT = 5.6f;
    private static final float TRACK_IN = 4.9f;
    private static final float STATOR_IN = 4.2f;
    private static final float NUMBERS_IN = 3.7f;
    private static final float POCKETS_IN = 2.95f;
    private static final float RIM_TOP = 0.66f;
    private static final float TRACK_TOP = 0.60f;
    private static final float TRACK_LOW = 0.50f;
    private static final float STATOR_LOW = 0.32f;
    private static final float NUMBERS_LOW = 0.24f;
    private static final float POCKET_FLOOR = 0.16f;
    private static final float TURRET_ARM = 0.95f;

    /** Top of the felt of the cloth. */
    private static final float FELT = 0.10f;
    /** Colour of each player's chips, in the order they bet. */
    private static final Material[] CHIPS = {Material.ORANGE_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.MAGENTA_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.PINK_CONCRETE,
            Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE};

    /** A bet waiting to be put on the cloth, or already on it. */
    private static final class Chip {
        private final Bet bet;
        private final int selection;
        private final String player;
        private final String amount;
        private final List<BlockDisplay> pieces = new ArrayList<>();
        private TextDisplay tag;

        private Chip(Bet bet, int selection, String player, String amount) {
            this.bet = bet;
            this.selection = selection;
            this.player = player;
            this.amount = amount;
        }
    }

    private final List<Material> sectors;
    private final int winner;
    private List<String> labels = List.of();
    private List<RouletteLayout.Field> cloth = List.of();
    private BiFunction<Bet, Integer, String> names = (bet, selection) -> "";
    /** A pocket that stands for the result on the cloth, -1 until a cloth is laid. */
    private int result = -1;
    private final List<Chip> chips = new ArrayList<>();
    private final Map<String, Material> chipColour = new HashMap<>();
    private final Map<RouletteLayout.Field, BlockDisplay> fills = new HashMap<>();

    private final List<BlockDisplay> numberTiles = new ArrayList<>();
    private final List<BlockDisplay> pocketTiles = new ArrayList<>();
    private final List<BlockDisplay> frets = new ArrayList<>();
    private final List<TextDisplay> numberLabels = new ArrayList<>();
    private final List<Integer> labelSector = new ArrayList<>();
    private final List<BlockDisplay> turret = new ArrayList<>();
    private final List<ItemDisplay> knobs = new ArrayList<>();
    private final List<BlockDisplay> bulbs = new ArrayList<>();
    private int[] tileSector;
    private double[] sectorAngle;
    private double slot;
    private RouletteSpin spin;
    private Location anchor;
    private Location ground;
    private ItemDisplay ball;
    private ItemDisplay cap;
    private int lastHop = -1;
    private boolean hitDeflector;
    private boolean dropped;

    /**
     * @param sectors colour of each pocket, in the order they sit round the wheel, clockwise
     *                seen from above
     * @param winner  index of the pocket the ball has to stop in
     * @param ticks   frames the spin lasts
     */
    public RouletteShow(MultiverseGamblingPlugin plugin, ArenaStage stage, List<Material> sectors, int winner,
                        int ticks) {
        super(plugin, stage, ticks);
        this.sectors = List.copyOf(sectors);
        this.winner = sectors.isEmpty() ? 0 : Math.floorMod(winner, sectors.size());
    }

    /**
     * Where one of {@code total} players stands: along the side of the cloth, the first
     * ones next to the wheel, all of them looking down at the table.
     */
    public static Location watchSpot(ArenaStage stage, int index, int total) {
        double x = total <= 1 ? 1.0 : -0.5 + 12.0 * Math.max(0, Math.min(total - 1, index)) / (total - 1);
        double z = 4.4;
        Location spot = stage.local(x, 0, z);
        // Turned a little towards the wheel, the cloth in front.
        spot.setYaw(stage.frame().lookYaw(x, z, x - 3.5, -0.3));
        spot.setPitch(WATCH_PITCH);
        return spot;
    }

    /**
     * Numbers written on the number ring, one per pocket. Empty strings leave a pocket blank.
     */
    public RouletteShow labels(List<String> labels) {
        this.labels = labels == null ? List.of() : List.copyOf(labels);
        return this;
    }

    /**
     * Lays the full cloth of the classic roulette.
     *
     * @param pocket the number that came up, to light the fields it pays
     * @param names  words written on the dozens, the columns and the even-money fields
     */
    public RouletteShow numbersCloth(boolean doubleZero, int pocket, BiFunction<Bet, Integer, String> names) {
        this.cloth = RouletteLayout.numbers(doubleZero);
        this.result = pocket;
        this.names = names;
        return this;
    }

    /**
     * Lays the three fields of the colour roulette; the field that wins is the colour of
     * the winning pocket.
     *
     * @param names words written on each field: the zero for green, the colour bets for red
     *              and black
     */
    public RouletteShow coloursCloth(BiFunction<Bet, Integer, String> names) {
        this.cloth = RouletteLayout.colours();
        Material colour = sectors.isEmpty() ? Material.LIME_CONCRETE : sectors.get(winner);
        // Any pocket of the right colour stands for the result: 1 is red, 2 is black.
        this.result = colour == Material.RED_CONCRETE ? 1 : colour == Material.BLACK_CONCRETE ? 2 : 0;
        this.names = names;
        return this;
    }

    /**
     * Puts a player's chip on the field of a bet. Several players may share a field.
     */
    public RouletteShow bet(Bet bet, int selection, String player, String amount) {
        Chip chip = new Chip(bet, selection, player, amount);
        chips.add(chip);
        if (ground != null) {
            placeChip(chip);
        }
        return this;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void onStart() {
        if (sectors.isEmpty()) {
            return;
        }
        anchor = local(WHEEL_X, 0, 0);
        ground = local(0, 0, 0);
        int[] perSector = WheelMath.allocate(null, sectors.size(), 36);
        int total = 0;
        for (int count : perSector) {
            total += count;
        }
        slot = WheelMath.TAU / total;
        tileSector = new int[total];
        sectorAngle = new double[sectors.size()];
        int tile = 0;
        for (int sector = 0; sector < sectors.size(); sector++) {
            // Clockwise, as the numbers run round a real wheel.
            sectorAngle[sector] = -slot * (tile + (perSector[sector] - 1) / 2.0);
            for (int i = 0; i < perSector[sector]; i++) {
                tileSector[tile++] = sector;
            }
        }
        // Where the ball rests inside its pocket is jittered a little: it is only paint.
        double inside = (Rng.next() - 0.5) * 0.3 * slot;
        spin = new RouletteSpin(duration(), Rng.next() * WheelMath.TAU, Rng.next() * WheelMath.TAU,
                sectorAngle[winner] + inside);

        buildBowl();
        buildCone();
        double rotor = spin.rotor(0);
        for (int index = 0; index < total; index++) {
            Material colour = sectors.get(tileSector[index]);
            numberTiles.add(block(anchor, colour, numberPose(index, rotor, 0.0f)));
            pocketTiles.add(block(anchor, colour, pocketPose(index, rotor, 0.0f)));
            frets.add(block(anchor, Material.IRON_BLOCK, fretPose(index, rotor)));
        }
        buildLabels(rotor);
        for (int arm = 0; arm < 2; arm++) {
            turret.add(block(anchor, Material.IRON_BLOCK, armPose(rotor, arm)));
        }
        turret.add(block(anchor, Material.IRON_BLOCK, spindlePose(rotor)));
        for (int knob = 0; knob < 4; knob++) {
            knobs.add(item(anchor, Material.GOLD_BLOCK, knobPose(rotor, knob)));
        }
        cap = item(anchor, Material.GOLD_BLOCK, capPose(rotor));
        buildCloth();
        for (Chip chip : chips) {
            placeChip(chip);
        }
        // The ball faces the camera, so it is moved by teleporting it: an offset in its
        // transformation would be turned with the camera and leave it floating off the wheel.
        ball = item(ballLocation(0), Material.SNOWBALL, Props.scaled(BALL));
        ball.setBillboard(Display.Billboard.CENTER);
        ball.setTeleportDuration(1);
        playSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f);
    }

    private void buildBowl() {
        // The wooden wall of the bowl, standing on the ground, and its gold rim.
        ring(Material.DARK_OAK_PLANKS, TRACK_OUT, BOWL, RIM_TOP - 0.04f, RIM_TOP - 0.04f, RIM_TOP - 0.04f);
        ring(Material.GOLD_BLOCK, TRACK_OUT - 0.05f, BOWL + 0.05f, RIM_TOP, RIM_TOP, 0.04f);
        // The polished ball track and the lower track, both sloping down towards the middle.
        ring(Material.SPRUCE_PLANKS, TRACK_IN, TRACK_OUT, TRACK_LOW, TRACK_TOP, 0.14f);
        ring(Material.DARK_OAK_PLANKS, STATOR_IN, TRACK_IN, STATOR_LOW, TRACK_LOW, 0.14f);
        ring(Material.GOLD_BLOCK, STATOR_IN - 0.03f, STATOR_IN + 0.03f, STATOR_LOW + 0.02f, STATOR_LOW + 0.02f, 0.05f);
        // Eight deflectors on the lower track, alternately long and wide.
        float middle = (STATOR_IN + TRACK_IN) / 2;
        double slope = Math.atan2(TRACK_LOW - STATOR_LOW, TRACK_IN - STATOR_IN);
        for (int i = 0; i < 8; i++) {
            double angle = WheelMath.TAU * (i + 0.5) / 8;
            Quaternionf rotation = Props.yaw(angle).mul(Props.pitch(slope)).mul(Props.yaw(Math.PI / 4));
            Vector3f size = i % 2 == 0 ? new Vector3f(0.3f, 0.1f, 0.3f) : new Vector3f(0.22f, 0.1f, 0.42f);
            Vector3f centre = onWheel(angle, middle, surface(middle) + 0.04f);
            block(anchor, Material.GOLD_BLOCK, Props.box(centre, rotation, size));
        }
        for (int i = 0; i < BULBS; i++) {
            double angle = WheelMath.TAU * i / BULBS;
            bulbs.add(block(anchor, i % 2 == 0 ? Material.GLOWSTONE : Material.GOLD_BLOCK, Props.box(
                    onWheel(angle, (TRACK_OUT + BOWL) / 2, RIM_TOP + 0.07f), Props.yaw(angle),
                    new Vector3f(0.22f, 0.14f, 0.22f))));
        }
    }

    /** The cone in the middle of the rotor: round, so it never needs to turn. */
    private void buildCone() {
        disc(Material.DARK_OAK_PLANKS, POCKETS_IN, 0.06f, 0.24f);
        disc(Material.GOLD_BLOCK, 2.25f, 0.24f, 0.27f);
        disc(Material.DARK_OAK_PLANKS, 2.2f, 0.24f, 0.36f);
        disc(Material.GOLD_BLOCK, 1.45f, 0.36f, 0.39f);
        disc(Material.DARK_OAK_PLANKS, 1.4f, 0.36f, 0.50f);
        disc(Material.GOLD_BLOCK, 0.75f, 0.50f, 0.60f);
    }

    /**
     * A flat round slab made of wedges from the axle out: squares turned round each other
     * would make a star, not a circle.
     */
    private void disc(Material material, float radius, float bottom, float top) {
        for (int i = 0; i < DISC_PIECES; i++) {
            block(anchor, material, segment(WheelMath.TAU * i / DISC_PIECES, DISC_PIECES, 0.0f, radius, top, top,
                    top - bottom, i % 2 == 0 ? 0.0f : 0.002f));
        }
    }

    /**
     * A round band between two radii whose top runs from {@code lowY} on the inside to
     * {@code highY} on the outside, made of short sloped pieces.
     */
    private void ring(Material material, float inner, float outer, float lowY, float highY, float thickness) {
        for (int i = 0; i < RING_PIECES; i++) {
            block(anchor, material, segment(WheelMath.TAU * i / RING_PIECES, RING_PIECES, inner, outer, lowY, highY,
                    thickness, i % 2 == 0 ? 0.0f : 0.004f));
        }
    }

    private void buildLabels(double rotor) {
        for (int sector = 0; sector < sectors.size() && sector < labels.size(); sector++) {
            String label = labels.get(sector);
            if (label == null || label.isBlank()) {
                continue;
            }
            TextDisplay text = text(anchor, Text.c(WheelShow.textColour(sectors.get(sector)) + "&l" + label),
                    labelPose(rotor + sectorAngle[sector]), Display.Billboard.FIXED);
            text.setShadowed(false);
            numberLabels.add(text);
            labelSector.add(sector);
        }
    }

    // ------------------------------------------------------------------ cloth

    /**
     * The green felt in a wooden rail, and on it every field with a white edge, its colour
     * and its words.
     */
    private void buildCloth() {
        if (cloth.isEmpty()) {
            return;
        }
        float margin = 0.25f;
        float left = RouletteLayout.minX(cloth) - margin;
        float right = RouletteLayout.maxX(cloth) + margin;
        float far = RouletteLayout.minZ(cloth) - margin;
        float near = RouletteLayout.maxZ(cloth) + margin;
        block(ground, Material.GREEN_WOOL, Props.box(new Vector3f((left + right) / 2, FELT / 2, (far + near) / 2),
                new Vector3f(right - left, FELT, near - far)));
        float rail = 0.2f;
        float railHeight = 0.24f;
        block(ground, Material.DARK_OAK_PLANKS, Props.box(new Vector3f((left + right) / 2, railHeight / 2, far - rail / 2),
                new Vector3f(right - left + 2 * rail, railHeight, rail)));
        block(ground, Material.DARK_OAK_PLANKS, Props.box(new Vector3f((left + right) / 2, railHeight / 2, near + rail / 2),
                new Vector3f(right - left + 2 * rail, railHeight, rail)));
        block(ground, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(left - rail / 2, railHeight / 2, (far + near) / 2),
                new Vector3f(rail, railHeight, near - far)));
        block(ground, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(right + rail / 2, railHeight / 2, (far + near) / 2),
                new Vector3f(rail, railHeight, near - far)));
        boolean full = cloth.size() > 3;
        for (RouletteLayout.Field field : cloth) {
            block(ground, Material.WHITE_CONCRETE, Props.box(new Vector3f(field.x(), FELT + 0.01f, field.z()),
                    new Vector3f(field.width(), 0.02f, field.depth())));
            Material fill = fillOf(field, full);
            fills.put(field, block(ground, fill, Props.box(new Vector3f(field.x(), FELT + 0.03f, field.z()),
                    new Vector3f(field.width() - 0.08f, 0.02f, field.depth() - 0.08f))));
            if (full && field.bet() == Bet.COLOR) {
                // A red or a black diamond, as printed on a real cloth.
                block(ground, field.selection() == 0 ? Material.RED_CONCRETE : Material.BLACK_CONCRETE, Props.box(
                        new Vector3f(field.x(), FELT + 0.05f, field.z()), Props.yaw(Math.PI / 4),
                        new Vector3f(0.36f, 0.02f, 0.36f)));
                continue;
            }
            String words = field.bet() == Bet.NUMBER && full ? RouletteTable.label(field.selection())
                    : field.bet() == Bet.COLUMN ? "2:1" : Text.strip(names.apply(field.bet(), field.selection()));
            clothText(field, words);
        }
    }

    private static Material fillOf(RouletteLayout.Field field, boolean full) {
        if (field.bet() == Bet.NUMBER) {
            return switch (RouletteTable.colorOf(field.selection())) {
                case RED -> Material.RED_CONCRETE;
                case BLACK -> Material.BLACK_CONCRETE;
                case GREEN -> Material.LIME_CONCRETE;
            };
        }
        if (!full && field.bet() == Bet.COLOR) {
            return field.selection() == 0 ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
        }
        return Material.GREEN_WOOL;
    }

    /** Words printed flat on a field, reading from the side where the players stand. */
    private void clothText(RouletteLayout.Field field, String words) {
        if (words == null || words.isBlank()) {
            return;
        }
        String[] lines = words.split("\n");
        int longest = 1;
        for (String line : lines) {
            longest = Math.max(longest, line.length());
        }
        float scale = (float) Math.min(field.bet() == Bet.NUMBER ? 1.4 : 1.6,
                Math.min(field.width() * 0.8 / (longest * 0.16), field.depth() * 0.75 / (lines.length * 0.25)));
        // Text grows away from the players from its origin: start half its height nearer.
        float height = (lines.length * 10 + 1) / 40.0f * scale;
        TextDisplay text = text(ground, Text.c("&f&l" + words.replace("\n", "\n&f&l")),
                Props.centred(new Vector3f(field.x(), FELT + 0.045f, field.z() + height / 2), Props.pitch(-Math.PI / 2),
                        new Vector3f(scale, scale, scale)), Display.Billboard.FIXED);
        text.setShadowed(false);
    }

    /** A chip with the colour of its player on the field of the bet, and their name over it. */
    private void placeChip(Chip chip) {
        RouletteLayout.Field field = RouletteLayout.fieldOf(cloth, chip.bet, chip.selection);
        if (field == null) {
            return;
        }
        Material colour = chipColour.computeIfAbsent(chip.player, name -> CHIPS[chipColour.size() % CHIPS.length]);
        // Players sharing a field spread their chips along it.
        int shared = 0;
        for (Chip other : chips) {
            if (other == chip) {
                break;
            }
            if (other.bet == chip.bet && other.selection == chip.selection) {
                shared++;
            }
        }
        float step = Math.min(0.32f, field.width() / 4);
        float x = field.x() + (shared % 2 == 0 ? 1 : -1) * ((shared + 1) / 2) * step;
        float z = field.z() + (shared / 3) * 0.2f;
        for (int edge = 0; edge < 2; edge++) {
            chip.pieces.add(block(ground, colour, chipPose(x, z, edge * Math.PI / 4, 0.36f, 0.08f, 0.0f)));
        }
        chip.pieces.add(block(ground, Material.WHITE_CONCRETE, chipPose(x, z, Math.PI / 8, 0.18f, 0.085f, 0.0f)));
        chip.tag = text(local(x, FELT + 0.45, z), Text.c("&f&l" + chip.player + " &e" + chip.amount),
                Props.scaled(0.45f), Display.Billboard.VERTICAL);
        chip.tag.setBackgroundColor(Props.argb(150, 0, 0, 0));
        chip.tag.setTeleportDuration(6);
        playSound(Sound.BLOCK_CHAIN_PLACE, 0.6f, 1.6f);
    }

    private static Transformation chipPose(float x, float z, double turn, float side, float height, float lift) {
        return Props.box(new Vector3f(x, FELT + 0.05f + height / 2 + lift, z), Props.yaw(turn),
                new Vector3f(side, height, side));
    }

    // ----------------------------------------------------------------- frames

    @Override
    protected void onFrame(int elapsed, int duration) {
        if (spin == null) {
            return;
        }
        double progress = progress(elapsed);
        placeRotor(spin.rotor(progress), 1);
        ball.teleport(ballLocation(progress));
        sounds(progress, elapsed);
    }

    private void sounds(double progress, int elapsed) {
        if (progress < RouletteSpin.ON_TRACK) {
            if (elapsed % 2 == 0) {
                playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.25f, (float) (0.6 + progress));
            }
        } else if (!hitDeflector && spin.ballRadius(progress) <= (STATOR_IN + TRACK_IN) / 2) {
            hitDeflector = true;
            playSound(Sound.BLOCK_METAL_HIT, 0.9f, 1.5f);
            Vector3f spot = ballSpot(progress);
            particles(Particle.CRIT, WHEEL_X + spot.x, spot.y, spot.z, 6, 0.1, 0.05);
        } else if (progress >= RouletteSpin.DROPPED && progress < RouletteSpin.LANDED) {
            int hop = (int) Math.floor(3 * (progress - RouletteSpin.DROPPED)
                    / (RouletteSpin.LANDED - RouletteSpin.DROPPED));
            if (hop != lastHop) {
                lastHop = hop;
                playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.6f - hop * 0.15f);
            }
        } else if (!dropped && spin.landed(progress)) {
            dropped = true;
            playSound(Sound.BLOCK_STONE_BUTTON_CLICK_ON, 1.0f, 0.8f);
        }
    }

    @Override
    protected void onAnimate(int age) {
        // The bulbs chase each other round the rim, faster while the wheel turns.
        int period = active() ? 3 : 6;
        if (age % period != 0 || bulbs.isEmpty()) {
            return;
        }
        int phase = age / period;
        for (int i = 0; i < bulbs.size(); i++) {
            BlockDisplay bulb = bulbs.get(i);
            if (bulb.isValid()) {
                boolean lit = settled() ? phase % 2 == 0 : (i + phase) % 4 < 2;
                bulb.setBlock((lit ? Material.GLOWSTONE : Material.GOLD_BLOCK).createBlockData());
            }
        }
    }

    @Override
    protected void onSettle() {
        if (spin == null) {
            return;
        }
        double rotor = spin.rotor(1);
        placeRotor(rotor, 2);
        ball.setTeleportDuration(2);
        ball.teleport(ballLocation(1));
        // Everything but the winning pocket dims, and the winner glows and rises.
        Material colour = sectors.get(winner);
        Color glow = Props.colourOf(colour);
        int rgb = glow.asRGB();
        if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) < 180) {
            // A dark pocket would get a dark outline nobody can see.
            glow = Color.WHITE;
        }
        for (int index = 0; index < tileSector.length; index++) {
            BlockDisplay number = numberTiles.get(index);
            BlockDisplay pocket = pocketTiles.get(index);
            if (tileSector[index] == winner) {
                for (BlockDisplay tile : List.of(number, pocket)) {
                    tile.setGlowColorOverride(glow);
                    tile.setGlowing(true);
                }
                Props.animate(number, numberPose(index, rotor, 0.12f), 6);
                Props.animate(pocket, pocketPose(index, rotor, 0.06f), 6);
            } else {
                number.setBrightness(new Display.Brightness(4, 4));
                pocket.setBrightness(new Display.Brightness(4, 4));
            }
        }
        for (int i = 0; i < numberLabels.size(); i++) {
            if (labelSector.get(i) != winner && numberLabels.get(i).isValid()) {
                numberLabels.get(i).setTextOpacity((byte) 90);
            }
        }
        String label = winner < labels.size() ? labels.get(winner) : "";
        if (label != null && !label.isBlank()) {
            TextDisplay title = text(local(WHEEL_X, 3.4, 0), Text.c(WheelShow.textColour(colour) + "&l" + label),
                    Props.centred(new Vector3f(), 0.1f), Display.Billboard.VERTICAL);
            title.setBackgroundColor(Props.argb(150, 0, 0, 0));
            later(1, () -> Props.animate(title, Props.centred(new Vector3f(), 2.6f), 8));
        }
        settleCloth();
        Vector3f spot = ballSpot(1);
        particles(Particle.TOTEM_OF_UNDYING, WHEEL_X + spot.x, spot.y + 0.3, spot.z, 50, 0.5, 0.4);
        particles(Particle.FIREWORK, WHEEL_X + spot.x, spot.y + 0.3, spot.z, 30, 0.4, 0.1);
        playSound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f);
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.4f);
    }

    /**
     * The fields the ball pays light up; the winning chips rise and glow, and the dealer
     * sweeps the losing ones off the cloth towards the wheel.
     */
    private void settleCloth() {
        if (result < 0) {
            return;
        }
        for (Map.Entry<RouletteLayout.Field, BlockDisplay> entry : fills.entrySet()) {
            RouletteLayout.Field field = entry.getKey();
            BlockDisplay fill = entry.getValue();
            if (RouletteTable.payoutOf(field.bet(), field.selection(), result) > 0) {
                fill.setGlowColorOverride(Color.fromRGB(0xFFD700));
                fill.setGlowing(true);
            } else {
                fill.setBrightness(new Display.Brightness(7, 7));
            }
        }
        for (Chip chip : chips) {
            if (chip.tag == null) {
                continue;
            }
            boolean wins = RouletteTable.payoutOf(chip.bet, chip.selection, result) > 0;
            chip.tag.text(Text.c((wins ? "&a&l✔ " : "&c&l✖ ") + "&f" + chip.player + " &e" + chip.amount));
            for (BlockDisplay piece : chip.pieces) {
                Transformation now = piece.getTransformation();
                if (wins) {
                    piece.setGlowColorOverride(Color.fromRGB(0xFFD700));
                    piece.setGlowing(true);
                    Props.animate(piece, new Transformation(new Vector3f(now.getTranslation()).add(0, 0.35f, 0),
                            now.getLeftRotation(), now.getScale(), now.getRightRotation()), 8);
                } else {
                    later(20, () -> Props.animate(piece, new Transformation(
                            new Vector3f(now.getTranslation()).add(-2.5f, 0, -1.0f), now.getLeftRotation(),
                            new Vector3f(0.01f, 0.01f, 0.01f), now.getRightRotation()), 14));
                }
            }
            if (!wins) {
                chip.tag.setTextOpacity((byte) 120);
            }
        }
    }

    @Override
    protected int extraLinger() {
        return chips.isEmpty() ? 20 : 50;
    }

    // ----------------------------------------------------------------- poses

    private void placeRotor(double rotor, int ticks) {
        for (int index = 0; index < numberTiles.size(); index++) {
            Props.animate(numberTiles.get(index), numberPose(index, rotor, 0.0f), ticks);
            Props.animate(pocketTiles.get(index), pocketPose(index, rotor, 0.0f), ticks);
            Props.animate(frets.get(index), fretPose(index, rotor), ticks);
        }
        for (int i = 0; i < numberLabels.size(); i++) {
            Props.animate(numberLabels.get(i), labelPose(rotor + sectorAngle[labelSector.get(i)]), ticks);
        }
        Props.animate(turret.get(0), armPose(rotor, 0), ticks);
        Props.animate(turret.get(1), armPose(rotor, 1), ticks);
        Props.animate(turret.get(2), spindlePose(rotor), ticks);
        for (int knob = 0; knob < knobs.size(); knob++) {
            Props.animate(knobs.get(knob), knobPose(rotor, knob), ticks);
        }
        Props.animate(cap, capPose(rotor), ticks);
    }

    private Transformation numberPose(int index, double rotor, float lift) {
        return segment(rotor - slot * index, tileSector.length, NUMBERS_IN, STATOR_IN, NUMBERS_LOW + lift,
                STATOR_LOW + lift, 0.12f, index % 2 == 0 ? 0.0f : 0.004f);
    }

    private Transformation pocketPose(int index, double rotor, float lift) {
        return segment(rotor - slot * index, tileSector.length, POCKETS_IN, NUMBERS_IN, POCKET_FLOOR + lift,
                POCKET_FLOOR + lift, 0.14f, index % 2 == 0 ? 0.0f : 0.004f);
    }

    /** The metal fret between a pocket and the next one. */
    private Transformation fretPose(int index, double rotor) {
        double angle = rotor - slot * (index + 0.5);
        float middle = (POCKETS_IN + NUMBERS_IN) / 2;
        return Props.box(onWheel(angle, middle, POCKET_FLOOR + 0.05f), Props.yaw(angle),
                new Vector3f(0.04f, 0.1f, NUMBERS_IN - POCKETS_IN));
    }

    /**
     * A number lying on the sloped number ring, its top towards the middle of the wheel,
     * so it reads from the rim where the players stand.
     */
    private Transformation labelPose(double angle) {
        double slope = Math.atan2(STATOR_LOW - NUMBERS_LOW, STATOR_IN - NUMBERS_IN);
        float scale = 1.1f;
        // Text grows from its bottom edge: start a little outwards so it is centred.
        float along = (NUMBERS_IN + STATOR_IN) / 2 + 0.12f * scale;
        Quaternionf rotation = Props.yaw(angle).mul(Props.pitch(slope))
                .mul(Props.yaw(Math.PI)).mul(Props.pitch(-Math.PI / 2));
        return Props.centred(onWheel(angle, along, surface(along) + 0.015f), rotation,
                new Vector3f(scale, scale, scale));
    }

    private Transformation armPose(double rotor, int arm) {
        return Props.box(new Vector3f(0, TURRET_ARM, 0), Props.yaw(rotor + arm * Math.PI / 2),
                new Vector3f(1.9f, 0.12f, 0.12f));
    }

    private Transformation spindlePose(double rotor) {
        return Props.box(new Vector3f(0, 0.83f, 0), Props.yaw(rotor), new Vector3f(0.26f, 0.46f, 0.26f));
    }

    private Transformation knobPose(double rotor, int knob) {
        double angle = rotor + knob * Math.PI / 2;
        return Props.centred(onWheel(angle, 0.95f, TURRET_ARM), Props.yaw(angle), new Vector3f(0.24f, 0.24f, 0.24f));
    }

    private Transformation capPose(double rotor) {
        return Props.centred(new Vector3f(0, 1.13f, 0), Props.yaw(rotor + Math.PI / 4),
                new Vector3f(0.34f, 0.34f, 0.34f));
    }

    private Location ballLocation(double progress) {
        Vector3f spot = ballSpot(progress);
        return local(WHEEL_X + spot.x, spot.y, spot.z);
    }

    /** Where the ball is, relative to the middle of the wheel. */
    private Vector3f ballSpot(double progress) {
        float radius = (float) spin.ballRadius(progress);
        // The snowball fills about two thirds of its sprite: lifted by that radius it rests on the wood.
        return onWheel(spin.ball(progress), radius, surface(radius) + BALL * 0.33f + (float) spin.hop(progress));
    }

    /**
     * A sloped piece of a ring, centred on {@code angle}, wide enough at its outer edge to
     * close the gap with its neighbours.
     */
    private static Transformation segment(double angle, int pieces, float inner, float outer, float lowY,
                                          float highY, float thickness, float nudge) {
        double slope = Math.atan2(highY - lowY, outer - inner);
        float depth = (float) Math.hypot(outer - inner, highY - lowY);
        float width = (float) (WheelMath.TAU * outer / pieces * 1.02);
        Quaternionf rotation = Props.yaw(angle).mul(Props.pitch(slope));
        Vector3f top = onWheel(angle, (inner + outer) / 2, (lowY + highY) / 2 + nudge);
        Vector3f centre = top.sub(rotation.transform(new Vector3f(0, thickness / 2, 0)));
        return Props.box(centre, rotation, new Vector3f(width, thickness, depth));
    }

    /** Height of the surface of the bowl at that distance from the axle. */
    static float surface(float radius) {
        if (radius >= TRACK_IN) {
            return TRACK_LOW + (radius - TRACK_IN) / (TRACK_OUT - TRACK_IN) * (TRACK_TOP - TRACK_LOW);
        }
        if (radius >= STATOR_IN) {
            return STATOR_LOW + (radius - STATOR_IN) / (TRACK_IN - STATOR_IN) * (TRACK_LOW - STATOR_LOW);
        }
        if (radius >= NUMBERS_IN) {
            return NUMBERS_LOW + (radius - NUMBERS_IN) / (STATOR_IN - NUMBERS_IN) * (STATOR_LOW - NUMBERS_LOW);
        }
        return POCKET_FLOOR;
    }

    /**
     * Point at that angle (anticlockwise seen from above, 0 at the far side), distance
     * from the axle and height over the ground.
     */
    private static Vector3f onWheel(double angle, float distance, float height) {
        return new Vector3f((float) (-Math.sin(angle) * distance), height, (float) (-Math.cos(angle) * distance));
    }
}
