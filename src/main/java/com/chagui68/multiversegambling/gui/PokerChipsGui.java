package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.ItemBank;
import com.chagui68.multiversegambling.economy.ItemRules;
import com.chagui68.multiversegambling.games.group.PokerGame;
import com.chagui68.multiversegambling.util.Items;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The item cashier of the poker table: the player drops items in the chest and sees,
 * live, what each kind is worth (from {@code item-values.yml}) and how many chips they
 * add up to. Vanilla, Slimefun and MultiverseCreatures items can be mixed.
 *
 * <p>The items stay the player's: they are kept aside while the player sits and come
 * back when they stand up, as long as their chips still cover them. Closing the menu
 * without confirming gives everything back.</p>
 */
public final class PokerChipsGui extends Gui {

    private static final int[] CHEST = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SUMMARY = 40;
    private static final int CONFIRM = 49;

    private final PokerGame game;
    private final int seat;
    private final boolean topUp;
    private boolean confirmed;

    public PokerChipsGui(MultiverseGamblingPlugin plugin, Player player, PokerGame game, int seat, boolean topUp) {
        super(plugin, player, 6, plugin.messages().forSender(player, "poker.items.title"));
        this.game = game;
        this.seat = seat;
        this.topUp = topUp;
    }

    /** What the chest holds right now. */
    private record Count(List<PokerGame.Escrow> kinds, List<String> worthless, long cents) {
    }

