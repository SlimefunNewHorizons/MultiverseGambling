package com.chagui68.multiversegambling.game;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaShow;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import java.time.Duration;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Shared base for every game: stakes, messages, sound and announcements. */
public abstract class AbstractGame implements Game {

    protected final MultiverseGamblingPlugin plugin;
    private final GameMeta meta;

    protected AbstractGame(MultiverseGamblingPlugin plugin, GameMeta meta) {
        this.plugin = plugin;
        this.meta = meta;
    }

    @Override
    public final GameMeta meta() {
        return meta;
    }

    @Override
    public boolean enabled() {
        return plugin.config().gameEnabled(id());
    }

    @Override
    public double minBet() {
        return plugin.config().minBet(id());
    }

    @Override
    public double maxBet() {
        return plugin.config().maxBet(id());
    }

    // ---------------------------------------------------------------- localisation

    /** Game name in the language of the viewer; lang files may override {@code catalog.<id>.name}. */
    @Override
    public String displayName(CommandSender viewer) {
        return plugin.messages().gameName(viewer, id(), name());
    }

    /** Description in the language of the viewer, falling back to the built in one. */
    @Override
    public List<String> displayDescription(CommandSender viewer) {
        return plugin.messages().gameDescription(viewer, id(), description());
    }

    // ------------------------------------------------------------------ helpers

    // These helpers are public on purpose: the nested menus of each game (classes
    // inside another class) need them and are not subclasses of this one.

    public void message(Player player, String key, Object... replacements) {
        plugin.messages().send(player, key, replacements);
    }

    public void info(Player player, String legacyText) {
        plugin.messages().sendRaw(player, legacyText);
    }

    /**
     * The one "another round?" line every game sends when a round is over: a prompt, the
     * "play again" button and, for a game that can be played against the house, the house
     * button. Every game uses the same line, so a player always knows where to click.
     */
    public Component replayLine(Player player, String promptKey) {
        Component line = plugin.messages().componentPlainFor(player, promptKey, "game", displayName(player))
                .append(Text.c(" "))
                .append(Text.button(plugin.messages().forSender(player, "replay.again"),
                        "/mvgam play " + id(),
                        plugin.messages().forSender(player, "replay.again-hover", "game", displayName(player))));
        if (playableAgainstHouse()) {
            line = line.append(Text.c(" "))
                    .append(Text.button(plugin.messages().forSender(player, "replay.house"),
                            "/mvgam play " + id() + " house",
                            plugin.messages().forSender(player, "replay.house-hover", "game", displayName(player))));
        }
        return line;
    }

    public void actionBar(Player player, String legacyText) {
        player.sendActionBar(Text.c(legacyText));
    }

    public void sound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public Component money(double amount) {
        return Text.c(plugin.economy().format(amount));
    }

    /**
     * Settles a solo bet: pays out, records the statistics and announces the win when
     * the prize is worth mentioning.
     *
     * @param multiplier multiplier over what was staked; 0 or less means a loss
     * @return the money returned to the player
     */
    public double settle(Player player, Wager wager, double multiplier) {
        double payout = wager.amount() * Math.max(0, multiplier);
        if (payout <= 0) {
            wager.lose();
        } else {
            wager.payAbsolute(payout);
        }
        // Statistics and win announcements are in money; a bet in items is neither.
        if (!(wager instanceof com.chagui68.multiversegambling.economy.ItemWager)) {
            plugin.stats().record(player.getUniqueId(), id(), wager.amount(), payout);
            plugin.games().announceWin(player, wager.amount(), payout);
        }
        return payout;
    }

    /**
     * What {@code units} of a stake are, in words: money for an ordinary bet, a number of
     * copies of the item for a bet staked with items (with the chance of one more when it
     * is not a whole number).
     */
    public String stakeText(Wager wager, double units) {
        if (wager instanceof com.chagui68.multiversegambling.economy.ItemWager items) {
            return itemsText(items.item(), units);
        }
        return plugin.economy().format(units);
    }

    /**
     * {@code units} copies of an item, in words.
     */
    public String itemsText(org.bukkit.inventory.ItemStack item, double units) {
        String name = com.chagui68.multiversegambling.economy.ItemBank.nameOf(item);
        int whole = (int) Math.floor(Math.max(0, units) + 1e-9);
        double extra = com.chagui68.multiversegambling.economy.ItemMath.extraChance(units);
        if (extra <= 0) {
            return plugin.messages().get("items.amount", "count", whole, "item", name);
        }
        return plugin.messages().get("items.amount-chance", "count", whole, "item", name,
                "chance", Text.percent(extra));
    }

