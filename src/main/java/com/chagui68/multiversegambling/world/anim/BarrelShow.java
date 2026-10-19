package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * A giant revolver for the russian roulette: the cylinder faces the audience with its
 * chambers in a ring, the loaded ones showing a brass bullet, so everyone can see the
 * odds while they wait for the trigger.
 *
 * <p>Every pull spins the cylinder, slows it down and stops it with one chamber under
 * the hammer at the top; after a heartbeat the hammer falls and the revolver either
 * fires, with a flash at the muzzle, or clicks. Whether it fires was already decided by
 * the provably fair generator; the show picks a chamber that agrees with it.</p>
 *
 * <p>The heads of the players hang in front of the muzzle: whoever has the revolver
 * stares straight down the barrel and the rest wait in a row underneath it. A shot knocks
 * the head at the muzzle away; a click only makes it flinch.</p>
 */
public final class BarrelShow extends ArenaShow {

    /** Ticks the cylinder spins on every pull. */
    private static final int SPIN_TICKS = 20;
    /** Ticks between the cylinder stopping and the hammer falling. */
    private static final int SUSPENSE_TICKS = 6;
    /** Ticks the result of a pull stays in place before the next head moves in. */
    private static final int LINEUP_DELAY_TICKS = 14;
    /** Turn of the head at the muzzle: mostly at the barrel, a little towards the audience. */
    private static final double FACE_THE_BARREL = Math.toRadians(-60);
    private static final float MUZZLE_Z = -0.45f;

    private final int chambers;
    private int bullets;
    private final float radius;
    private final Vector3f axle = new Vector3f(0, 4.2f, 0);

    private final List<ItemDisplay> body = new ArrayList<>();
    private final List<ItemDisplay> holes = new ArrayList<>();
    private final List<ItemDisplay> rounds = new ArrayList<>();
    private final Map<String, ItemDisplay> heads = new LinkedHashMap<>();
    private Location anchor;
    private Location ground;
    private BlockDisplay hammer;
    private TextDisplay odds;
    private TextDisplay targetLabel;
    /** Name of the player whose head is at the muzzle. */
    private String target;
    /** Head standing at the muzzle when the trigger was pulled. */
    private String victim;
    /** A lineup sent while the cylinder was still turning, shown once the shot is over. */
    private List<ItemStack> queuedHeads;
    private List<String> queuedNames;
    /** Bullets added while the cylinder was turning, loaded once the shot is over. */
    private int queuedBullets;
    private int flushAt = -1;
    private double angle;
    private double spinFrom;
    private double spinArc;
    private int spinStart = -1;
    private int age;
    private boolean pendingFire;
    private boolean pending;

    public BarrelShow(MultiverseGamblingPlugin plugin, ArenaStage stage, int chambers, int bullets, int ticks) {
        super(plugin, stage, ticks);
        this.chambers = Math.max(2, chambers);
        this.bullets = Math.max(0, Math.min(bullets, this.chambers - 1));
        this.radius = (float) Math.max(1.5, this.chambers * 0.27);
    }

