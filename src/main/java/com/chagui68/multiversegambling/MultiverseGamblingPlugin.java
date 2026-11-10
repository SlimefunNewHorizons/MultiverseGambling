package com.chagui68.multiversegambling;

import com.chagui68.multiversegambling.command.MultiverseGamblingCommand;
import com.chagui68.multiversegambling.config.Messages;
import com.chagui68.multiversegambling.config.MultiverseGamblingConfig;
import com.chagui68.multiversegambling.economy.EconomyManager;
import com.chagui68.multiversegambling.economy.ItemBank;
import com.chagui68.multiversegambling.fair.FairnessService;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.game.GameRegistry;
import com.chagui68.multiversegambling.games.group.BombBoardGame;
import com.chagui68.multiversegambling.games.group.ColorRouletteGame;
import com.chagui68.multiversegambling.games.group.DicePokerGame;
import com.chagui68.multiversegambling.games.group.DuelGame;
import com.chagui68.multiversegambling.games.group.HorseRaceGame;
import com.chagui68.multiversegambling.games.group.HotBombGame;
import com.chagui68.multiversegambling.games.group.JackpotGame;
import com.chagui68.multiversegambling.games.group.RaffleGame;
import com.chagui68.multiversegambling.games.group.RussianRouletteGame;
import com.chagui68.multiversegambling.games.solo.BlackjackGame;
import com.chagui68.multiversegambling.games.solo.ClassicRouletteGame;
import com.chagui68.multiversegambling.games.solo.CoinFlipGame;
import com.chagui68.multiversegambling.games.solo.CrashGame;
import com.chagui68.multiversegambling.games.solo.DiceGame;
import com.chagui68.multiversegambling.games.solo.HighLowGame;
import com.chagui68.multiversegambling.games.solo.LuckyWheelGame;
import com.chagui68.multiversegambling.games.solo.MinesGame;
import com.chagui68.multiversegambling.games.solo.PlinkoGame;
import com.chagui68.multiversegambling.games.solo.ScratchCardGame;
import com.chagui68.multiversegambling.games.solo.SlotsGame;
import com.chagui68.multiversegambling.games.solo.TowersGame;
import com.chagui68.multiversegambling.gui.GuiListener;
import com.chagui68.multiversegambling.gui.GuiManager;
import com.chagui68.multiversegambling.i18n.LanguageStore;
import com.chagui68.multiversegambling.listener.BoardListener;
import com.chagui68.multiversegambling.listener.PropListener;
import com.chagui68.multiversegambling.listener.PlayerListener;
import com.chagui68.multiversegambling.session.SessionManager;
import com.chagui68.multiversegambling.stats.StatsStore;
import com.chagui68.multiversegambling.world.CasinoWorldManager;
import com.chagui68.multiversegambling.world.anim.ArenaShow;
import com.chagui68.multiversegambling.world.board.ArenaBoard;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Chance and betting engine for Paper.
 *
 * <p>All the money flows through {@link EconomyManager}, every playable roll comes
 * from the {@code engine} package (pinned by unit tests) and all the suspense runs
 * on the single clock of {@link SessionManager}. Text is resolved per player by
 * {@link Messages}, so the whole plugin can be switched between languages in game.</p>
 *
 * <p>Enabling is written so that no single piece can take the plugin down with it: the
 * catalogue, the casino world and the data files are each prepared on their own, and a
 * failing one is reported and skipped. The menus, the wallet and the games keep working
 * without the dedicated world, and the JSON files survive being edited by hand or left
 * half written by a crash (see {@code util.JsonStore}).</p>
 */
public final class MultiverseGamblingPlugin extends JavaPlugin {

    private MultiverseGamblingConfig config;
    private Messages messages;
    private LanguageStore languages;
    private EconomyManager economy;
    private FairnessService fair;
    private ItemBank items;
    private com.chagui68.multiversegambling.economy.ItemValues itemValues;
    private com.chagui68.multiversegambling.help.Guide guide;
    private StatsStore stats;
    private SessionManager sessions;
    private GuiManager guis;
    private GameRegistry games;
    private CasinoWorldManager world;
    private BukkitTask autosave;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        config = new MultiverseGamblingConfig(this);
        messages = new Messages(this);
        languages = new LanguageStore(this);
        economy = new EconomyManager(this);
        economy.setup();
        fair = new FairnessService(this);
        items = new ItemBank(this);
        // Bets a crash or a failed payout left unsettled go back to their owners, now and every minute.
        getServer().getScheduler().runTaskTimer(this, () -> economy.recover(null), 20L, 1200L);
        itemValues = new com.chagui68.multiversegambling.economy.ItemValues(this);
        itemValues.load();
        guide = new com.chagui68.multiversegambling.help.Guide(this);
        stats = new StatsStore(this);
        sessions = new SessionManager(this);
        guis = new GuiManager(this);
        games = new GameRegistry(this);

        registerGames();

        world = new CasinoWorldManager(this);
        try {
            world.setup();
        } catch (RuntimeException error) {
            // The dedicated world is the one optional piece: the menus, the wallet and
            // every game work without it, so a broken world is reported instead of
            // aborting the startup.
            getLogger().log(Level.SEVERE, "The casino world could not be prepared; the games"
                    + " stay menu-only and /mvgam world build can be tried again later.", error);
        }

