package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.ItemBank;
import com.chagui68.multiversegambling.economy.ItemRules;
import com.chagui68.multiversegambling.economy.ItemWager;
import com.chagui68.multiversegambling.game.AbstractSoloGame;
import com.chagui68.multiversegambling.game.ItemOutcome;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The menu to stake items: a chest in the middle where the player drops the items, and
 * beside it, live, how many of them every possible result of the game gives back.
 *
 * <p>Only one kind of item can be staked at a time (any amount of it, custom items
 * included), so the payout is always "so many of that item". Whatever is left in the
 * chest when the menu is closed without playing goes back to the player.</p>
 */
public final class ItemStakeGui extends Gui {

    /** The chest: seven columns, three rows. */
    private static final int[] CHEST = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SUMMARY = 39;
    private static final int OUTCOMES = 41;
    private static final int CONFIRM = 49;

    private final AbstractSoloGame game;
    private boolean played;

    public ItemStakeGui(MultiverseGamblingPlugin plugin, Player player, AbstractSoloGame game) {
        super(plugin, player, 6, plugin.messages().forSender(player, "items.title",
                "game", Text.strip(game.displayName(player))));
        this.game = game;
    }

    /**
     * What is in the chest right now.
     */
    private record Stake(ItemStack item, int count, String problem) {
    }

