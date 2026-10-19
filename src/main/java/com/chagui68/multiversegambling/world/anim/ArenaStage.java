package com.chagui68.multiversegambling.world.anim;

import com.chagui68.multiversegambling.world.CasinoLayout;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * The piece of the casino world that belongs to one game: the pavilion built by
 * {@code CasinoWorldManager} plus the maths to place a show on it.
 *
 * <p>Two sets of coordinates live here. {@link #at(double, double, double)} is relative
 * to the centre of the pavilion along the world axes, which is what the boards played by
 * clicking blocks use. {@link #local(double, double, double)} goes through the
 * {@link StageFrame} of the pavilion, so a show is designed once, facing an audience on
 * its {@code +z}, and always ends up facing the main gate. The ground of the pavilion is
 * at {@link #floorY()}; a player standing on it has their feet one block higher.</p>
 */
public final class ArenaStage {

    /**
     * Where a show can drop a spectator when the game asks for it.
     */
    public static final int VIEW_DISTANCE = 14;

    /**
     * Pitch to watch a show standing about eye level: a table, a wheel, a cabinet.
     */
    public static final float TABLE_PITCH = -4.0f;

    /**
     * Looking up at a tall show, such as plinko or the crash rocket.
     */
    public static final float BOARD_PITCH = -12.0f;

    /**
     * Widest angle, either side of the gate axis, the audience of a group show is
     * spread over: everybody stays in front of the show instead of behind it.
     */
    public static final double AUDIENCE_ARC = Math.toRadians(62.0);

    /**
     * How far a sound from the arena reaches.
     */
    public static final double SOUND_RADIUS = 64.0;

    private final World world;
    private final CasinoLayout.Arena arena;
    private final int floorY;
    private final StageFrame frame;

    public ArenaStage(World world, CasinoLayout.Arena arena, int floorY) {
        this.world = world;
        this.arena = arena;
        this.floorY = floorY;
        this.frame = StageFrame.facing(CasinoLayout.entrance(arena));
    }

    /**
     * Where one of {@code total} watchers stands, for a show that wants its audience
     * somewhere else than on the usual arc.
     */
    @FunctionalInterface
    public interface Spot {
        Location of(ArenaStage stage, int index, int total);
    }

    public World world() {
        return world;
    }

    public CasinoLayout.Arena arena() {
        return arena;
    }

    /**
     * Y of the blocks the arena floor is made of.
     */
    public int floorY() {
        return floorY;
    }

    /**
     * The local axes of the stage.
     */
    public StageFrame frame() {
        return frame;
    }

    /**
     * Yaw that turns a display so its local {@code +z} faces the audience.
     */
    public float yaw() {
        return frame.yaw();
    }

    /**
     * Standing spot in the middle of the arena.
     */
    public Location center() {
        return at(0, 0, 0);
    }

    /**
     * Location relative to the centre of the arena along the world axes; {@code dy = 0}
     * is the ground.
     */
    public Location at(double dx, double dy, double dz) {
        return new Location(world,
                arena.centerX() + 0.5 + dx,
                floorY + 1 + dy,
                arena.centerZ() + 0.5 + dz);
    }

    /**
     * Location of a point of a show, in the local frame of the stage: {@code +z} towards
     * the audience, {@code +x} to their right, {@code y = 0} the ground. The location
     * carries the yaw of the frame, so a display spawned there faces the audience.
     */
    public Location local(double x, double y, double z) {
        Location spot = new Location(world,
                arena.centerX() + 0.5 + frame.worldX(x, z),
                floorY + 1 + y,
                arena.centerZ() + 0.5 + frame.worldZ(x, z));
        spot.setYaw(frame.yaw());
        spot.setPitch(0.0f);
        return spot;
    }

    /**
     * Where a spectator stands to watch a show, straight in front of it.
     */
    public Location watcher(int distance) {
        return watcher(distance, TABLE_PITCH, 0, 1);
    }

    /**
     * Spectator spot for one of {@code total} watchers, spread on an arc in front of
     * the show and all of them facing its middle.
     */
    public Location watcher(int distance, int index, int total) {
        return watcher(distance, TABLE_PITCH, index, total);
    }

    /**
     * Spectator spot looking at a show with the given pitch: positive looks down at a
     * table on the ground, negative looks up at a board or a tower.
     */
    public Location watcher(int distance, float pitch) {
        return watcher(distance, pitch, 0, 1);
    }

    /**
     * One of {@code total} watchers of a show, spread on an arc in front of it, all of
     * them facing its middle with the same pitch.
     */
    public Location watcher(int distance, float pitch, int index, int total) {
        double angle = audienceAngle(index, total);
        double x = Math.sin(angle) * distance;
        double z = Math.cos(angle) * distance;
        Location spot = local(x, 0, z);
        spot.setYaw(frame.lookYaw(x, z, 0, 0));
        spot.setPitch(pitch);
        return spot;
    }

    /**
     * Angle of a watcher on the audience arc, measured from the gate axis: a single
     * watcher stands straight in front, a crowd fans out evenly.
     */
    static double audienceAngle(int index, int total) {
        if (total <= 1) {
            return 0.0;
        }
        double step = 2 * AUDIENCE_ARC / (total - 1);
        return -AUDIENCE_ARC + step * Math.max(0, Math.min(total - 1, index));
    }

    /**
     * Loads the chunks the arena sits on, so blocks and entities can be placed.
     */
    public void load() {
        int minChunkX = arena.minX() >> 4;
        int maxChunkX = arena.maxX() >> 4;
        int minChunkZ = arena.minZ() >> 4;
        int maxChunkZ = arena.maxZ() >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                world.getChunkAt(chunkX, chunkZ);
            }
        }
    }

    /**
     * Players gathered round the arena, the audience of a show.
     */
    public List<Player> nearbyPlayers() {
        return nearbyPlayers(SOUND_RADIUS);
    }

    /**
     * Players within that many blocks of the middle of the arena.
     */
    public List<Player> nearbyPlayers(double radius) {
        List<Player> players = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center(), radius, radius, radius)) {
            if (entity instanceof Player player) {
                players.add(player);
            }
        }
        return players;
    }
}
