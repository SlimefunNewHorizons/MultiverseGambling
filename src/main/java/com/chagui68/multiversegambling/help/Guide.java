package com.chagui68.multiversegambling.help;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.economy.ItemRules;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * The in-game manual: every rule of the casino explained from lang/ and filled with the
 * live values of config.yml, so it can never disagree with what the casino does.
 */
public final class Guide {

    public static final List<String> TOPICS = List.of("general", "wallets", "items", "fair", "limits");

    private final MultiverseGamblingPlugin plugin;

    public Guide(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    /** Sentence about the house's take of a game, with the real percentage. */
    public String takeLine(CommandSender who, String gameId) {
        var cfg = plugin.config();
        HouseTake.Kind kind = HouseTake.kindOf(gameId);
        double rake = "poker".equals(gameId) ? Math.max(cfg.pokerRake(), 0) : 0;
        double share = HouseTake.share(kind, cfg.houseEdge(), cfg.groupHouseCut(), rake);
        return plugin.messages().forSender(who, HouseTake.key(kind), "percent", Text.percent(share),
                "house", Text.percent(cfg.pokerHouseRake()));
    }

    /** Lines of one topic or one game; {@code null} when the name is neither. */
    public List<String> lines(CommandSender who, String name) {
        String key = name == null ? "general" : name.toLowerCase(Locale.ROOT);
        if (TOPICS.contains(key)) {
            return plugin.messages().loreFor(who, "help." + key, values());
        }
        Game game = plugin.games().byId(key).orElse(null);
        if (game == null) {
            return null;
        }
        List<String> out = new ArrayList<>();
        out.add(plugin.messages().forSender(who, "help.game-title", "game", game.displayName(who)));
        out.addAll(game.displayDescription(who));
        out.add(plugin.messages().forSender(who, "help.game-bets",
                "min", plugin.economy().format(game.minBet()), "max", plugin.economy().format(game.maxBet())));
        out.add(takeLine(who, game.id()));
        boolean items = game instanceof com.chagui68.multiversegambling.game.AbstractSoloGame solo
                && solo.supportsItemBets() && plugin.config().itemBetsEnabled();
        out.add(plugin.messages().forSender(who, items ? "help.game-items-yes" : "help.game-items-no"));
        out.add(plugin.messages().forSender(who, "help.game-play", "id", game.id()));
        return out;
    }

    /** The index shown by {@code /mvgam help}: topics and every game id. */
    public List<String> index(CommandSender who) {
        List<String> out = new ArrayList<>(plugin.messages().loreFor(who, "help.index", values()));
        out.add(plugin.messages().forSender(who, "help.index-topics", "topics", String.join(", ", TOPICS)));
        List<String> ids = plugin.games().enabled().stream().map(Game::id).toList();
        out.add(plugin.messages().forSender(who, "help.index-games", "games", String.join(", ", ids)));
        return out;
    }

    /** One written book with the index, every topic and each game, one page per block. */
    public Book book(Player who) {
        List<Component> pages = new ArrayList<>();
        pages.add(Text.c(String.join("\n", index(who))));
        for (String topic : TOPICS) {
            pages.add(Text.c(String.join("\n", lines(who, topic))));
        }
        for (Game game : plugin.games().enabled()) {
            pages.add(Text.c(String.join("\n", lines(who, game.id()))));
        }
        return Book.book(Text.c(plugin.messages().forSender(who, "help.book-title")),
                Text.c("MultiverseGambling"), pages);
    }

    private Object[] values() {
        var cfg = plugin.config();
        return new Object[]{
                "haircut", Text.percent(cfg.itemBetsDepositHaircut()),
                "cut", Text.percent(cfg.itemBetsHouseCut()),
                "retention", Text.percent(ItemRules.retention(cfg.itemBetsDepositHaircut(), cfg.itemBetsHouseCut())),
                "maxitem", plugin.economy().format(cfg.itemBetsMaxItemValue()),
                "maxbetitems", plugin.economy().format(cfg.itemBetsMaxBetValue()),
                "min", plugin.economy().format(cfg.minBet()),
                "max", plugin.economy().format(cfg.maxBet()),
                "maxstake", plugin.economy().format(cfg.maxStake()),
                "maxloss", plugin.economy().format(cfg.maxLossPerHour()),
                "rate", cfg.maxBetsPer10s(),
                "edge", Text.percent(cfg.houseEdge()),
                "commission", Text.percent(cfg.groupHouseCut()),
                "rake", Text.percent(cfg.pokerRake()),
                "houserake", Text.percent(cfg.pokerHouseRake())
        };
    }
}
