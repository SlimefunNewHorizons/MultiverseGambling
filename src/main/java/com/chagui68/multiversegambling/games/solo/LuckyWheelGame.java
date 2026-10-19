package com.chagui68.multiversegambling.games.solo;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.engine.PrizeWheel;
import com.chagui68.multiversegambling.economy.Wager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameMeta;
import com.chagui68.multiversegambling.gui.Gui;
import com.chagui68.multiversegambling.session.TimedSession;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.anim.ArenaStage;
import com.chagui68.multiversegambling.world.anim.WheelShow;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Lucky wheel: twelve tiles with different multipliers.
 *
 * <p>The winning tile comes from the provably fair generator. The return is the average
 * of the tiles and is pinned by a test, so editing the wheel in the configuration
 * cannot break the economy unnoticed.</p>
 */
public final class LuckyWheelGame extends AbstractSoloGame {

    public LuckyWheelGame(MultiverseGamblingPlugin plugin) {
        super(plugin, GameMeta.builder("lucky-wheel", "Lucky Wheel", GameCategory.SOLO, Material.COMPASS)
                .desc("&7Twelve segments, many of them empty",
                        "&7and hits of up to &f4x&7. One spin",
                        "&7decides the prize.")
                .build());
    }

    PrizeWheel wheel() {
        List<Double> configured = plugin.config().luckyWheelSegments();
        if (configured == null || configured.size() < 2) {
            return PrizeWheel.defaultWheel();
        }
        double[] pockets = new double[configured.size()];
        for (int i = 0; i < pockets.length; i++) {
            pockets[i] = Math.max(0, configured.get(i));
        }
        return new PrizeWheel(pockets);
    }

    @Override
    public boolean supportsItemBets() {
        return true;
    }

    /** Every multiplier of the wheel, with how many tiles carry it. */
    @Override
    public List<com.chagui68.multiversegambling.game.ItemOutcome> itemOutcomes(Player viewer) {
        PrizeWheel wheel = wheel();
        java.util.Map<Double, Integer> counts = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        for (int i = 0; i < wheel.size(); i++) {
            counts.merge(wheel.multiplier(i), 1, Integer::sum);
        }
        List<com.chagui68.multiversegambling.game.ItemOutcome> out = new ArrayList<>();
        counts.forEach((multiplier, tiles) -> out.add(new com.chagui68.multiversegambling.game.ItemOutcome(
                plugin.messages().forSender(viewer, "items.outcome.tiles", "count", tiles),
                multiplier, tiles / (double) wheel.size())));
        return out;
    }

    @Override
    protected void start(Player player, double bet) {
        Wager wager = stake(player, bet);
        if (wager == null) {
            return;
        }
        new WheelGui(plugin, player, this, wager).show();
    }

