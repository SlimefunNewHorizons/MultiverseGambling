package com.chagui68.multiversegambling.economy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.entity.Player;

/**
 * The shared pot of the group games.
 *
 * <p>It keeps one {@link Wager} per player, so the money is already out of the wallets
 * when the round starts. Paying out the pot empties the bets, which means no code path
 * can pay twice.</p>
 */
public final class Pot {

    private final EconomyManager economy;
    private final Map<UUID, Wager> wagers = new LinkedHashMap<>();

    public Pot(EconomyManager economy) {
        this.economy = economy;
    }

    /**
     * Charges the bet and puts it in the pot.
     */
    public boolean add(Player player, double amount) {
        Wager wager = economy.stake(player, amount);
        if (wager == null) {
            return false;
        }
        Wager previous = wagers.put(player.getUniqueId(), wager);
        if (previous != null) {
            previous.refund();
        }
        return true;
    }

    /**
     * Adds an already created bet (for example, the one of a player who disconnected).
     */
    public void addRaw(UUID playerId, Wager wager) {
        wagers.put(playerId, wager);
    }

    /**
     * Takes the bet out of the pot and refunds the money if it was not settled yet.
     */
    public void remove(UUID playerId) {
        Wager wager = wagers.remove(playerId);
        if (wager != null && !wager.isSettled()) {
            wager.refund();
        }
    }

    public boolean contains(UUID playerId) {
        return wagers.containsKey(playerId);
    }

    public Wager get(UUID playerId) {
        return wagers.get(playerId);
    }

    public double amountOf(UUID playerId) {
        Wager wager = wagers.get(playerId);
        return wager == null ? 0 : wager.amount();
    }

    public double total() {
        double total = 0;
        for (Wager wager : wagers.values()) {
            if (!wager.isSettled()) {
                total += wager.amount();
            }
        }
        return total;
    }

    public int size() {
        return wagers.size();
    }

    public boolean isEmpty() {
        return wagers.isEmpty();
    }

    public Set<UUID> participants() {
        return new LinkedHashSet<>(wagers.keySet());
    }

    public Map<UUID, Double> amounts() {
        Map<UUID, Double> out = new LinkedHashMap<>();
        wagers.forEach((id, wager) -> out.put(id, wager.amount()));
        return out;
    }

    /**
     * Refunds all the money and empties the pot.
     */
    public void refundAll() {
        settle((id, wager) -> wager.refund());
    }

    /**
     * The whole pot for one winner.
     */
    public void payAllTo(UUID winner) {
        payAllTo(winner, economy.plugin().config().groupHouseCut());
    }

    /**
     * The whole pot for one winner, with a commission for the house.
     */
    public void payAllTo(UUID winner, double houseCut) {
        double total = total();
        double prize = total * (1.0 - Math.max(0, Math.min(0.5, houseCut)));
        settle((id, wager) -> {
            if (id.equals(winner)) {
                wager.payAbsolute(prize);
            } else {
                wager.lose();
            }
        });
    }

    /**
     * Splits the pot evenly between several winners.
     */
    public void shareAmong(Set<UUID> winners) {
        if (winners.isEmpty()) {
            refundAll();
            return;
        }
        double share = total() / winners.size();
        settle((id, wager) -> {
            if (winners.contains(id)) {
                wager.payAbsolute(share);
            } else {
                wager.lose();
            }
        });
    }

    /**
     * Splits the pot according to what {@code prizeFor} returns: a multiplier over the
     * stake, or 0 to lose. The amount is computed before settling anything.
     */
    public void payoutByMultiplier(java.util.function.ToDoubleFunction<UUID> prizeFor) {
        Map<UUID, Double> prizes = new LinkedHashMap<>();
        wagers.forEach((id, wager) -> prizes.put(id, wager.amount() * prizeFor.applyAsDouble(id)));
        settle((id, wager) -> {
            Double prize = prizes.get(id);
            if (prize == null || prize <= 0) {
                wager.lose();
            } else {
                wager.payAbsolute(prize);
            }
        });
    }

    /**
     * Applies a result to every bet and empties the pot.
     */
    public void settle(BiConsumer<UUID, Wager> settlement) {
        wagers.forEach(settlement);
        wagers.clear();
    }

    /**
     * Marks every bet as lost without paying anybody.
     */
    public void burn() {
        settle((id, wager) -> wager.lose());
    }
}
