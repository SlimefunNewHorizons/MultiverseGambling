package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.config.Messages;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.util.Items;

import java.util.function.DoubleConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Bet selector shared by every game.
 *
 * <p>Keeping it in one place avoids the classic bug of a game accepting a bet above
 * the balance or below the minimum. Every path clamps here.</p>
 */
public final class BetSelectorGui extends Gui {

    private static final ItemStack FILLER =
            Items.of(Material.BLACK_STAINED_GLASS_PANE).build();

    private final Game game;
    private final DoubleConsumer onConfirm;
    private final DoubleConsumer onHouse;
    private double bet;

    public BetSelectorGui(MultiverseGamblingPlugin plugin, Player player, Game game,
                          double initial, DoubleConsumer onConfirm) {
        this(plugin, player, game, initial, onConfirm, null);
    }

    /**
     * @param onHouse what betting against the house does, or {@code null} when the game
     *                does not offer it right now
     */
    public BetSelectorGui(MultiverseGamblingPlugin plugin, Player player, Game game,
                          double initial, DoubleConsumer onConfirm, DoubleConsumer onHouse) {
        super(plugin, player, 5, plugin.messages().forSender(player, "gui.bet.title",
                "game", game.displayName(player)));
        this.game = game;
        this.onConfirm = onConfirm;
        this.onHouse = onHouse;
        this.bet = clamp(initial);
    }

    private Messages messages() {
        return plugin.messages();
    }

    private double balance() {
        return plugin.economy().balance(player().getUniqueId());
    }

    private double upperLimit() {
        return Math.max(game.minBet(), Math.min(game.maxBet(), balance()));
    }

    private double clamp(double value) {
        double rounded = Math.rint(value * 100.0) / 100.0;
        return Math.max(game.minBet(), Math.min(upperLimit(), rounded));
    }

    private void adjust(double factor) {
        bet = clamp(bet * factor);
        refresh();
    }

    private void setTo(double value) {
        bet = clamp(value);
        refresh();
    }

    @Override
    protected void render() {
        clearActions();
        frame(Material.YELLOW_STAINED_GLASS_PANE);
        Player viewer = player();

        double balance = balance();
        double limit = upperLimit();
        boolean afford = balance + 1e-9 >= game.minBet();

        set(4, Items.of(Material.GOLD_INGOT)
                .name(messages().forSender(viewer, "gui.bet.current",
                        "bet", plugin.economy().format(bet)))
                .lore(messages().loreFor(viewer, "gui.bet.lore",
                        "game", game.displayName(viewer),
                        "balance", plugin.economy().format(balance),
                        "min", plugin.economy().format(game.minBet()),
                        "max", plugin.economy().format(Math.min(game.maxBet(), balance))))
                .glow(true)
                .build());

        button(10, Material.RED_DYE, "gui.bet.half", "gui.bet.half-lore", e -> adjust(0.5));
        button(11, Material.GOLD_NUGGET, "gui.bet.down", "gui.bet.down-lore", e -> adjust(0.9));
        button(12, Material.LIME_DYE, "gui.bet.up", "gui.bet.up-lore", e -> adjust(1.1));
        button(13, Material.GOLD_BLOCK, "gui.bet.double", "gui.bet.double-lore", e -> adjust(2.0));
        button(14, Material.IRON_NUGGET, "gui.bet.min", "gui.bet.min-lore", e -> setTo(game.minBet()));
        button(15, Material.DIAMOND, "gui.bet.half-balance", "gui.bet.half-balance-lore",
                e -> setTo(balance / 2.0));
        button(16, Material.EMERALD_BLOCK, "gui.bet.all", "gui.bet.all-lore", e -> setTo(balance));

        set(22, Items.of(Material.PAPER)
                .name(messages().forSender(viewer, "gui.bet.selected"))
                .lore(messages().loreFor(viewer, "gui.bet.selected-lore",
                        "bet", plugin.economy().format(bet),
                        "limit", plugin.economy().format(limit)))
                .build());

        boolean itemOption = game instanceof com.chagui68.multiversegambling.game.AbstractSoloGame itemGame
                && itemGame.supportsItemBets() && plugin.config().itemBetsEnabled();
        if (itemOption && game instanceof com.chagui68.multiversegambling.game.AbstractSoloGame solo) {
            set(42, Items.of(Material.CHEST)
                    .name(messages().forSender(viewer, "gui.bet.items"))
                    .lore(messages().loreFor(viewer, "gui.bet.items-lore"))
                    .glow(true)
                    .build(), e -> {
                close();
                plugin.getServer().getScheduler().runTask(plugin,
                        () -> new ItemStakeGui(plugin, viewer, solo).show());
            });
        }
        if (afford && onHouse != null) {
            set(42, Items.of(Material.GOLD_BLOCK)
                    .name(messages().forSender(viewer, "gui.bet.house"))
                    .lore(messages().loreFor(viewer, "gui.bet.house-lore",
                            "bet", plugin.economy().format(bet)))
                    .glow(true)
                    .build(), e -> confirm(onHouse));
        }
        if (afford) {
            set(onHouse != null || itemOption ? 38 : 40, Items.of(Material.LIME_CONCRETE)
                    .name(messages().forSender(viewer, "gui.bet.confirm"))
                    .lore(messages().loreFor(viewer, "gui.bet.confirm-lore",
                            "bet", plugin.economy().format(bet),
                            "game", game.displayName(viewer)))
                    .glow(true)
                    .build(), e -> confirm(onConfirm));
        } else {
            set(40, Items.of(Material.RED_CONCRETE)
                    .name(messages().forSender(viewer, "gui.bet.no-funds"))
                    .lore(messages().loreFor(viewer, "gui.bet.no-funds-lore",
                            "min", plugin.economy().format(game.minBet()),
                            "balance", plugin.economy().format(balance)))
                    .build());
        }

        set(36, Items.of(Material.BARRIER)
                .name(messages().forSender(viewer, "gui.bet.back"))
                .lore(messages().loreFor(viewer, "gui.bet.back-lore"))
                .build(), e -> {
            close();
            plugin.guis().openHub(viewer);
        });
    }

    /**
     * Places a labelled button that runs the given action when clicked.
     */
    private void button(int slot, Material material, String nameKey, String loreKey,
                        java.util.function.Consumer<InventoryClickEvent> action) {
        Player viewer = player();
        ItemStack item = Items.of(material)
                .name(messages().forSender(viewer, nameKey))
                .lore(messages().loreFor(viewer, loreKey))
                .build();
        set(slot, item, action);
    }

    private boolean confirmed;

    private void confirm(DoubleConsumer action) {
        if (confirmed) {
            return;
        }
        confirmed = true;
        double amount = bet;
        close();
        // The game opens its own menu right after this, and a menu opened in the same
        // tick as the closing of another one left some clients looking at an inventory
        // that never took a click. One tick apart, the new menu always wins.
        plugin.getServer().getScheduler().runTask(plugin, () -> action.accept(amount));
    }

    @Override
    public String sessionId() {
        return game.id();
    }

    @Override
    protected void onClose() {
        // Closing the selector without confirming costs nothing: nothing was charged yet.
    }

    @Override
    protected boolean holdsRound() {
        return false;
    }
}
