package com.chagui68.multiversegambling.economy;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LedgerTest {

    /** Wallets by "player@wallet"; can be told to fail. */
    static final class Fake implements EconomyProvider {
        final Map<String, Double> money = new HashMap<>();
        String current = "survival";
        boolean failDeposit;
        boolean failWithdraw;

        double of(UUID id, String w) { return money.getOrDefault(id + "@" + w, 0.0); }
        public String name() { return "fake"; }
        public String wallet(UUID id) { return current; }
        public double balance(UUID id) { return of(id, current); }
        public double balance(UUID id, String w) { return of(id, w); }
        public boolean set(UUID id, double a) { money.put(id + "@" + current, a); return true; }
        public boolean withdraw(UUID id, double a) { return withdraw(id, current, a, null); }
        public boolean deposit(UUID id, double a) { return deposit(id, current, a, null); }
        public boolean withdraw(UUID id, String w, double a, String ref) {
            if (failWithdraw || of(id, w) + 1e-9 < a) return false;
            money.put(id + "@" + w, of(id, w) - a);
            return true;
        }
        public boolean deposit(UUID id, String w, double a, String ref) {
            if (failDeposit) return false;
            money.put(id + "@" + w, of(id, w) + a);
            return true;
        }
    }

    @TempDir Path dir;
    Fake eco;
    Ledger ledger;
    File file;
    AtomicLong clock;
    final UUID p = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        eco = new Fake();
        eco.money.put(p + "@survival", 1000.0);
        eco.money.put(p + "@oneblock", 500.0);
        file = dir.resolve("journal.tsv").toFile();
        clock = new AtomicLong(1_000_000);
        ledger = newLedger(file);
    }

    Ledger newLedger(File f) {
        return new Ledger(() -> eco, new Journal(f), new StakeLimits(1000, 300, 3_600_000, 5, 10_000, 0),
                Logger.getAnonymousLogger(), clock::get);
    }

    @Test
    void stakeThenPayCreditsExactlyOnce() {
        Wager w = ledger.stake(p, 100);
        assertNotNull(w);
        assertEquals(900, eco.of(p, "survival"), 1e-9);
        assertTrue(w.pay(2));
        assertFalse(w.pay(2), "a second settlement must do nothing");
        assertFalse(w.refund());
        w.lose();
        assertEquals(1100, eco.of(p, "survival"), 1e-9);
        assertEquals(0, new Journal(file).size());
    }

    @Test
    void cannotStakeWithoutBalanceNorCreateMoney() {
        assertNull(ledger.stake(p, 1001));
        assertEquals(1000, eco.of(p, "survival"), 1e-9);
        assertNull(ledger.stake(p, 0));
        assertNull(ledger.stake(p, -5));
        assertNull(ledger.stake(p, Double.NaN));
        assertNull(ledger.stake(p, Double.POSITIVE_INFINITY));
        assertNull(ledger.stake(p, 1e300));
        assertEquals("economy.invalid-amount", ledger.takeRefusal());
        assertEquals(0, new Journal(file).size());
    }

    @Test
    void theStakeIsRoundedToCents() {
        Wager w = ledger.stake(p, 10.004);
        assertEquals(10.0, w.amount(), 1e-9);
        assertEquals(990, eco.of(p, "survival"), 1e-9);
    }

    @Test
    void aPayoutGoesToTheWalletTheStakeCameFrom() {
        Wager w = ledger.stake(p, 100);
        eco.current = "oneblock"; // the player switches wallet mid-round
        assertTrue(w.pay(2));
        assertEquals(1100, eco.of(p, "survival"), 1e-9);
        assertEquals(500, eco.of(p, "oneblock"), 1e-9);
    }

    @Test
    void aFailedPayoutStaysOwedAndIsRetried() {
        Wager w = ledger.stake(p, 100);
        eco.failDeposit = true;
        assertFalse(w.pay(3));
        assertEquals(900, eco.of(p, "survival"), 1e-9);
        assertTrue(ledger.busy(p), "money is owed: wallet changes stay blocked");
        eco.failDeposit = false;
        assertEquals(1, ledger.recover(p, null));
        assertEquals(1200, eco.of(p, "survival"), 1e-9, "the 300 owed, not the stake");
        assertFalse(ledger.busy(p));
        assertEquals(0, ledger.recover(p, null), "recovering twice pays once");
    }

    @Test
    void aCrashAfterTheStakeRefundsItAtStartup() {
        assertNotNull(ledger.stake(p, 250));
        assertEquals(750, eco.of(p, "survival"), 1e-9);
        Ledger restarted = newLedger(file); // new process, same journal file
        assertEquals(1, restarted.recover(null, null));
        assertEquals(1000, eco.of(p, "survival"), 1e-9);
        assertEquals(0, restarted.recover(null, null));
    }

    @Test
    void liveBetsAreNotRecoveredUnderTheirFeet() {
        Wager w = ledger.stake(p, 100);
        assertEquals(0, ledger.recover(null, null));
        assertTrue(w.pay(1));
        assertEquals(1000, eco.of(p, "survival"), 1e-9);
    }

    @Test
    void anUnreadableJournalLineDoesNotHideTheOthers() throws Exception {
        assertNotNull(ledger.stake(p, 100));
        java.nio.file.Files.writeString(file.toPath(), "garbage\n" + java.nio.file.Files.readString(file.toPath()));
        assertEquals(1, new Journal(file).size());
    }

    @Test
    void limitsStopHugeFastAndLosingStreaks() {
        assertNull(ledger.stake(p, 1001));
        assertEquals(StakeLimits.TOO_BIG, ledger.takeRefusal());
        eco.money.put(p + "@survival", 100000.0);
        for (int i = 0; i < 3; i++) {
            ledger.stake(p, 100).lose(); // loses 300 in total: at the cap
        }
        assertNull(ledger.stake(p, 1));
        assertEquals(StakeLimits.LOSS, ledger.takeRefusal());
        clock.addAndGet(3_600_001);
        assertNotNull(ledger.stake(p, 1), "the window moved on");
    }

    @Test
    void betsCannotBeStartedTooFast() {
        for (int i = 0; i < 5; i++) {
            ledger.stake(p, 1).pay(1);
        }
        assertNull(ledger.stake(p, 1));
        assertEquals(StakeLimits.RATE, ledger.takeRefusal());
    }

    @Test
    void anInvalidPayoutRefundsTheStakeInsteadOfPayingGarbage() {
        Wager w = ledger.stake(p, 100);
        assertTrue(w.payAbsolute(Double.NaN));
        assertEquals(1000, eco.of(p, "survival"), 1e-9);
    }

    @Test
    void aFailedWithdrawLeavesNoTraceInTheJournal() {
        eco.failWithdraw = true;
        assertNull(ledger.stake(p, 10));
        assertEquals(0, new Journal(file).size());
        assertEquals(1000, eco.of(p, "survival"), 1e-9);
    }

    @Test
    void concurrentStakesNeverSpendMoreThanTheBalance() throws Exception {
        eco.money.put(p + "@survival", 500.0);
        Ledger big = new Ledger(() -> eco, new Journal(dir.resolve("j2.tsv").toFile()),
                new StakeLimits(0, 0, 1, 0, 1, 0), Logger.getAnonymousLogger(), clock::get);
        var ok = new java.util.concurrent.atomic.AtomicInteger();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<?>> fs = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            fs.add(pool.submit(() -> { if (big.stake(p, 100) != null) ok.incrementAndGet(); }));
        }
        for (var f : fs) f.get();
        pool.shutdown();
        assertEquals(5, ok.get());
        assertEquals(0, eco.of(p, "survival"), 1e-9);
    }
}
