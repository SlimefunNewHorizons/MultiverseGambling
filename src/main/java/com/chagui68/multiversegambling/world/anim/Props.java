package com.chagui68.multiversegambling.world.anim;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Small toolbox to shape display entities.
 *
 * <p>A {@code BlockDisplay} draws its block from its own origin to {@code (1, 1, 1)},
 * so turning one round its middle needs the corner moved back by the turned half size;
 * an {@code ItemDisplay} and a {@code TextDisplay} are already centred. These helpers
 * hide that difference so a show only says where the middle of a piece goes, how big it
 * is and how it is turned.</p>
 *
 * <p>Movement is left to the client: {@link #animate(Display, Transformation, int)}
 * hands the next pose over with an interpolation time, and the client glides there on
 * its own, so a wheel or a ball moves smoothly at 60 frames per second instead of
 * jumping once per server tick.</p>
 */
public final class Props {

    /**
     * Scoreboard tag carried by every prop, so listeners can tell show pieces apart and
     * leftovers of a crash can be found and removed.
     */
    public static final String TAG = "mvgam_prop";

    /**
     * Fully lit: a show looks the same at noon, at midnight and under a roof.
     */
    public static final Display.Brightness FULL_BRIGHT = new Display.Brightness(15, 15);

    private Props() {
    }

    /**
     * No rotation.
     */
    public static Quaternionf none() {
        return new Quaternionf();
    }

    /**
     * Rotation round the vertical axis.
     */
    public static Quaternionf yaw(double radians) {
        return new Quaternionf().rotateY((float) radians);
    }

    /**
     * Rotation round the axis pointing at the audience: positive turns anticlockwise as
     * the audience sees it.
     */
    public static Quaternionf roll(double radians) {
        return new Quaternionf().rotateZ((float) radians);
    }

    /**
     * Rotation round the axis running left to right in front of the audience.
     */
    public static Quaternionf pitch(double radians) {
        return new Quaternionf().rotateX((float) radians);
    }

    /**
     * A block display box of that size, centred on {@code center} and turned round its
     * own middle by {@code rotation}.
     */
    public static Transformation box(Vector3f center, Quaternionf rotation, Vector3f size) {
        Vector3f half = new Vector3f(size).mul(0.5f);
        Vector3f corner = rotation.transform(new Vector3f(half));
        Vector3f translation = new Vector3f(center).sub(corner);
        return new Transformation(translation, new Quaternionf(rotation), new Vector3f(size), new Quaternionf());
    }

    /**
     * A box of that size centred on the display itself.
     */
    public static Transformation box(float x, float y, float z) {
        return box(new Vector3f(), none(), new Vector3f(x, y, z));
    }

    /**
     * A box of that size centred on {@code center}, not turned.
     */
    public static Transformation box(Vector3f center, Vector3f size) {
        return box(center, none(), size);
    }

    /**
     * Pose of a centred display (item or text): where its middle goes, how it is turned
     * and how big it is.
     */
    public static Transformation centred(Vector3f center, Quaternionf rotation, Vector3f scale) {
        return new Transformation(new Vector3f(center), new Quaternionf(rotation), new Vector3f(scale),
                new Quaternionf());
    }

    /**
     * A centred display scaled evenly.
     */
    public static Transformation centred(Vector3f center, float scale) {
        return centred(center, none(), new Vector3f(scale, scale, scale));
    }

    /**
     * A centred display at its own origin, scaled evenly.
     */
    public static Transformation scaled(float scale) {
        return centred(new Vector3f(), scale);
    }

    /**
     * Glides a display to a new pose over that many ticks. The delay is set every time
     * on purpose: it is the signal that makes the client start a new interpolation.
     */
    public static void animate(Display display, Transformation pose, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(Math.max(0, ticks));
        display.setTransformation(pose);
    }

    /**
     * The head of a player, for a show that puts the players themselves on the stage.
     */
    public static org.bukkit.inventory.ItemStack head(java.util.UUID player) {
        org.bukkit.inventory.ItemStack head = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta meta) {
            meta.setOwningPlayer(org.bukkit.Bukkit.getOfflinePlayer(player));
            head.setItemMeta(meta);
        }
        return head;
    }

    /**
     * ARGB colour for text backgrounds.
     */
    public static Color argb(int alpha, int red, int green, int blue) {
        return Color.fromARGB(alpha, red, green, blue);
    }

    /**
     * RGB colour of a dyed block, used for glow outlines, particles and dyed armour.
     * Anything that is not a coloured block falls back to gold.
     */
    public static Color colourOf(Material material) {
        String name = material.name();
        for (org.bukkit.DyeColor dye : org.bukkit.DyeColor.values()) {
            if (name.startsWith(dye.name() + "_")) {
                return dye.getColor();
            }
        }
        return switch (material) {
            case DIAMOND_BLOCK -> Color.fromRGB(0x5DECF5);
            case EMERALD_BLOCK -> Color.fromRGB(0x17DD62);
            case REDSTONE_BLOCK -> Color.fromRGB(0xD0281B);
            case IRON_BLOCK -> Color.fromRGB(0xD8D8D8);
            case SEA_LANTERN -> Color.fromRGB(0xD5EEE6);
            default -> Color.fromRGB(0xFFC72C);
        };
    }
}
