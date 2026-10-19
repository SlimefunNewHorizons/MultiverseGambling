package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.HouseDuelTable;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.BarrelShow;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Turn based Russian roulette.
 *
 * <p>Every player pulls the trigger on their turn. The chance of a shot is exactly
 * {@code bullets / chambers} on each pull, decided by the provably fair generator, so
 * the round is as auditable as any other casino bet. Whoever falls leaves their money
 * in the pot for the survivor.</p>
 *
 * <p>A player who is alone in the room does not have to wait: they can take on
 * <b>the house</b> instead. That duel spins the cylinder once, the player pulls first
 * and the dealer answers, and a win pays {@link HouseDuelTable}, which is the same game
 * with the dealer as the only rival.</p>
 */
public final class RussianRouletteGame extends AbstractGroupGame {

    private static final int FIRST_DELAY_TICKS = 40;
    /** Ticks between two pulls of the duel, so the shot is watched and not skipped. */
    private static final int DUEL_TURN_TICKS = 45;

    private final List<UUID> order = new ArrayList<>();
    private final Set<UUID> alive = new LinkedHashSet<>();
    private int turnIndex;
    private int turnTicks;
    private boolean started;
    /** Full laps of the table in this round, and the bullets they added. */
    private int laps;
    private int extraBullets;
    /** Pulls taken in the duel against the house. */
    private int duelTurn;
    /** Chamber that fires first in the duel, counted from 1. */
    private int duelFirstLoaded;
    /** Cylinder painted on the arena, when there is one. */
    private BarrelShow show;

