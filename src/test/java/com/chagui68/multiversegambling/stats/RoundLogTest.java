package com.chagui68.multiversegambling.stats;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundLogTest {
    private static final UUID ID = UUID.fromString("43595861-122d-4633-b77f-225a157ff422");

    @Test
    void marcaGanaPierdeEmpate() {
        var t = LocalDateTime.of(2026, 11, 20, 10, 6, 14);
        assertTrue(RoundLog.line(t, ID, "Moltzy", "coin-flip", 2992, 0, "survival").contains("\"result\":\"LOSS\",\"world\":\"survival\""));
        assertTrue(RoundLog.line(t, ID, "Moltzy", "coin-flip", 2250, 4410, "survival").contains("\"net\":2160.00,\"result\":\"WIN\""));
        assertTrue(RoundLog.line(t, ID, "Moltzy", "slots", 100, 100, "").contains("\"result\":\"PUSH\""));
    }

    @Test
    void escapaComillasEnElNombre() {
        assertTrue(RoundLog.line(LocalDateTime.now(), ID, "a\"b", "crash", 1, 0, "w").contains("\"name\":\"a\\\"b\""));
    }
}
