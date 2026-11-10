package com.chagui68.multiversegambling.economy;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Where bets staked with items come from and where their winnings go.
 *
 * <p>Winnings go straight into the inventory of the player; whatever does not fit is
 * dropped at their feet. A player who is no longer online (a round settled after a
 * logout, a refund during a shutdown) keeps their items here, saved to
 * {@code pending-items.yml}, and gets them the next time they join. Items are stored
 * with their full data, so custom items come back exactly as they were staked.</p>
 */
public final class ItemBank {

    private final MultiverseGamblingPlugin plugin;
    private final File file;
    private final Map<UUID, List<ItemStack>> pending = new LinkedHashMap<>();

    public ItemBank(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pending-items.yml");
        load();
    }

    // ------------------------------------------------------------------ staking

    /**
     * Turns items already taken from the player into a bet.
     */
    public ItemWager wager(Player player, ItemStack item, int count) {
        UUID playerId = player.getUniqueId();
        String id = plugin.economy().ledger().openItems(playerId,
                Base64.getEncoder().encodeToString(item.asOne().serializeAsBytes()), count);
        return new ItemWager(plugin.economy().ledger(), playerId, item, count, this,
                () -> plugin.fair().roll(playerId), id);
    }

    /**
     * Why an item cannot be staked, as a language key, or {@code null} when it can.
     */
    public String refusal(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return "items.empty";
        }
        if (ItemMath.blocked(item.getType().name(), plugin.config().itemBetsBlocked())) {
            return "items.blocked";
        }
        if (item.getAmount() > item.getMaxStackSize()) {
            return "items.blocked";
        }
        ItemMeta meta = item.getItemMeta();
        // A payout is a fresh copy of the staked item: anything that carries other items
        // inside would be duplicated with its contents.
        if (meta instanceof org.bukkit.inventory.meta.BundleMeta bundle && bundle.hasItems()) {
            return "items.blocked";
        }
        if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta state && state.hasBlockState()
                && state.getBlockState() instanceof org.bukkit.inventory.InventoryHolder holder
                && !isEmpty(holder.getInventory())) {
            return "items.blocked";
        }
        return null;
    }

    private static boolean isEmpty(org.bukkit.inventory.Inventory inventory) {
        for (ItemStack content : inventory.getContents()) {
            if (content != null && content.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    /**
     * Name of an item for messages: its custom name when it has one, the vanilla one
     * otherwise.
     */
    @SuppressWarnings("deprecation")
    public static String nameOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return LegacyComponentSerializer.legacyAmpersand().serialize(meta.displayName());
        }
        return item.getI18NDisplayName();
    }

    // ------------------------------------------------------------------ paying

    /**
     * Gives that many copies of the item to the player, now or the next time they join.
     */
    public void give(UUID playerId, ItemStack item, int amount) {
        if (amount <= 0) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            pending.computeIfAbsent(playerId, id -> new ArrayList<>()).addAll(split(item, amount));
            save();
            return;
        }
        handOver(player, split(item, amount));
    }

    /**
     * Gives back a set of stacks as they are (the leftovers of the item bet menu), now or
     * the next time the player joins.
     */
    public void giveStacks(UUID playerId, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            return;
        }
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            List<ItemStack> kept = pending.computeIfAbsent(playerId, id -> new ArrayList<>());
            stacks.forEach(stack -> kept.add(stack.clone()));
            save();
            return;
        }
        handOver(player, stacks);
    }

    /**
     * Hands over everything kept for a player who just joined.
     */
    public void deliverPending(Player player) {
        List<ItemStack> kept = pending.remove(player.getUniqueId());
        if (kept == null || kept.isEmpty()) {
            return;
        }
        save();
        handOver(player, kept);
        plugin.messages().send(player, "items.delivered", "count", kept.stream().mapToInt(ItemStack::getAmount).sum());
    }

    private void handOver(Player player, List<ItemStack> stacks) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(stacks.toArray(new ItemStack[0]));
        if (!left.isEmpty()) {
            for (ItemStack rest : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            plugin.messages().send(player, "items.dropped");
        }
    }

    private static List<ItemStack> split(ItemStack item, int amount) {
        List<ItemStack> out = new ArrayList<>();
        for (int size : ItemMath.stacks(amount, item.getMaxStackSize())) {
            out.add(item.asQuantity(size));
        }
        return out;
    }

    // ---------------------------------------------------------------- storage

    private void load() {
        if (!file.isFile()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                List<ItemStack> items = new ArrayList<>();
                for (String encoded : yaml.getStringList(key)) {
                    items.add(ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
                }
                if (!items.isEmpty()) {
                    pending.put(id, items);
                }
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Skipped unreadable pending items of " + key + ": " + error.getMessage());
            }
        }
    }

    /**
     * Writes the items waiting for their owners; called on every change, because they are
     * real items and must survive a crash.
     */
    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        pending.forEach((id, items) -> {
            List<String> encoded = new ArrayList<>();
            for (ItemStack item : items) {
                encoded.add(Base64.getEncoder().encodeToString(item.serializeAsBytes()));
            }
            yaml.set(id.toString(), encoded);
        });
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException error) {
            plugin.getLogger().severe("Could not save pending-items.yml: " + error.getMessage());
        }
    }
}