    public RussianRouletteGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("russian-roulette", "Russian Roulette", GameCategory.GROUP, Material.FLINT_AND_STEEL)
                .desc("&7Players take turns pulling the trigger.",
                        "&7With &f1 bullet in 6 chambers&7, luck",
                        "&7decides who stays on their feet.")
                .players(2, 8)
                .build());
    }

    private int chambers() {
        return plugin.config().russianRouletteChambers();
    }

    private int bullets() {
        return Math.min(plugin.config().russianRouletteBullets() + extraBullets, chambers() - 1);
    }

    /**
     * A full lap of the table went by: every few laps one more chamber is loaded, so a
     * table of lucky players does not go on forever.
     */
    private void lapDone() {
        laps++;
        int every = plugin.config().russianRouletteBulletEvery();
        if (every <= 0 || laps % every != 0 || bullets() >= chambers() - 1) {
            return;
        }
        extraBullets++;
        broadcastPlain("group.russian-roulette.extra-bullet",
                "laps", laps, "bullets", bullets(), "chambers", chambers(),
                "chance", Text.percent((double) bullets() / chambers()));
        soundAll(Sound.ITEM_CROSSBOW_LOADING_END, 1.0f, 0.8f);
        if (show != null) {
            show.addBullet();
        }
    }

    /** The head of a player, or a skull for the dealer. */
    private ItemStack headOf(UUID id) {
        if (isHouse(id)) {
            return new ItemStack(Material.WITHER_SKELETON_SKULL);
        }
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            Player player = online(id);
            meta.setOwningPlayer(player != null ? player : plugin.getServer().getOfflinePlayer(id));
            head.setItemMeta(meta);
        }
        return head;
    }

    /** Puts the heads in front of the barrel, whoever shoots next at the muzzle. */
    private void showLineup(List<UUID> inTurn) {
        if (show == null) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (UUID id : inTurn) {
            stacks.add(headOf(id));
            names.add(playerName(id));
        }
        show.lineup(stacks, names);
    }

    /** Players still standing, starting with whoever holds the revolver now. */
    private List<UUID> turnOrder() {
        List<UUID> out = new ArrayList<>();
        for (int k = 0; k < order.size(); k++) {
            UUID id = order.get((turnIndex + k) % order.size());
            if (alive.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** The duel: the player and the dealer, whoever pulls next first. */
    private List<UUID> duelOrder() {
        UUID playerId = houseDuelPlayer();
        boolean playerNext = (duelTurn + 1) % 2 == 1;
        return playerNext ? List.of(playerId, FairnessService.HOUSE) : List.of(FairnessService.HOUSE, playerId);
    }

    @Override
    protected boolean houseDuelAvailable() {
        return true;
    }

    @Override
    protected double houseDuelMultiplier(Player player) {
        return HouseDuelTable.multiplier(chambers(), bullets(), plugin.config().houseEdge());
    }

    private UUID current() {
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        return order.get(turnIndex % Math.max(1, order.size()));
    }

    @Override
    protected void onRoundStart() {
        order.clear();
        order.addAll(pot.participants());
        alive.clear();
        alive.addAll(order);
        turnIndex = 0;
        turnTicks = 0;
        started = false;
        timer = 0;
        laps = 0;
        extraBullets = 0;

        if (houseDuelActive()) {
            startDuel();
            return;
        }

        broadcastRoundHeader();
        broadcastPlain("panel.common.pot", "pot", plugin.economy().format(pot.total()));
        broadcastPlain("group.russian-roulette.revolver",
                "bullets", bullets(), "chambers", chambers(),
                "chance", Text.percent((double) bullets() / chambers()));
        if (plugin.config().russianRouletteBulletEvery() > 0) {
            broadcastPlain("group.russian-roulette.escalation",
                    "laps", plugin.config().russianRouletteBulletEvery());
        }
        broadcastPlain("group.russian-roulette.turn",
                "player", playerName(current()),
                "seconds", plugin.config().groupCountdownSeconds());

        ArenaStage stage = gatherArena();
        if (stage != null) {
            show = new BarrelShow(plugin, stage, chambers(), bullets(),
                    Math.max(60, plugin.config().groupCountdownSeconds() * 20 * 4));
            show.start();
        }
        prompt();
    }

    /**
     * The duel against the house: one player, the dealer in the other chair and a
     * cylinder spun once, so the chambers fire in a fixed order.
     */
    private void startDuel() {
        UUID playerId = houseDuelPlayer();
        order.add(playerId);
        alive.add(playerId);
        duelTurn = 0;
        duelFirstLoaded = firstLoadedChamber();
        broadcastPlain("group.russian-roulette.duel-intro",
                "bullets", bullets(), "chambers", chambers(),
                "multiplier", Text.multiplier(houseDuelMultiplier(online(playerId))));

        ArenaStage stage = gatherArena();
        if (stage != null) {
            show = new BarrelShow(plugin, stage, chambers(), bullets(),
                    Math.max(60, DUEL_TURN_TICKS * (chambers() + 2)));
            show.start();
            showLineup(duelOrder());
        }
    }

    /**
     * Where the first loaded chamber sits, drawn from the provably fair generator: the
     * loaded chambers are the ones with the lowest rolls, which is a uniform choice of
     * {@code bullets} chambers out of {@code chambers}. The dealer has no advantage in
     * the draw, and the same draw is what the table pays on.
     */
    private int firstLoadedChamber() {
        int count = chambers();
        double[] rolls = plugin.fair().rolls(FairnessService.HOUSE, count);
        int[] chambersByRoll = new int[count];
        for (int index = 0; index < count; index++) {
            chambersByRoll[index] = index;
        }
        // Small insertion sort: the cylinder is tiny and the order has to be exact.
        for (int index = 1; index < count; index++) {
            int value = chambersByRoll[index];
            int slot = index - 1;
            while (slot >= 0 && rolls[chambersByRoll[slot]] > rolls[value]) {
                chambersByRoll[slot + 1] = chambersByRoll[slot];
                slot--;
            }
            chambersByRoll[slot + 1] = value;
        }
        int first = count;
        for (int index = 0; index < bullets(); index++) {
            first = Math.min(first, chambersByRoll[index]);
        }
        return first + 1;
    }

    /**
     * One pull of the duel per turn: the player first, then the dealer.
     */
    private void tickDuel() {
        if (timer < FIRST_DELAY_TICKS || (timer - FIRST_DELAY_TICKS) % DUEL_TURN_TICKS != 0) {
            return;
        }
        int turn = ++duelTurn;
        boolean playerTurn = turn % 2 == 1;
        boolean fires = turn == duelFirstLoaded;
        if (show != null) {
            show.pull(fires);
        }
        UUID playerId = houseDuelPlayer();
        Player player = online(playerId);

        if (!fires) {
            broadcastPlain(playerTurn ? "group.russian-roulette.duel-empty-you"
                    : "group.russian-roulette.duel-empty-house");
            showLineup(duelOrder());
            return;
        }

        showTitle(player, "group.russian-roulette.bang-title", "group.russian-roulette.bang-subtitle");
        soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        if (playerTurn) {
            // The player met the bullet: the stake stays with the house.
            broadcastPlain("group.russian-roulette.duel-lost",
                    "amount", plugin.economy().format(pot.amountOf(playerId)));
            endRound();
            return;
        }

        double multiplier = houseDuelMultiplier(player);
        double prize = pot.amountOf(playerId) * multiplier;
        pot.payoutByMultiplier(id -> id.equals(playerId) ? multiplier : 0);
        broadcastPlain("group.russian-roulette.duel-won",
                "prize", plugin.economy().format(prize),
                "multiplier", Text.multiplier(multiplier));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        showTitle(player, "group.russian-roulette.title",
                "group.russian-roulette.title-subtitle",
                "prize", plugin.economy().format(prize));
        endRound();
    }

    @Override
    protected void tickRound() {
        timer++;
        if (houseDuelActive()) {
            tickDuel();
            return;
        }
        if (timer < FIRST_DELAY_TICKS) {
            return;
        }
        if (!started) {
            started = true;
            return;
        }
        if (alive.size() <= 1) {
            settle();
            return;
        }
        turnTicks++;
        UUID currentId = current();
        Player player = online(currentId);
        if (player != null) {
            int left = Math.max(0, plugin.config().groupCountdownSeconds() - turnTicks / 20);
            actionBarKey(player, "group.russian-roulette.your-turn",
                    "seconds", left, "pot", plugin.economy().format(pot.total()));
        }
        if (turnTicks % 20 == 0 && turnTicks / 20 > 0) {
            soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.5f);
        }
        if (turnTicks >= plugin.config().groupCountdownSeconds() * 20) {
            broadcastPlain("group.russian-roulette.out-of-time", "player", playerName(currentId));
            pull(currentId);
        }
    }

    /**
     * Pulls the trigger.
     */
    public void trigger(Player player, boolean fromChat) {
        if (alive.size() <= 1) {
            message(player, "group.russian-roulette-finished");
            return;
        }
        UUID currentId = current();
        if (!currentId.equals(player.getUniqueId())) {
            message(player, "group.russian-roulette-not-your-turn", "player", playerName(currentId));
            return;
        }
        pull(currentId);
    }

    private void pull(UUID shooter) {
        boolean fires = plugin.fair().rollInt(FairnessService.HOUSE, chambers()) < bullets();
        turnTicks = 0;
        if (show != null) {
            show.pull(fires);
        }
        Player player = online(shooter);

        if (!fires) {
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 0.6f);
            }
            broadcastPlain("group.russian-roulette.empty", "player", playerName(shooter));
            advance();
            return;
        }

        alive.remove(shooter);
        soundAll(Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        if (player != null) {
            showTitle(player, "group.russian-roulette.bang-title",
                    "group.russian-roulette.bang-subtitle");
        }
        broadcastPlain("group.russian-roulette.bang",
                "player", playerName(shooter),
                "amount", plugin.economy().format(pot.amountOf(shooter)));
        broadcastPlain("group.russian-roulette.pot-now",
                "pot", plugin.economy().format(pot.total()), "alive", alive.size());

        if (alive.size() <= 1) {
            settle();
            return;
        }
        // The cylinder spins again and the revolver moves on to the next player.
        order.remove(shooter);
        if (turnIndex >= order.size()) {
            turnIndex = 0;
            lapDone();
        }
        prompt();
    }

    private void advance() {
        turnIndex++;
        if (turnIndex >= order.size()) {
            turnIndex = 0;
            lapDone();
        }
        prompt();
    }

    private void prompt() {
        if (alive.size() <= 1) {
            return;
        }
        showLineup(turnOrder());
        UUID currentId = current();
        Player player = online(currentId);
        if (player == null) {
            return;
        }
        player.sendMessage(plugin.messages().componentPlainFor(player, "group.russian-roulette.prompt")
                .append(chatButton(
                        plugin.messages().forSender(player, "group.russian-roulette.pull"), "shoot",
                        plugin.messages().forSender(player, "group.russian-roulette.pull-hover",
                                "chance", Text.percent((double) bullets() / chambers())))));
    }

    private void settle() {
        if (alive.isEmpty()) {
            broadcastPlain("group.russian-roulette.no-survivors");
            pot.burn();
            endRound();
            return;
        }
        UUID winner = alive.iterator().next();
        double total = pot.total();
        pot.payAllTo(winner);
        broadcastPlain("group.russian-roulette.banner");
        broadcastPlain("group.russian-roulette.winner",
                "player", playerName(winner), "pot", plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        showTitle(online(winner), "group.russian-roulette.title",
                "group.russian-roulette.title-subtitle",
                "prize", plugin.economy().format(total));
        endRound();
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("shoot".equals(action)) {
            trigger(player, true);
            return;
        }
        super.handleAction(player, action, args);
    }

    @Override
    protected void onQuitDuringRound(UUID playerId) {
        alive.remove(playerId);
        order.remove(playerId);
        if (turnIndex >= order.size()) {
            turnIndex = 0;
        }
        if (alive.size() <= 1 && !alive.isEmpty()) {
            settle();
        } else {
            showLineup(turnOrder());
        }
    }

    @Override
    protected void onRoundEnd() {
        if (show != null) {
            // The scenery is left standing for a moment, so the last shot is seen.
            show.settle();
            show = null;
        }
        order.clear();
        alive.clear();
        turnIndex = 0;
        turnTicks = 0;
        duelTurn = 0;
        started = false;
        laps = 0;
        extraBullets = 0;
    }
}
