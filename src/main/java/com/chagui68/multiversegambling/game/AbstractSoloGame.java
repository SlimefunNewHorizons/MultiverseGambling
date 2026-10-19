package com.chagui68.multiversegambling.game;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.ItemWager;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.util.Text;

import java.time.Duration;
import java.util.function.DoubleConsumer;

import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.entity.Player;

/**
 * Solo game. The sequence is always the same: permission, stake, round. Subclasses
 * only implement {@link #start}.
 */
public abstract class AbstractSoloGame extends AbstractGame {

    /** Item bets handed in through the item menu, waiting for {@link #stake} to pick them up. */
    private final java.util.Map<java.util.UUID, ItemWager> pendingItems = new java.util.HashMap<>();

    protected AbstractSoloGame(MultiverseGamblingPlugin plugin, GameMeta meta) {
        super(plugin, meta);
    }

    /**
     * True when this game takes bets staked with items. Only games settled by a single
     * multiplier do: the item menu can then tell how many items every result gives back.
     */
    public boolean supportsItemBets() {
        return false;
    }

    /**
     * The possible results of a round and what they multiply the stake by, for the item
     * menu. Labels are in the language of the viewer.
     */
    public java.util.List<com.chagui68.multiversegambling.game.ItemOutcome> itemOutcomes(Player viewer) {
        return java.util.List.of();
    }

    /**
     * Starts a round staked with items: the game runs exactly as with money, and the
     * stake it asks for is the items already handed in.
     */
    public final void startWithItems(Player player, ItemWager wager) {
        pendingItems.put(player.getUniqueId(), wager);
        try {
            start(player, wager.amount());
        } finally {
            // A game that did not take the items (it refused to start) gives them back.
            ItemWager untouched = pendingItems.remove(player.getUniqueId());
            if (untouched != null) {
                untouched.refund();
            }
        }
    }

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
        if (plugin.sessions().busy(player.getUniqueId())) {
            message(player, "games.already-playing");
            return;
        }
        boolean items = supportsItemBets() && plugin.config().itemBetsEnabled();
        if (!items && !plugin.economy().has(player.getUniqueId(), minBet())) {
            message(player, "economy.not-enough-money", "bet", plugin.economy().format(minBet()));
            return;
        }
        plugin.guis().openBetSelector(player, this, begin(player));
    }

    /**
     * Bet confirmation callback, ready to hand over to the menu.
     */
    protected final DoubleConsumer begin(Player player) {
        return bet -> start(player, bet);
    }

    /**
     * Runs the round. It must charge the stake with {@link #stake}.
     */
    protected abstract void start(Player player, double bet);

    /**
     * Takes the stake out of the wallet.
     *
     * @return {@code null} when the balance is gone, in which case the player has
     * already been told about it
     */
    public final Wager stake(Player player, double bet) {
        ItemWager items = pendingItems.remove(player.getUniqueId());
        if (items != null) {
            return items;
        }
        Wager wager = plugin.economy().stake(player, bet);
        if (wager == null) {
            message(player, "economy.not-enough-money", "bet", plugin.economy().format(bet));
        }
        return wager;
    }

    /**
     * Result message shared by every game.
     */
    public void showResult(Player player, double bet, double payout) {
        if (payout > bet) {
            message(player, "games.win",
                    "bet", plugin.economy().format(bet),
                    "prize", plugin.economy().format(payout),
                    "profit", plugin.economy().format(payout - bet));
        } else if (payout == bet) {
            message(player, "games.tie", "bet", plugin.economy().format(bet));
        } else {
            message(player, "games.lose", "bet", plugin.economy().format(bet));
        }
    }

    /**
     * Result message for any bet: money as usual, or the items that really came back for
     * a bet staked with items.
     */
    public void showResult(Player player, Wager wager, double payout) {
        if (wager instanceof ItemWager items) {
            String name = com.chagui68.multiversegambling.economy.ItemBank.nameOf(items.item());
            if (items.paid() > 0) {
                message(player, "items.won", "count", items.paid(), "item", name, "staked", items.count());
            } else {
                message(player, "items.lost", "count", items.count(), "item", name);
            }
            return;
        }
        showResult(player, wager.amount(), payout);
    }

    /**
     * Multiplier the house can pay without emptying its chests.
     */
    public double cappedMultiplier(double multiplier, double cap) {
        return Math.min(Math.max(0, multiplier), cap);
    }

    /**
     * Offers another round with a chat button, without going back to the menu.
     */
    public void offerReplay(Player player) {
        if (!player.isOnline()) {
            return;
        }
        player.sendMessage(replayLine(player, "replay.prompt"));
    }

    /**
     * Result header with the on screen title.
     */
    public void announceResult(Player player, boolean won, String detail) {
        String title = plugin.messages().forSender(player, won ? "games.win-title" : "games.lose-title");
        String subtitle = detail == null ? "" : detail;
        player.showTitle(Title.title(Text.c(title), Text.c(subtitle), Times.times(
                Duration.ofMillis(150), Duration.ofMillis(1500), Duration.ofMillis(300))));
    }
}
