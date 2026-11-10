package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.games.group.PokerGame;
import com.chagui68.multiversegambling.util.Items;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * The cashier of the poker table: how many chips to sit down with (or to add to the
 * stack), bought with money, or a door to the item cashier.
 *
 * <p>Nothing is charged until the confirm button is pressed, and then the money leaves
 * the balance in one step before the game seats the player; if the seat is gone by then
 * the game gives it straight back.</p>
 */
public final class PokerBuyInGui extends Gui {

    private final PokerGame game;
    private final int seat;
    private final boolean topUp;
    private long chips;

    public PokerBuyInGui(MultiverseGamblingPlugin plugin, Player player, PokerGame game, int seat, boolean topUp) {
        super(plugin, player, 5, plugin.messages().forSender(player, topUp ? "poker.gui.top-up-title"
                : "poker.gui.title", "seat", seat + 1));
        this.game = game;
        this.seat = seat;
        this.topUp = topUp;
        this.chips = clamp(Math.max(game.minBuyIn(), game.bigBlindCents() * 100));
    }

    private long balance() {
        return PokerGame.cents(plugin.economy().balance(player().getUniqueId()));
    }

    /** Smallest purchase: the minimum buy-in to sit down, one big blind to top up. */
    private long lower() {
        return topUp ? game.bigBlindCents() : game.minBuyIn();
    }

    /** Largest purchase: what the table allows, never more than the balance. */
    private long upper() {
        long room = topUp ? game.maxBuyIn() - Math.max(0, game.stackOf(player().getUniqueId())) : game.maxBuyIn();
        return Math.min(room, balance());
    }

    private long clamp(long value) {
        long unit = Math.max(1, game.smallBlindCents());
        long rounded = Math.round(value / (double) unit) * unit;
        return Math.max(lower(), Math.min(Math.max(lower(), upper()), rounded));
    }

    private void setTo(long value) {
        chips = clamp(value);
        refresh();
    }

    @Override
    protected void render() {
        clearActions();
        frame(Material.GREEN_STAINED_GLASS_PANE);
        Player viewer = player();
        long bb = game.bigBlindCents();
        boolean afford = upper() >= lower();

        set(4, Items.of(Material.PAPER)
                .name(label("poker.gui.header", "seat", seat + 1))
                .lore(plugin.messages().loreFor(viewer, "poker.gui.header-lore",
                        "small", game.format(game.smallBlindCents()), "big", game.format(bb),
                        "min", game.format(game.minBuyIn()), "max", game.format(game.maxBuyIn()),
                        "balance", game.format(balance())))
                .glow(true)
                .build());

        set(13, Items.of(Material.GOLD_BLOCK)
                .name(label("poker.gui.chips", "chips", game.format(chips)))
                .lore(plugin.messages().loreFor(viewer, "poker.gui.chips-lore",
                        "blinds", Math.round(chips / (double) bb)))
                .glow(true)
                .build());

        preset(19, Material.RED_DYE, "poker.gui.less", () -> setTo(Math.round(chips * 0.8)));
        preset(20, Material.IRON_NUGGET, "poker.gui.minimum", () -> setTo(lower()));
        preset(21, Material.COPPER_INGOT, "poker.gui.blinds-50", () -> setTo(bb * 50));
        preset(22, Material.GOLD_INGOT, "poker.gui.blinds-100", () -> setTo(bb * 100));
        preset(23, Material.DIAMOND, "poker.gui.blinds-200", () -> setTo(bb * 200));
        preset(24, Material.EMERALD, "poker.gui.maximum", () -> setTo(upper()));
        preset(25, Material.LIME_DYE, "poker.gui.more", () -> setTo(Math.round(chips * 1.25)));

        if (afford) {
            set(38, Items.of(Material.LIME_CONCRETE)
                    .name(label(topUp ? "poker.gui.confirm-top-up" : "poker.gui.confirm", "chips", game.format(chips)))
                    .lore(plugin.messages().loreFor(viewer, "poker.gui.confirm-lore", "chips", game.format(chips)))
                    .glow(true)
                    .build(), e -> confirm());
        } else {
            set(38, Items.of(Material.RED_CONCRETE)
                    .name(label("poker.gui.no-funds"))
                    .lore(plugin.messages().loreFor(viewer, "poker.gui.no-funds-lore",
                            "min", game.format(lower()), "balance", game.format(balance())))
                    .build());
        }
        if (game.itemBuyIn()) {
            set(42, Items.of(Material.CHEST)
                    .name(label("poker.gui.items"))
                    .lore(plugin.messages().loreFor(viewer, "poker.gui.items-lore"))
                    .glow(true)
                    .build(), e -> {
                close();
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new PokerChipsGui(plugin, viewer, game, seat, topUp).show());
            });
        }
        set(36, Items.of(Material.BARRIER)
                .name(label("poker.gui.cancel"))
                .build(), e -> close());
    }

    private String label(String key, Object... replacements) {
        return plugin.messages().forSender(player(), key, replacements);
    }

    private void preset(int slot, Material material, String key, Runnable action) {
        set(slot, Items.of(material).name(label(key)).build(), e -> action.run());
    }

    private void confirm() {
        Player viewer = player();
        long amount = clamp(chips);
        if (amount < lower() || amount > upper()) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            refresh();
            return;
        }
        var buyIn = plugin.economy().ledger().hold(viewer.getUniqueId(), amount / 100.0);
        if (buyIn == null) {
            String why = plugin.economy().takeRefusal();
            plugin.messages().send(viewer, why != null ? why : "economy.not-enough-money", "bet", game.format(amount));
            return;
        }
        close();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                game.sitDown(viewer, seat, amount, List.of(), buyIn);
            } else {
                buyIn.refund();
            }
        });
    }

    @Override
    protected boolean holdsRound() {
        return false;
    }

    @Override
    public String sessionId() {
        return "poker-buy-in";
    }
}
