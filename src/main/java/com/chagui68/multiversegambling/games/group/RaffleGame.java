package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.engine.WeightedTable;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaShow;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.Props;
import com.chagui68.multiversegambling.world.anim.RaffleShow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Raffle with three prizes.
 *
 * <p>Tickets are sold at a fixed price: each one costs what the configuration says
 * and gives one more chance. The draw splits the pot into three prizes (70%, 20% and
 * 10%), so being lucky once is not enough: you want to be near the top of the
 * draw order.</p>
 */
public final class RaffleGame extends AbstractGroupGame {

    private static final double[] PRIZES = {0.70, 0.20, 0.10};
    private static final int PREPARE_TICKS = 40;
    private static final int DRAW_TIMER = 100;

    /** Colour of each player's balls in the drum, cycled when out of colours. */
    private static final Material[] SECTOR_COLOURS = {
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.MAGENTA_CONCRETE, Material.CYAN_CONCRETE,
            Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE, Material.PINK_CONCRETE,
            Material.WHITE_CONCRETE, Material.BROWN_CONCRETE, Material.BLACK_CONCRETE
    };

    private final Map<UUID, Integer> tickets = new LinkedHashMap<>();
    /** Winners of the three prizes, best first. */
    private List<UUID> order = new ArrayList<>();
    /** Drum and podium painted on the arena, when there is one. */
    private ArenaShow show;

