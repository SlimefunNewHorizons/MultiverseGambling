package com.chagui68.multiversegambling.economy;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Every stake and every payout of the casino goes through here, one player at a time.
 *
 * <p>The order is what keeps money honest: take the stake, <b>then</b> write it to the
 * journal; pay, <b>then</b> erase it from the journal. A failure in between is never
 * silent: a stake that cannot be journaled is handed back, and a payout that cannot be
 * delivered stays in the journal as money owed, and is retried (on join, on the next bet
 * and at startup) until it lands.</p>
 */
public final class Ledger {

    /** Hands a recovered item bet back (the casino's item bank). */
    public interface ItemSink {
        void give(UUID player, String itemBase64, int count);
    }

    private final Supplier<EconomyProvider> provider;
    private final Journal journal;
    private final StakeLimits limits;
    private final Logger log;
    private final java.util.function.LongSupplier clock;
    private final Set<String> live = new HashSet<>();
    private final java.util.concurrent.ConcurrentHashMap<UUID, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();
    private long counter;
    private String refusal;

    public Ledger(Supplier<EconomyProvider> provider, Journal journal, StakeLimits limits, Logger log,
                  java.util.function.LongSupplier clock) {
        this.provider = provider;
        this.journal = journal;
        this.limits = limits;
        this.log = log;
        this.clock = clock;
    }

    EconomyProvider provider() {
        return provider.get();
    }

    Logger logger() {
        return log;
    }

    private Object lock(UUID id) {
        return locks.computeIfAbsent(id, k -> new Object());
    }

    /** Message key of why the last {@code stake} returned null, or null if it was just a lack of funds. */
    public synchronized String takeRefusal() {
        String r = refusal;
        refusal = null;
        return r;
    }

    private synchronized void refuse(String key) {
        refusal = key;
    }

    /**
     * Takes a bet out of the wallet and journals it.
     *
     * @return the bet, or {@code null} (see {@link #takeRefusal()})
     */
    public Wager stake(UUID player, double amount) {
        double clean = Amounts.clean(amount);
        if (Double.isNaN(clean) || clean <= 0) {
            refuse("economy.invalid-amount");
            return null;
        }
        EconomyProvider eco = provider();
        synchronized (lock(player)) {
            retryOwed(player, null);
            long now = clock.getAsLong();
            String why = limits.check(player, clean, now);
            if (why != null) {
                refuse(why);
                return null;
            }
            String wallet = eco.wallet(player);
            String id = newId();
            if (!eco.has(player, clean) || !eco.withdraw(player, wallet, clean, id)) {
                return null;
            }
            try {
                journal.open(new Journal.Entry(id, player, wallet, clean, -1, ""));
            } catch (IOException error) {
                log.severe("Cannot write the bet journal (" + error.getMessage() + "); bet " + id + " cancelled.");
                if (!eco.deposit(player, wallet, clean, id)) {
                    log.severe("MONEY AT RISK: could not give back " + clean + " to " + player + " (bet " + id + ").");
                }
                refuse("economy.journal-failed");
                return null;
            }
            synchronized (this) {
                live.add(id);
            }
            limits.staked(player, clean, now);
            return new Wager(this, player, clean, id, wallet);
        }
    }

    private synchronized String newId() {
        return Long.toString(clock.getAsLong(), 36) + "-" + Long.toString(++counter, 36)
                + "-" + Integer.toString(java.util.concurrent.ThreadLocalRandom.current().nextInt(1296), 36);
    }

    /**
     * Journals an item bet whose items were already taken from the player.
     *
     * @return the id of the bet, or {@code null} when it could not be journaled
     */
    public String openItems(UUID player, String itemBase64, int count) {
        if (count <= 0) {
            return null;
        }
        String id = newId();
        synchronized (lock(player)) {
            try {
                journal.open(new Journal.Entry(id, player, null, count, -1, itemBase64));
            } catch (IOException error) {
                log.severe("Cannot write the bet journal (" + error.getMessage() + "); item bet " + id + " not journaled.");
                return null;
            }
            synchronized (this) {
                live.add(id);
            }
        }
        return id;
    }

    /**
     * Called by a bet when it is settled. {@code paid} is what the player gets; {@code ok} says
     * whether it reached the wallet.
     */
    void settled(Wager wager, double paid, boolean ok) {
        String id = wager.id();
        if (id == null) {
            return;
        }
        UUID player = wager.playerId();
        synchronized (lock(player)) {
            limits.paid(player, paid, clock.getAsLong());
            if (limits.winAlert(player, clock.getAsLong())) {
                log.warning("ALERT: " + player + " is far ahead of the house this hour (bet " + id + ").");
            }
            synchronized (this) {
                live.remove(id);
            }
            try {
                if (ok) {
                    journal.close(id);
                } else {
                    journal.owe(id, paid);
                    log.severe("Payout of " + paid + " to " + player + " (bet " + id + ") failed; kept in the journal and retried.");
                }
            } catch (IOException error) {
                log.severe("Cannot update the bet journal for bet " + id + " (" + error.getMessage()
                        + "); the bet may be refunded again after a restart.");
            }
        }
    }

    /** True while the player has any bet not yet settled or paid. */
    public boolean busy(UUID player) {
        return journal.has(player);
    }

    /**
     * Gives back what the journal still holds: after a crash, a kick, a reload or a failed payout.
     * Bets that are alive in this session are not touched.
     *
     * @param only restrict to one player, or {@code null} for everybody
     * @return how many entries were settled
     */
    public int recover(UUID only, ItemSink items) {
        int done = 0;
        for (Journal.Entry entry : journal.all()) {
            if (only != null && !entry.player().equals(only)) {
                continue;
            }
            synchronized (lock(entry.player())) {
                synchronized (this) {
                    if (live.contains(entry.id())) {
                        continue;
                    }
                }
                if (settleEntry(entry, items)) {
                    done++;
                }
            }
        }
        return done;
    }

    private void retryOwed(UUID player, ItemSink items) {
        recover(player, items);
    }

    private boolean settleEntry(Journal.Entry entry, ItemSink items) {
        boolean paid;
        if (!entry.item().isEmpty()) {
            if (items == null) {
                return false;
            }
            items.give(entry.player(), entry.item(), (int) Math.round(entry.due()));
            paid = true;
        } else {
            paid = entry.due() <= 0 || provider().deposit(entry.player(), entry.wallet(), entry.due(), entry.id());
        }
        if (!paid) {
            log.warning("Bet " + entry.id() + " of " + entry.player() + " still cannot be returned (" + entry.due() + ").");
            return false;
        }
        try {
            journal.close(entry.id());
        } catch (IOException error) {
            log.severe("Returned bet " + entry.id() + " but could not clear it from the journal: " + error.getMessage());
        }
        log.warning("Returned unsettled bet " + entry.id() + ": " + entry.due() + " to " + entry.player() + ".");
        return true;
    }

    List<Journal.Entry> pending() {
        return journal.all();
    }
}
