package com.chagui68.multiversegambling.world;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.game.BoardGame;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.Props;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The living decoration of the casino: the floating name of every pavilion with its
 * game icon turning slowly under it, the names over the gates, the giant golden coin
 * spinning over the fountain and the welcome board at spawn.
 *
 * <p>All of it is made of display entities that are never saved: each piece is spawned
 * when its chunk is loaded and simply disappears with the chunk, so nothing piles up
 * across restarts and a change of language or of the catalogue only needs a refresh.
 * A slow task turns the spinning pieces; the client interpolates the turn, so they glide
 * instead of ticking.</p>
 */
final class CasinoDecor {

    /** Ticks between two checks for pieces whose chunk came back. */
    private static final int CHECK_TICKS = 40;
    /** Ticks per spinning step, and degrees turned per step. */
    private static final int SPIN_TICKS = 5;
    private static final float SPIN_DEGREES = 12.0f;

    /**
     * A group of entities standing at one spot, rebuilt together.
     */
    private static final class Piece {
        private final Location anchor;
        private final Function<Location, List<Entity>> builder;
        private final boolean spinning;
        private final List<Entity> entities = new ArrayList<>();

        Piece(Location anchor, boolean spinning, Function<Location, List<Entity>> builder) {
            this.anchor = anchor;
            this.spinning = spinning;
            this.builder = builder;
        }

        boolean intact() {
            if (entities.isEmpty()) {
                return false;
            }
            for (Entity entity : entities) {
                if (!entity.isValid()) {
                    return false;
                }
            }
            return true;
        }

        void clear() {
            for (Entity entity : entities) {
                if (entity.isValid()) {
                    entity.remove();
                }
            }
            entities.clear();
        }
    }

    private final MultiverseGamblingPlugin plugin;
    private final List<Piece> pieces = new ArrayList<>();
    private BukkitTask checker;
    private BukkitTask spinner;
    private float spin;

    CasinoDecor(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Plans every piece for this layout and starts looking after them.
     */
    void start(World world, CasinoLayout layout, int floor) {
        stop();
        plan(world, layout, floor);
        checker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::check, 20L, CHECK_TICKS);
        spinner = plugin.getServer().getScheduler().runTaskTimer(plugin, this::spin, 20L, SPIN_TICKS);
    }

    /**
     * Removes every piece and stops the tasks.
     */
    void stop() {
        if (checker != null) {
            checker.cancel();
            checker = null;
        }
        if (spinner != null) {
            spinner.cancel();
            spinner = null;
        }
        for (Piece piece : pieces) {
            piece.clear();
        }
        pieces.clear();
    }

    private void check() {
        for (Piece piece : pieces) {
            World world = piece.anchor.getWorld();
            if (world == null || !world.isChunkLoaded(piece.anchor.getBlockX() >> 4, piece.anchor.getBlockZ() >> 4)) {
                continue;
            }
            if (piece.intact()) {
                continue;
            }
            piece.clear();
            try {
                piece.entities.addAll(piece.builder.apply(piece.anchor.clone()));
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Could not place a decoration of the casino: " + error);
            }
        }
    }

    private void spin() {
        spin = (spin + SPIN_DEGREES) % 360.0f;
        for (Piece piece : pieces) {
            if (!piece.spinning) {
                continue;
            }
            for (Entity entity : piece.entities) {
                if (entity.isValid()) {
                    entity.setRotation(spin, 0.0f);
                }
            }
        }
    }

    // --------------------------------------------------------------- planning

