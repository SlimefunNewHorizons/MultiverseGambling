package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.CoinFlipShow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Duel, one against one.
 *
 * <p>A player challenges another one for an exact amount. Both stake the same money
 * and a single roll decides who takes it all. If the challenged player does not
 * accept in time the challenger is refunded automatically: nobody holds a deposit.</p>
 */
public final class DuelGame extends AbstractGame {

    private final Map<UUID, Challenge> pending = new HashMap<>();

    /**
     * A challenge waiting for an answer.
     */
    private static final class Challenge {
        final UUID challenger;
        final UUID target;
        final double amount;
        final Wager wager;
        int ticksLeft;

        Challenge(UUID challenger, UUID target, double amount, Wager wager, int ticksLeft) {
            this.challenger = challenger;
            this.target = target;
            this.amount = amount;
            this.wager = wager;
            this.ticksLeft = ticksLeft;
        }
    }

    public DuelGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("duel", "Duel 1v1", GameCategory.GROUP, Material.IRON_SWORD)
                .desc("&7Challenge another player for an amount.",
                        "&7Both stake the same and one roll",
                        "&7decides who takes it all.")
                .players(2, 2)
                .build());
        // This game runs by itself: it never uses the shared waiting room.
        plugin.sessions().register(id(), this::tick);
    }

    @Override
    public boolean playableAgainstHouse() {
        return true;
    }

    @Override
    public boolean ownsPlayer(UUID playerId) {
        Challenge challenge = pending.get(playerId);
        return challenge != null;
    }

    @Override
    public List<String> statusLore(CommandSender viewer) {
        if (pending.isEmpty()) {
            return List.of(label(viewer, "panel.status.no-challenges"));
        }
        return List.of(label(viewer, "panel.status.pending-challenges", "amount", pending.size()));
    }

    /**
     * Opens the menu to pick a rival.
     */
    @Override
    public void open(Player player) {
        new RivalGui(plugin, player, this).show();
    }

    @Override
    public void openAgainstHouse(Player player) {
        plugin.guis().openBetSelector(player, this, amount -> challengeHouse(player, amount));
    }

    /**
     * One chat line after a duel: challenge somebody again, or the house.
     */
    private void offerRematch(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        player.sendMessage(replayLine(player, "replay.prompt"));
    }

    /**
     * Challenges another player.
     */
    public void challenge(Player challenger, Player target, double amount) {
        if (challenger.getUniqueId().equals(target.getUniqueId())) {
            message(challenger, "duel.self-duel");
            return;
        }
        if (!plugin.config().gameEnabled(id())) {
            message(challenger, "games.disabled", "game", name());
            return;
        }
        if (pending.containsKey(challenger.getUniqueId())
                || pending.containsKey(target.getUniqueId())) {
            message(challenger, "duel.already-busy");
            return;
        }
        double stake = Math.max(minBet(), Math.min(maxBet(), amount));
        if (!plugin.economy().has(target.getUniqueId(), stake)) {
            message(challenger, "duel.rival-cannot-afford", "player", target.getName());
            return;
        }
        Wager wager = plugin.economy().stake(challenger, stake);
        if (wager == null) {
            message(challenger, "economy.not-enough-money", "bet", plugin.economy().format(stake));
            return;
        }
        Challenge challenge = new Challenge(challenger.getUniqueId(), target.getUniqueId(), stake, wager,
                plugin.config().duelTimeoutSeconds() * 20);
        pending.put(target.getUniqueId(), challenge);

        message(challenger, "duel.challenge-sent",
                "player", target.getName(), "amount", plugin.economy().format(stake));
        target.sendMessage(Text.c(plugin.messages().forSender(target, "duel.challenge-received",
                        "player", challenger.getName(),
                        "amount", plugin.economy().format(stake),
                        "seconds", plugin.config().duelTimeoutSeconds()))
                .append(Text.c(" "))
                .append(Text.button(plugin.messages().forSender(target, "duel.accept"),
                        "/mvgam action accept", plugin.messages().forSender(target, "duel.accept-hover")))
                .append(Text.c(" "))
                .append(Text.button(plugin.messages().forSender(target, "duel.decline"),
                        "/mvgam action decline", plugin.messages().forSender(target, "duel.decline-hover"))));
        target.playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 1.4f);
    }

    @Override
    public void handleAction(Player player, String action, String[] args) {
        switch (action) {
            case "accept" -> accept(player);
            case "decline" -> decline(player);
            default -> message(player, "group.unknown-action");
        }
    }

    private void accept(Player target) {
        Challenge challenge = pending.remove(target.getUniqueId());
        if (challenge == null) {
            message(target, "duel.no-challenge");
            return;
        }
        Wager defenderWager = plugin.economy().stake(target, challenge.amount);
        if (defenderWager == null) {
            message(target, "economy.not-enough-money", "bet", plugin.economy().format(challenge.amount));
            refund(challenge.wager);
            Player challenger = online(challenge.challenger);
            if (challenger != null) {
                message(challenger, "duel.challenge-no-funds");
            }
            return;
        }

        Player challenger = online(challenge.challenger);
        // Provably fair roll: the winner is decided here with the same generator as everything else.
        boolean challengerWins = plugin.fair().roll(challenge.challenger) < 0.5;
        // A duel is player against player, so the house only keeps the commission
        // the server configures (none by default).
        double total = challenge.amount * 2 * (1.0 - plugin.config().groupHouseCut());

        if (challengerWins) {
            challenge.wager.payAbsolute(total);
            defenderWager.lose();
        } else {
            defenderWager.payAbsolute(total);
            challenge.wager.lose();
        }
        plugin.stats().record(challenge.challenger, id(), challenge.amount,
                challengerWins ? total : 0);
        plugin.stats().record(target.getUniqueId(), id(), challenge.amount,
                challengerWins ? 0 : total);

        Player loser = challengerWins ? target : challenger;
        Player winner = challengerWins ? challenger : target;
        UUID challengerId = challenge.challenger;
        double amount = challenge.amount;
        Runnable announce = () -> {
            announceBoth(winner, loser, total);
            Player challengerNow = online(challengerId);
            if (challengerNow != null) {
                info(challengerNow, title(challengerNow));
                message(challengerNow, challengerWins ? "duel.won-against" : "duel.lost-against",
                        "player", target.getName(),
                        "amount", plugin.economy().format(amount));
                offerRematch(challengerNow);
            }
            if (target.isOnline()) {
                info(target, title(target));
                message(target, challengerWins ? "duel.lost-against" : "duel.won-against",
                        "player", playerName(challengerId),
                        "amount", plugin.economy().format(amount));
                offerRematch(target);
            }
        };
        // The money is already settled; the coin in the pavilion only keeps the suspense
        // and the result is announced the moment it lands.
        if (!flipInArena(challenger, target, challengerWins, announce)) {
            announce.run();
        }
    }

    /**
     * A duel against the house: the same coin, the player against the dealer, an even
     * toss paid with the house edge, so nobody needs a rival online to play.
     */
    public void challengeHouse(Player player, double amount) {
        if (!plugin.config().gameEnabled(id())) {
            message(player, "games.disabled", "game", name());
            return;
        }
        if (pending.containsKey(player.getUniqueId())) {
            message(player, "duel.already-busy");
            return;
        }
        double stake = Math.max(minBet(), Math.min(maxBet(), amount));
        Wager wager = plugin.economy().stake(player, stake);
        if (wager == null) {
            message(player, "economy.not-enough-money", "bet", plugin.economy().format(stake));
            return;
        }
        double edge = Math.max(0.0, Math.min(0.5, plugin.config().houseEdge()));
        double multiplier = Math.max(1.0, 2.0 * (1.0 - edge));
        // One provably fair toss, attributed to the player like every solo bet.
        boolean wins = plugin.fair().roll(player.getUniqueId()) < 0.5;
        double payout = settle(player, wager, wins ? multiplier : 0);
        String house = Text.strip(plugin.messages().getOr("group.house-duel.house-name", "The house"));
        Runnable announce = () -> {
            if (!player.isOnline()) {
                return;
            }
            info(player, title(player));
            message(player, wins ? "duel.house-won" : "duel.house-lost",
                    "amount", plugin.economy().format(stake),
                    "prize", plugin.economy().format(payout));
            player.showTitle(Title.title(
                    Text.c(plugin.messages().forSender(player, wins ? "duel.win-title" : "duel.lose-title")),
                    Text.c(wins ? "&f" + plugin.economy().format(payout)
                            : plugin.messages().forSender(player, "duel.lose-subtitle")),
                    Title.Times.times(java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(1800),
                            java.time.Duration.ofMillis(300))));
            player.playSound(player.getLocation(), wins ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                    1.0f, wins ? 1.2f : 0.9f);
            offerRematch(player);
        };
        ArenaStage stage = arenaFor(player);
        if (stage == null) {
            announce.run();
            return;
        }
        int frames = 60;
        CoinFlipShow show = new CoinFlipShow(plugin, stage, wins, frames)
                .captions(player.getName(), house)
                .contenders(head(player), new ItemStack(Material.GOLD_BLOCK));
        show.start();
        new BukkitRunnable() {
            private int frame;

            @Override
            public void run() {
                if (frame++ < frames) {
                    show.tick();
                    return;
                }
                cancel();
                show.settle();
                announce.run();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Tosses the coin of the duel in the pavilion, the heads of both players on either
     * side of it.
     *
     * @return false when there is no pavilion to toss it in
     */
    private boolean flipInArena(Player challenger, Player target, boolean challengerWins, Runnable announce) {
        if (challenger == null) {
            return false;
        }
        ArenaStage stage = arenaFor(target);
        if (stage == null) {
            return false;
        }
        int distance = plugin.config().worldAnimationsViewDistance();
        if (plugin.config().worldAnimationsTeleport()) {
            challenger.teleport(stage.watcher(distance, ArenaStage.TABLE_PITCH, 0, 2));
            target.teleport(stage.watcher(distance, ArenaStage.TABLE_PITCH, 1, 2));
        }
        int frames = 60;
        CoinFlipShow show = new CoinFlipShow(plugin, stage, challengerWins, frames)
                .captions(challenger.getName(), target.getName())
                .contenders(head(challenger), head(target));
        show.start();
        new BukkitRunnable() {
            private int frame;

            @Override
            public void run() {
                if (frame++ < frames) {
                    show.tick();
                    return;
                }
                cancel();
                show.settle();
                announce.run();
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return true;
    }

    private static ItemStack head(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }

    private void announceBoth(Player winner, Player loser, double total) {
        if (winner != null) {
            winner.showTitle(Title.title(
                    Text.c(plugin.messages().forSender(winner, "duel.win-title")),
                    Text.c("&f" + plugin.economy().format(total)), Title.Times.times(
                            java.time.Duration.ofMillis(150),
                            java.time.Duration.ofMillis(2000),
                            java.time.Duration.ofMillis(300))));
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
        if (loser != null) {
            loser.showTitle(Title.title(
                    Text.c(plugin.messages().forSender(loser, "duel.lose-title")),
                    Text.c(plugin.messages().forSender(loser, "duel.lose-subtitle")), Title.Times.times(
                            java.time.Duration.ofMillis(150),
                            java.time.Duration.ofMillis(1600),
                            java.time.Duration.ofMillis(300))));
            loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.9f);
        }
    }

    private void decline(Player target) {
        Challenge challenge = pending.remove(target.getUniqueId());
        if (challenge == null) {
            message(target, "duel.no-challenge");
            return;
        }
        refund(challenge.wager);
        message(target, "duel.declined");
        Player challenger = online(challenge.challenger);
        if (challenger != null) {
            message(challenger, "duel.challenge-declined", "player", target.getName());
        }
    }

    /**
     * The duel keeps its own clock: unanswered challenges expire.
     */
    private void tick() {
        if (pending.isEmpty()) {
            return;
        }
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Challenge> entry : pending.entrySet()) {
            Challenge challenge = entry.getValue();
            challenge.ticksLeft--;
            if (challenge.ticksLeft <= 0) {
                expired.add(entry.getKey());
            }
        }
        for (UUID target : expired) {
            Challenge challenge = pending.remove(target);
            if (challenge == null) {
                continue;
            }
            refund(challenge.wager);
            Player challenger = online(challenge.challenger);
            if (challenger != null) {
                message(challenger, "duel.challenge-expired", "player", playerName(target));
            }
            Player targetPlayer = online(target);
            if (targetPlayer != null) {
                message(targetPlayer, "duel.challenge-timed-out");
            }
        }
    }

    private String playerName(UUID playerId) {
        Player player = online(playerId);
        if (player != null) {
            return player.getName();
        }
        String name = plugin.getServer().getOfflinePlayer(playerId).getName();
        return name == null ? playerId.toString().substring(0, 8) : name;
    }

    private Player online(UUID playerId) {
        return plugin.getServer().getPlayer(playerId);
    }

    private final class RivalGui extends Gui {

        private final DuelGame game;

        RivalGui(MultiverseGamblingPlugin plugin, Player player, DuelGame game) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.duel.title",
                    "game", displayName(player)));
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            frame(Material.WHITE_STAINED_GLASS_PANE);

            set(4, Items.of(Material.IRON_SWORD)
                    .name(label(player(), "panel.duel.info"))
                    .lore(labelLore(player(), "panel.duel.info-lore",
                            "seconds", plugin.config().duelTimeoutSeconds()))
                    .glow(true)
                    .build());

            int slot = 10;
            for (Player target : plugin.getServer().getOnlinePlayers()) {
                if (target.getUniqueId().equals(player().getUniqueId())) {
                    continue;
                }
                if (slot > 34) {
                    break;
                }
                if (slot == 17 || slot == 26) {
                    slot = slot + 2;
                }
                double balance = plugin.economy().balance(target.getUniqueId());
                set(slot, Items.of(Material.PLAYER_HEAD)
                        .name(label(player(), "panel.duel.player", "player", target.getName()))
                        .lore(labelLore(player(), "panel.duel.player-lore",
                                "balance", plugin.economy().format(balance)))
                        .build(), e -> {
                    close();
                    plugin.guis().openBetSelector(player(), game, amount ->
                            game.challenge(player(), target, amount));
                });
                slot++;
            }

            set(38, Items.of(Material.GOLD_BLOCK)
                    .name(label(player(), "panel.duel.house"))
                    .lore(labelLore(player(), "panel.duel.house-lore"))
                    .glow(true)
                    .build(), e -> {
                close();
                plugin.guis().openBetSelector(player(), game, amount -> game.challengeHouse(player(), amount));
            });

            set(42, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .build(), e -> close());
        }

        @Override
        public String sessionId() {
            return "duel";
        }
    }
}
