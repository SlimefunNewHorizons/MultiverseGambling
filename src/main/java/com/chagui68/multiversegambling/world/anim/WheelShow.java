package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The lucky wheel as a carnival wheel of fortune: a big wheel standing in a striped booth
 * under an awning, a metal peg between every prize and a leather flapper at the top that
 * clacks against each peg as it goes by, slower and slower until the last peg barely
 * makes it over.
 *
 * <p>The game only says which colour each sector has, what it is called and which one
 * wins; the winner is always handed over already decided by the provably fair generator,
 * so the wheel only ever paints a result.</p>
 *
 * <p>Every moving piece is turned through its transformation with a one tick
 * interpolation, so the client draws the spin smoothly between server ticks. The
 * pieces are spawned at the middle of the wheel, so turning a tile round the axle is a
 * rotation plus a short translation, and the angle per step is kept small enough for
 * the interpolation to stay on the circle.</p>
 */
public final class WheelShow extends ArenaShow {

    /** Fewest tiles a wheel is drawn with, so few sectors still make a round wheel. */
    private static final int MIN_TILES = 36;
    /** Radial depth of a tile. */
    private static final float TILE_DEPTH = 1.5f;
    /** Thickness of a tile. */
    private static final float TILE_THICKNESS = 0.14f;
    /** Bulbs round the rim. */
    private static final int BULBS = 32;
    /** Share of the show spent spinning; the rest is the result on display. */
    private static final double SPIN_SHARE = 0.8;
    /** Half the width of the booth. */
    private static final float BOOTH = 6.0f;
    /** Height of the back of the awning; it slopes down towards the audience. */
    private static final float AWNING = 12.2f;

    private final List<Material> sectors;
    private final int winner;
    private List<String> labels = List.of();
    private String title = "";
    private List<String> paytable = List.of();

    // Geometry, fixed when the show starts.
    private float radius;
    private Vector3f hub;
    private int[] tileSector;
    private double[] sectorAngle;
    /** Angle of every peg on the wheel, between two sectors. */
    private double[] pegAngle;
    private double slot;

    private final List<BlockDisplay> tiles = new ArrayList<>();
    private final List<TextDisplay> tileLabels = new ArrayList<>();
    private final List<Integer> labelSector = new ArrayList<>();
    private final List<BlockDisplay> bulbs = new ArrayList<>();
    private final List<BlockDisplay> marquee = new ArrayList<>();
    private final List<BlockDisplay> pegs = new ArrayList<>();
    private Location anchor;
    private BlockDisplay flapper;
    private double startAngle;
    private double wheelArc;
    private double landingOffset;
    private double wheelAngle;
    /** Pegs gone past the flapper so far. */
    private int pegsPassed;

    /**
     * @param sectors colour of each sector, in the order they sit round the wheel
     * @param winner  index of the sector the wheel has to stop on
     * @param ticks   frames the spin lasts
     */
    public WheelShow(MultiverseGamblingPlugin plugin, ArenaStage stage,
                     List<Material> sectors, int winner, int ticks) {
        super(plugin, stage, ticks);
        this.sectors = List.copyOf(sectors);
        this.winner = sectors.isEmpty() ? 0 : Math.floorMod(winner, sectors.size());
    }

    /**
     * Text written on each sector, such as the multipliers. Empty strings leave a sector
     * blank.
     */
    public WheelShow labels(List<String> labels) {
        this.labels = labels == null ? List.of() : List.copyOf(labels);
        return this;
    }

