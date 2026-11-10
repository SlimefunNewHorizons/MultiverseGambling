package com.chagui68.multiversegambling.economy;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Write-ahead journal of the bets taken from a wallet and not settled yet.
 *
 * <p>A bet is written here <b>after</b> it leaves the wallet and removed <b>after</b> the
 * payout (or the decision that it was lost) reached the wallet. Whatever is still here when
 * the server starts is a crash, a kick or a failed payout, and is returned to its owner,
 * so money is never silently lost. Every change rewrites the file atomically.</p>
 */
public final class Journal {

    /**
     * @param owed  amount still owed to the player, or -1 when the stake itself is to be refunded
     * @param item  Base64 of the staked item for an item bet, or empty for money
     */
    public record Entry(String id, UUID player, String wallet, double stake, double owed, String item) {
        public Entry withOwed(double amount) {
            return new Entry(id, player, wallet, stake, amount, item);
        }

        public double due() {
            return owed >= 0 ? owed : stake;
        }
    }

    private final File file;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public Journal(File file) {
        this.file = file;
        load();
    }

    public synchronized void open(Entry entry) throws IOException {
        entries.put(entry.id(), entry);
        save();
    }

    public synchronized void owe(String id, double amount) throws IOException {
        Entry entry = entries.get(id);
        if (entry != null) {
            entries.put(id, entry.withOwed(amount));
            save();
        }
    }

    /** @return true when the entry existed and its removal is on disk */
    public synchronized boolean close(String id) throws IOException {
        if (entries.remove(id) == null) {
            return false;
        }
        save();
        return true;
    }

    public synchronized boolean has(UUID player) {
        return entries.values().stream().anyMatch(e -> e.player().equals(player));
    }

    public synchronized List<Entry> all() {
        return new ArrayList<>(entries.values());
    }

    public synchronized int size() {
        return entries.size();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] p = line.split("\t", -1);
                if (p.length < 6) {
                    continue;
                }
                try {
                    entries.put(p[0], new Entry(p[0], UUID.fromString(p[1]), p[2],
                            Double.parseDouble(p[3]), Double.parseDouble(p[4]), p[5]));
                } catch (IllegalArgumentException ignored) {
                    // a damaged line must not hide the others
                }
            }
        } catch (IOException ignored) {
            // unreadable: start empty, the caller logs nothing it could act on
        }
    }

    private void save() throws IOException {
        StringBuilder out = new StringBuilder();
        for (Entry e : entries.values()) {
            out.append(e.id()).append('\t').append(e.player()).append('\t').append(e.wallet() == null ? "" : e.wallet())
                    .append('\t').append(e.stake()).append('\t').append(e.owed()).append('\t')
                    .append(e.item() == null ? "" : e.item()).append('\n');
        }
        File dir = file.getAbsoluteFile().getParentFile();
        dir.mkdirs();
        File tmp = new File(dir, file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), out.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
