package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.session.SoloSession;
import com.chagui68.multiversegambling.util.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Chest menu with one action per slot.
 *
 * <p>It registers itself as a session, so an open menu survives ticks, refreshes without
 * flickering and closes by itself when the player leaves.</p>
 */
public abstract class Gui implements InventoryHolder, SoloSession {

    protected final MultiverseGamblingPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    private boolean opened;
    private boolean swapping;
    private boolean headless;
    private long lastClick = Long.MIN_VALUE / 2;

    protected Gui(MultiverseGamblingPlugin plugin, Player player, int rows, String title) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, Text.c(title));
    }

    @Override
    public final Inventory getInventory() {
        return inventory;
    }

    protected final Player player() {
        return player;
    }

    protected final MultiverseGamblingPlugin plugin() {
        return plugin;
    }

    protected final int size() {
        return inventory.getSize();
    }

    // -------------------------------------------------------------- construccion

    /**
     * Paints the menu content. Called when opening and on every refresh.
     */
    protected abstract void render();

    /**
     * Places a decorative item.
     */
    protected final void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    protected final void set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (action == null) {
            actions.remove(slot);
        } else {
            actions.put(slot, action);
        }
    }

    protected final void clearActions() {
        actions.clear();
    }

    protected final void fill(ItemStack item) {
        for (int slot = 0; slot < size(); slot++) {
            set(slot, item);
        }
    }

    protected final void fillRow(int row, ItemStack item) {
        int start = row * 9;
        for (int slot = start; slot < start + 9; slot++) {
            set(slot, item);
        }
    }

    /**
     * The look shared by the casino menus: a dark background inside a frame of panes in
     * the colour of the game. Buttons placed afterwards sit on top of it.
     */
    protected final void frame(org.bukkit.Material accent) {
        fill(com.chagui68.multiversegambling.util.Items.of(org.bukkit.Material.BLACK_STAINED_GLASS_PANE)
                .name(" ").build());
        border(com.chagui68.multiversegambling.util.Items.of(accent).name(" ").build());
    }

    /**
     * Wraps the border of the menu with an item, leaving the inside empty.
     */
    protected final void border(ItemStack item) {
        int rows = size() / 9;
        fillRow(0, item);
        fillRow(rows - 1, item);
        for (int row = 1; row < rows - 1; row++) {
            set(row * 9, item);
            set(row * 9 + 8, item);
        }
    }

    // ------------------------------------------------------------------ apertura

    public final void show() {
        show(false);
    }

    /**
     * Registers the round without opening the menu: the game is painted on the blocks
     * of its arena and those clicks are the input. Everything else (ticks, refresh,
     * closing by hand or by the plug-in) works exactly like a normal menu.
     */
    public final void showOnArena() {
        show(true);
    }

    private void show(boolean onArena) {
        if (!player.isOnline()) {
            return;
        }
        headless = onArena;
        render();
        opened = true;
        // Only the menus of a round are a session: a plain menu opened on top of a
        // running round (the main menu, the statistics) must never cancel it.
        if (holdsRound()) {
            plugin.sessions().track(this);
        }
        if (!onArena) {
            player.openInventory(inventory);
        }
    }

    /**
     * Redraws, and reopens the menu when the round is not being played on an arena.
     */
    public final void refresh() {
        if (!opened || !player.isOnline()) {
            return;
        }
        render();
        if (headless) {
            return;
        }
        swapping = true;
        try {
            player.openInventory(inventory);
        } finally {
            swapping = false;
        }
    }

    /**
     * Closes the menu on purpose, without firing the exit logic.
     */
    public final void close() {
        cancel();
    }

    // ------------------------------------------------------------------- eventos

    public final void handleClick(InventoryClickEvent event) {
        // Two clicks inside 100 ms are one: the second one never runs an action twice.
        long now = System.nanoTime();
        if (now - lastClick < 100_000_000L) {
            return;
        }
        lastClick = now;
        Consumer<InventoryClickEvent> action = actions.get(event.getSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    /**
     * Called by the listener when the player closes the menu by hand.
     */
    public final void handleClose() {
        if (swapping || !opened) {
            return;
        }
        opened = false;
        onClose();
    }

    protected void onClose() {
    }

    /**
     * True when this menu is the round itself (a bet is riding on it), so it takes the
     * player's session slot. Menus that only show or pick things answer false.
     */
    protected boolean holdsRound() {
        return true;
    }

    /**
     * True for a slot the player may put items into and take them out of (the chest of
     * the item bet menu). Every other slot of every menu is locked.
     */
    public boolean editable(int slot) {
        return false;
    }

    /**
     * True when this menu has editable slots at all.
     */
    public boolean acceptsItems() {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (editable(slot)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Called one tick after the player moved items in or out of the editable slots.
     */
    public void contentsChanged() {
    }

    // --------------------------------------------------------------- SoloSession

    @Override
    public final UUID playerId() {
        return player.getUniqueId();
    }

    @Override
    public String sessionId() {
        return "menu";
    }

    @Override
    public final void tick() {
        if (!opened) {
            return;
        }
        onTick();
    }

    /**
     * For animated menus.
     */
    protected void onTick() {
    }

    @Override
    public final boolean active() {
        return opened && player.isOnline();
    }

    @Override
    public void cancel() {
        if (!opened) {
            return;
        }
        opened = false;
        if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == this) {
            player.closeInventory();
        }
        onClose();
    }
}