    /** Name painted on the sign of the booth. */
    public WheelShow title(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    /** Lines of the prize table, hung on a board beside the booth. */
    public WheelShow paytable(List<String> lines) {
        this.paytable = lines == null ? List.of() : List.copyOf(lines);
        return this;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void onStart() {
        if (sectors.isEmpty()) {
            return;
        }
        radius = 4.6f;
        hub = new Vector3f(0, 5.4f, 0);
        anchor = local(hub.x, hub.y, hub.z);

        int[] perSector = WheelMath.allocate(null, sectors.size(), MIN_TILES);
        int total = 0;
        for (int count : perSector) {
            total += count;
        }
        slot = WheelMath.TAU / total;
        tileSector = new int[total];
        sectorAngle = new double[sectors.size()];
        pegAngle = new double[sectors.size()];
        int tile = 0;
        for (int sector = 0; sector < sectors.size(); sector++) {
            sectorAngle[sector] = slot * (tile + (perSector[sector] - 1) / 2.0);
            pegAngle[sector] = slot * (tile - 0.5);
            for (int i = 0; i < perSector[sector]; i++) {
                tileSector[tile++] = sector;
            }
        }
        // A random start, so the wheel never sets off from the same place twice. The
        // landing point inside the winning sector is jittered too: it is only paint.
        startAngle = Rng.next() * WheelMath.TAU;
        int sectorTiles = perSector[winner];
        landingOffset = (Rng.next() - 0.5) * 0.7 * slot * Math.max(0, sectorTiles - 1);
        double turns = Math.max(1.0, Math.min(3.0, duration() / 40.0));
        double target = reference() - sectorAngle[winner] - landingOffset;
        wheelArc = WheelMath.TAU * Math.floor(turns) + WheelMath.normalize(target - startAngle);
        wheelAngle = startAngle;
        pegsPassed = pegsPast(startAngle);

        buildBooth();
        buildStand();
        buildBackPlate();
        float width = (float) (2 * Math.PI * (radius - TILE_DEPTH / 2) / total * 1.06);
        for (int index = 0; index < total; index++) {
            Material colour = sectors.get(tileSector[index]);
            // Odd tiles sit a hair forward, so the slight overlap that closes the gaps
            // between neighbours never flickers.
            float depth = index % 2 == 0 ? 0.0f : 0.006f;
            tiles.add(block(anchor, colour, tilePose(index, startAngle, width, depth, 1.0f)));
        }
        buildLabels();
        buildHubAndRim();
        for (int peg = 0; peg < pegAngle.length; peg++) {
            pegs.add(block(anchor, Material.IRON_BLOCK, pegPose(peg, startAngle)));
        }
        // The flapper hangs from a gold pivot over the top of the wheel.
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, radius + 0.85f, 0.33f),
                new Vector3f(0.4f, 0.4f, 0.3f)));
        flapper = block(anchor, Material.RED_CONCRETE, flapperPose(0.0));

