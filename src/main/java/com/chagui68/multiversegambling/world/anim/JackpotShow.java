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
 * The jackpot as a jar of gold and a strip of faces.
 *
 * <p>First every stake is poured into a big glass jar: a few nuggets fly in from where the
 * player stands and the jar fills with a band in that player's colour, as tall as their
 * share of the pot, so the whole room sees who holds what chance. Then a strip with the
 * head of every player, each cell as wide as their share, races under a gold marker and
 * creeps to a stop on the winner ({@link JackpotStrip}). The jar shatters and its gold
 * flies to the winner. The winner is drawn by the game before the show starts; the show
 * only paints it.</p>
 */
public final class JackpotShow extends ArenaShow {

    /** One player in the draw. */
    public record Entry(String name, ItemStack head, Material colour, double stake) {
    }

    /** Share of the show spent pouring the stakes into the jar. */
    private static final double POUR = 0.3;
    /** Share of the show after which the strip has stopped. */
    private static final double STOP = 0.92;
    private static final float JAR = 1.3f;
    private static final float JAR_BOTTOM = 0.72f;
    private static final float JAR_HEIGHT = 3.2f;
    /** Height of the gold when the whole pot is in. */
    private static final float FULL = 2.9f;
    private static final float STRIP_Y = 7.5f;
    private static final float STRIP_Z = -0.55f;

    private final List<Entry> entries;
    private final int winner;
    private String potText = "";
    private JackpotStrip strip;

    private Location anchor;
    private final List<BlockDisplay> panes = new ArrayList<>();
    private final List<BlockDisplay[]> bands = new ArrayList<>();
    private final List<float[]> bandSpan = new ArrayList<>();
    private final List<BlockDisplay> backs = new ArrayList<>();
    private final List<ItemDisplay> heads = new ArrayList<>();
    private final List<TextDisplay> names = new ArrayList<>();
    private final List<BlockDisplay> bulbs = new ArrayList<>();
    /** Where each cell was drawn last frame, to spot the one that wraps round. */
    private double[] lastXs;
    private int lastUnder = -2;
    private boolean stopped;

    /**
     * @param entries the players in the draw, in the order they stand round the arena
     * @param winner  index of the entry that won
     * @param ticks   frames the show lasts, pouring included
     */
    public JackpotShow(MultiverseGamblingPlugin plugin, ArenaStage stage, List<Entry> entries, int winner,
                       int ticks) {
        super(plugin, stage, ticks);
        this.entries = List.copyOf(entries);
        this.winner = entries.isEmpty() ? 0 : Math.floorMod(winner, entries.size());
    }

    /** Words over the jar: the whole pot. */
    public JackpotShow pot(String text) {
        this.potText = text == null ? "" : text;
        return this;
    }

    // ------------------------------------------------------------------ setup

    @Override
    protected void onStart() {
        if (entries.isEmpty()) {
            return;
        }
        anchor = local(0, 0, 0);
        double[] stakes = new double[entries.size()];
        for (int i = 0; i < stakes.length; i++) {
            stakes[i] = entries.get(i).stake();
        }
        strip = new JackpotStrip(stakes, winner, Rng.next() * 2 - 1, 2);
        buildJar(stakes);
        buildStrip();
        playSound(Sound.BLOCK_CHAIN_PLACE, 0.8f, 0.8f);
    }

