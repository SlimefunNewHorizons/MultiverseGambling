package com.chagui68.multiversegambling.economy;

import static org.junit.jupiter.api.Assertions.*;

import com.chagui68.multiversegambling.util.RateLimiter;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StakeLimitsTest {

    @Test
    void amountsAreSanitised() {
        assertTrue(Double.isNaN(Amounts.clean(Double.NaN)));
        assertTrue(Double.isNaN(Amounts.clean(-1)));
        assertTrue(Double.isNaN(Amounts.clean(Double.MAX_VALUE)));
        assertTrue(Double.isNaN(Amounts.clean(0.001)));
        assertEquals(1.01, Amounts.clean(1.005), 1e-9);
        assertTrue(Double.isNaN(Amounts.cleanPayout(-0.01)));
        assertEquals(0, Amounts.cleanPayout(0), 0);
        assertEquals(Amounts.CEILING, Amounts.cleanPayout(1e300), 0);
    }

    @Test
    void winningsGiveRoomAgain() {
        StakeLimits l = new StakeLimits(0, 100, 1000, 0, 1, 0);
        UUID p = UUID.randomUUID();
        l.staked(p, 90, 0);
        assertEquals(StakeLimits.LOSS, l.check(p, 20, 1));
        l.paid(p, 90, 2);
        assertNull(l.check(p, 20, 3));
    }

    @Test
    void winAlertFiresOnlyWhenFarAhead() {
        StakeLimits l = new StakeLimits(0, 0, 1000, 0, 1, 500);
        UUID p = UUID.randomUUID();
        l.staked(p, 100, 0);
        l.paid(p, 700, 1);
        assertTrue(l.winAlert(p, 2));
        assertFalse(l.winAlert(p, 5000));
    }

    @Test
    void rateLimiterWindowSlides() {
        RateLimiter r = new RateLimiter(2, 1000);
        assertTrue(r.tryAcquire("a", 0));
        assertTrue(r.tryAcquire("a", 10));
        assertFalse(r.tryAcquire("a", 20));
        assertTrue(r.tryAcquire("b", 20));
        assertTrue(r.tryAcquire("a", 1000));
    }
}
