package com.chagui68.multiversegambling.games.group;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.ColorWheel;
import com.chagui68.multiversegambling.engine.ColorWheel.Outcome;
import com.chagui68.multiversegambling.engine.Rng;
import com.chagui68.multiversegambling.engine.RouletteTable;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.AbstractGroupGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.world.anim.ArenaShow;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.RouletteShow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;

/**
 * Colour roulette for a group.
 *
 * <p>Every player stakes and picks a colour. Green is a single pocket, so it pays
 * around 36 times: it is the thrill of the round and still carries the same edge as
 * red. Every colour is paid with the real pockets of the wheel, never with an
 * invented number.</p>
 */
public final class ColorRouletteGame extends AbstractGroupGame {

    private static final int ANNOUNCE_TICKS = 60;
    /** Long enough to watch the ball lap the bowl and drop into its pocket. */
    private static final int SPIN_TICKS = 100;

    private final Map<UUID, Outcome> choices = new LinkedHashMap<>();
    private Outcome result;
    /**
     * Wheel painted in the arena for the round, when there is an arena to paint on.
     */
    private ArenaShow show;

    public ColorRouletteGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("color-roulette", "Colour Roulette", GameCategory.GROUP, Material.RED_WOOL)
                .desc("&7Red, black or green. Everybody bets",
                        "&7and the wheel decides who gets paid.",
                        "&7Green pays &f36x&7.")
                .players(2, 24)
                .build());
    }

    private ColorWheel wheel() {
        return new ColorWheel(18, 18, 1, plugin.config().houseEdge());
    }

    @Override
    protected void onBetPlaced(Player player, double amount) {
        choices.putIfAbsent(player.getUniqueId(), Outcome.RED);
        new ColorGui(plugin, player, this).show();
        broadcastPlain("group.color-roulette.joined",
                "player", player.getName(),
                "amount", plugin.economy().format(amount),
                "pot", plugin.economy().format(pot.total()));
        // The menu is one way to pick a colour, the chat buttons are the other: whoever
        // is watching the wheel instead of the menu can still change their mind.
        offerColours(player);
    }

    /**
     * One line with a button per colour, in the language of the reader.
     */
    private void offerColours(Player player) {
        Component line = plugin.messages().componentPlainFor(player, "group.color-roulette.pick");
        for (Outcome outcome : Outcome.values()) {
            line = line.append(Text.c("  ")).append(chatButton(
                    colourFor(player, outcome),
                    "color " + outcome.name().toLowerCase(Locale.ROOT),
                    plugin.messages().forSender(player, "group.color-roulette.pick-hover",
                            "colour", colourFor(player, outcome))));
        }
        player.sendMessage(line);
    }

    /**
     * Chat button path: {@code /mvgam action color red}, so the colour can be picked
     * without a menu at all.
     */
    @Override
    public void handleAction(Player player, String action, String[] args) {
        if ("color".equals(action) && args.length > 0) {
            Outcome outcome = outcomeFor(args[0]);
            if (outcome != null) {
                choose(player, outcome);
                return;
            }
        }
        super.handleAction(player, action, args);
    }

    private static Outcome outcomeFor(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "red" -> Outcome.RED;
            case "black" -> Outcome.BLACK;
            case "green" -> Outcome.GREEN;
            default -> null;
        };
    }

    /**
     * The player picks which colour to back.
     */
    void choose(Player player, Outcome outcome) {
        if (!pot.contains(player.getUniqueId())) {
            message(player, "group.not-in-color-round");
            return;
        }
        choices.put(player.getUniqueId(), outcome);
        sound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
        houseChoiceMade(player);
        ColorWheel wheel = wheel();
        broadcastPlainFor(viewer -> new Object[]{
                        "player", player.getName(),
                        "colour", colourFor(viewer, outcome),
                        "multiplier", Text.multiplier(wheel.payout(outcome))},
                "group.color-roulette.chose");
    }

    /**
     * Colour name in the language of the reader.
     */
    String colourFor(Player viewer, Outcome outcome) {
        return plugin.messages().forSender(viewer, "group.color-roulette.colour-"
                + outcome.name().toLowerCase(java.util.Locale.ROOT));
    }

    Outcome choiceOf(UUID playerId) {
        return choices.get(playerId);
    }

    @Override
    protected boolean houseDuelAvailable() {
        return true;
    }

    @Override
    protected boolean houseDuelNeedsChoice() {
        return true;
    }

    /**
     * The duel pays what the colour really pays, so a player alone in the room bets
     * against the wheel instead of against a pot that would be their own money.
     */
    @Override
    protected double houseDuelMultiplier(Player player) {
        return wheel().payout(choices.getOrDefault(player.getUniqueId(), Outcome.RED));
    }

    @Override
    protected void onRoundStart() {
        result = null;
        timer = 0;

        if (houseDuelActive()) {
            startDuelRound();
            return;
        }

        broadcastRoundHeader();

        // Whoever did not pick a colour gets their money back and leaves the round.
        for (UUID id : pot.participants()) {
            if (!choices.containsKey(id)) {
                pot.remove(id);
                tellKeyed(id, "group.color-roulette.refunded");
            }
        }
        if (pot.size() < minPlayers()) {
            broadcastPlain("group.color-roulette.not-enough");
            endRound();
            return;
        }
        showOdds();
        broadcastPlain("group.color-roulette.spins-in", "seconds", 3);
    }

    /**
     * The duel: one player, one colour and one spin, paid by the wheel.
     */
    private void startDuelRound() {
        UUID playerId = houseDuelPlayer();
        Outcome choice = choices.getOrDefault(playerId, Outcome.RED);
        broadcastRoundHeader();
        broadcastPlain("group.color-roulette.duel-intro",
                "colour", colourFor(online(playerId), choice),
                "multiplier", Text.multiplier(wheel().payout(choice)));
    }

    private void tickDuelRound() {
        if (result == null) {
            // Drawn before the wheel is painted, so the ball lands on the colour that
            // really came up. The wheel belongs to nobody, so the roll is the house's.
            result = wheel().spin(() -> plugin.fair().roll(FairnessService.HOUSE));
            show = startWheelShow();
            return;
        }
        if (timer <= SPIN_TICKS) {
            if (show != null) {
                show.tick();
            }
            return;
        }
        if (show != null) {
            show.settle();
            show = null;
        }
        settleDuel();
    }

    /**
     * Pays the duel: the wheel's own payout when the called colour came up, nothing when
     * it did not. The stake was charged when the player bet, as in every round.
     */
    private void settleDuel() {
        UUID playerId = houseDuelPlayer();
        Player viewer = online(playerId);
        Outcome choice = choices.getOrDefault(playerId, Outcome.RED);
        double multiplier = wheel().payout(result);
        double stake = pot.amountOf(playerId);
        boolean won = choice == result;
        if (won) {
            pot.payoutByMultiplier(id -> id.equals(playerId) ? multiplier : 0);
        }
        broadcastPlainFor(v -> new Object[]{"colour", colourFor(v, result)},
                "group.color-roulette.stopped");
        if (viewer != null) {
            viewer.sendMessage(plugin.messages().componentPlainFor(viewer,
                    won ? "group.color-roulette.won" : "group.color-roulette.lost",
                    "colour", colourFor(viewer, choice),
                    "multiplier", Text.multiplier(multiplier),
                    "prize", plugin.economy().format(stake * multiplier)));
        }
        soundAll(won ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 0.9f, 1.2f);
        endRound();
    }

    @Override
    protected void tickRound() {
        ColorWheel wheel = wheel();
        timer++;
        if (houseDuelActive()) {
            tickDuelRound();
            return;
        }
        if (timer <= ANNOUNCE_TICKS) {
            if (timer % 20 == 0) {
                int seconds = (ANNOUNCE_TICKS - timer) / 20;
                if (seconds > 0) {
                    broadcastPlain("group.color-roulette.counts", "seconds", seconds);
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.4f);
                }
            }
            return;
        }

        int elapsed = timer - ANNOUNCE_TICKS;
        if (elapsed == 1) {
            // Provably fair roll attributed to the house: the wheel belongs to nobody.
            // Drawn before the show starts, so the ball in the arena can land on the
            // colour that really came up.
            result = wheel.spin(() -> plugin.fair().roll(FairnessService.HOUSE));
            show = startWheelShow();
        }
        if (elapsed <= SPIN_TICKS) {
            if (show != null) {
                show.tick();
            } else {
                double progress = (double) elapsed / SPIN_TICKS;
                int wait = 1 + (int) (progress * progress * 8);
                if (elapsed % wait == 0) {
                    Outcome filler = Outcome.values()[Rng.intBetween(0, Outcome.values().length - 1)];
                    actionBarFor(viewer -> new Object[]{"colour", colourFor(viewer, filler)},
                            "group.color-roulette.spinning");
                    soundAll(Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 0.9f + (float) progress * 0.9f);
                }
            }
            return;
        }
        if (show != null) {
            show.settle();
            show = null;
        }

        double multiplier = wheel.payout(result);

        Map<UUID, Outcome> bets = new LinkedHashMap<>(choices);
        Map<UUID, Double> stakes = new LinkedHashMap<>(pot.amounts());
        pot.payoutByMultiplier(id -> bets.get(id) == result ? multiplier : 0);

        broadcastPlainFor(viewer -> new Object[]{"colour", colourFor(viewer, result)},
                "group.color-roulette.stopped");
        for (Map.Entry<UUID, Outcome> entry : bets.entrySet()) {
            boolean won = entry.getValue() == result;
            Player viewer = online(entry.getKey());
            if (viewer == null) {
                continue;
            }
            viewer.sendMessage(plugin.messages().componentPlainFor(viewer,
                    won ? "group.color-roulette.won" : "group.color-roulette.lost",
                    "colour", colourFor(viewer, won ? result : entry.getValue()),
                    "multiplier", Text.multiplier(multiplier),
                    "prize", plugin.economy().format(
                            stakes.getOrDefault(entry.getKey(), 0.0) * multiplier)));
        }
        soundAll(wonSound(bets), 0.9f, 1.2f);
        choices.clear();
        endRound();
    }

    private Sound wonSound(Map<UUID, Outcome> bets) {
        boolean anyWinner = bets.values().stream().anyMatch(value -> value == result);
        return anyWinner ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO;
    }

    private void showOdds() {
        // Everybody gets the buttons again when the betting window opens, so a colour can
        // still be changed until the wheel starts.
        for (UUID id : pot.participants()) {
            Player viewer = online(id);
            if (viewer != null) {
                offerColours(viewer);
            }
        }
        ColorWheel wheel = wheel();
        broadcastPlainFor(viewer -> {
            StringBuilder builder = new StringBuilder();
            for (Outcome outcome : Outcome.values()) {
                if (builder.length() > 0) {
                    builder.append(" &8| ");
                }
                builder.append(colourFor(viewer, outcome)).append(" &f")
                        .append(Text.multiplier(wheel.payout(outcome)));
            }
            return new Object[]{"payouts", builder.toString()};
        }, "group.color-roulette.odds");
    }

    @Override
    protected void onRoundEnd() {
        if (show != null) {
            show.cancel();
            show = null;
        }
        choices.clear();
        result = null;
    }

    /**
     * Builds the wheel on the arena; {@code null} when the round has no arena to use.
     */
    private ArenaShow startWheelShow() {
        ArenaStage stage = gatherArena(RouletteShow::watchSpot);
        if (stage == null) {
            return null;
        }
        List<Material> sectors = colourSectors();
        RouletteShow wheelShow = new RouletteShow(plugin, stage, sectors, sectorFor(sectors, result), SPIN_TICKS)
                .coloursCloth((bet, selection) -> {
                    Outcome outcome = outcomeOf(bet, selection);
                    return Text.strip(colourFor(null, outcome)) + "\n" + Text.multiplier(wheel().payout(outcome));
                });
        // Every player's chip on the colour they called.
        for (Map.Entry<UUID, Outcome> entry : choices.entrySet()) {
            if (!pot.contains(entry.getKey())) {
                continue;
            }
            Outcome outcome = entry.getValue();
            String name = plugin.getServer().getOfflinePlayer(entry.getKey()).getName();
            wheelShow.bet(outcome == Outcome.GREEN ? RouletteTable.Bet.NUMBER : RouletteTable.Bet.COLOR,
                    outcome == Outcome.BLACK ? 1 : 0, name == null ? "?" : name,
                    plugin.economy().format(pot.amountOf(entry.getKey())));
        }
        wheelShow.start();
        return wheelShow;
    }

    /** The colour a field of the cloth stands for. */
    private static Outcome outcomeOf(RouletteTable.Bet bet, int selection) {
        if (bet == RouletteTable.Bet.NUMBER) {
            return Outcome.GREEN;
        }
        return selection == 0 ? Outcome.RED : Outcome.BLACK;
    }

    /**
     * The real wheel: one green pocket and eighteen red and black ones around it.
     */
    private static List<Material> colourSectors() {
        int pockets = 37;
        List<Material> sectors = new ArrayList<>(pockets);
        sectors.add(sectorColour(Outcome.GREEN));
        for (int index = 1; index < pockets; index++) {
            sectors.add(index % 2 == 1 ? sectorColour(Outcome.RED) : sectorColour(Outcome.BLACK));
        }
        return sectors;
    }

    /**
     * Picks the pocket the ball lands on for the colour that really came up.
     */
    private static int sectorFor(List<Material> sectors, Outcome outcome) {
        Material wanted = sectorColour(outcome);
        List<Integer> matching = new ArrayList<>();
        for (int index = 0; index < sectors.size(); index++) {
            if (sectors.get(index) == wanted) {
                matching.add(index);
            }
        }
        if (matching.isEmpty()) {
            return 0;
        }
        return matching.get(Rng.intBetween(0, matching.size() - 1));
    }

    private static Material sectorColour(Outcome outcome) {
        return switch (outcome) {
            case RED -> Material.RED_CONCRETE;
            case BLACK -> Material.BLACK_CONCRETE;
            case GREEN -> Material.LIME_CONCRETE;
        };
    }

    private static Material materialOf(Outcome outcome) {
        return switch (outcome) {
            case RED -> Material.RED_WOOL;
            case BLACK -> Material.BLACK_WOOL;
            case GREEN -> Material.GREEN_WOOL;
        };
    }

    /**
     * Three button menu to pick a colour.
     */
    private final class ColorGui extends Gui {

        /** Each colour is a column of three: the middle one is the button. */
        private static final int[][] COLUMNS = {{10, 19, 28}, {13, 22, 31}, {16, 25, 34}};

        private final ColorRouletteGame game;

        ColorGui(MultiverseGamblingPlugin plugin, Player player, ColorRouletteGame game) {
            super(plugin, player, 5, plugin.messages().forSender(player, "panel.color-roulette.title",
                    "game", displayName(player)));
            this.game = game;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
            ItemStack frame = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
            for (int slot : new int[]{0, 1, 2, 3, 5, 6, 7, 8, 36, 37, 38, 39, 41, 42, 43, 44}) {
                set(slot, frame);
            }

            ColorWheel wheel = game.wheel();
            Outcome chosen = game.choiceOf(player().getUniqueId());
            String state = chosen == null
                    ? label(player(), "panel.color-roulette.not-picked")
                    : label(player(), "panel.color-roulette.picked",
                    "colour", game.colourFor(player(), chosen));
            set(4, Items.of(Material.GOLD_INGOT)
                    .name(label(player(), "panel.color-roulette.info"))
                    .lore(labelLore(player(), "panel.color-roulette.info-lore",
                            "pot", plugin.economy().format(game.pot.total()),
                            "state", state))
                    .glow(true)
                    .build());

            Outcome[] values = Outcome.values();
            for (int i = 0; i < values.length && i < COLUMNS.length; i++) {
                Outcome outcome = values[i];
                boolean selected = outcome == chosen;
                ItemStack pillar = Items.of(selected ? Material.LIME_STAINED_GLASS_PANE : paneOf(outcome))
                        .name((selected ? "&a\u25B6 " : "") + game.colourFor(player(), outcome))
                        .build();
                ItemStack button = Items.of(materialOf(outcome))
                        .name((selected ? "&a\u25B6 " : "") + game.colourFor(player(), outcome)
                                + " &8\u00b7 &f" + Text.multiplier(wheel.payout(outcome)))
                        .lore(
                                label(player(), "panel.color-roulette.pockets", "count", wheel.pockets(outcome)),
                                label(player(), "panel.common.pays",
                                        "multiplier", Text.multiplier(wheel.payout(outcome))),
                                label(player(), "panel.color-roulette.chance",
                                        "percent", Text.percent(wheel.chance(outcome))),
                                "",
                                selected ? label(player(), "panel.common.selected")
                                        : label(player(), "panel.color-roulette.click-pick"))
                        .glow(selected)
                        .build();
                java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> pick = e -> {
                    game.choose(player(), outcome);
                    refresh();
                };
                set(COLUMNS[i][0], pillar, pick);
                set(COLUMNS[i][1], button, pick);
                set(COLUMNS[i][2], pillar, pick);
            }

            // Who is in the room and what everybody backs.
            List<String> room = new ArrayList<>();
            for (UUID id : game.pot.participants()) {
                Outcome pick = game.choiceOf(id);
                room.add(label(player(), "panel.color-roulette.room-line",
                        "player", game.playerName(id),
                        "colour", pick == null ? "&8-" : game.colourFor(player(), pick),
                        "bet", plugin.economy().format(game.pot.amountOf(id))));
            }
            set(40, Items.of(Material.PLAYER_HEAD)
                    .name(label(player(), "panel.color-roulette.room", "players", room.size()))
                    .lore(room)
                    .build());

            set(44, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.close"))
                    .lore(label(player(), "panel.color-roulette.close-lore"))
                    .build(), e -> close());
        }

        private static Material paneOf(Outcome outcome) {
            return switch (outcome) {
                case RED -> Material.RED_STAINED_GLASS_PANE;
                case BLACK -> Material.BLACK_STAINED_GLASS_PANE;
                case GREEN -> Material.GREEN_STAINED_GLASS_PANE;
            };
        }

        @Override
        public String sessionId() {
            return "color-roulette";
        }
    }
}