    /**
     * Settles the bet of a player who is no longer online (a round closed by a logout
     * or a server stop): pays and records it, without any message.
     */
    public double settleOffline(java.util.UUID playerId, Wager wager, double multiplier) {
        double payout = wager.amount() * Math.max(0, multiplier);
        if (payout <= 0) {
            wager.lose();
        } else {
            wager.payAbsolute(payout);
        }
        if (!(wager instanceof com.chagui68.multiversegambling.economy.ItemWager)) {
            plugin.stats().record(playerId, id(), wager.amount(), payout);
        }
        return payout;
    }

    /** Gives the stake back without counting it as a played round. */
    public double refund(Wager wager) {
        if (wager == null) {
            return 0;
        }
        wager.refund();
        return wager.amount();
    }

    /** Header line with the game name, in the language of the reader. */
    public String title(CommandSender viewer) {
        return plugin.messages().forSender(viewer, "panel.header", "game", displayName(viewer));
    }

    /** Panel label (item name, button or title) in the language of the reader. */
    public String label(CommandSender viewer, String key, Object... replacements) {
        return plugin.messages().forSender(viewer, key, replacements);
    }

    /** Panel lore block in the language of the reader. */
    public List<String> labelLore(CommandSender viewer, String key, Object... replacements) {
        return plugin.messages().loreFor(viewer, key, replacements);
    }

    /** On screen title with its subtitle, both built from language keys. */
    public void showTitle(Player player, String titleKey, String subtitleKey, Object... replacements) {
        if (player == null) {
            return;
        }
        player.showTitle(Title.title(
                plugin.messages().componentPlainFor(player, titleKey),
                plugin.messages().componentPlainFor(player, subtitleKey, replacements),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500),
                        Duration.ofMillis(400))));
    }

    /** Action bar line built from a language key, in the language of the reader. */
    public void actionBarKey(Player player, String key, Object... replacements) {
        if (player == null) {
            return;
        }
        player.sendActionBar(plugin.messages().componentPlainFor(player, key, replacements));
    }

    // ----------------------------------------------------------------- world shows

    /**
     * The arena of this game in the casino world, ready to paint a show on, or
     * {@code null} when there is nothing to paint on.
     *
     * <p>With {@code world.animations.teleport-players} on the player is moved to the
     * arena so they can watch; with it off only players already in the casino world get
     * a show and everybody else keeps the text animation of their game.</p>
     */
    public ArenaStage arenaFor(Player player) {
        return arenaFor(player, ArenaStage.TABLE_PITCH);
    }

    /**
     * Same as {@link #arenaFor(Player)} but placing the watcher with their own pitch,
     * so a board or a tower standing on the arena is looked at from below.
     */
    public ArenaStage arenaFor(Player player, float pitch) {
        int distance = plugin.config().worldAnimationsViewDistance();
        return arenaFor(player, (stage, index, total) -> stage.watcher(distance, pitch));
    }

    /**
     * Same as {@link #arenaFor(Player, float)} but watching from the spot the show picks,
     * such as the side of a roulette table.
     */
    public ArenaStage arenaFor(Player player, ArenaStage.Spot spot) {
        if (player == null || plugin.world() == null || !plugin.config().worldAnimationsEnabled()) {
            return null;
        }
        ArenaStage stage = plugin.world().stage(id());
        if (stage == null || ArenaShow.busy(id())) {
            // Somebody else's round is on the pavilion right now: this one keeps its text
            // animation rather than painting over theirs.
            return null;
        }
        if (plugin.config().worldAnimationsTeleport()) {
            stage.load();
            player.teleport(spot.of(stage, 0, 1));
            return stage;
        }
        return player.getWorld() == stage.world() ? stage : null;
    }

    /**
     * The arena of this game when the round is going to be played by clicking its
     * blocks, or {@code null} when the round must use its menu.
     *
     * <p>Unlike {@link #arenaFor(Player, float)} this never moves the player to a
     * watching spot: a board is played standing on it. Without
     * {@code world.animations.teleport-players} the board is only used by whoever is
     * already standing in the arena, so nobody is dragged away from wherever they are.</p>
     */
    public ArenaStage boardStageFor(Player player) {
        if (player == null || plugin.world() == null || !plugin.config().worldAnimationsEnabled()) {
            return null;
        }
        ArenaStage stage = plugin.world().stage(id());
        if (stage == null) {
            return null;
        }
        if (plugin.config().worldAnimationsTeleport()) {
            return stage;
        }
        return stage.arena().contains(player.getLocation().getBlockX(), player.getLocation().getBlockZ())
                ? stage : null;
    }
}