        getServer().getPluginManager().registerEvents(new GuiListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new BoardListener(this), this);
        getServer().getPluginManager().registerEvents(new PropListener(), this);
        getServer().getPluginManager().registerEvents(
                new com.chagui68.multiversegambling.listener.WorldProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(
                new com.chagui68.multiversegambling.listener.TableListener(this), this);

        MultiverseGamblingCommand command = new MultiverseGamblingCommand(this);
        PluginCommand pluginCommand = getCommand("mvgam");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        } else {
            getLogger().severe("Could not register /mvgam; check plugin.yml");
        }

        sessions.start();
        scheduleAutosave();

        long solo = games.byCategory(GameCategory.SOLO).size();
        long group = games.byCategory(GameCategory.GROUP).size();
        getLogger().info("Catalogue loaded: " + solo + " solo games and " + group
                + " group games (" + games.enabled().size() + " enabled).");
        getLogger().info("Provably fair rolls: " + (fair.enabled() ? "enabled" : "disabled")
                + " | current secret " + fair.serverSeedHash().substring(0, 16) + "...");
        getLogger().info("Languages available: " + messages.localeList()
                + " | default " + messages.defaultLocale() + ".");
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.shutdownAll();
        }
        if (sessions != null) {
            sessions.shutdown();
        }
        // Shows whose scenery was waiting to be taken down would lose their delayed
        // cleanup with the scheduler, so they are cleared here instead. Boards left on
        // an arena are put back the same way.
        ArenaShow.clearAll();
        ArenaBoard.clearAll();
        if (world != null) {
            world.shutdown();
        }
        if (autosave != null) {
            autosave.cancel();
        }
        if (economy != null) {
            economy.save();
        }
        if (stats != null) {
            stats.save();
        }
        if (items != null) {
            items.save();
        }
        if (fair != null) {
            fair.save();
        }
        if (languages != null) {
            languages.save();
        }
        getLogger().info("Plugin disabled; money from unfinished games was refunded.");
    }

    /**
     * The whole catalogue. Adding a game is adding one line here.
     *
     * <p>Each game is built inside {@link #registerGame(Supplier)}, so one game that
     * cannot be prepared is reported and skipped while the rest of the catalogue loads.
     * The hubs are built from the registry, so a missing game simply has no entry.</p>
     */
    private void registerGames() {
        // --- Solo ---
        registerGame(() -> new ClassicRouletteGame(this));
        registerGame(() -> new SlotsGame(this));
        registerGame(() -> new CrashGame(this));
        registerGame(() -> new MinesGame(this));
        registerGame(() -> new TowersGame(this));
        registerGame(() -> new BlackjackGame(this));
        registerGame(() -> new HighLowGame(this));
        registerGame(() -> new DiceGame(this));
        registerGame(() -> new PlinkoGame(this));
        registerGame(() -> new LuckyWheelGame(this));
        registerGame(() -> new ScratchCardGame(this));
        registerGame(() -> new CoinFlipGame(this));

        // --- Group ---
        registerGame(() -> new ColorRouletteGame(this));
        registerGame(() -> new JackpotGame(this));
        registerGame(() -> new HotBombGame(this));
        registerGame(() -> new BombBoardGame(this));
        registerGame(() -> new RussianRouletteGame(this));
        registerGame(() -> new HorseRaceGame(this));
        registerGame(() -> new DuelGame(this));
        registerGame(() -> new RaffleGame(this));
        registerGame(() -> new DicePokerGame(this));
        registerGame(() -> new com.chagui68.multiversegambling.games.group.PokerGame(this));
    }

    /**
     * Builds and registers one game. A game is never worth stopping the plugin for.
     */
    private void registerGame(Supplier<Game> factory) {
        Game game = null;
        try {
            game = factory.get();
            games.register(game);
        } catch (RuntimeException error) {
            getLogger().log(Level.SEVERE, "Could not prepare the game "
                    + (game == null ? "of the catalogue" : "'" + game.id() + "'")
                    + "; it will be missing from the menus.", error);
        }
    }

    private void scheduleAutosave() {
        long ticks = Math.max(1, config.statsSaveMinutes()) * 60L * 20L;
        autosave = getServer().getScheduler().runTaskTimer(this, () -> {
            stats.saveAsync();
            economy.saveAsync();
            fair.saveAsync();
        }, ticks, ticks);
    }

    /**
     * Reloads config, messages and languages without touching balances or live games.
     */
    public void reloadAll() {
        reloadConfig();
        config.reload();
        messages.reload();
        economy.setup();
        itemValues.load();
        if (world != null) {
            // The floating names of the pavilions are written in the default language.
            world.refreshDecor();
        }
        if (autosave != null) {
            autosave.cancel();
        }
        scheduleAutosave();
    }

    public MultiverseGamblingConfig config() {
        return config;
    }

    public Messages messages() {
        return messages;
    }

    public LanguageStore languages() {
        return languages;
    }

    public EconomyManager economy() {
        return economy;
    }

    /**
     * Items staked in bets and items waiting for players who were offline.
     */
    public ItemBank items() {
        return items;
    }

    /**
     * Reference value of items, used to buy poker chips with them.
     */
    public com.chagui68.multiversegambling.help.Guide guide() {
        return guide;
    }

    public com.chagui68.multiversegambling.economy.ItemValues itemValues() {
        return itemValues;
    }

    public FairnessService fair() {
        return fair;
    }

    public StatsStore stats() {
        return stats;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public GuiManager guis() {
        return guis;
    }

    public GameRegistry games() {
        return games;
    }

    public CasinoWorldManager world() {
        return world;
    }
}