    @Override
    protected void onStart() {
        anchor = local(axle.x, axle.y, axle.z);
        ground = local(0, 0, 0);
        float frame = radius + 0.95f;
        // The frame of the gun round the cylinder, the barrel to the right, the grip below.
        block(ground, Material.GRAY_CONCRETE, Props.box(new Vector3f(0, axle.y, -0.75f),
                new Vector3f(frame * 2, frame * 2, 0.5f)));
        block(ground, Material.IRON_BLOCK, Props.box(new Vector3f(frame + 2.0f, axle.y + radius, -0.45f),
                new Vector3f(4.2f, 0.7f, 0.7f)));
        block(ground, Material.BLACK_CONCRETE, Props.box(new Vector3f(frame + 4.12f, axle.y + radius, -0.45f),
                new Vector3f(0.06f, 0.42f, 0.42f)));
        block(ground, Material.DARK_OAK_PLANKS, Props.box(new Vector3f(-frame - 0.6f, axle.y - frame - 0.6f, -0.6f),
                Props.roll(Math.toRadians(-28)), new Vector3f(1.3f, 3.2f, 0.7f)));
        block(ground, Material.POLISHED_BLACKSTONE_BRICKS, Props.box(new Vector3f(0, 0.4f, -0.4f),
                new Vector3f(4.5f, 0.8f, 2.6f)));
        block(ground, Material.POLISHED_DEEPSLATE, Props.box(new Vector3f(0, (axle.y - frame) / 2 + 0.4f, -0.7f),
                new Vector3f(0.8f, axle.y - frame - 0.8f, 0.6f)));

        for (int plate = 0; plate < 3; plate++) {
            body.add(item(anchor, Material.POLISHED_DEEPSLATE, bodyPose(0.0, plate)));
        }
        for (int chamber = 0; chamber < chambers; chamber++) {
            holes.add(item(anchor, Material.BLACK_CONCRETE, chamberPose(0.0, chamber, 0.85f, 0.1f, 0.62f)));
            ItemDisplay round = loaded(chamber)
                    ? item(anchor, Material.GOLD_BLOCK, chamberPose(0.0, chamber, 0.55f, 0.22f, 0.7f))
                    : null;
            rounds.add(round);
        }
        item(anchor, Material.IRON_BLOCK, Props.centred(new Vector3f(0, 0, 0.66f), Props.none(),
                new Vector3f(0.5f, 0.5f, 0.2f)));
        hammer = block(anchor, Material.REDSTONE_BLOCK, hammerPose(0.0f));
        odds = text(local(0, axle.y + frame + 1.3f, 0), oddsText(), Props.scaled(1.6f), Display.Billboard.VERTICAL);
        targetLabel = text(local(muzzleX() + 1.1, muzzleY() + 1.15, MUZZLE_Z), Text.c(""), Props.scaled(0.9f),
                Display.Billboard.VERTICAL);
        angle = Rng.next() * WheelMath.TAU;
        place(angle, 0);
    }

    private net.kyori.adventure.text.Component oddsText() {
        return Text.c("&c&l" + bullets + "&7/&f" + chambers + "  &8│  &e" + Text.percent((double) bullets / chambers));
    }

    // ------------------------------------------------------------------ heads

    private float muzzleX() {
        return radius + 0.95f + 4.3f;
    }

    private float muzzleY() {
        return axle.y + radius;
    }

    /** True while a shot is still being shown: the cylinder turning or the result on screen. */
    private boolean busy() {
        return spinStart >= 0 || pending || flushAt >= 0;
    }

    /**
     * Who is playing, in turn order: the first head goes in front of the muzzle and the
     * others wait in a row under the barrel. Sent while a shot is still being shown, it
     * waits until the shot is over, so the head being shot at stays put.
     *
     * @param stacks the head of each player
     * @param names  the name of each player, in the same order
     */
    public void lineup(List<ItemStack> stacks, List<String> names) {
        if (busy()) {
            queuedHeads = new ArrayList<>(stacks);
            queuedNames = new ArrayList<>(names);
            return;
        }
        applyLineup(stacks, names);
    }

    /**
     * Loads one more chamber. Sent while a shot is still being shown, it is loaded once
     * the shot is over, so the chamber under the hammer never changes under the player.
     */
    public void addBullet() {
        if (busy()) {
            queuedBullets++;
            return;
        }
        loadOne();
    }

    private void loadOne() {
        if (holes.isEmpty() || bullets >= chambers - 1) {
            return;
        }
        int chamber = bullets;
        bullets++;
        ItemDisplay round = item(anchor, Material.GOLD_BLOCK, chamberPose(angle, chamber, 0.55f, 0.22f, 0.7f));
        round.setGlowColorOverride(org.bukkit.Color.YELLOW);
        round.setGlowing(true);
        later(40, () -> {
            if (round.isValid()) {
                round.setGlowing(false);
            }
        });
        rounds.set(chamber, round);
        if (odds != null && odds.isValid()) {
            odds.text(oddsText());
        }
        particles(Particle.CRIT, 0, axle.y, 0.9, 25, radius * 0.6, 0.15);
        playSound(Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 0.8f);
    }

    /** Shows whatever was held back while a shot was being shown. */
    private void flush() {
        flushAt = -1;
        while (queuedBullets > 0) {
            queuedBullets--;
            loadOne();
        }
        if (queuedNames != null) {
            List<ItemStack> stacks = queuedHeads;
            List<String> names = queuedNames;
            queuedHeads = null;
            queuedNames = null;
            applyLineup(stacks, names);
        }
    }

