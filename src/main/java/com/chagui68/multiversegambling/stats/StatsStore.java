package com.chagui68.multiversegambling.stats;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.util.JsonStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Record of everything that happens in the casino, stored in {@code stats.json}.
 *
 * <p>A stats.json that cannot be parsed never stops the plugin: {@link JsonStore} moves
 * it aside and the history starts empty.</p>
 */
public final class StatsStore {

    private static final Type MAP_TYPE = new TypeToken<Map<String, PlayerStats>>() {
    }.getType();

    public record TopEntry(UUID playerId, String name, PlayerStats stats) {
    }

    private final MultiverseGamblingPlugin plugin;
    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, PlayerStats> stats = new HashMap<>();
    private final RoundLog rounds;

    public StatsStore(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.json");
        this.rounds = new RoundLog(plugin.getDataFolder());
        load();
    }

    public PlayerStats of(UUID playerId) {
        return stats.computeIfAbsent(playerId, id -> new PlayerStats());
    }

    public PlayerStats peek(UUID playerId) {
        return stats.get(playerId);
    }

    /**
     * Records a round that has already been settled.
     *
     * @param bet    what was taken out of the wallet
     * @param payout what was returned to the player
     */
    public void record(UUID playerId, String gameId, double bet, double payout) {
        PlayerStats entry = of(playerId);
        entry.games++;
        entry.wagered += bet;
        entry.returned += payout;
        entry.plays.merge(gameId, 1, Integer::sum);
        if (payout > bet) {
            entry.wins++;
        }
        entry.biggestWin = Math.max(entry.biggestWin, payout);
        entry.biggestLoss = Math.max(entry.biggestLoss, bet - payout);
        try {
            var online = plugin.getServer().getPlayer(playerId);
            rounds.append(RoundLog.line(java.time.LocalDateTime.now(), playerId, nameOf(playerId), gameId, bet, payout,
                    online == null ? "" : online.getWorld().getName()));
        } catch (java.io.IOException | RuntimeException error) {
            // The history is an audit aid: never let it break a round that is already paid.
            plugin.getLogger().warning("Could not write the round history: " + error.getMessage());
        }
    }

    /**
     * Ranking by net profit.
     */
    public List<TopEntry> topByProfit(int limit) {
        return ranking(Comparator.comparingDouble((TopEntry entry) -> entry.stats().profit()).reversed(), limit);
    }

    public List<TopEntry> topByWagered(int limit) {
        return ranking(Comparator.comparingDouble((TopEntry entry) -> entry.stats().wagered).reversed(), limit);
    }

    public List<TopEntry> topByBiggestWin(int limit) {
        return ranking(Comparator.comparingDouble((TopEntry entry) -> entry.stats().biggestWin).reversed(), limit);
    }

    private List<TopEntry> ranking(Comparator<TopEntry> comparator, int limit) {
        List<TopEntry> entries = new ArrayList<>();
        stats.forEach((id, value) -> entries.add(new TopEntry(id, nameOf(id), value)));
        entries.sort(comparator);
        return entries.size() > limit ? entries.subList(0, limit) : entries;
    }

    private String nameOf(UUID playerId) {
        var player = plugin.getServer().getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }
        // A player may have no resolvable name (account deleted from the proxy), and a
        // "null" in the ranking looks terrible.
        String name = plugin.getServer().getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    /**
     * Total money the house has won.
     */
    public double houseProfit() {
        double profit = 0;
        for (PlayerStats entry : stats.values()) {
            profit -= entry.profit();
        }
        return profit;
    }

    private void load() {
        Map<String, PlayerStats> raw = JsonStore.read(plugin.getLogger(), file, gson, MAP_TYPE);
        if (raw == null) {
            return;
        }
        raw.forEach((key, value) -> {
            try {
                stats.put(UUID.fromString(key), value == null ? new PlayerStats() : value);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid UUID in stats.json: " + key);
            }
        });
    }

    public void save() {
        Map<String, PlayerStats> raw = new HashMap<>();
        stats.forEach((id, value) -> raw.put(id.toString(), value));
        JsonStore.write(plugin.getLogger(), file, gson, MAP_TYPE, raw);
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }
}
