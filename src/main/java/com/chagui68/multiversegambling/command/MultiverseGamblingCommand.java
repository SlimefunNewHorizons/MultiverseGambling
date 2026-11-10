package com.chagui68.multiversegambling.command;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.config.Messages;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.i18n.Language;
import com.chagui68.multiversegambling.stats.PlayerStats;
import com.chagui68.multiversegambling.stats.StatsStore;
import com.chagui68.multiversegambling.util.Text;
import com.chagui68.multiversegambling.world.CasinoWorldManager;
import com.chagui68.multiversegambling.world.WorldReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /mvgam}: opens the menu and exposes balance, ranking, audit and administration.
 */
public final class MultiverseGamblingCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "menu", "games", "play", "action", "balance", "wallet", "help", "guia", "stats", "top", "verify",
            "world", "language", "info", "reload", "give", "take", "set", "cancel");

    private final MultiverseGamblingPlugin plugin;
    private final com.chagui68.multiversegambling.util.RateLimiter limiter =
            new com.chagui68.multiversegambling.util.RateLimiter(3, 1500);

    public MultiverseGamblingCommand(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                plugin.messages().send(sender, "command.players-only");
                return true;
            }
            if (!player.hasPermission("mvgam_play")) {
                plugin.messages().send(player, "general.no-permission");
                return true;
            }
            plugin.guis().openHub(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sender instanceof Player who && !who.hasPermission("mvgam_admin")
                && !limiter.tryAcquire(who.getUniqueId(), System.currentTimeMillis())) {
            plugin.messages().send(sender, "command.too-fast");
            return true;
        }
        switch (sub) {
            case "menu" -> {
                return withPlayer(sender, player -> plugin.guis().openHub(player));
            }
            case "games" -> {
                return listGames(sender, args);
            }
            case "play" -> {
                return playGame(sender, args);
            }
            case "balance" -> {
                return balance(sender, args);
            }
            case "help", "guia" -> {
                return help(sender, args);
            }
            case "wallet" -> {
                return wallet(sender, args);
            }
            case "stats" -> {
                return stats(sender, args);
            }
            case "top" -> {
                return top(sender, args);
            }
            case "verify" -> {
                return verify(sender, args);
            }
            case "world" -> {
                return world(sender, args);
            }
            case "language" -> {
                return language(sender, args);
            }
            case "info" -> {
                return info(sender);
            }
            case "reload" -> {
                if (!sender.hasPermission("mvgam_admin")) {
                    plugin.messages().send(sender, "general.no-permission");
                    return true;
                }
                plugin.reloadAll();
                plugin.messages().send(sender, "command.reloaded", "games", plugin.games().enabled().size());
                return true;
            }
            case "give", "take", "set" -> {
                return adminMoney(sender, sub, args);
            }
            case "action" -> {
                return action(sender, args);
            }
            case "cancel" -> {
                return cancel(sender, args);
            }
            default -> {
                plugin.messages().send(sender, "command.unknown", "subcommand", sub);
                return true;
            }
        }
    }

    // ----------------------------------------------------------------- actions

    private boolean withPlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }
        if (!player.hasPermission("mvgam_play")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        action.accept(player);
        return true;
    }

    /**
     * Entry point for the chat buttons. Each group game decides which actions it
     * accepts, so a new game never has to touch the command.
     */
    private boolean action(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(player, "command.usage", "usage", "/mvgam action <action>");
            return true;
        }
        var game = plugin.games().activeGameOf(player.getUniqueId());
        if (game.isEmpty()) {
            plugin.messages().send(player, "group.not-in-table");
            return true;
        }
        String[] rest = java.util.Arrays.copyOfRange(args, 2, args.length);
        try {
            game.get().handleAction(player, args[1].toLowerCase(Locale.ROOT), rest);
        } catch (RuntimeException error) {
            plugin.getLogger().severe("Error while handling the action '" + args[1] + "': " + error);
            error.printStackTrace();
        }
        return true;
    }

    private boolean listGames(CommandSender sender, String[] args) {
        GameCategory filter = null;
        if (args.length > 1) {
            filter = args[1].toLowerCase(Locale.ROOT).startsWith("g") ? GameCategory.GROUP
                    : (args[1].toLowerCase(Locale.ROOT).startsWith("s") ? GameCategory.SOLO : null);
        }
        plugin.messages().sendRaw(sender, "&8&m        &r &6MultiverseGambling catalogue &8&m        ");
        for (GameCategory category : GameCategory.values()) {
            if (filter != null && category != filter) {
                continue;
            }
            plugin.messages().sendRaw(sender, "&6" + plugin.messages().forSenderOr(sender,
                    "gui.category." + category.key() + ".name", category.label())
                    + " &7- " + plugin.messages().forSenderOr(sender,
                    "gui.category." + category.key() + ".description", category.description()));
            for (Game game : plugin.games().byCategory(category)) {
                String state = game.enabled() ? "&a●" : "&c●";
                plugin.messages().sendRaw(sender, "  " + state + " &f"
                        + plugin.messages().gameName(sender, game.id(), game.name())
                        + " &7(" + game.id() + ") &8- &7" + plugin.economy().shortFormat(game.minBet())
                        + " to " + plugin.economy().shortFormat(game.maxBet()));
            }
        }
        plugin.messages().send(sender, "command.play-help");
        return true;
    }

    private boolean playGame(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }
        if (!player.hasPermission("mvgam_play")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.guis().openHub(player);
            return true;
        }
        // A trailing "house" asks for the duel against the dealer (the chat buttons use it).
        int end = args.length;
        boolean house = end > 2 && "house".equalsIgnoreCase(args[end - 1]);
        if (house) {
            end--;
        }
        // Only the exact id counts: the remaining arguments are joined with hyphens so
        // "/mvgam play lucky wheel" ends up asking for the id "lucky-wheel".
        String id = String.join("-", java.util.Arrays.copyOfRange(args, 1, end));
        Game game = plugin.games().byId(id).orElse(null);
        if (game == null) {
            plugin.messages().send(player, "command.unknown-game", "game", id);
            return true;
        }
        if (house) {
            game.openAgainstHouse(player);
        } else {
            game.open(player);
        }
        return true;
    }

    private boolean balance(CommandSender sender, String[] args) {
        if (args.length > 1) {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target.getName() == null && !target.hasPlayedBefore()) {
                plugin.messages().send(sender, "command.unknown-player", "player", args[1]);
                return true;
            }
            plugin.messages().send(sender, "command.balance-other",
                    "player", String.valueOf(target.getName()),
                    "balance", plugin.economy().format(plugin.economy().balance(target.getUniqueId())));
            return true;
        }
        return withPlayer(sender, player -> plugin.messages().send(player, "command.balance",
                "balance", plugin.economy().format(plugin.economy().balance(player.getUniqueId()))
                        + walletSuffix(player.getUniqueId())));
    }

    /**
     * {@code /mvgam help}: opens the manual as a book; {@code help <topic|game>} prints that page.
     */
    private boolean help(CommandSender sender, String[] args) {
        var guide = plugin.guide();
        if (args.length > 1) {
            List<String> lines = guide.lines(sender, args[1]);
            if (lines == null) {
                plugin.messages().send(sender, "help.unknown", "topic", args[1]);
                guide.index(sender).forEach(line -> plugin.messages().sendRaw(sender, line));
            } else {
                lines.forEach(line -> plugin.messages().sendRaw(sender, line));
            }
            return true;
        }
        if (sender instanceof Player player) {
            player.openBook(guide.book(player));
        } else {
            guide.index(sender).forEach(line -> plugin.messages().sendRaw(sender, line));
        }
        return true;
    }

    private String walletSuffix(java.util.UUID id) {
        String wallet = plugin.economy().wallet(id);
        return wallet == null ? "" : " &7(" + wallet + ")";
    }

    /**
     * {@code /mvgam wallet}: lists the wallets with their balance; {@code /mvgam wallet <name>} switches.
     */
    private boolean wallet(CommandSender sender, String[] args) {
        return withPlayer(sender, player -> {
            var id = player.getUniqueId();
            if (args.length > 1) {
                String refusal = plugin.economy().chooseWallet(player, args[1]);
                if (refusal != null) {
                    plugin.messages().send(player, refusal, "wallet", args[1]);
                    return;
                }
            }
            String current = plugin.economy().wallet(id);
            if (current == null) {
                plugin.messages().send(player, "economy.wallet-none");
                return;
            }
            plugin.messages().send(player, "economy.wallet-header", "wallet", current);
            for (String w : plugin.economy().wallets(id)) {
                plugin.messages().sendRaw(player, (w.equals(current) ? "&a▶ &f" : "&7  ") + w + " &8- &6"
                        + plugin.economy().format(plugin.economy().provider().balance(id, w)));
            }
            plugin.messages().send(player, "economy.wallet-hint");
        });
    }

    private boolean stats(CommandSender sender, String[] args) {
        Player target = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
        Player player = target;
        if (player == null && sender instanceof Player self) {
            player = self;
        }
        if (player == null) {
            plugin.messages().send(sender, "command.unknown-player",
                    "player", args.length > 1 ? args[1] : "-");
            return true;
        }
        PlayerStats stats = plugin.stats().of(player.getUniqueId());
        plugin.messages().sendRaw(sender, "&8&m        &r &6Stats for " + player.getName() + " &8&m        ");
        plugin.messages().sendRaw(sender, "&7Games: &f" + stats.games
                + " &8| &7Wins: &f" + stats.wins + " &8(" + Text.percent(stats.winRate()) + ")");
        plugin.messages().sendRaw(sender, "&7Wagered: &f" + plugin.economy().format(stats.wagered)
                + " &8| &7Returned: &f" + plugin.economy().format(stats.returned));
        plugin.messages().sendRaw(sender, (stats.profit() >= 0 ? "&7Profit: &a" : "&7Profit: &c")
                + plugin.economy().format(stats.profit())
                + " &8| &7Actual RTP: &f" + Text.percent(stats.rtp()));
        String favourite = plugin.messages().gameName(sender, stats.favouriteGame(), stats.favouriteGame());
        plugin.messages().sendRaw(sender, "&7Biggest win: &f" + plugin.economy().format(stats.biggestWin)
                + " &8| &7Favourite game: &f" + favourite);
        return true;
    }

    private boolean top(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mvgam_top")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        String mode = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "profit";
        List<StatsStore.TopEntry> entries = switch (mode) {
            case "wagered" -> plugin.stats().topByWagered(10);
            case "prize" -> plugin.stats().topByBiggestWin(10);
            default -> plugin.stats().topByProfit(10);
        };
        plugin.messages().sendRaw(sender, "&8&m        &r &6Ranking &7(" + mode + ") &8&m        ");
        if (entries.isEmpty()) {
            plugin.messages().sendRaw(sender, "&7No data yet.");
            return true;
        }
        int position = 1;
        for (StatsStore.TopEntry entry : entries) {
            double value = switch (mode) {
                case "wagered" -> entry.stats().wagered;
                case "prize" -> entry.stats().biggestWin;
                default -> entry.stats().profit();
            };
            String colour = position == 1 ? "&6" : (position <= 3 ? "&e" : "&7");
            plugin.messages().sendRaw(sender, colour + position + ". &f" + entry.name()
                    + " &8- &f" + plugin.economy().format(value));
            position++;
        }
        return true;
    }

    private boolean verify(CommandSender sender, String[] args) {
        if (args.length > 1 && sender instanceof Player player) {
            plugin.fair().setClientSeed(player, args[1]);
            plugin.messages().send(player, "fairness.seed-changed",
                    "seed", plugin.fair().clientSeed(player.getUniqueId()));
            return true;
        }
        UUID id = sender instanceof Player player ? player.getUniqueId() : new UUID(0, 0);
        plugin.messages().sendRaw(sender, "&8&m        &r &6Provably fair &8&m        ");
        for (String line : plugin.fair().auditLines(id)) {
            plugin.messages().sendRaw(sender, line);
        }
        plugin.messages().send(sender, "fairness.explanation");
        return true;
    }

    /**
     * Teleports to the casino world; {@code /mvgam world build} rebuilds the structures.
     */
    private boolean world(CommandSender sender, String[] args) {
        // The report answers administrators and the console, so it is served before the
        // player-only checks of the teleport.
        if (args.length > 1 && args[1].equalsIgnoreCase("info")) {
            if (!sender.hasPermission("mvgam_admin")) {
                plugin.messages().send(sender, "general.no-permission");
                return true;
            }
            return worldInfo(sender);
        }
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }
        if (!player.hasPermission("mvgam_play")) {
            plugin.messages().send(player, "general.no-permission");
            return true;
        }
        if (!plugin.config().worldEnabled()) {
            plugin.messages().send(player, "world.disabled");
            return true;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("build")) {
            if (!sender.hasPermission("mvgam_admin")) {
                plugin.messages().send(sender, "general.no-permission");
                return true;
            }
            String worldName = plugin.config().worldName();
            java.util.UUID requester = player.getUniqueId();
            CasinoWorldManager.BuildStart start = plugin.world().rebuild(ok -> {
                Player online = plugin.getServer().getPlayer(requester);
                if (online != null) {
                    plugin.messages().send(online, ok ? "world.built" : "world.build-failed", "world", worldName);
                }
            });
            switch (start) {
                case STARTED -> plugin.messages().send(player, "world.building", "world", worldName);
                case BUSY -> plugin.messages().send(player, "world.build-busy",
                        "progress", Text.percent(plugin.world().buildProgress()));
                case FAILED -> plugin.messages().send(player, "world.build-failed", "world", worldName);
            }
            return true;
        }
        if (plugin.world().teleport(player)) {
            plugin.messages().send(player, "world.teleporting", "world", plugin.config().worldName());
        } else {
            plugin.messages().send(player, "world.unavailable");
        }
        return true;
    }

    /**
     * Self translation: {@code /mvgam language es} switches everything this player
     * reads. The list of available languages comes from the {@code lang} folder, so an
     * admin can drop an extra file and it shows up without touching the code.
     */
    private boolean language(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "command.players-only");
            return true;
        }
        Messages messages = plugin.messages();
        if (args.length > 1 && matches(args[1], "reset", "auto", "default")) {
            plugin.languages().clear(player.getUniqueId());
            messages.send(player, "language.reset", "language", messages.localeOf(player));
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "language.current", "language", messages.localeOf(player),
                    "languages", messages.localeList());
            messages.send(player, "language.usage", "usage", "/mvgam language <" + String.join("|", messages.locales()) + ">");
            return true;
        }
        String query = args[1].toLowerCase(Locale.ROOT);
        Language known = Language.match(query);
        String code = known == null ? query : known.code();
        if (!messages.supports(code)) {
            messages.send(player, "language.unknown", "language", args[1],
                    "languages", messages.localeList());
            return true;
        }
        plugin.languages().set(player.getUniqueId(), code);
        // Sent after the switch, so the confirmation already arrives in the new language.
        messages.send(player, "language.changed", "language", code, "native", localeName(code));
        return true;
    }

    private String localeName(String code) {
        Language language = Language.match(code);
        return language == null ? code : language.nativeName();
    }

    private boolean matches(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code /mvgam world info}: what exists, what is built and what has to be fixed.
     */
    private boolean worldInfo(CommandSender sender) {
        WorldReport report = plugin.world().report();
        plugin.messages().sendRaw(sender, plugin.messages().forSender(sender, "world.info.header"));
        plugin.messages().sendRaw(sender, plugin.messages().forSender(sender, "world.info.line-world",
                "world", report.name(), "source", infoSource(sender, report.source())));
        if (report.source() != WorldReport.Source.ABSENT) {
            plugin.messages().sendRaw(sender, plugin.messages().forSender(sender, "world.info.line-terrain",
                    "terrain", plugin.messages().forSender(sender, report.flat()
                            ? "world.info.terrain-flat" : "world.info.terrain-existing")));
        }
        if (report.measured()) {
            plugin.messages().sendRaw(sender, plugin.messages().forSender(sender, "world.info.line-size",
                    "size", report.size(), "ground", report.groundY()));
            plugin.messages().sendRaw(sender, plugin.messages().forSender(sender, "world.info.line-arenas",
                    "built", report.arenasBuilt(), "planned", report.arenasPlanned(),
                    "games", report.games()));
            plugin.messages().sendRaw(sender, plugin.messages().forSender(sender, "world.info.line-boards",
                    "boards", report.boards()));
        }
        for (String hintKey : report.hintKeys()) {
            plugin.messages().sendRaw(sender, plugin.messages().forSender(sender, "world.info.hint",
                    "hint", plugin.messages().forSender(sender, hintKey,
                            "games", String.join(", ", report.boardsTooBig()))));
        }
        return true;
    }

    private String infoSource(CommandSender sender, WorldReport.Source source) {
        return plugin.messages().forSender(sender, switch (source) {
            case CREATED_HERE -> "world.info.source-created";
            case LOADED -> "world.info.source-loaded";
            case ABSENT -> "world.info.source-absent";
        });
    }

    private boolean info(CommandSender sender) {
        plugin.messages().sendRaw(sender, "&8&m        &r &6MultiverseGambling &8&m        ");
        plugin.messages().sendRaw(sender, "&7Economy: &f" + plugin.economy().provider().name());
        plugin.messages().sendRaw(sender, "&7Games: &f" + plugin.games().all().size()
                + " &7(" + plugin.games().enabled().size() + " active)");
        plugin.messages().sendRaw(sender, "&7House edge: &f" + Text.percent(plugin.config().houseEdge()));
        plugin.messages().sendRaw(sender, "&7Provably fair: &f"
                + (plugin.fair().enabled() ? "enabled" : "disabled"));
        plugin.messages().sendRaw(sender, "&7Current secret: &f" + plugin.fair().serverSeedHash());
        plugin.messages().sendRaw(sender, "&7World: &f"
                + (plugin.config().worldEnabled() ? plugin.config().worldName() : "disabled"));
        return true;
    }

    private boolean adminMoney(CommandSender sender, String mode, String[] args) {
        if (!sender.hasPermission("mvgam_admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "command.usage", "usage", "/mvgam " + mode + " <player> <amount>");
            return true;
        }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getName() == null && !target.hasPlayedBefore()) {
            plugin.messages().send(sender, "command.unknown-player", "player", args[1]);
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2].replace(",", "."));
        } catch (NumberFormatException error) {
            plugin.messages().send(sender, "command.invalid-amount", "value", args[2]);
            return true;
        }
        if (!Double.isFinite(amount) || Math.abs(amount) > com.chagui68.multiversegambling.economy.Amounts.CEILING) {
            plugin.messages().send(sender, "command.invalid-amount", "value", args[2]);
            return true;
        }
        UUID id = target.getUniqueId();
        switch (mode) {
            case "give" -> plugin.economy().deposit(id, Math.abs(amount));
            case "take" -> plugin.economy().provider().withdraw(id, Math.abs(amount));
            default -> plugin.economy().set(id, Math.max(0, amount));
        }
        plugin.messages().send(sender, "command.balance-updated",
                "player", String.valueOf(target.getName()),
                "balance", plugin.economy().format(plugin.economy().balance(id)));
        return true;
    }

    private boolean cancel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mvgam_admin")) {
            plugin.messages().send(sender, "general.no-permission");
            return true;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "command.usage", "usage", "/mvgam cancel <player>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "command.unknown-player", "player", args[1]);
            return true;
        }
        plugin.sessions().cancel(target.getUniqueId());
        plugin.messages().send(sender, "command.session-cancelled", "player", target.getName());
        return true;
    }

    // ------------------------------------------------------------ tab complete

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBS, args[0]);
        }
        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "play" -> filter(plugin.games().all().stream().map(Game::id).toList(), args[1]);
                case "games" -> filter(List.of("solo", "group"), args[1]);
                case "top" -> filter(List.of("profit", "wagered", "prize"), args[1]);
                case "verify" -> filter(List.of("<seed>"), args[1]);
                case "world" -> filter(List.of("build", "info"), args[1]);
                case "language" -> filter(plugin.messages().locales(), args[1]);
                case "help", "guia" -> {
                    List<String> names = new java.util.ArrayList<>(com.chagui68.multiversegambling.help.Guide.TOPICS);
                    plugin.games().enabled().forEach(g -> names.add(g.id()));
                    yield filter(names, args[1]);
                }
                case "wallet" -> sender instanceof Player p
                        ? filter(plugin.economy().wallets(p.getUniqueId()), args[1]) : List.of();
                case "give", "take", "set", "cancel", "balance", "stats" -> filter(onlineNames(), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3 && "play".equalsIgnoreCase(args[0])) {
            Game game = plugin.games().byId(args[1]).orElse(null);
            boolean house = game != null && game.category() == GameCategory.GROUP && game.playableAgainstHouse();
            return house ? filter(List.of("house"), args[2]) : List.of();
        }
        if (args.length == 3 && List.of("give", "take", "set").contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(List.of("100", "1000", "10000"), args[2]);
        }
        return List.of();
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String needle = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) {
                out.add(option);
            }
        }
        return out;
    }
}