    private void applyLineup(List<ItemStack> stacks, List<String> names) {
        for (Map.Entry<String, ItemDisplay> entry : new ArrayList<>(heads.entrySet())) {
            if (!names.contains(entry.getKey())) {
                discard(entry.getValue());
                heads.remove(entry.getKey());
            }
        }
        for (int i = 0; i < names.size() && i < stacks.size(); i++) {
            Transformation pose = i == 0 ? muzzlePose() : queuePose(i - 1);
            ItemDisplay head = heads.get(names.get(i));
            if (head == null || !head.isValid()) {
                heads.put(names.get(i), item(ground, stacks.get(i), pose));
            } else {
                Props.animate(head, pose, 8);
            }
        }
        target = names.isEmpty() ? null : names.get(0);
        if (targetLabel != null && targetLabel.isValid()) {
            targetLabel.text(Text.c(target == null ? "" : "&c☠ &f&l" + target));
        }
    }

    private Transformation muzzlePose() {
        return Props.centred(new Vector3f(muzzleX() + 1.1f, muzzleY(), MUZZLE_Z),
                Props.yaw(FACE_THE_BARREL), new Vector3f(1.3f, 1.3f, 1.3f));
    }

    private Transformation queuePose(int place) {
        float x = radius + 0.95f + 0.8f + place * 0.75f;
        return Props.centred(new Vector3f(x, muzzleY() - 1.9f, MUZZLE_Z), Props.none(),
                new Vector3f(0.7f, 0.7f, 0.7f));
    }

    /** The head at the muzzle takes the bullet and flies off the stage. */
    private void knockOut(String name) {
        ItemDisplay head = name == null ? null : heads.remove(name);
        if (head == null || !head.isValid()) {
            return;
        }
        float x = muzzleX() + 1.1f;
        dust(org.bukkit.Color.fromRGB(0x8A0303), x, muzzleY(), MUZZLE_Z, 40, 0.35, 1.6f);
        head.setGlowColorOverride(org.bukkit.Color.RED);
        head.setGlowing(true);
        Props.animate(head, Props.centred(new Vector3f(x + 2.4f, 0.4f, MUZZLE_Z + 0.8f),
                new Quaternionf().rotateZ((float) (-Math.PI * 0.6)).rotateY((float) FACE_THE_BARREL),
                new Vector3f(1.1f, 1.1f, 1.1f)), 12);
        later(45, () -> discard(head));
        if (targetLabel != null && targetLabel.isValid()) {
            targetLabel.text(Text.c("&4&l✖ &7" + name));
        }
    }

    /** The head at the muzzle flinches at the click. */
    private void flinch(String name) {
        ItemDisplay head = name == null ? null : heads.get(name);
        if (head == null || !head.isValid()) {
            return;
        }
        Props.animate(head, Props.centred(new Vector3f(muzzleX() + 1.45f, muzzleY() + 0.1f, MUZZLE_Z),
                new Quaternionf().rotateY((float) FACE_THE_BARREL).rotateX(-0.35f),
                new Vector3f(1.25f, 1.25f, 1.25f)), 2);
        later(6, () -> {
            if (head.isValid() && name.equals(target)) {
                Props.animate(head, muzzlePose(), 6);
            }
        });
    }

    // ------------------------------------------------------------------ the shot

    /** Somebody pulled the trigger: the cylinder spins and the shot either fires or does not. */
    public void pull(boolean fires) {
        if (holes.isEmpty()) {
            return;
        }
        if (flushAt >= 0 || queuedNames != null || queuedBullets > 0) {
            // The last shot still had something to show: it goes up before this pull.
            flush();
        }
        victim = target;
        // A chamber that agrees with the result comes to rest under the hammer.
        List<Integer> candidates = new ArrayList<>();
        for (int chamber = 0; chamber < chambers; chamber++) {
            if (loaded(chamber) == fires) {
                candidates.add(chamber);
            }
        }
        int target = candidates.isEmpty() ? 0 : candidates.get(Rng.intBetween(0, candidates.size() - 1));
        double rest = -WheelMath.angleOf(target, chambers);
        spinFrom = angle;
        spinArc = WheelMath.TAU + WheelMath.normalize(rest - angle);
        spinStart = age;
        pendingFire = fires;
        pending = true;
        Props.animate(hammer, hammerPose(0.35f), 3);
        playSound(Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.6f);
    }