        playSound(Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 0.8f);
    }

    /**
     * Angle (in the wheel plane) the winner has to stop at: the top, under the flapper.
     */
    private double reference() {
        return 0.0;
    }

    /**
     * The carnival booth round the wheel: a striped back wall, an awning sloping down
     * towards the audience with a scalloped edge, two posts, a lit sign and the prize
     * board on the side.
     */
    private void buildBooth() {
        Location ground = local(0, 0, 0);
        int stripes = 8;
        float stripe = 2 * BOOTH / stripes;
        float front = 2.3f;
        float back = -1.0f;
        float drop = 0.9f;
        double slope = Math.atan2(drop, front - back);
        float run = (float) Math.hypot(front - back, drop);
        for (int i = 0; i < stripes; i++) {
            float x = -BOOTH + stripe * (i + 0.5f);
            Material colour = i % 2 == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE;
            block(ground, colour, Props.box(new Vector3f(x, AWNING / 2, -0.9f), new Vector3f(stripe, AWNING, 0.2f)));
            block(ground, colour, Props.box(new Vector3f(x, AWNING - drop / 2, (front + back) / 2), Props.pitch(slope),
                    new Vector3f(stripe, 0.14f, run)));
            // The scalloped edge hangs from the front of the awning.
            block(ground, colour, Props.box(new Vector3f(x, AWNING - drop - 0.35f, front + 0.04f),
                    new Vector3f(stripe * 0.92f, 0.7f, 0.08f)));
        }
        for (int side : new int[]{-1, 1}) {
            block(ground, Material.WHITE_CONCRETE, Props.box(new Vector3f(side * (BOOTH + 0.1f), (AWNING - drop) / 2, front),
                    new Vector3f(0.35f, AWNING - drop, 0.35f)));
            item(local(side * (BOOTH + 0.1f), AWNING - drop + 0.2f, front), Material.GOLD_BLOCK,
                    Props.centred(new Vector3f(), new Quaternionf().rotateY((float) (Math.PI / 4)).rotateX((float) (Math.PI / 4)),
                            new Vector3f(0.5f, 0.5f, 0.5f)));
        }
        // A row of bulbs under the edge of the awning.
        for (int i = 0; i <= 20; i++) {
            float x = -BOOTH + i * (2 * BOOTH / 20);
            marquee.add(block(ground, Material.GLOWSTONE, Props.box(new Vector3f(x, AWNING - drop - 0.78f, front + 0.1f),
                    new Vector3f(0.2f, 0.2f, 0.12f))));
        }
        if (!title.isBlank()) {
            block(ground, Material.BLACK_CONCRETE, Props.box(new Vector3f(0, AWNING + 0.55f, 0.6f),
                    new Vector3f(2 * BOOTH - 1.0f, 1.3f, 0.15f)));
            block(ground, Material.GOLD_BLOCK, Props.box(new Vector3f(0, AWNING + 0.55f, 0.55f),
                    new Vector3f(2 * BOOTH - 0.8f, 1.5f, 0.1f)));
            text(local(0, AWNING + 0.2f, 0.72f), Text.c("&6&l★ &e&l" + title + " &6&l★"), Props.scaled(2.0f),
                    Display.Billboard.FIXED);
        }
        if (!paytable.isEmpty()) {
            block(ground, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(-BOOTH - 1.8f, 1.4f, 0.6f),
                    new Vector3f(0.25f, 2.8f, 0.25f)));
            TextDisplay board = text(local(-BOOTH - 1.8f, 2.8f, 0.75f), Text.c(String.join("\n", paytable)),
                    Props.scaled(1.3f), Display.Billboard.FIXED);
            board.setBackgroundColor(Props.argb(200, 30, 12, 8));
            board.setAlignment(TextDisplay.TextAlignment.LEFT);
        }
    }

    private void buildStand() {
        Location ground = local(0, 0, 0);
        // Two legs spread like an easel and a heavy base.
        block(ground, Material.POLISHED_BLACKSTONE_BRICKS,
                Props.box(new Vector3f(0, 0.25f, -0.4f), new Vector3f(5.0f, 0.5f, 2.0f)));
        for (int side : new int[]{-1, 1}) {
            Quaternionf lean = Props.roll(side * Math.toRadians(14));
            block(ground, Material.POLISHED_DEEPSLATE, Props.box(
                    new Vector3f(side * 1.1f, hub.y / 2, -0.45f), lean, new Vector3f(0.45f, hub.y + 0.6f, 0.45f)));
        }
        block(ground, Material.GOLD_BLOCK,
                Props.box(new Vector3f(0, 0.55f, 0.5f), new Vector3f(5.0f, 0.1f, 0.1f)));
    }

    private void buildBackPlate() {
        disc(Material.BLACK_CONCRETE, radius + 0.55f, -TILE_THICKNESS - 0.04f, 0.08f);
    }

    /**
     * A round plate in the plane of the wheel, made of wedges from the axle out: squares
     * turned round each other would make a star and cover the prizes.
     */
    private void disc(Material material, float reach, float lift, float thickness) {
        int pieces = 24;
        float width = (float) (WheelMath.TAU * reach / pieces * 1.04);
        for (int i = 0; i < pieces; i++) {
            double angle = WheelMath.TAU * i / pieces;
            block(anchor, material, Props.box(onWheel(angle, reach / 2, lift), Props.roll(angle),
                    new Vector3f(width, reach, thickness)));
        }
    }

    private void buildHubAndRim() {
        float inner = radius - TILE_DEPTH;
        float side = 2 * inner;
        disc(Material.POLISHED_DEEPSLATE, inner, 0.05f, 0.12f);
        disc(Material.GOLD_BLOCK, inner * 0.35f, 0.1f, 0.06f);
        // Gold cap and spokes on the hub.
        for (int i = 0; i < 4; i++) {
            block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, 0, 0.13f), Props.roll(i * Math.PI / 4),
                    new Vector3f(0.18f, side * 0.92f, 0.05f)));
        }
        item(anchor, Material.GOLD_BLOCK,
                Props.centred(new Vector3f(0, 0, 0.3f), Props.none(), new Vector3f(0.9f, 0.9f, 0.3f)));
        for (int i = 0; i < BULBS; i++) {
            double angle = WheelMath.TAU * i / BULBS;
            Vector3f centre = onWheel(angle, radius + 0.32f, 0.0f);
            bulbs.add(block(anchor, i % 2 == 0 ? Material.GLOWSTONE : Material.GOLD_BLOCK,
                    Props.box(centre, Props.roll(angle), new Vector3f(0.34f, 0.34f, 0.34f))));
        }
    }

    private void buildLabels() {
        if (labels.isEmpty()) {
            return;
        }
        int total = tileSector.length;
        for (int sector = 0; sector < sectors.size() && sector < labels.size(); sector++) {
            String label = labels.get(sector);
            if (label == null || label.isBlank()) {
                continue;
            }
            int tilesOfSector = 0;
            for (int owner : tileSector) {
                if (owner == sector) {
                    tilesOfSector++;
                }
            }
            double arc = (radius - TILE_DEPTH / 2) * WheelMath.TAU * tilesOfSector / total;
            int visible = Text.strip(label).length();
            float scale = (float) Math.max(0.35, Math.min(1.3, arc * 0.8 / Math.max(1, visible * 0.16)));
            TextDisplay text = text(anchor, Text.c(textColour(sectors.get(sector)) + "&l" + label),
                    labelPose(sectorAngle[sector] + startAngle, scale), Display.Billboard.FIXED);
            tileLabels.add(text);
            labelSector.add(sector);
        }
    }

    // ----------------------------------------------------------------- frames

    @Override
    protected void onFrame(int elapsed, int duration) {
        if (tiles.isEmpty()) {
            return;
        }
        double spinFrames = duration * SPIN_SHARE;
        double spin = Math.min(1, elapsed / spinFrames);
        wheelAngle = startAngle + wheelArc * WheelMath.easeQuad(spin);
        placeTiles(wheelAngle, 1);
        int past = pegsPast(wheelAngle);
        if (past != pegsPassed) {
            pegsPassed = past;
            clack(1 - spin);
        }
    }

    /**
     * How many pegs have gone under the flapper since the wheel was at angle zero: the
     * flapper clacks every time this number changes.
     */
    private int pegsPast(double angle) {
        int past = 0;
        for (double peg : pegAngle) {
            past += (int) Math.floor((angle + peg) / WheelMath.TAU);
        }
        return past;
    }

    /**
     * A peg knocks the flapper aside: the faster the wheel, the further it flies, and it
     * springs back for the next one.
     */
    private void clack(double speed) {
        double kick = -Math.toRadians(14 + 26 * Math.min(1, speed * 1.5));
        Props.animate(flapper, flapperPose(kick), 1);
        later(2, () -> Props.animate(flapper, flapperPose(0.0), 3));
        playSound(Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.6f, (float) (0.9 + 0.8 * speed));
        playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, (float) (0.8 + 0.9 * (1 - speed)));
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
        for (int i = 0; i < marquee.size(); i++) {
            boolean lit = settled() ? phase % 2 == 1 : (i + phase) % 3 == 0;
            marquee.get(i).setBlock((lit ? Material.GLOWSTONE : Material.ORANGE_TERRACOTTA).createBlockData());
        }
    }

    @Override
    protected void onSettle() {
        if (tiles.isEmpty()) {
            return;
        }
        double finalAngle = startAngle + wheelArc;
        wheelAngle = finalAngle;
        placeTiles(finalAngle, 2);
        Props.animate(flapper, flapperPose(0.0), 2);
        // Everything but the winning sector dims, and the winner glows and pops out.
        Material colour = sectors.get(winner);
        Color glow = Props.colourOf(colour);
        int rgb = glow.asRGB();
        if (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF) < 180) {
            // A dark pocket would get a dark outline nobody can see.
            glow = Color.WHITE;
        }
        float width = (float) (2 * Math.PI * (radius - TILE_DEPTH / 2) / tileSector.length * 1.06);
        for (int index = 0; index < tiles.size(); index++) {
            BlockDisplay tile = tiles.get(index);
            if (!tile.isValid()) {
                continue;
            }
            if (tileSector[index] == winner) {
                tile.setGlowColorOverride(glow);
                tile.setGlowing(true);
                Props.animate(tile, tilePose(index, finalAngle, width, 0.25f, 1.6f), 6);
            } else {
                tile.setBrightness(new Display.Brightness(4, 4));
            }
        }
        for (int i = 0; i < tileLabels.size(); i++) {
            if (labelSector.get(i) != winner && tileLabels.get(i).isValid()) {
                tileLabels.get(i).setTextOpacity((byte) 90);
            }
        }
        Vector3f spot = new Vector3f(hub).add(onWheel(finalAngle + sectorAngle[winner], radius, 0.4f));
        String label = winner < labels.size() ? labels.get(winner) : "";
        if (label != null && !label.isBlank()) {
            Component banner = Text.c(textColour(colour) + "&l" + label);
            TextDisplay title = text(local(0, hub.y + 1.6f, 1.2f), banner,
                    Props.centred(new Vector3f(), 0.1f), Display.Billboard.VERTICAL);
            title.setBackgroundColor(Props.argb(170, 0, 0, 0));
            later(1, () -> Props.animate(title, Props.centred(new Vector3f(), 2.6f), 8));
        }
        celebrateAt(spot);
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.4f);
    }

    @Override
    protected int extraLinger() {
        return 20;
    }

    private void celebrateAt(Vector3f spot) {
        // The spot is in show coordinates relative to the ground of the stage.
        stage().world().spawnParticle(Particle.TOTEM_OF_UNDYING, local(spot.x, spot.y, spot.z), 50, 0.5, 0.5, 0.5, 0.4);
        stage().world().spawnParticle(Particle.FIREWORK, local(spot.x, spot.y, spot.z), 30, 0.4, 0.4, 0.4, 0.1);
        playSound(Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.8f, 1.2f);
    }

    // ----------------------------------------------------------------- poses

    private void placeTiles(double angle, int ticks) {
        float width = (float) (2 * Math.PI * (radius - TILE_DEPTH / 2) / tileSector.length * 1.06);
        for (int index = 0; index < tiles.size(); index++) {
            float depth = index % 2 == 0 ? 0.0f : 0.006f;
            Props.animate(tiles.get(index), tilePose(index, angle, width, depth, 1.0f), ticks);
        }
        for (int i = 0; i < tileLabels.size(); i++) {
            int sector = labelSector.get(i);
            float scale = tileLabels.get(i).getTransformation().getScale().x;
            Props.animate(tileLabels.get(i), labelPose(angle + sectorAngle[sector], scale), ticks);
        }
        for (int peg = 0; peg < pegs.size(); peg++) {
            Props.animate(pegs.get(peg), pegPose(peg, angle), ticks);
        }
    }

    /**
     * Pose of tile {@code index} with the wheel turned by {@code angle}.
     */
    private Transformation tilePose(int index, double angle, float width, float depth, float thickness) {
        double at = angle + slot * index;
        Vector3f centre = onWheel(at, radius - TILE_DEPTH / 2, depth);
        return Props.box(centre, Props.roll(at),
                new Vector3f(width, TILE_DEPTH, TILE_THICKNESS * thickness));
    }

    /** A metal peg on the rim between two sectors, sticking out towards the audience. */
    private Transformation pegPose(int peg, double angle) {
        double at = angle + pegAngle[peg];
        return Props.box(onWheel(at, radius - 0.12f, TILE_THICKNESS / 2 + 0.17f), Props.roll(at),
                new Vector3f(0.14f, 0.14f, 0.34f));
    }

    /** The flapper hanging from its pivot over the top, swung aside by {@code kick}. */
    private Transformation flapperPose(double kick) {
        Vector3f pivot = new Vector3f(0, radius + 0.8f, 0.33f);
        Quaternionf rotation = Props.roll(kick);
        Vector3f centre = rotation.transform(new Vector3f(0, -0.5f, 0)).add(pivot);
        return Props.box(centre, rotation, new Vector3f(0.24f, 1.0f, 0.06f));
    }

    private Transformation labelPose(double angle, float scale) {
        Quaternionf rotation = Props.roll(angle);
        // Text grows upwards from its origin: start a little inwards so it is centred on
        // the tile.
        float along = radius - TILE_DEPTH / 2 - 0.13f * scale;
        Vector3f centre = onWheel(angle, along, TILE_THICKNESS / 2 + 0.03f);
        return Props.centred(centre, rotation, new Vector3f(scale, scale, scale));
    }

    /**
     * Point of the wheel at that angle (anticlockwise from the top, as the audience sees
     * it), distance from the axle and height over the face, relative to the hub.
     */
    private Vector3f onWheel(double angle, float distance, float lift) {
        return new Vector3f((float) (-Math.sin(angle) * distance), (float) (Math.cos(angle) * distance), lift);
    }

    /**
     * Colour code that reads on a tile of that colour.
     */
    static String textColour(Material tile) {
        return switch (tile) {
            case WHITE_CONCRETE, YELLOW_CONCRETE, LIME_CONCRETE, GOLD_BLOCK, LIGHT_GRAY_CONCRETE,
                 LIGHT_BLUE_CONCRETE, PINK_CONCRETE -> "&0";
            default -> "&f";
        };
    }
}
