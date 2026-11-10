package com.chagui68.multiversegambling.listener;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.game.Game;

import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Closes games and rooms when a player joins or leaves the server.
 */
public final class PlayerListener implements Listener {

    private final MultiverseGamblingPlugin plugin;

    public PlayerListener(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.economy().ensureAccount(event.getPlayer());
        // Winnings in items settled while the player was away are handed over once the
        // inventory is ready.
        org.bukkit.entity.Player joined = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (joined.isOnline()) {
                plugin.economy().recover(joined.getUniqueId());
                plugin.items().deliverPending(joined);
            }
        }, 20L);
        if (plugin.world() != null) {
            plugin.world().handleJoin(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        // Close the single player game first: it refunds whatever is left over.
        plugin.sessions().cancel(id);
        // Then pull the player out of any open room or table.
        for (Game game : plugin.games().all()) {
            game.handleQuit(id);
        }
    }
}