    private void buildJar(double[] stakes) {
        // A round pedestal with a gold trim.
        for (int i = 0; i < 2; i++) {
            block(anchor, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(new Vector3f(0, 0.3f, 0),
                    Props.yaw(i * Math.PI / 4), new Vector3f(3.6f, 0.6f, 3.6f)));
            block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, 0.64f, 0),
                    Props.yaw(i * Math.PI / 4), new Vector3f(3.0f, 0.08f, 3.0f)));
        }
        // Eight panes of glass make the jar, a gold lid and knob close it.
        float pane = (float) (2 * JAR * Math.tan(Math.PI / 8)) + 0.02f;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            panes.add(block(anchor, Material.GLASS, Props.box(
                    new Vector3f((float) (Math.sin(angle) * JAR), JAR_BOTTOM + JAR_HEIGHT / 2, (float) (Math.cos(angle) * JAR)),
                    Props.yaw(angle), new Vector3f(pane, JAR_HEIGHT, 0.06f))));
        }
        for (int i = 0; i < 2; i++) {
            panes.add(block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, JAR_BOTTOM + JAR_HEIGHT + 0.1f, 0),
                    Props.yaw(i * Math.PI / 4 + Math.PI / 8), new Vector3f(2.75f, 0.2f, 2.75f))));
        }
        panes.add(block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, JAR_BOTTOM + JAR_HEIGHT + 0.35f, 0),
                Props.yaw(Math.PI / 4), new Vector3f(0.45f, 0.3f, 0.45f))));
        // One band per player, empty until their stake is poured in.
        double total = 0;
        for (double stake : stakes) {
            total += stake;
        }
        float floor = JAR_BOTTOM + 0.03f;
        for (int i = 0; i < entries.size(); i++) {
            float height = (float) (total <= 0 ? FULL / entries.size() : FULL * stakes[i] / total);
            bandSpan.add(new float[]{floor, height});
            BlockDisplay[] band = new BlockDisplay[2];
            for (int k = 0; k < 2; k++) {
                band[k] = block(anchor, entries.get(i).colour(), bandPose(floor, 0.0f, k));
            }
            bands.add(band);
            floor += height;
        }
        if (!potText.isBlank()) {
            TextDisplay pot = text(local(0, JAR_BOTTOM + JAR_HEIGHT + 0.8, 0), Text.c("&6&l" + potText),
                    Props.scaled(1.5f), Display.Billboard.VERTICAL);
            pot.setBackgroundColor(Props.argb(140, 0, 0, 0));
        }
    }

    private Transformation bandPose(float bottom, float height, int turn) {
        float side = 2 * JAR * 0.9f;
        return Props.box(new Vector3f(0, bottom + height / 2, 0), Props.yaw(turn * Math.PI / 4),
                new Vector3f(side, Math.max(0.001f, height), side));
    }

    private void buildStrip() {
        float half = JackpotStrip.WINDOW / 2;
        // A dark board in a gold frame, with the marker on top.
        block(anchor, Material.BLACK_CONCRETE, Props.box(new Vector3f(0, STRIP_Y, STRIP_Z - 0.2f),
                new Vector3f(JackpotStrip.WINDOW + 0.6f, 2.3f, 0.15f)));
        for (int side : new int[]{-1, 1}) {
            block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, STRIP_Y + side * 1.2f, STRIP_Z - 0.1f),
                    new Vector3f(JackpotStrip.WINDOW + 0.9f, 0.14f, 0.3f)));
            block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(side * (half + 0.38f), STRIP_Y, STRIP_Z - 0.1f),
                    new Vector3f(0.16f, 2.5f, 0.3f)));
            // Two legs carry the board over the jar.
            block(anchor, Material.POLISHED_DEEPSLATE, Props.box(new Vector3f(side * (half + 0.38f), (STRIP_Y - 1.2f) / 2,
                    STRIP_Z - 0.2f), new Vector3f(0.3f, STRIP_Y - 1.2f, 0.3f)));
        }
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, STRIP_Y + 1.5f, STRIP_Z + 0.05f),
                Props.roll(Math.PI / 4), new Vector3f(0.42f, 0.42f, 0.2f)));
        block(anchor, Material.GOLD_BLOCK, Props.box(new Vector3f(0, STRIP_Y, STRIP_Z + 0.16f),
                new Vector3f(0.05f, 2.1f, 0.02f)));
        for (int i = 0; i <= 24; i++) {
            float x = -half - 0.2f + i * (JackpotStrip.WINDOW + 0.4f) / 24;
            bulbs.add(block(anchor, Material.GLOWSTONE, Props.box(new Vector3f(x, STRIP_Y + 1.2f, STRIP_Z + 0.08f),
                    new Vector3f(0.16f, 0.16f, 0.1f))));
        }
        double scroll = strip.scroll(0);
        lastXs = new double[strip.cells()];
        for (int cell = 0; cell < strip.cells(); cell++) {
            Entry entry = entries.get(strip.ownerOf(cell));
            double x = strip.x(cell, scroll);
            lastXs[cell] = x;
            float fade = fade(x);
            backs.add(block(anchor, entry.colour(), backPose(cell, x, fade, 1.0f)));
            heads.add(item(anchor, entry.head(), headPose(cell, x, fade, 1.0f)));
            TextDisplay name = text(anchor, Text.c("&f&l" + entry.name()), namePose(cell, x, fade), Display.Billboard.FIXED);
            name.setShadowed(true);
            names.add(name);
        }
    }

    // ----------------------------------------------------------------- frames

    @Override
    protected void onFrame(int elapsed, int duration) {
        if (strip == null) {
            return;
        }
        double progress = progress(elapsed);
        pour(elapsed, duration);
        if (progress < POUR) {
            return;
        }
        double spin = Math.min(1, (progress - POUR) / (STOP - POUR));
        placeStrip(strip.scroll(spin), 1);
        int under = strip.underMarker(strip.scroll(spin));
        if (under != lastUnder && spin < 1) {
            lastUnder = under;
            playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, (float) (0.8 + 0.9 * spin));
        }
        if (spin >= 1 && !stopped) {
            stopped = true;
            playSound(Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
        }
    }

    /** Pours the stakes one after the other: nuggets fly in, then the band rises. */
    private void pour(int elapsed, int duration) {
        int window = (int) (duration * POUR);
        int gap = Math.max(1, (window - 14) / Math.max(1, entries.size()));
        if (elapsed % gap != 0 || elapsed / gap >= entries.size() || elapsed > window) {
            return;
        }
        int index = elapsed / gap;
        double angle = ArenaStage.audienceAngle(index, entries.size());
        Vector3f from = new Vector3f((float) (Math.sin(angle) * 9), 1.6f, (float) (Math.cos(angle) * 9));
        Vector3f top = new Vector3f(0, JAR_BOTTOM + JAR_HEIGHT + 0.4f, 0);
        for (int n = 0; n < 3; n++) {
            Vector3f start = new Vector3f(from).add((float) (Rng.next() - 0.5), (float) Rng.next() * 0.4f,
                    (float) (Rng.next() - 0.5));
            ItemDisplay nugget = item(anchor, Material.GOLD_NUGGET, Props.centred(start, 0.5f));
            Vector3f arc = new Vector3f(start).lerp(top, 0.5f).add(0, 2.5f, 0);
            later(1 + n * 2, () -> Props.animate(nugget, Props.centred(arc, 0.5f), 6));
            later(7 + n * 2, () -> Props.animate(nugget, Props.centred(top, 0.4f), 6));
            later(13 + n * 2, () -> discard(nugget));
        }
        later(14, () -> {
            float[] span = bandSpan.get(index);
            for (int k = 0; k < 2; k++) {
                Props.animate(bands.get(index)[k], bandPose(span[0], span[1], k), 8);
            }
            playSound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f + index * 0.05f);
        });
        playSound(Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.2f);
    }

    private void placeStrip(double scroll, int ticks) {
        for (int cell = 0; cell < backs.size(); cell++) {
            double x = strip.x(cell, scroll);
            // A cell that wraps round from one end of the loop to the other jumps.
            int glide = Math.abs(x - lastXs[cell]) > strip.length() / 2 ? 0 : ticks;
            float fade = fade(x);
            Props.animate(backs.get(cell), backPose(cell, x, fade, 1.0f), glide);
            Props.animate(heads.get(cell), headPose(cell, x, fade, 1.0f), glide);
            Props.animate(names.get(cell), namePose(cell, x, fade), glide);
            lastXs[cell] = x;
        }
    }

    /** Cells shrink away near the ends of the window, as if the strip turned round a drum. */
    private static float fade(double x) {
        double edge = JackpotStrip.WINDOW / 2 - Math.abs(x);
        return (float) Math.max(0, Math.min(1, edge / 0.9));
    }

    private Transformation backPose(int cell, double x, float fade, float grow) {
        float width = (strip.widthOf(cell) - 0.06f) * fade;
        return Props.box(new Vector3f((float) x, STRIP_Y, STRIP_Z), new Vector3f(Math.max(0.001f, width),
                1.9f * grow, 0.08f));
    }

    private Transformation headPose(int cell, double x, float fade, float grow) {
        float size = Math.min(0.95f, strip.widthOf(cell) * 0.75f) * fade * grow;
        return Props.centred(new Vector3f((float) x, STRIP_Y + 0.25f, STRIP_Z + 0.3f), Props.none(),
                new Vector3f(size, size, size));
    }

    private Transformation namePose(int cell, double x, float fade) {
        String name = Text.strip(entries.get(strip.ownerOf(cell)).name());
        float scale = (float) Math.min(0.45, (strip.widthOf(cell) - 0.1) / Math.max(1, name.length() * 0.16)) * fade;
        return Props.centred(new Vector3f((float) x, STRIP_Y - 0.85f, STRIP_Z + 0.06f), Props.none(),
                new Vector3f(scale, scale, scale));
    }

    @Override
    protected void onAnimate(int age) {
        int period = active() ? 3 : 5;
        if (age % period != 0 || bulbs.isEmpty()) {
            return;
        }
        int phase = age / period;
        for (int i = 0; i < bulbs.size(); i++) {
            boolean lit = settled() ? phase % 2 == 0 : (i + phase) % 3 == 0;
            bulbs.get(i).setBlock((lit ? Material.GLOWSTONE : Material.GOLD_BLOCK).createBlockData());
        }
    }

    // ----------------------------------------------------------------- result

    @Override
    protected void onSettle() {
        if (strip == null) {
            return;
        }
        // Whatever was still on its way lands now.
        for (int i = 0; i < bands.size(); i++) {
            float[] span = bandSpan.get(i);
            for (int k = 0; k < 2; k++) {
                Props.animate(bands.get(i)[k], bandPose(span[0], span[1], k), 2);
            }
        }
        double scroll = strip.scroll(1);
        placeStrip(scroll, 2);
        Color glow = Props.colourOf(entries.get(winner).colour());
        for (int cell = 0; cell < backs.size(); cell++) {
            if (strip.ownerOf(cell) != winner) {
                backs.get(cell).setBrightness(new Display.Brightness(5, 5));
                continue;
            }
            double x = strip.x(cell, scroll);
            if (Math.abs(x) > strip.widthOf(cell)) {
                continue;
            }
            backs.get(cell).setGlowColorOverride(glow);
            backs.get(cell).setGlowing(true);
            Props.animate(backs.get(cell), backPose(cell, x, 1.0f, 1.15f), 6);
            Props.animate(heads.get(cell), headPose(cell, x, 1.0f, 1.3f), 6);
        }
        for (BlockDisplay piece : bands.get(winner)) {
            piece.setGlowColorOverride(glow);
            piece.setGlowing(true);
        }
        later(12, this::shatter);
        Entry entry = entries.get(winner);
        TextDisplay banner = text(local(0, STRIP_Y + 2.3, STRIP_Z), Text.c("&6&l★ &f&l" + entry.name() + " &6&l★"),
                Props.scaled(0.1f), Display.Billboard.VERTICAL);
        banner.setBackgroundColor(Props.argb(160, 0, 0, 0));
        later(1, () -> Props.animate(banner, Props.scaled(2.2f), 8));
        playSound(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.3f);
    }

    /** The jar bursts and its gold flies to the winner. */
    private void shatter() {
        for (BlockDisplay pane : panes) {
            Transformation now = pane.getTransformation();
            Vector3f out = new Vector3f(now.getTranslation()).mul(1, 0, 1);
            if (out.lengthSquared() < 0.01f) {
                out.set(0, 0, 1);
            }
            out.normalize().mul(2.5f + (float) Rng.next() * 1.5f).add(0, 1.0f + (float) Rng.next(), 0);
            Quaternionf tumble = new Quaternionf().rotateXYZ((float) Rng.next() * 3, (float) Rng.next() * 3,
                    (float) Rng.next() * 3);
            Props.animate(pane, new Transformation(new Vector3f(now.getTranslation()).add(out), tumble,
                    new Vector3f(now.getScale()).mul(0.3f), new Quaternionf()), 12);
            later(14, () -> discard(pane));
        }
        double angle = ArenaStage.audienceAngle(winner, entries.size());
        Vector3f to = new Vector3f((float) (Math.sin(angle) * 9), 2.0f, (float) (Math.cos(angle) * 9));
        Vector3f from = new Vector3f(0, JAR_BOTTOM + 1.5f, 0);
        for (int n = 0; n < 14; n++) {
            Vector3f start = new Vector3f(from).add((float) (Rng.next() - 0.5) * 2, (float) Rng.next() * 1.5f,
                    (float) (Rng.next() - 0.5) * 2);
            ItemDisplay nugget = item(anchor, Material.GOLD_NUGGET, Props.centred(start, 0.6f));
            Vector3f arc = new Vector3f(start).lerp(to, 0.5f).add(0, 3.0f + (float) Rng.next(), 0);
            later(1 + n, () -> Props.animate(nugget, Props.centred(arc, 0.6f), 8));
            later(9 + n, () -> Props.animate(nugget, Props.centred(to, 0.3f), 8));
            later(18 + n, () -> discard(nugget));
        }
        particles(Particle.TOTEM_OF_UNDYING, 0, JAR_BOTTOM + 2, 0, 60, 0.8, 0.45);
        particles(Particle.FIREWORK, 0, JAR_BOTTOM + 2, 0, 30, 0.6, 0.12);
        playSound(Sound.BLOCK_GLASS_BREAK, 1.0f, 0.9f);
        playSound(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
    }

    @Override
    protected int extraLinger() {
        return 40;
    }
}
