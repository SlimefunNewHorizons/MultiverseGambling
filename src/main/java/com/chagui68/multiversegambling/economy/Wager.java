package com.chagui68.multiversegambling.economy;

import java.util.UUID;

/**
 * A bet already taken out of the player's wallet.
 *
 * <p>It exists to make the classic casino bug impossible: paying the same round twice.
 * All the money of a round goes through here, and settling twice does nothing the
 * second time.</p>
 */
public class Wager {

    private final Ledger economy;
    private final UUID playerId;
    private final double amount;
    private final String id;
    private final String wallet;
    private boolean settled;

    Wager(Ledger economy, UUID playerId, double amount) {
        this(economy, playerId, amount, null, null);
    }

    /**
     * @param id     journal id of the bet, or {@code null} for a bet that is not journaled
     * @param wallet the wallet the stake came out of: the payout goes back to the same one
     */
    Wager(Ledger economy, UUID playerId, double amount, String id, String wallet) {
        this.economy = economy;
        this.playerId = playerId;
        this.amount = amount;
        this.id = id;
        this.wallet = wallet;
    }

    /** Identifier of this round, written to the journal and to the bank audit log. */
    public String id() {
        return id;
    }

    public UUID playerId() {
        return playerId;
    }

    public double amount() {
        return amount;
    }

    public boolean isSettled() {
        return settled;
    }

    /**
     * Pays {@code amount * multiplier}. Returns the money taken when it wins.
     */
    public boolean pay(double multiplier) {
        if (multiplier <= 0) {
            lose();
            return false;
        }
        return payAbsolute(amount * multiplier);
    }

    /**
     * Returns the capital (push or voided round).
     */
    public boolean refund() {
        return payAbsolute(amount);
    }

    /**
     * Pays an exact amount, ignoring the original bet.
     */
    public synchronized boolean payAbsolute(double payout) {
        if (settled) {
            return false;
        }
        settled = true;
        double clean = Amounts.cleanPayout(payout);
        if (Double.isNaN(clean)) {
            // Never pay a number we cannot trust; the stake goes back instead.
            economy.logger().severe("Invalid payout " + payout + " for bet " + id + "; refunding the stake.");
            clean = amount;
        }
        boolean ok = deliver(clean);
        economy.settled(this, clean, ok);
        return ok;
    }

    /**
     * Hands the payout over: money by default, items for a bet staked with items.
     */
    protected boolean deliver(double payout) {
        return economy.provider().deposit(playerId, wallet, payout, id);
    }

    /** Wallet the stake came out of, or {@code null}. */
    public String wallet() {
        return wallet;
    }

    /**
     * Marks the bet as lost: the money already left the wallet.
     */
    public synchronized void lose() {
        if (!settled) {
            settled = true;
            economy.settled(this, 0, true);
        }
    }

    /**
     * Net profit of this bet, or 0 when it has not been settled yet.
     */
    public double profit() {
        return settled ? -amount : 0;
    }
}
