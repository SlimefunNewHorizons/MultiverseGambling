package com.chagui68.multiversegambling.economy;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WalletsTest {

    @Test
    void onlyModalitiesWithTheirOwnEconomyAreWallets() {
        assertEquals("oneblock", Wallets.normalise(" OneBlock "));
        assertEquals("skyblock", Wallets.normalise("bskyblock"));
        assertNull(Wallets.normalise("lobby"));
        assertNull(Wallets.normalise("banana"));
        assertNull(Wallets.normalise(null));
    }

    @Test
    void theChosenWalletWinsWhenItsBankExists() {
        Set<String> banks = Set.of("survival", "oneblock");
        assertEquals("oneblock", Wallets.pick("oneblock", "survival", banks::contains));
    }

    @Test
    void withoutChoiceItFollowsTheWorldThenSurvival() {
        Set<String> banks = Set.of("survival", "clasico");
        assertEquals("clasico", Wallets.pick(null, "clasico", banks::contains));
        assertEquals("survival", Wallets.pick(null, "oneblock", banks::contains));
        assertEquals("survival", Wallets.pick("skyblock", "lobby", banks::contains));
    }

    @Test
    void noBankMeansNoWalletSoNothingCanBeStaked() {
        assertNull(Wallets.pick("oneblock", "survival", w -> false));
    }

    @Test
    void choicesSurviveARestart(@TempDir Path dir) {
        File file = dir.resolve("wallets").toFile();
        UUID id = UUID.randomUUID();
        Wallets first = new Wallets();
        first.put(id, "onechunk");
        assertTrue(first.save(file));
        Wallets second = new Wallets();
        second.load(file);
        assertEquals("onechunk", second.get(id));
        assertNull(second.get(UUID.randomUUID()));
    }
}