    @Override
    protected void onAnimate(int age) {
        this.age = age;
        if (flushAt >= 0 && spinStart < 0 && age >= flushAt) {
            flush();
        }
        if (spinStart < 0) {
            return;
        }
        int since = age - spinStart;
        if (since <= SPIN_TICKS) {
            double eased = WheelMath.easeQuad(since / (double) SPIN_TICKS);
            angle = spinFrom + spinArc * eased;
            place(angle, 1);
            int notch = (int) Math.floor(WheelMath.normalize(angle) / (WheelMath.TAU / chambers));
            if (since % 2 == 0 && since < SPIN_TICKS) {
                playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.7f + notch * 0.05f);
            }
            return;
        }
        if (pending && since >= SPIN_TICKS + SUSPENSE_TICKS) {
            pending = false;
            spinStart = -1;
            Props.animate(hammer, hammerPose(-0.25f), 1);
            later(3, () -> Props.animate(hammer, hammerPose(0.0f), 4));
            if (pendingFire) {
                fire();
                knockOut(victim);
            } else {
                particles(Particle.SMOKE, 0, axle.y + radius, 0.9, 6, 0.1, 0.01);
                playSound(Sound.BLOCK_DISPENSER_FAIL, 1.0f, 1.4f);
                playSound(Sound.BLOCK_NOTE_BLOCK_HAT, 0.9f, 0.5f);
                flinch(victim);
            }
            flushAt = age + LINEUP_DELAY_TICKS;
        }
    }

    private void fire() {
        double muzzleX = muzzleX();
        double muzzleY = muzzleY();
        particles(Particle.EXPLOSION, muzzleX, muzzleY, -0.45, 2, 0.1, 0.0);
        particles(Particle.FLAME, muzzleX, muzzleY, -0.45, 30, 0.25, 0.12);
        particles(Particle.LARGE_SMOKE, muzzleX + 0.6, muzzleY, -0.45, 20, 0.3, 0.05);
        particles(Particle.LAVA, 0, axle.y + radius, 0.8, 6, 0.2, 0.0);
        for (ItemDisplay plate : body) {
            if (plate.isValid()) {
                plate.setGlowColorOverride(org.bukkit.Color.RED);
                plate.setGlowing(true);
            }
        }
        later(12, () -> body.forEach(plate -> {
            if (plate.isValid()) {
                plate.setGlowing(false);
            }
        }));
        playSound(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);
        playSound(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.0f, 0.6f);
    }

    private void place(double spin, int ticks) {
        for (int plate = 0; plate < body.size(); plate++) {
            Props.animate(body.get(plate), bodyPose(spin, plate), ticks);
        }
        for (int chamber = 0; chamber < chambers; chamber++) {
            Props.animate(holes.get(chamber), chamberPose(spin, chamber, 0.85f, 0.1f, 0.62f), ticks);
            ItemDisplay round = rounds.get(chamber);
            if (round != null) {
                Props.animate(round, chamberPose(spin, chamber, 0.55f, 0.22f, 0.7f), ticks);
            }
        }
    }

    private boolean loaded(int chamber) {
        return chamber < bullets;
    }

    private Transformation bodyPose(double spin, int plate) {
        float side = 2 * (radius + 0.72f);
        Quaternionf rotation = Props.roll(spin + plate * Math.PI / 6);
        return Props.centred(new Vector3f(), rotation, new Vector3f(side, side, 1.2f));
    }

    private Transformation chamberPose(double spin, int chamber, float size, float depth, float front) {
        double at = spin + WheelMath.angleOf(chamber, chambers);
        Quaternionf rotation = Props.roll(at);
        Vector3f centre = rotation.transform(new Vector3f(0, radius, front));
        return Props.centred(centre, rotation, new Vector3f(size, size, depth));
    }

    private Transformation hammerPose(float lift) {
        Vector3f tip = new Vector3f(0, radius + 1.2f + lift, 0.6f);
        return Props.box(tip, Props.roll(Math.PI / 4), new Vector3f(0.5f, 0.5f, 0.25f));
    }
}