    void spin(Player player, Wager wager) {
        PrizeWheel wheel = wheel();
        int winner = wheel.spin(() -> plugin.fair().roll(player.getUniqueId()));
        int total = 90;
        UUID playerId = player.getUniqueId();

        ArenaStage stage = arenaFor(player);
        if (stage != null) {
            WheelShow show = new WheelShow(plugin, stage, sectors(wheel), winner, total)
                    .labels(labels(wheel))
                    .title(Text.strip(displayName(player)).toUpperCase(java.util.Locale.ROOT))
                    .paytable(paytable(wheel));
            new TimedSession(plugin, player, id(), total) {

                @Override
                protected void onStart() {
                    show.start();
                }

                @Override
                protected void onFrame(int elapsed, int duration) {
                    show.tick();
                }

                @Override
                protected void onFinish() {
                    show.settle();
                    settleSpin(playerId, wager, winner);
                }

                @Override
                protected void onCancel() {
                    show.cancel();
                    settleSpin(playerId, wager, winner);
                }
            }.run();
            return;
        }

        // No arena to paint on: the action bar keeps the suspense.
        new TimedSession(plugin, player, id(), total) {

            @Override
            protected void onFrame(int elapsed, int duration) {
                Player online = player();
                if (online == null) {
                    return;
                }
                double progress = (double) elapsed / duration;
                int wait = 1 + (int) (progress * progress * 10);
                if (elapsed % wait == 0) {
                    int filler = (int) (elapsed * 1.7) % wheel.size();
                    online.sendActionBar(Text.c(plugin.messages().forSender(online,
                            "panel.lucky-wheel.spinning",
                            "multiplier", Text.multiplier(wheel.multiplier(filler)))));
                    online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f,
                            0.9f + (float) progress * 0.9f);
                }
            }

            @Override
            protected void onFinish() {
                settleSpin(playerId, wager, winner);
            }

            @Override
            protected void onCancel() {
                settleSpin(playerId, wager, winner);
            }
        }.run();
    }

    /**
     * Pays a spin whose winning tile was drawn when the round started.
     */
    private void settleSpin(UUID playerId, Wager wager, int winner) {
        double multiplier = wheel().multiplier(winner);
        Player online = plugin.getServer().getPlayer(playerId);
        if (online == null) {
            // Gone before the end: the result was already drawn, so it is paid as drawn.
            // Refunding here would let anybody cancel a round they saw coming out badly.
            settleOffline(playerId, wager, multiplier);
            return;
        }
        double payout = settle(online, wager, multiplier);
        announceResult(online, payout > wager.amount(),
                multiplier > 0
                        ? plugin.messages().forSender(online, "panel.lucky-wheel.tile",
                        "multiplier", Text.multiplier(multiplier))
                        : plugin.messages().forSender(online, "panel.common.no-prize"));
        info(online, title(online));
        message(online, "panel.lucky-wheel.result",
                "tile", winner + 1, "multiplier", Text.multiplier(multiplier));
        showResult(online, wager, payout);
        sound(online, payout > wager.amount() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO,
                0.9f, payout > wager.amount() ? 1.3f : 0.9f);
        offerReplay(online);
    }

    /**
     * Multiplier written on each tile of the wheel.
     */
    static List<String> labels(PrizeWheel wheel) {
        List<String> labels = new ArrayList<>(wheel.size());
        for (int index = 0; index < wheel.size(); index++) {
            double multiplier = wheel.multiplier(index);
            labels.add(multiplier <= 0 ? "✖" : Text.multiplier(multiplier));
        }
        return labels;
    }

    /** Colours of the prizes that are neither nothing nor the top one, smallest first. */
    private static final Material[] TIERS = {Material.LIME_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.BLUE_CONCRETE, Material.PURPLE_CONCRETE, Material.MAGENTA_CONCRETE};
    private static final String[] TIER_CODES = {"&a", "&b", "&9", "&5", "&d"};

    /**
     * Colour of each tile: grey when it pays nothing, gold for the top prize and a colour
     * a step richer for every prize in between, so the prizes read at a glance.
     */
    static List<Material> sectors(PrizeWheel wheel) {
        List<Double> tiers = tiers(wheel);
        List<Material> sectors = new ArrayList<>(wheel.size());
        for (int index = 0; index < wheel.size(); index++) {
            double multiplier = wheel.multiplier(index);
            sectors.add(multiplier <= 0 ? Material.GRAY_CONCRETE
                    : multiplier >= wheel.best() ? Material.GOLD_BLOCK
                    : TIERS[Math.min(TIERS.length - 1, tiers.indexOf(multiplier))]);
        }
        return sectors;
    }

    /** The prizes between nothing and the top one, smallest first, each once. */
    private static List<Double> tiers(PrizeWheel wheel) {
        java.util.TreeSet<Double> tiers = new java.util.TreeSet<>();
        for (int index = 0; index < wheel.size(); index++) {
            double multiplier = wheel.multiplier(index);
            if (multiplier > 0 && multiplier < wheel.best()) {
                tiers.add(multiplier);
            }
        }
        return new ArrayList<>(tiers);
    }

    /** The prize board of the booth: every prize, best first, and how many tiles pay it. */
    static List<String> paytable(PrizeWheel wheel) {
        List<Double> tiers = tiers(wheel);
        java.util.TreeMap<Double, Integer> counts = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        for (int index = 0; index < wheel.size(); index++) {
            counts.merge(wheel.multiplier(index), 1, Integer::sum);
        }
        List<String> lines = new ArrayList<>(counts.size());
        for (java.util.Map.Entry<Double, Integer> entry : counts.entrySet()) {
            double multiplier = entry.getKey();
            String code = multiplier <= 0 ? "&7" : multiplier >= wheel.best() ? "&6&l"
                    : TIER_CODES[Math.min(TIER_CODES.length - 1, tiers.indexOf(multiplier))];
            lines.add(code + (multiplier <= 0 ? "✖" : Text.multiplier(multiplier)) + "  &7" + entry.getValue()
                    + "/" + wheel.size());
        }
        return lines;
    }

    private final class WheelGui extends Gui {

        /**
         * The twelve tiles drawn as a ring, clockwise from the top left: five on top, one
         * on the right, five underneath going back and one on the left, round the spin
         * button in the middle.
         */
        private static final int[] RING = {11, 12, 13, 14, 15, 24, 33, 32, 31, 30, 29, 20};

        private final LuckyWheelGame game;
        private final Wager wager;
        private boolean armed;

        WheelGui(MultiverseGamblingPlugin plugin, Player player, LuckyWheelGame game, Wager wager) {
            super(plugin, player, 6, plugin.messages().forSender(player,
                    "panel.lucky-wheel.title", "game", displayName(player)));
            this.game = game;
            this.wager = wager;
        }

        @Override
        protected void render() {
            clearActions();
            fill(Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build());
            ItemStack frame = Items.of(Material.YELLOW_STAINED_GLASS_PANE).name(" ").build();
            for (int slot : new int[]{0, 1, 2, 3, 5, 6, 7, 8, 45, 46, 47, 48, 50, 51, 52, 53}) {
                set(slot, frame);
            }

            PrizeWheel wheel = game.wheel();
            set(4, Items.of(Material.COMPASS)
                    .name(label(player(), "panel.common.bet",
                            "bet", stakeText(wager, wager.amount())))
                    .lore(
                            label(player(), "panel.lucky-wheel.segments", "count", wheel.size()),
                            label(player(), "panel.lucky-wheel.top-prize",
                                    "multiplier", Text.multiplier(wheel.best())),
                            label(player(), "panel.lucky-wheel.top-chance",
                                    "percent", Text.percent(wheel.bestChance())),
                            label(player(), "panel.lucky-wheel.rtp",
                                    "percent", Text.percent(wheel.rtp())),
                            "",
                            label(player(), "panel.lucky-wheel.equally-likely"))
                    .glow(true)
                    .build());

            int shown = Math.min(RING.length, wheel.size());
            for (int i = 0; i < shown; i++) {
                double multiplier = wheel.multiplier(i);
                boolean best = multiplier >= wheel.best() && multiplier > 0;
                Material material = multiplier <= 0 ? Material.GRAY_STAINED_GLASS_PANE
                        : best ? Material.GOLD_BLOCK
                        : multiplier >= 2 ? Material.LIME_CONCRETE
                        : Material.LIME_STAINED_GLASS_PANE;
                set(RING[i], Items.of(material)
                        .name(multiplier <= 0
                                ? label(player(), "panel.lucky-wheel.no-prize")
                                : label(player(), "panel.lucky-wheel.tile", "multiplier", Text.multiplier(multiplier)))
                        .lore(label(player(), "panel.lucky-wheel.chance", "percent", Text.percent(1.0 / wheel.size())),
                                label(player(), "panel.lucky-wheel.pays",
                                        "prize", stakeText(wager, wager.amount() * multiplier)))
                        .glow(best)
                        .build());
            }
            // The pointer of the wheel, over the first tile.
            set(22, Items.of(Material.EMERALD_BLOCK)
                    .name(label(player(), "panel.lucky-wheel.spin"))
                    .lore(label(player(), "panel.lucky-wheel.will-stake",
                                    "bet", stakeText(wager, wager.amount())),
                            label(player(), "panel.lucky-wheel.all-in"), "",
                            label(player(), "panel.common.click-to-spin"))
                    .glow(true)
                    .build(), e -> {
                armed = true;
                close();
                game.spin(player(), wager);
            });
            set(21, Items.of(Material.ARROW).name(label(player(), "panel.lucky-wheel.spin")).build(), e -> {
                armed = true;
                close();
                game.spin(player(), wager);
            });
            set(23, Items.of(Material.ARROW).name(label(player(), "panel.lucky-wheel.spin")).build(), e -> {
                armed = true;
                close();
                game.spin(player(), wager);
            });

            // A summary of the table: how many tiles pay each multiplier.
            java.util.Map<Double, Integer> counts = new java.util.TreeMap<>();
            for (int i = 0; i < wheel.size(); i++) {
                counts.merge(wheel.multiplier(i), 1, Integer::sum);
            }
            List<String> lines = new ArrayList<>();
            for (java.util.Map.Entry<Double, Integer> entry : counts.entrySet()) {
                lines.add(label(player(), "panel.lucky-wheel.summary-line",
                        "count", entry.getValue(),
                        "multiplier", entry.getKey() <= 0
                                ? label(player(), "panel.lucky-wheel.no-prize")
                                : Text.multiplier(entry.getKey())));
            }
            set(40, Items.of(Material.PAPER)
                    .name(label(player(), "panel.lucky-wheel.summary"))
                    .lore(lines)
                    .build());

            set(49, Items.of(Material.BARRIER)
                    .name(label(player(), "panel.common.cancel"))
                    .lore(label(player(), "panel.common.refund-lore"))
                    .build(), e -> close());
        }

        @Override
        protected void onClose() {
            if (!armed) {
                game.refund(wager);
            }
        }

        @Override
        public String sessionId() {
            return "lucky-wheel";
        }
    }
}