    @Override
    protected void render() {
        clearActions();
        ItemStack dark = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        ItemStack frame = Items.of(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build();
        Inventory inventory = getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!editable(slot)) {
                set(slot, slot < 9 || slot >= 36 || slot % 9 == 0 || slot % 9 == 8 ? frame : dark);
            }
        }
        Player viewer = player();
        set(4, Items.of(Material.CHEST)
                .name(plugin.messages().forSender(viewer, "items.header", "game", game.displayName(viewer)))
                .lore(plugin.messages().loreFor(viewer, "items.header-lore",
                        "max", plugin.config().itemBetsMaxItems()))
                .glow(true)
                .build());
        set(45, Items.of(Material.BARRIER)
                .name(plugin.messages().forSender(viewer, "items.cancel"))
                .lore(plugin.messages().loreFor(viewer, "items.cancel-lore"))
                .build(), e -> close());
        set(53, Items.of(Material.GOLD_INGOT)
                .name(plugin.messages().forSender(viewer, "items.money"))
                .lore(plugin.messages().loreFor(viewer, "items.money-lore"))
                .build(), e -> {
            close();
            plugin.getServer().getScheduler().runTask(plugin, () -> game.open(viewer));
        });
        updateInfo();
    }

    @Override
    public boolean editable(int slot) {
        for (int chest : CHEST) {
            if (chest == slot) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void contentsChanged() {
        updateInfo();
    }

    /**
     * Rewrites the summary, the table of results and the play button for what is in the
     * chest, without touching the chest itself.
     */
    private void updateInfo() {
        Player viewer = player();
        Stake stake = read();
        if (stake.problem() != null) {
            set(SUMMARY - 1, Items.of(Material.PURPLE_STAINED_GLASS_PANE).name(" ").build());
            set(SUMMARY, Items.of(Material.HOPPER)
                    .name(plugin.messages().forSender(viewer, stake.problem()))
                    .lore(plugin.messages().loreFor(viewer, "items.summary-help"))
                    .build());
            set(OUTCOMES, Items.of(Material.MAP)
                    .name(plugin.messages().forSender(viewer, "items.outcomes"))
                    .lore(plugin.messages().forSender(viewer, "items.outcomes-empty"))
                    .build());
            set(CONFIRM, Items.of(Material.GRAY_DYE)
                    .name(plugin.messages().forSender(viewer, "items.play-locked"))
                    .build());
            return;
        }
        String name = ItemBank.nameOf(stake.item());
        ItemStack shown = stake.item().asQuantity(Math.max(1, Math.min(stake.item().getMaxStackSize(), stake.count())));
        set(SUMMARY, Items.of(Material.PAPER)
                .name(plugin.messages().forSender(viewer, "items.summary", "count", stake.count(), "item", name))
                .lore(plugin.messages().loreFor(viewer, "items.summary-lore"))
                .build());
        // The summary shows the real item, so custom items are recognisable at a glance. It
        // carries an extra lore line on purpose: a copy identical to the staked item could
        // be stacked into by a shift click and swallow items.
        org.bukkit.inventory.meta.ItemMeta meta = shown.getItemMeta();
        if (meta != null) {
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null
                    ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Text.c(plugin.messages().forSender(viewer, "items.preview-tag")));
            meta.lore(lore);
            shown.setItemMeta(meta);
        }
        getInventory().setItem(SUMMARY - 1, shown);

        List<String> lines = new ArrayList<>();
        for (ItemOutcome outcome : game.itemOutcomes(viewer)) {
            String chance = outcome.chance() >= 0 ? " &8(" + Text.percent(outcome.chance()) + ")" : "";
            lines.add(plugin.messages().forSender(viewer, "items.outcome-line",
                    "result", outcome.label(), "chance", chance,
                    "multiplier", Text.multiplier(outcome.multiplier()),
                    "items", game.itemsText(stake.item(), stake.count() * outcome.multiplier() * ItemRules.retention(
                            plugin.config().itemBetsDepositHaircut(), plugin.config().itemBetsHouseCut()))));
        }
        lines.add(plugin.messages().forSender(viewer, "items.outcome-lose", "count", stake.count(), "item", name));
        lines.add("");
        lines.add(plugin.messages().forSender(viewer, "items.outcomes-note"));
        set(OUTCOMES, Items.of(Material.FILLED_MAP)
                .name(plugin.messages().forSender(viewer, "items.outcomes"))
                .lore(lines)
                .glow(true)
                .build());
        set(CONFIRM, Items.of(Material.EMERALD_BLOCK)
                .name(plugin.messages().forSender(viewer, "items.play"))
                .lore(plugin.messages().loreFor(viewer, "items.play-lore", "count", stake.count(), "item", name))
                .glow(true)
                .build(), e -> play());
    }

    /**
     * Reads the chest: one kind of item, how many, or what is wrong with it.
     */
    private Stake read() {
        Inventory inventory = getInventory();
        ItemStack first = null;
        int count = 0;
        for (int slot : CHEST) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            if (first == null) {
                first = stack;
            } else if (!first.isSimilar(stack)) {
                return new Stake(null, 0, "items.mixed");
            }
            String bad = plugin.items().refusal(stack);
            if (bad != null) {
                return new Stake(null, 0, bad);
            }
            count += stack.getAmount();
        }
        if (first == null) {
            return new Stake(null, 0, "items.empty-chest");
        }
        String refusal = plugin.items().refusal(first);
        if (refusal != null) {
            return new Stake(null, 0, refusal);
        }
        if (count > plugin.config().itemBetsMaxItems()) {
            return new Stake(null, 0, "items.too-many");
        }
        if (ItemRules.betTooBig(count, plugin.itemValues().centsOf(first), plugin.config().itemBetsMaxBetValue())) {
            return new Stake(null, 0, "items.too-valuable");
        }
        return new Stake(first.asOne(), count, null);
    }

    private void play() {
        Stake stake = read();
        Player viewer = player();
        if (stake.problem() != null) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            updateInfo();
            return;
        }
        // The items leave the chest and become the bet.
        for (int slot : CHEST) {
            getInventory().setItem(slot, null);
        }
        played = true;
        close();
        ItemWager wager = plugin.items().wager(viewer, stake.item(), stake.count());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                game.startWithItems(viewer, wager);
            } else {
                wager.refund();
            }
        });
    }

    @Override
    protected void onClose() {
        if (played) {
            return;
        }
        // Nothing was played: everything left in the chest goes back.
        List<ItemStack> left = new ArrayList<>();
        for (int slot : CHEST) {
            ItemStack stack = getInventory().getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                left.add(stack.clone());
                getInventory().setItem(slot, null);
            }
        }
        plugin.items().giveStacks(player().getUniqueId(), left);
    }

    @Override
    protected boolean holdsRound() {
        return false;
    }

    @Override
    public String sessionId() {
        return game.id() + "-items";
    }
}
