package com.chagui68.multiversegambling.economy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemRulesTest {
    static final Set<String> OK = Set.of("slimefun", "multiversecreatures");

    @Test
    void anUnpricedItemCanNeverBeStaked() {
        assertEquals("items.unpriced", ItemRules.refusal(-1, 5000, List.of(), OK));
        assertEquals("items.unpriced", ItemRules.refusal(0, 5000, List.of(), OK));
    }

    @Test
    void ceilingsAndUnknownDataAreRefused() {
        assertEquals("items.too-valuable", ItemRules.refusal(600_000, 5000, List.of("slimefun"), OK));
        assertNull(ItemRules.refusal(600_000, 0, List.of("slimefun"), OK), "0 turns the ceiling off");
        assertEquals("items.blocked", ItemRules.refusal(100, 5000, List.of("slimefun", "someplugin"), OK));
        assertNull(ItemRules.refusal(100, 5000, List.of("SlimeFun"), OK));
        assertTrue(ItemRules.betTooBig(1000, 10_000, 50_000));
        assertFalse(ItemRules.betTooBig(10, 10_000, 50_000));
    }

    @Test
    void theHouseAlwaysBites() {
        assertEquals(0.855, ItemRules.retention(0.10, 0.05), 1e-9);
        assertTrue(ItemRules.retention(0, 0) <= 1.0);
        assertEquals(0.05, ItemRules.retention(5, 5), 1e-9, "silly config is clamped");
        assertEquals(90, ItemRules.credit(100, 0.10));
        assertEquals(98, ItemRules.credit(100, 0.011));
        assertTrue(ItemRules.credit(100, 0.10) < 100);
    }
}