    private void plan(World world, CasinoLayout layout, int floor) {
        double ground = floor + 1;
        String brand = "&6&lMultiverse&e&lGambling";
        // The coin over the fountain, its title and the welcome board.
        pieces.add(new Piece(new Location(world, 0.5, ground + 10.5, 0.5), true, anchor -> coin(anchor)));
        pieces.add(new Piece(new Location(world, 0.5, ground + 14.2, 0.5), false, anchor -> List.of(
                label(anchor, Text.c(brand), 4.2f, Display.Billboard.VERTICAL, 0))));
        pieces.add(new Piece(new Location(world, 0.5, ground + 1.2, 12.5, 0.0f, 0.0f), false, anchor -> List.of(
                board(anchor))));

        for (CasinoLayout.Arena arena : layout.arenas()) {
            Game game = plugin.games().byId(arena.gameId()).orElse(null);
            String name = name(arena, game);
            boolean blocks = game instanceof BoardGame;
            String hint = blocks
                    ? plugin.messages().getOr("world.decor.click-board", "&7Click the board to play")
                    : plugin.messages().getOr("world.decor.play", "&7/mvgam play {game}", "game", arena.gameId());
            double x = arena.centerX() + 0.5;
            double z = arena.centerZ() + 0.5;
            Material icon = game == null ? Material.GOLD_INGOT : game.icon();
            pieces.add(new Piece(new Location(world, x, ground + 17.5, z), false, anchor -> List.of(
                    label(anchor, Text.c("&f&l" + name + "\n" + hint), 3.0f, Display.Billboard.CENTER, 110))));
            pieces.add(new Piece(new Location(world, x, ground + 15.0, z), true, anchor -> List.of(
                    icon(anchor, icon, 3.2f))));
            if (game != null) {
                // How to play, the bet limits, the house's bite: short view range, rebuilt when missing.
                pieces.add(new Piece(new Location(world, x, ground + 3.4, z), false, anchor -> List.of(
                        usage(anchor, game, name))));
            }
            for (CasinoLayout.Edge edge : CasinoLayout.Edge.values()) {
                pieces.add(gateSign(world, arena, edge, ground, name));
            }
        }
    }

    private String name(CasinoLayout.Arena arena, Game game) {
        String fallback = game == null ? arena.gameId() : game.name();
        return plugin.messages().gameName((CommandSender) null, arena.gameId(), fallback);
    }

    /**
     * The name of the game on the outer face of the lintel over a gate.
     */
    private Piece gateSign(World world, CasinoLayout.Arena arena, CasinoLayout.Edge edge, double ground, String name) {
        int r = CasinoLayout.ARENA_RADIUS;
        double outside = r + 0.56;
        double x = arena.centerX() + 0.5;
        double z = arena.centerZ() + 0.5;
        float yaw;
        switch (edge) {
            case NORTH -> {
                z -= outside;
                yaw = 180.0f;
            }
            case SOUTH -> {
                z += outside;
                yaw = 0.0f;
            }
            case WEST -> {
                x -= outside;
                yaw = 90.0f;
            }
            default -> {
                x += outside;
                yaw = -90.0f;
            }
        }
        Location spot = new Location(world, x, ground + 6.25, z, yaw, 0.0f);
        return new Piece(spot, false, anchor -> List.of(
                label(anchor, Text.c("&6&l" + name), 1.25f, Display.Billboard.FIXED, 0)));
    }

    // --------------------------------------------------------------- entities

    private TextDisplay label(Location anchor, Component text, float scale, Display.Billboard billboard, int background) {
        return anchor.getWorld().spawn(anchor, TextDisplay.class, display -> {
            prepare(display);
            display.text(text);
            display.setBillboard(billboard);
            display.setShadowed(true);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setLineWidth(400);
            display.setBackgroundColor(Color.fromARGB(background, 0, 0, 0));
            display.setTransformation(Props.scaled(scale));
            display.setViewRange(3.0f);
        });
    }