    public RaffleGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("raffle", "Raffle", GameCategory.GROUP, Material.FIREWORK_STAR)
                .desc("&7Fixed price tickets. The draw splits",
                        "&7the pot into three prizes:",
                        "&f70%&7, &f20% &7and &f10%&7.")
                .players(2, 24)
                .build());
    }

    private double ticketPrice() {
        return plugin.config().raffleTicketPrice();
    }

    @Override
    protected boolean houseDuelAvailable() {
        return true;
    }

    /**
     * Against the house, the house buys as many tickets as the player and a single prize
     * is drawn: an even draw, paid with the house edge.
     */
    @Override
    protected double houseDuelMultiplier(Player player) {
        return fairDuelMultiplier(0.5);
    }

    @Override
    protected void onBetPlaced(Player player, double amount) {
        int bought = Math.max(1, (int) Math.floor(amount / ticketPrice() + 1e-9));
        int capped = Math.min(bought, plugin.config().raffleMaxTickets());
        double cost = capped * ticketPrice();
        if (Math.abs(cost - amount) > 0.004) {
            // Only whole tickets are sold: the stake is trimmed to what the tickets cost,
            // so the chances in the draw always match the money put in.
            pot.remove(player.getUniqueId());
            if (!pot.add(player, cost)) {
                tickets.remove(player.getUniqueId());
                message(player, "economy.not-enough-money", "bet", plugin.economy().format(cost));
                return;
            }
        }
        tickets.put(player.getUniqueId(), capped);
        int total = tickets.values().stream().mapToInt(Integer::intValue).sum();
        broadcastPlain("group.raffle.tickets-bought",
                "player", player.getName(),
                "tickets", capped,
                "amount", plugin.economy().format(amount),
                "total", total);
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        // Tickets of anybody who left the room before the draw go with them.
        tickets.keySet().removeIf(id -> !pot.contains(id));
        if (houseDuelActive()) {
            UUID playerId = houseDuelPlayer();
            // The house buys exactly as many tickets as the player.
            tickets.put(FairnessService.HOUSE, tickets.getOrDefault(playerId, 1));
            broadcastRoundHeader();
            broadcastPlain("group.raffle.duel-intro", "tickets", tickets.getOrDefault(playerId, 1),
                    "multiplier", Text.multiplier(houseDuelMultiplier(null)));
            return;
        }
        broadcastRoundHeader();
        int total = tickets.values().stream().mapToInt(Integer::intValue).sum();
        broadcastPlain("group.raffle.sold",
                "total", total, "price", plugin.economy().format(ticketPrice()));
        broadcastPlain("group.raffle.pot", "pot", plugin.economy().format(pot.total()));
        for (Map.Entry<UUID, Integer> entry : tickets.entrySet()) {
            double share = pot.amountOf(entry.getKey()) / Math.max(0.0001, pot.total());
            broadcastPlain("group.raffle.line",
                    "player", playerName(entry.getKey()),
                    "tickets", entry.getValue(),
                    "chance", String.format("%.1f%%", share * 100));
        }
        broadcastPlain("group.raffle.starting");
    }

    @Override
    protected void tickRound() {
        timer++;
        if (timer == 1) {
            // Draw without replacement, weighted by tickets and with provably fair rolls.
            // The tickets are sold, so it is drawn right away: the drum in the arena then
            // hands out the prizes to the players that really won them.
            order = new ArrayList<>();
            Map<UUID, Integer> remaining = new LinkedHashMap<>(tickets);
            for (int prize = 0; prize < PRIZES.length && !remaining.isEmpty(); prize++) {
                WeightedTable<UUID> draw = new WeightedTable<>();
                for (Map.Entry<UUID, Integer> entry : remaining.entrySet()) {
                    draw.add(entry.getKey(), Math.max(1, entry.getValue()));
                }
                UUID winner = draw.roll(plugin.fair().roll(FairnessService.HOUSE));
                order.add(winner);
                remaining.remove(winner);
            }
            show = startShow();
        }
        if (timer <= PREPARE_TICKS + DRAW_TIMER && show != null) {
            show.tick();
            return;
        }
        if (timer <= PREPARE_TICKS) {
            if (timer % 20 == 0) {
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                actionBarAllKey("group.raffle.preparing");
            }
            return;
        }
        int elapsed = timer - PREPARE_TICKS;
        if (elapsed <= DRAW_TIMER) {
            // No arena: the draw is told in the action bar instead.
            double progress = (double) elapsed / DRAW_TIMER;
            int wait = 1 + (int) (progress * progress * 8);
            if (elapsed % wait == 0) {
                List<UUID> pool = new ArrayList<>(tickets.keySet());
                if (!pool.isEmpty()) {
                    actionBarAllKey("group.raffle.drawing",
                            "player", playerName(Rng.pick(pool)),
                            "pot", plugin.economy().format(pot.total()));
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) progress);
                }
            }
            return;
        }
        if (show != null) {
            show.settle();
            show = null;
        }

        if (houseDuelActive()) {
            UUID first = order.isEmpty() ? FairnessService.HOUSE : order.get(0);
            broadcastPlain("group.raffle.duel-winner", "player", playerName(first));
            settleHouseDuel(isHouse(first) ? -1 : 1, houseDuelMultiplier(null));
            return;
        }

        double total = pot.total();
        // With fewer winners than prizes (two players for three prizes) the shares of the
        // prizes that were drawn are scaled up, so the whole pot is always paid out.
        int drawn = Math.min(order.size(), PRIZES.length);
        double share = 0;
        for (int i = 0; i < drawn; i++) {
            share += PRIZES[i];
        }
        double[] amounts = new double[drawn];
        Map<UUID, Double> prizes = new LinkedHashMap<>();
        for (int i = 0; i < drawn; i++) {
            amounts[i] = share <= 0 ? 0 : total * PRIZES[i] / share;
            prizes.merge(order.get(i), amounts[i], Double::sum);
        }
        // The pot pays multipliers over every stake: an absolute prize becomes one here.
        pot.payoutByMultiplier(id -> {
            double stake = pot.amountOf(id);
            return stake <= 0 ? 0 : prizes.getOrDefault(id, 0.0) / stake;
        });

        broadcastPlain("group.raffle.banner");
        String[] labels = {"group.raffle.first-prize", "group.raffle.second-prize",
                "group.raffle.third-prize"};
        for (int i = 0; i < drawn && i < labels.length; i++) {
            final int index = i;
            broadcastPlainFor(player -> new Object[]{
                            "prize", plugin.messages().forSender(player, labels[index]),
                            "player", playerName(order.get(index)),
                            "amount", plugin.economy().format(amounts[index])},
                    "group.raffle.prize");
        }
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        if (drawn > 0) {
            showTitle(online(order.get(0)), "group.raffle.title", "group.raffle.title-subtitle",
                    "prize", plugin.economy().format(amounts[0]));
        }
        endRound();
    }

    @Override
    protected void onRoundEnd() {
        if (show != null) {
            show.cancel();
            show = null;
        }
        tickets.clear();
        order = new ArrayList<>();
    }

    /** Builds the drum and the podium in the arena; {@code null} without an arena. */
    private ArenaShow startShow() {
        ArenaStage stage = gatherArena();
        if (stage == null) {
            return null;
        }
        List<UUID> pool = new ArrayList<>(tickets.keySet());
        List<RaffleShow.Entry> entries = new ArrayList<>(pool.size());
        int sold = 0;
        for (int index = 0; index < pool.size(); index++) {
            UUID id = pool.get(index);
            int bought = Math.max(1, tickets.getOrDefault(id, 1));
            sold += bought;
            // More tickets, more balls in the drum: it is drawn like the draw itself.
            entries.add(new RaffleShow.Entry(playerName(id),
                    isHouse(id) ? new ItemStack(Material.GOLD_BLOCK) : Props.head(id),
                    isHouse(id) ? Material.BLACK_CONCRETE : SECTOR_COLOURS[index % SECTOR_COLOURS.length], bought));
        }
        // Against the house only the first prize is played for.
        int drawn = houseDuelActive() ? Math.min(1, order.size()) : Math.min(order.size(), PRIZES.length);
        List<Integer> winners = new ArrayList<>(drawn);
        List<String> shares = new ArrayList<>(drawn);
        double share = 0;
        for (int i = 0; i < drawn; i++) {
            share += PRIZES[i];
        }
        for (int i = 0; i < drawn; i++) {
            winners.add(pool.indexOf(order.get(i)));
            shares.add(houseDuelActive() ? Text.multiplier(houseDuelMultiplier(null))
                    : Math.round(100 * PRIZES[i] / share) + "%");
        }
        RaffleShow raffle = new RaffleShow(plugin, stage, entries, winners, shares, PREPARE_TICKS + DRAW_TIMER)
                .title("&6&l" + Text.strip(displayName(null)).toUpperCase(java.util.Locale.ROOT) + "\n"
                        + plugin.messages().get("group.raffle.sold", "total", sold,
                        "price", plugin.economy().format(ticketPrice())));
        raffle.start();
        return raffle;
    }
}
