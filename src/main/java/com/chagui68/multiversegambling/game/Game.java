package com.chagui68.multiversegambling.game;

import java.util.List;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * One casino game.
 *
 * <p>{@link #open(Player)} is always the single entry point: solo games open the bet
 * selector, group games drop the player in the waiting room of the round in
 * progress.</p>
 */
public interface Game {

    GameMeta meta();

    default String id() {
        return meta().id();
    }

    default String name() {
        return meta().name();
    }

    default GameCategory category() {
        return meta().category();
    }

    /**
     * True when one player alone can play it against the house: every solo game, and
     * the group games that seat the dealer when the room is empty.
     */
    default boolean playableAgainstHouse() {
        return category() == GameCategory.SOLO;
    }

    default Material icon() {
        return meta().icon();
    }

    default List<String> description() {
        return meta().description();
    }

    /**
     * Game name in the language of the viewer.
     */
    String displayName(CommandSender viewer);

    /**
     * Description in the language of the viewer, line by line.
     */
    List<String> displayDescription(CommandSender viewer);

    default String permission() {
        return meta().permission();
    }

    default int minPlayers() {
        return meta().minPlayers();
    }

    default int maxPlayers() {
        return meta().maxPlayers();
    }

    default boolean solo() {
        return category() == GameCategory.SOLO;
    }

    double minBet();

    double maxBet();

    boolean enabled();

    /**
     * Enters the game: opens the bet selector or the waiting room.
     */
    void open(Player player);

    /**
     * Enters the game straight against the house: what the chat button that offers it
     * runs ({@code /mvgam play <game> house}). A game without a house duel just opens.
     */
    default void openAgainstHouse(Player player) {
        open(player);
    }

    /**
     * Action fired from a chat button ({@code /mvgam action ...}). Does nothing by
     * default: only the interactive games use it.
     */
    default void handleAction(Player player, String action, String[] args) {
    }

    /**
     * @return true when the player is currently inside this game. This is what lets a
     * chat button know which round it belongs to.
     */
    default boolean ownsPlayer(UUID playerId) {
        return false;
    }

    /**
     * Extra menu line with the current state, in the language of the viewer.
     */
    default List<String> statusLore(CommandSender viewer) {
        return List.of();
    }

    /**
     * How many people are waiting or playing right now.
     */
    default int activePlayers() {
        return 0;
    }

    /**
     * A player left the server: whatever they were doing in this game is closed.
     */
    default void handleQuit(UUID playerId) {
    }

    /**
     * Orderly shutdown: refunds the money of unfinished games.
     */
    default void shutdown() {
    }
}
