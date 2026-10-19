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
import com.chagui68.multiversegambling.world.anim.JackpotShow;
import com.chagui68.multiversegambling.world.anim.Props;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Shared jackpot.
 *
 * <p>Everybody puts money in and the tickets are proportional to what was staked, so
 * doubling the bet doubles the chances without changing anybody's edge. The winner
 * takes the whole pot minus the commission the server configures.</p>
 */
public final class JackpotGame extends AbstractGroupGame {

    private static final int PREPARE_TICKS = 40;
    private static final int SPIN_TICKS = 80;

    /** Colour of each player in the jar and on the strip, cycled when out of colours. */
    private static final Material[] SECTOR_COLOURS = {
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.MAGENTA_CONCRETE, Material.CYAN_CONCRETE,
            Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE, Material.PINK_CONCRETE,
            Material.WHITE_CONCRETE, Material.BROWN_CONCRETE, Material.BLACK_CONCRETE
    };

    private UUID winner;
    /** Jar and strip painted on the arena, when there is one. */
    private ArenaShow show;

    public JackpotGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("jackpot", "Jackpot", GameCategory.GROUP, Material.CHEST)
                .desc("&7Everybody drops money into one pot",
                        "&7and a weighted draw decides who",
                        "&7takes it all.")
                .players(2, 24)
                .build());
    }

    @Override
    protected boolean houseDuelAvailable() {
        return true;
    }

    /**
     * Against the house, the house matches the stake: half of the pot each, so a win pays
     * the pot minus the house edge.
     */
    @Override
    protected double houseDuelMultiplier(Player player) {
        return fairDuelMultiplier(0.5);
    }

    @Override
    protected void onRoundStart() {
        timer = 0;
        if (houseDuelActive()) {
            broadcastRoundHeader();
            broadcastPlain("group.jackpot.duel-intro",
                    "amount", plugin.economy().format(pot.amountOf(houseDuelPlayer())),
                    "multiplier", Text.multiplier(houseDuelMultiplier(null)));
            return;
        }
        broadcastRoundHeader();
        broadcastPlain("group.jackpot.total-pot",
                "pot", plugin.economy().format(pot.total()));
        for (UUID id : pot.participants()) {
            double stake = pot.amountOf(id);
            double chance = stake / Math.max(0.0001, pot.total());
            broadcastPlain("group.jackpot.line",
                    "player", playerName(id),
                    "amount", plugin.economy().format(stake),
                    "chance", String.format("%.1f%%", chance * 100));
        }
        broadcastPlain("group.jackpot.starting");
    }

    @Override
    protected void tickRound() {
        timer++;
        int elapsed = timer;

        if (elapsed == 1) {
            // The real draw: tickets proportional to the stake, with provably fair rolls.
            // The bets are closed, so it is drawn right away: the show in the arena then
            // fills the jar and stops its strip on the ticket that really won.
            WeightedTable<UUID> draw = new WeightedTable<>();
            pot.participants().forEach(id -> draw.add(id, Math.max(0.0001, pot.amountOf(id))));
            if (houseDuelActive()) {
                // The house puts in as much as the player: one even draw.
                draw.add(FairnessService.HOUSE, Math.max(0.0001, pot.amountOf(houseDuelPlayer())));
            }
            winner = draw.isEmpty() ? null : draw.roll(plugin.fair().roll(FairnessService.HOUSE));
            show = startShow(winner);
        }
        if (elapsed <= PREPARE_TICKS + SPIN_TICKS && show != null) {
            show.tick();
            return;
        }
        if (elapsed <= PREPARE_TICKS) {
            if (elapsed % 20 == 0) {
                broadcastPlain("group.jackpot.preparing");
                soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
            }
            return;
        }
        int spin = elapsed - PREPARE_TICKS;
        if (spin <= SPIN_TICKS) {
            // No arena: the draw is told in the action bar instead.
            double progress = (double) spin / SPIN_TICKS;
            int wait = 1 + (int) (progress * progress * 8);
            if (spin % wait == 0) {
                List<UUID> pool = new ArrayList<>(pot.participants());
                if (!pool.isEmpty()) {
                    actionBarAllKey("group.jackpot.drawing",
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
        if (winner == null) {
            endRound();
            return;
        }
        if (houseDuelActive()) {
            broadcastPlain("group.jackpot.duel-winner", "player", playerName(winner));
            settleHouseDuel(isHouse(winner) ? -1 : 1, houseDuelMultiplier(null));
            return;
        }
        double total = pot.total();
        double cut = plugin.config().groupHouseCut();
        double prize = total * (1.0 - cut);

        pot.payAllTo(winner, cut);

        broadcastPlain("group.jackpot.banner");
        broadcastPlain("group.jackpot.winner",
                "player", playerName(winner),
                "prize", plugin.economy().format(prize));
        if (cut > 0) {
            broadcastPlain("group.jackpot.commission",
                    "amount", plugin.economy().format(total - prize));
        }
        broadcastPlain("group.jackpot.took",
                "staked", plugin.economy().format(pot.total() > 0 ? total : 0),
                "pot", plugin.economy().format(total));
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        showTitle(online(winner), "group.jackpot.title", "group.jackpot.winner-subtitle",
                "prize", plugin.economy().format(prize));
        endRound();
    }

    /** Builds the jar and the strip in the arena; {@code null} when there is no arena. */
    private ArenaShow startShow(UUID winner) {
        ArenaStage stage = gatherArena();
        if (stage == null) {
            return null;
        }
        List<UUID> pool = new ArrayList<>(pot.participants());
        if (houseDuelActive()) {
            pool.add(FairnessService.HOUSE);
        }
        List<JackpotShow.Entry> entries = new ArrayList<>(pool.size());
        double total = 0;
        for (int index = 0; index < pool.size(); index++) {
            UUID id = pool.get(index);
            double stake = Math.max(0.0001, isHouse(id) ? pot.amountOf(houseDuelPlayer()) : pot.amountOf(id));
            total += stake;
            // A bigger stake is a taller band in the jar and a wider cell on the strip,
            // exactly like the draw.
            entries.add(new JackpotShow.Entry(playerName(id),
                    isHouse(id) ? new ItemStack(Material.GOLD_BLOCK) : Props.head(id),
                    isHouse(id) ? Material.BLACK_CONCRETE : SECTOR_COLOURS[index % SECTOR_COLOURS.length], stake));
        }
        int landing = winner == null ? 0 : Math.max(0, pool.indexOf(winner));
        JackpotShow jackpot = new JackpotShow(plugin, stage, entries, landing, PREPARE_TICKS + SPIN_TICKS)
                .pot(plugin.economy().format(total));
        jackpot.start();
        return jackpot;
    }

    @Override
    protected void onRoundEnd() {
        if (show != null) {
            show.cancel();
            show = null;
        }
        winner = null;
    }
}
