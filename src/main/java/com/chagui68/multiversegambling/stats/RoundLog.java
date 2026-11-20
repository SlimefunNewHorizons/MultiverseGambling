package com.chagui68.multiversegambling.stats;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Persistent history of every settled round: one JSON line per round in
 * {@code history/rounds-yyyy-MM-dd.jsonl}. {@code stats.json} only keeps totals, so without this
 * it is impossible to tell which game a player won or lost at, or to audit the maths later.
 */
public final class RoundLog {

    private final File dir;

    public RoundLog(File dataFolder) {
        this.dir = new File(dataFolder, "history");
    }

    /** One line: when, who, game, stake, payout, net and WIN / LOSS / PUSH. */
    public static String line(LocalDateTime when, UUID player, String name, String game, double bet, double payout, String world) {
        double net = payout - bet;
        String result = net > 0.005 ? "WIN" : net < -0.005 ? "LOSS" : "PUSH";
        return String.format(Locale.ROOT,
                "{\"ts\":\"%s\",\"uuid\":\"%s\",\"name\":\"%s\",\"game\":\"%s\",\"bet\":%.2f,\"payout\":%.2f,\"net\":%.2f,\"result\":\"%s\",\"world\":\"%s\"}%n",
                when.withNano(0), player, esc(name), esc(game), bet, payout, net, result, esc(world));
    }

    public void append(String line) throws IOException {
        dir.mkdirs();
        Files.writeString(new File(dir, "rounds-" + LocalDate.now() + ".jsonl").toPath(), line, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
