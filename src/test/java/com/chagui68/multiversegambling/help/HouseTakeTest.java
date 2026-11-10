package com.chagui68.multiversegambling.help;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HouseTakeTest {
    @Test
    void everyGameKindHasAKeyAndAShare() {
        assertEquals(HouseTake.Kind.RAKE, HouseTake.kindOf("poker"));
        for (String pot : new String[]{"jackpot", "duel", "dice-poker", "hot-bomb", "russian-roulette", "bomb-board"}) {
            assertEquals(HouseTake.Kind.POT_CUT, HouseTake.kindOf(pot));
        }
        assertEquals(HouseTake.Kind.EDGE, HouseTake.kindOf("slots"));
        assertEquals(0.02, HouseTake.share(HouseTake.Kind.EDGE, 0.02, 0.03, 0.04), 0);
        assertEquals(0.03, HouseTake.share(HouseTake.Kind.POT_CUT, 0.02, 0.03, 0.04), 0);
        assertEquals(0.04, HouseTake.share(HouseTake.Kind.RAKE, 0.02, 0.03, 0.04), 0);
        assertEquals("help.take-pot", HouseTake.key(HouseTake.Kind.POT_CUT));
    }
}
