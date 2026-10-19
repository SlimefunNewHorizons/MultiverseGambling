package com.chagui68.multiversegambling.game;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Pot;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Group game with chained rounds.
 *
 * <p>This class drives the cycle and it is always the same one, so no game can skip
 * either charging or refunding the money:</p>
 * <ol>
 *   <li>{@code WAITING}: people come in and stake. Reaching the minimum opens the round.</li>
 *   <li>{@code BETTING}: visible countdown; whoever does not stake is left out.</li>
 *   <li>{@code IN_GAME}: the subclass runs the round and calls {@link #endRound()}.</li>
 * </ol>
 *
 * <p>Players who disconnect during a round do not get their money back: their stake
 * stays in the pot and somebody else can win it. If they leave before the round
 * starts, it is refunded in full.</p>
 */
public abstract class AbstractGroupGame extends AbstractGame implements Ticking {

    public enum Phase {
        WAITING, BETTING, IN_GAME
    }

    protected final Pot pot;
    /** Players queued for the next round who have not staked yet. */
    protected final Set<UUID> waiting = new LinkedHashSet<>();
    /** Players who left mid round: still in the pot, but they can no longer win. */
    protected final Set<UUID> gone = new LinkedHashSet<>();

    protected Phase phase = Phase.WAITING;
    protected int timer;
    protected int round;
    /** True while the player alone in the room is playing the dealer. */
    private boolean houseDuel;
    private UUID houseDuelPlayer;
    /** Player who asked for the house from the bet selector and still has to choose. */
    private UUID houseRequested;
    /** Who played the running round, kept for the replay buttons once the pot is paid. */
    private final Set<UUID> roundPlayers = new LinkedHashSet<>();

    protected AbstractGroupGame(MultiverseGamblingPlugin plugin, GameMeta meta) {
        super(plugin, meta);
        this.pot = new Pot(plugin.economy());
    }

    // ------------------------------------------------------------------ entrada

    @Override
    public final void open(Player player) {
        if (!enabled()) {
            message(player, "games.disabled", "game", name());
            return;
        }
        if (!player.hasPermission(permission())) {
            message(player, "general.no-permission");
            return;
        }
        UUID id = player.getUniqueId();

        if (phase == Phase.IN_GAME) {
            if (waiting.add(id)) {
                message(player, "group.queued-next", "game", name());
            } else {
                message(player, "group.already-queued");
            }
            return;
        }

        int seated = pot.size() + waiting.size();
        if (maxPlayers() > 0 && seated >= maxPlayers() && !pot.contains(id) && !waiting.contains(id)) {
            message(player, "group.room-full", "max", maxPlayers());
            return;
        }

        // Alone in an empty room, the selector also offers to play straight against the
        // house, so nobody has to wait for company.
        boolean emptyRoom = phase != Phase.IN_GAME && pot.size() == 0 && waiting.isEmpty();
        if (houseDuelAvailable() && emptyRoom) {
            plugin.guis().openBetSelector(player, this, bet -> placeBet(player, bet),
                    bet -> betAgainstHouse(player, bet));
            return;
        }
        plugin.guis().openBetSelector(player, this, bet -> placeBet(player, bet));
    }

    /**
     * The "against the house" chat button: the bet selector whose confirm sits the
     * player down against the dealer. With company in the room, or with a round running,
     * it is an ordinary seat instead.
     */
    @Override
    public final void openAgainstHouse(Player player) {
        UUID id = player.getUniqueId();
        if (!houseDuelAvailable() || !enabled() || !player.hasPermission(permission())
                || phase == Phase.IN_GAME) {
            open(player);
            return;
        }
        if (pot.contains(id) && pot.size() == 1 && waiting.isEmpty()) {
            // Already staked and alone: the duel starts on the stake that is down.
            if (houseDuelNeedsChoice()) {
                houseRequested = id;
                message(player, "group.house-duel.choose-first");
            } else {
                startHouseDuel(player);
            }
            return;
        }
        boolean othersWaiting = waiting.size() > (waiting.contains(id) ? 1 : 0);
        if (pot.size() > 0 || othersWaiting) {
            message(player, "group.house-duel-crowded");
            open(player);
            return;
        }
        // Queued alone for the next round: the duel takes the place of that queue.
        waiting.remove(id);
        plugin.guis().openBetSelector(player, this, bet -> betAgainstHouse(player, bet));
    }

    /**
     * Stakes and sits down against the house right away; a game that needs a choice
     * first (a colour, a horse) starts the duel as soon as that choice is made.
     */
    private void betAgainstHouse(Player player, double amount) {
        if (pot.size() > 0 || !waiting.isEmpty() || phase == Phase.IN_GAME) {
            // Somebody came in meanwhile: an ordinary seat at the table.
            placeBet(player, amount);
            return;
        }
        houseRequested = player.getUniqueId();
        placeBet(player, amount);
        if (!pot.contains(player.getUniqueId())) {
            houseRequested = null;
            return;
        }
        if (!houseDuelNeedsChoice()) {
            houseRequested = null;
            startHouseDuel(player);
        } else {
            message(player, "group.house-duel.choose-first");
        }
    }

    /**
     * True when the duel can only start after the player chose something (a colour, a
     * horse): the game then calls {@link #houseChoiceMade(Player)} once it was made.
     */
    protected boolean houseDuelNeedsChoice() {
        return false;
    }

    /**
     * The choice a pending duel was waiting for is made: the duel starts.
     */
    protected final void houseChoiceMade(Player player) {
        if (houseRequested != null && houseRequested.equals(player.getUniqueId())) {
            houseRequested = null;
            startHouseDuel(player);
        }
    }

    /** Records (or replaces) the player's stake. */
    protected final void placeBet(Player player, double amount) {
        if (phase == Phase.IN_GAME) {
            message(player, "group.round-in-progress");
            return;
        }
        UUID id = player.getUniqueId();
        if (!pot.add(player, amount)) {
            message(player, "economy.not-enough-money", "bet", plugin.economy().format(amount));
            return;
        }
        waiting.remove(id);
        gone.remove(id);
        message(player, "group.bet-accepted",
                "amount", plugin.economy().format(amount), "game", name());
        onBetPlaced(player, amount);
        broadcastLobby();
        // Waiting alone is the moment to offer the duel against the house, so nobody is
        // stuck in a room that will never fill up.
        offerHouseDuel(player);
        if (phase == Phase.WAITING && pot.size() >= minPlayers()) {
            startBettingWindow();
        }
    }

    // -------------------------------------------------------------- against house

    /**
     * True when this game can be played one against the house, which is what saves a
     * player who is alone in the room from waiting for somebody else forever. The offer
     * with its chat button is sent by {@link #offerHouseDuel(Player)}.
     */
    protected boolean houseDuelAvailable() {
        return false;
    }

    @Override
    public final boolean playableAgainstHouse() {
        return houseDuelAvailable();
    }

    /**
     * What the duel pays right now, for the offer line. A game with a choice in it (a
     * colour, a horse) answers with what the current choice pays.
     */
    protected double houseDuelMultiplier(Player player) {
        return 1.0;
    }

    /**
     * Called before {@link #onRoundStart()} when the round is a duel, so a game can put
     * its own state in place first.
     */
    protected void onHouseDuelStart(Player player) {
    }

    /**
     * True while the single player of the room is duelling the dealer. The subclass uses
     * it in {@code onRoundStart} and {@code tickRound} to run the duel instead of the
     * group round.
     */
    protected final boolean houseDuelActive() {
        return houseDuel;
    }

    /**
     * The player of the running duel, or {@code null}.
     */
    protected final UUID houseDuelPlayer() {
        return houseDuelPlayer;
    }

    /**
     * Sends the offer and its button to a player who is alone in the room. Does nothing
     * for a game that cannot be played against the house, or when the room already has
     * company, so it is safe to call whenever the room changes.
     */
    protected final void offerHouseDuel(Player player) {
        if (!houseDuelAvailable() || phase == Phase.IN_GAME || pot.size() != 1
                || !pot.contains(player.getUniqueId()) || player.getUniqueId().equals(houseRequested)) {
            return;
        }
        player.sendMessage(plugin.messages()
                .componentPlainFor(player, "replay.house-offer",
                        "multiplier", Text.multiplier(houseDuelMultiplier(player)))
                .append(Text.c(" "))
                .append(chatButton(
                        plugin.messages().forSender(player, "replay.house"),
                        "house",
                        plugin.messages().forSender(player, "group.house-duel.hover"))));
    }

    /**
     * Starts the duel for whoever is alone in the room. Reachable from the chat button
     * and from {@code /mvgam action house}, so it works with or without a menu.
     */
    protected final void startHouseDuel(Player player) {
        if (!houseDuelAvailable()) {
            message(player, "group.unknown-action");
            return;
        }
        if (phase == Phase.IN_GAME) {
            message(player, "group.round-in-progress");
            return;
        }
        if (!pot.contains(player.getUniqueId())) {
            message(player, "group.not-in-table");
            return;
        }
        if (pot.size() != 1 || !waiting.isEmpty()) {
            message(player, "group.house-duel-crowded");
            return;
        }
        houseDuel = true;
        houseDuelPlayer = player.getUniqueId();
        onHouseDuelStart(player);
        beginRound();
    }

    /**
     * What a duel the player wins with that chance pays: the house edge taken out of a
     * fair bet, so every duel carries the same edge as the solo games.
     */
    protected final double fairDuelMultiplier(double chance) {
        if (chance <= 0) {
            return 1.0;
        }
        double edge = Math.max(0.0, Math.min(0.5, plugin.config().houseEdge()));
        return Math.max(1.0, Math.min(1000.0, (1.0 - edge) / chance));
    }

    /**
     * Settles the running duel and closes the round.
     *
     * @param outcome positive when the player beat the house, zero on a draw (the stake
     *                comes back) and negative when the house won
     */
    protected final void settleHouseDuel(int outcome, double multiplier) {
        UUID playerId = houseDuelPlayer;
        Player player = playerId == null ? null : online(playerId);
        double stake = playerId == null ? 0 : pot.amountOf(playerId);
        if (outcome > 0) {
            pot.payoutByMultiplier(id -> id.equals(playerId) ? multiplier : 0);
        } else if (outcome < 0) {
            pot.burn();
        }
        // A draw is refunded by endRound, like any money left in the pot.
        if (playerId != null && outcome != 0) {
            plugin.stats().record(playerId, id(), stake, outcome > 0 ? stake * multiplier : 0);
        }
        if (player != null) {
            if (outcome > 0) {
                message(player, "group.house-duel.won", "prize", plugin.economy().format(stake * multiplier),
                        "multiplier", Text.multiplier(multiplier));
                showTitle(player, "group.house-duel.won-title", "group.house-duel.won-subtitle",
                        "prize", plugin.economy().format(stake * multiplier));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                plugin.games().announceWin(player, stake, stake * multiplier);
            } else if (outcome == 0) {
                message(player, "group.house-duel.draw", "bet", plugin.economy().format(stake));
            } else {
                message(player, "group.house-duel.lost", "bet", plugin.economy().format(stake));
                showTitle(player, "group.house-duel.lost-title", "group.house-duel.lost-subtitle");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
            }
        }
        endRound();
    }

    /**
     * True for the seat of the house in a duel.
     */
    protected static boolean isHouse(UUID id) {
        return com.chagui68.multiversegambling.fair.FairnessService.HOUSE.equals(id);
    }

    /**
     * Name of the house, in the default language.
     */
    protected final String houseName() {
        return Text.strip(plugin.messages().getOr("group.house-duel.house-name", "The house"));
    }

    // ---------------------------------------------------------------- life cycle

    /** Called once per tick by the game registry. */
    @Override
    public final void tick() {
        switch (phase) {
            case WAITING -> {
                if (pot.size() >= minPlayers()) {
                    startBettingWindow();
                }
            }
            case BETTING -> {
                if (pot.size() < minPlayers()) {
                    phase = Phase.WAITING;
                    timer = 0;
                    broadcast("group.round-cancelled", "min", minPlayers());
                    return;
                }
                timer--;
                if (timer <= 0) {
                    beginRound();
                } else if (timer <= 5) {
                    broadcast("group.countdown", "seconds", timer);
                    tickSound();
                } else if (timer % 10 == 0) {
                    broadcast("group.time-left", "seconds", timer);
                }
            }
            case IN_GAME -> tickRound();
        }
    }

    private void startBettingWindow() {
        phase = Phase.BETTING;
        timer = plugin.config().groupBettingSeconds();
        broadcast("group.betting-open", "seconds", timer);
    }

    protected final void beginRound() {
        round++;
        roundPlayers.clear();
        roundPlayers.addAll(pot.participants());
        phase = Phase.IN_GAME;
        timer = 0;
        broadcast("group.round-starting", "round", round);
        onRoundStart();
    }

    /**
     * Runs the round. The subclass decides when it ends and must call
     * {@link #endRound()} after paying out the pot.
     */
    protected abstract void tickRound();

    /** Sets up a fresh round. */
    protected abstract void onRoundStart();

    /** Clears the state when a round ends. */
    protected void onRoundEnd() {
    }

    /** Optional hook for when somebody stakes. */
    protected void onBetPlaced(Player player, double amount) {
    }

    /** Hook for when somebody leaves mid round. */
    protected void onQuitDuringRound(UUID playerId) {
    }

    /**
     * Action requested from a chat button, for example
     * {@code /mvgam action shoot}. This is what lets people play without menus.
     */
    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("house".equals(action)) {
            startHouseDuel(player);
            return;
        }
        message(player, "group.unknown-action");
    }

    @Override
    public final boolean ownsPlayer(UUID playerId) {
        return contains(playerId);
    }

    /** @return true when the player is staking in this game right now. */
    public final boolean contains(UUID playerId) {
        return pot.contains(playerId) || waiting.contains(playerId);
    }

    /** Chat button label, ready to send. */
    protected final Component chatButton(String label, String action, String hover) {
        return Text.button(label, "/mvgam action " + action, hover);
    }

    /** Closes the round: refunds whatever was not settled and goes back to waiting. */
    protected final void endRound() {
        pot.refundAll();
        gone.clear();
        phase = Phase.WAITING;
        timer = 0;
        houseDuel = false;
        houseDuelPlayer = null;
        houseRequested = null;
        onRoundEnd();
        // No separate "round over" line: the replay line that follows already says so.
        offerReplay();
        promptWaiting();
    }

    /**
     * Whoever just played gets one chat line to go again: another round with the room,
     * or straight against the house, so nobody has to dig through the menus.
     */
    private void offerReplay() {
        for (UUID id : roundPlayers) {
            Player player = online(id);
            if (player == null || waiting.contains(id)) {
                continue;
            }
            player.sendMessage(replayLine(player, "replay.prompt"));
        }
        roundPlayers.clear();
    }

    /** Voids the running round and refunds all the money. */
    public final void abortRound() {
        if (phase == Phase.IN_GAME) {
            broadcast("group.round-voided");
        }
        pot.refundAll();
        waiting.clear();
        gone.clear();
        phase = Phase.WAITING;
        timer = 0;
        houseDuel = false;
        houseDuelPlayer = null;
        houseRequested = null;
        roundPlayers.clear();
        onRoundEnd();
    }

    /** Reopens the bet selector for whoever was waiting for their turn. */
    private void promptWaiting() {
        for (UUID id : new ArrayList<>(waiting)) {
            Player player = plugin.getServer().getPlayer(id);
            if (player == null || !player.isOnline()) {
                waiting.remove(id);
                continue;
            }
            player.sendMessage(replayLine(player, "group.betting-open-again"));
        }
    }

    @Override
    public void shutdown() {
        abortRound();
    }

    // ------------------------------------------------------------------ salidas

    /** Handles a player disconnecting. */
    @Override
    public final void handleQuit(UUID playerId) {
        waiting.remove(playerId);
        if (!pot.contains(playerId)) {
            gone.remove(playerId);
            return;
        }
        if (phase == Phase.IN_GAME) {
            // Their stake stays in the pot but they can no longer win it.
            gone.add(playerId);
            onQuitDuringRound(playerId);
        } else {
            pot.remove(playerId);
            gone.remove(playerId);
            broadcastLobby();
            // The room shrank to one player: offer them the dealer instead of an empty
            // waiting room.
            if (pot.size() == 1) {
                Player left = online(pot.participants().iterator().next());
                if (left != null) {
                    offerHouseDuel(left);
                }
            }
        }
    }

    @Override
    public int activePlayers() {
        return pot.size() + waiting.size();
    }

    @Override
    public List<String> statusLore(CommandSender viewer) {
        List<String> lore = new ArrayList<>();
        if (phase == Phase.IN_GAME) {
            lore.add(label(viewer, "panel.status.in-progress", "round", round));
        } else if (phase == Phase.BETTING) {
            lore.add(label(viewer, "panel.status.betting-open", "seconds", timer));
        } else {
            lore.add(label(viewer, "panel.status.waiting"));
        }
        lore.add(label(viewer, "panel.status.in-room",
                "current", activePlayers(), "max", maxPlayers()));
        lore.add(label(viewer, "panel.status.pot",
                "pot", plugin.economy().shortFormat(pot.total())));
        lore.add(label(viewer, "panel.status.minimum", "min", minPlayers()));
        return lore;
    }

    // --------------------------------------------------------------- utilidades

    protected final boolean isGone(UUID playerId) {
        return gone.contains(playerId);
    }

    // ----------------------------------------------------------------- world shows

    /**
     * The arena of this game in the casino world, ready to paint a show on, or
     * {@code null} when there is nothing to paint on.
     *
     * <p>With {@code world.animations.teleport-players} on the room is gathered round
     * the arena, spread out and all of them facing the middle, so everybody can watch
     * the round instead of reading it in the action bar.</p>
     */
    protected final ArenaStage gatherArena() {
        return gatherArena(ArenaStage.TABLE_PITCH);
    }

    /**
     * Same as {@link #gatherArena()} but with the pitch of the room, so a show standing
     * on the arena is looked at from below.
     */
    protected final ArenaStage gatherArena(float pitch) {
        int distance = plugin.config().worldAnimationsViewDistance();
        return gatherArena((stage, index, total) -> stage.watcher(distance, pitch, index, total));
    }

    /**
     * Same as {@link #gatherArena(float)} but gathering the room on the spots the show
     * picks, such as the side of a roulette table.
     */
    protected final ArenaStage gatherArena(ArenaStage.Spot spot) {
        if (plugin.world() == null || !plugin.config().worldAnimationsEnabled()) {
            return null;
        }
        ArenaStage stage = plugin.world().stage(id());
        if (stage == null || !plugin.config().worldAnimationsTeleport()) {
            return stage;
        }
        stage.load();
        List<Player> audience = new ArrayList<>();
        for (UUID id : pot.participants()) {
            Player player = online(id);
            if (player != null) {
                audience.add(player);
            }
        }
        for (int index = 0; index < audience.size(); index++) {
            audience.get(index).teleport(spot.of(stage, index, audience.size()));
        }
        return stage;
    }

    /** Participants still around, and therefore able to win. */
    protected final List<UUID> contenders() {
        List<UUID> out = new ArrayList<>();
        for (UUID id : pot.participants()) {
            if (!gone.contains(id) && plugin.getServer().getPlayer(id) != null) {
                out.add(id);
            }
        }
        return out;
    }

    protected final Player online(UUID playerId) {
        return plugin.getServer().getPlayer(playerId);
    }

    protected final String playerName(UUID playerId) {
        if (isHouse(playerId)) {
            return houseName();
        }
        Player player = online(playerId);
        if (player != null) {
            return player.getName();
        }
        String name = plugin.getServer().getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    /** Sends a language key with the prefix, each player reading it in their own language. */
    protected final void broadcast(String key, Object... replacements) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(plugin.messages().componentFor(player, key, replacements));
            }
        }
    }

    /** Sends already composed text to one player of the room. */
    protected final void tell(UUID playerId, String legacyText) {
        tell(playerId, Text.c(legacyText));
    }

    /** Sends a component (handy for text with buttons) to one player of the room. */
    protected final void tell(UUID playerId, Component component) {
        Player player = online(playerId);
        if (player != null) {
            player.sendMessage(component);
        }
    }

    protected final void broadcastRaw(String legacyText) {
        Component component = Text.c(legacyText);
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(component);
            }
        }
    }

    /** Sends a language key without the prefix, each player reading it in their own language. */
    protected final void broadcastPlain(String key, Object... replacements) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(plugin.messages().componentPlainFor(player, key, replacements));
            }
        }
    }

    /**
     * Same as {@link #broadcastPlain(String, Object...)} but the replacements are built
     * for each reader: needed when a value inside the line is itself translated, such as
     * the name of a dice poker hand.
     */
    protected final void broadcastPlainFor(Function<Player, Object[]> replacements, String key) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(plugin.messages().componentPlainFor(player, key,
                        replacements.apply(player)));
            }
        }
    }

    /** Action bar line in the language of every player of the room. */
    protected final void actionBarAllKey(String key, Object... replacements) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendActionBar(plugin.messages().componentPlainFor(player, key, replacements));
            }
        }
    }

    /** Action bar line whose replacements are built for each reader. */
    protected final void actionBarFor(Function<Player, Object[]> replacements, String key) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendActionBar(plugin.messages().componentPlainFor(player, key,
                        replacements.apply(player)));
            }
        }
    }

    /** Sends a language key without the prefix to one player of the room. */
    protected final void tellKeyed(UUID playerId, String key, Object... replacements) {
        Player player = online(playerId);
        if (player != null) {
            player.sendMessage(plugin.messages().componentPlainFor(player, key, replacements));
        }
    }

    /** Round header, each player reading it in their own language. */
    protected final void broadcastRoundHeader() {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendMessage(plugin.messages().componentPlainFor(player, "panel.round-header",
                        "game", displayName(player), "round", round));
            }
        }
    }

    protected final void actionBarAll(String legacyText) {
        Component component = Text.c(legacyText);
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.sendActionBar(component);
            }
        }
    }

    protected final void soundAll(Sound sound, float volume, float pitch) {
        for (UUID id : audience()) {
            Player player = online(id);
            if (player != null) {
                player.playSound(player.getLocation(), sound, volume, pitch);
            }
        }
    }

    private Set<UUID> audience() {
        Set<UUID> ids = new LinkedHashSet<>(pot.participants());
        ids.addAll(waiting);
        return ids;
    }

    protected final void broadcastLobby() {
        if (phase == Phase.WAITING && pot.size() < minPlayers()) {
            broadcast("group.waiting-players",
                    "current", pot.size(), "min", minPlayers());
        }
    }

    private void tickSound() {
        soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.4f);
    }

}