    /**
     * The guide board of a game: name, how to play, bet limits, the house's take, the wallet.
     */
    private TextDisplay usage(Location anchor, Game game, String name) {
        var messages = plugin.messages();
        List<String> lines = new ArrayList<>();
        lines.add(messages.getOr("world.usage.title", "&e&l{game}", "game", name));
        List<String> how = game.displayDescription(null);
        for (int i = 0; i < Math.min(3, how.size()); i++) {
            lines.add(how.get(i));
        }
        lines.add(messages.getOr("world.usage.bets", "&7Bet: &f{min} &7- &f{max}",
                "min", plugin.economy().format(game.minBet()), "max", plugin.economy().format(game.maxBet())));
        lines.add(plugin.guide().takeLine(null, game.id()));
        lines.add(messages.getOr("world.usage.wallet", "&7Plays with your wallet: &f/mvgam wallet"));
        lines.add(messages.getOr("world.usage.help", "&8/mvgam help {id}", "id", game.id()));
        TextDisplay display = label(anchor, Text.c(String.join("\n", lines)), 0.8f, Display.Billboard.CENTER, 140);
        display.setViewRange(0.2f);
        return display;
    }

    private ItemDisplay icon(Location anchor, Material material, float scale) {
        return anchor.getWorld().spawn(anchor, ItemDisplay.class, display -> {
            prepare(display);
            display.setItemStack(new ItemStack(material));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(Props.scaled(scale));
            display.setTeleportDuration(SPIN_TICKS);
            display.setViewRange(3.0f);
        });
    }

    /**
     * A big golden coin standing on its edge, a dollar sign on both faces.
     */
    private List<Entity> coin(Location anchor) {
        List<Entity> parts = new ArrayList<>();
        for (int plate = 0; plate < 2; plate++) {
            Quaternionf rotation = new Quaternionf().rotateZ((float) (plate * Math.PI / 4));
            Transformation pose = Props.centred(new Vector3f(), rotation, new Vector3f(4.6f, 4.6f, 0.5f));
            Material metal = plate == 0 ? Material.GOLD_BLOCK : Material.RAW_GOLD_BLOCK;
            parts.add(anchor.getWorld().spawn(anchor, ItemDisplay.class, display -> {
                prepare(display);
                display.setItemStack(new ItemStack(metal));
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                display.setTransformation(pose);
                display.setTeleportDuration(SPIN_TICKS);
                display.setViewRange(4.0f);
            }));
        }
        for (int face = 0; face < 2; face++) {
            Quaternionf rotation = new Quaternionf().rotateY((float) (face * Math.PI));
            Vector3f offset = rotation.transform(new Vector3f(0, -1.25f, 0.27f));
            Transformation pose = Props.centred(offset, rotation, new Vector3f(9f, 9f, 9f));
            parts.add(anchor.getWorld().spawn(anchor, TextDisplay.class, display -> {
                prepare(display);
                display.text(Text.c("&6&l$"));
                display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                display.setShadowed(false);
                display.setTransformation(pose);
                display.setTeleportDuration(SPIN_TICKS);
                display.setViewRange(4.0f);
            }));
        }
        return parts;
    }

    private TextDisplay board(Location anchor) {
        List<String> lines = new ArrayList<>();
        lines.add("&6&lMultiverse&e&lGambling");
        lines.add(plugin.messages().getOr("world.decor.welcome", "&fWelcome to the casino!"));
        lines.add("");
        lines.add(plugin.messages().getOr("world.decor.menu", "&e/mvgam menu &7- every game"));
        lines.add(plugin.messages().getOr("world.decor.play-any", "&e/mvgam play <id> &7- straight to a game"));
        lines.add(plugin.messages().getOr("world.decor.language", "&e/mvgam language &7- your language"));
        lines.add("");
        lines.add(plugin.messages().getOr("world.decor.walk", "&7Follow the boulevards: every pavilion is a game."));
        return label(anchor, Text.c(String.join("\n", lines)), 1.1f, Display.Billboard.FIXED, 150);
    }

    private static void prepare(Display display) {
        display.setPersistent(false);
        display.addScoreboardTag(Props.TAG);
        display.setBrightness(Props.FULL_BRIGHT);
        display.setShadowRadius(0.0f);
    }
}