    @Override
    protected void render() {
        clearActions();
        ItemStack dark = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        ItemStack frame = Items.of(Material.GREEN_STAINED_GLASS_PANE).name(" ").build();
        Inventory inventory = getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!editable(slot)) {
                set(slot, slot < 9 || slot >= 36 || slot % 9 == 0 || slot % 9 == 8 ? frame : dark);
            }
        }
        set(4, Items.of(Material.CHEST)
                .name(label("poker.items.header"))
                .lore(plugin.messages().loreFor(player(), "poker.items.header-lore",
                        "min", game.format(lower()), "max", game.format(upper())))
                .glow(true)
                .build());
        set(45, Items.of(Material.BARRIER)
                .name(label("poker.items.cancel"))
                .lore(plugin.messages().loreFor(player(), "poker.items.cancel-lore"))
                .build(), e -> close());
        set(53, Items.of(Material.GOLD_INGOT)
                .name(label("poker.items.money"))
                .build(), e -> {
            Player viewer = player();
            close();
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> new PokerBuyInGui(plugin, viewer, game, seat, topUp).show());
        });
        updateInfo();
    }

    private String label(String key, Object... replacements) {
        return plugin.messages().forSender(player(), key, replacements);
    }

    private long lower() {
        return topUp ? game.bigBlindCents() : game.minBuyIn();
    }

    private long upper() {
        return topUp ? game.maxBuyIn() - Math.max(0, game.stackOf(player().getUniqueId())) : game.maxBuyIn();
    }

    private long balance() {
        return PokerGame.cents(plugin.economy().balance(player().getUniqueId()));
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
     * Groups the chest by kind of item and values each kind.
     */
    private Count read() {
        List<PokerGame.Escrow> kinds = new ArrayList<>();
        List<String> worthless = new ArrayList<>();
        long cents = 0;
        for (int slot : CHEST) {
            ItemStack stack = getInventory().getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            long unit = plugin.items().refusal(stack) != null ? -1 : plugin.itemValues().centsOf(stack);
            if (unit <= 0) {
                String name = ItemBank.nameOf(stack);
                if (!worthless.contains(name)) {
                    worthless.add(name);
                }
                continue;
            }
            // Chips are credited short of the item's value (a sink); the item can only be bought
            // back at its full price.
            long credit = ItemRules.credit(unit, plugin.config().itemBetsDepositHaircut());
            if (credit <= 0) {
                continue;
            }
            boolean merged = false;
            for (int i = 0; i < kinds.size(); i++) {
                PokerGame.Escrow kind = kinds.get(i);
                if (kind.item().isSimilar(stack)) {
                    kinds.set(i, new PokerGame.Escrow(kind.item(), kind.count() + stack.getAmount(), kind.unitCents(), kind.backCents()));
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                kinds.add(new PokerGame.Escrow(stack.asOne(), stack.getAmount(), credit, unit));
            }
            cents += credit * stack.getAmount();
        }
        return new Count(kinds, worthless, cents);
    }

    private void updateInfo() {
        Count count = read();
        List<String> lines = new ArrayList<>();
        for (PokerGame.Escrow kind : count.kinds()) {
            if (lines.size() >= 12) {
                lines.add(label("poker.items.more"));
                break;
            }
            lines.add(label("poker.items.line", "count", kind.count(), "item", ItemBank.nameOf(kind.item()),
                    "unit", game.format(kind.unitCents()), "total", game.format(kind.cents())));
        }
        for (String name : count.worthless()) {
            lines.add(label("poker.items.worthless", "item", name));
        }
        if (lines.isEmpty()) {
            lines.add(label("poker.items.empty"));
        }
        long topUpCents = Math.max(0, lower() - count.cents());
        lines.add("");
        lines.add(label("poker.items.total", "chips", game.format(count.cents())));
        if (topUpCents > 0 && count.cents() > 0) {
            lines.add(label("poker.items.top-up", "money", game.format(topUpCents)));
        }
        set(SUMMARY, Items.of(Material.FILLED_MAP)
                .name(label("poker.items.summary"))
                .lore(lines)
                .glow(count.cents() > 0)
                .build());

        String problem = null;
        if (!count.worthless().isEmpty()) {
            problem = "poker.items.remove-worthless";
        } else if (count.cents() <= 0) {
            problem = "poker.items.empty";
        } else if (count.cents() > upper()) {
            problem = "poker.items.too-much";
        } else if (topUpCents > balance()) {
            problem = "poker.items.not-enough";
        }
        if (problem != null) {
            set(CONFIRM, Items.of(Material.GRAY_DYE)
                    .name(label(problem, "max", game.format(Math.max(0, upper())), "min", game.format(lower())))
                    .build());
            return;
        }
        set(CONFIRM, Items.of(Material.EMERALD_BLOCK)
                .name(label("poker.items.confirm", "chips", game.format(count.cents() + topUpCents)))
                .lore(plugin.messages().loreFor(player(), "poker.items.confirm-lore"))
                .glow(true)
                .build(), e -> confirm());
    }

    private void confirm() {
        Player viewer = player();
        Count count = read();
        long topUpCents = Math.max(0, lower() - count.cents());
        if (!count.worthless().isEmpty() || count.cents() <= 0 || count.cents() > upper()) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            updateInfo();
            return;
        }
        var buyIn = topUpCents > 0 ? plugin.economy().ledger().hold(viewer.getUniqueId(), topUpCents / 100.0) : null;
        if (topUpCents > 0 && buyIn == null) {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            updateInfo();
            return;
        }
        // The items leave the chest and are kept aside while the player sits.
        for (int slot : CHEST) {
            getInventory().setItem(slot, null);
        }
        confirmed = true;
        close();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) {
                game.sitDown(viewer, seat, topUpCents, count.kinds(), buyIn);
            } else {
                if (buyIn != null) {
                    buyIn.refund();
                }
                List<ItemStack> back = new ArrayList<>();
                for (PokerGame.Escrow kind : count.kinds()) {
                    int max = Math.max(1, kind.item().getMaxStackSize());
                    for (int given = 0; given < kind.count(); given += max) {
                        back.add(kind.item().asQuantity(Math.min(max, kind.count() - given)));
                    }
                }
                plugin.items().giveStacks(viewer.getUniqueId(), back);
            }
        });
    }

    @Override
    protected void onClose() {
        if (confirmed) {
            return;
        }
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
        return "poker-chips";
    }
}
