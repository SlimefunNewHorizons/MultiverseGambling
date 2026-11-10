package com.chagui68.multiversegambling.gui;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.config.Messages;
import com.chagui68.multiversegambling.game.Game;
import com.chagui68.multiversegambling.game.GameCategory;
import com.chagui68.multiversegambling.stats.PlayerStats;
import com.chagui68.multiversegambling.util.Items;
import com.chagui68.multiversegambling.util.Text;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Main menu of the casino.
 *
 * <p>Laid out like a lobby: the player's own card in the middle of the top row between
 * the two tabs (solo and group), the games of the selected tab framed in the colour of
 * that tab, and a bar of shortcuts at the bottom (travel to the casino world, language,
 * provably fair audit, statistics). A left click on a game plays it; a right click
 * travels to its pavilion. Every label follows the player's language.</p>
 */
public final class HubGui extends Gui {

    private static final int[] GAME_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SOLO_TAB = 2;
    private static final int GROUP_TAB = 6;

    private GameCategory tab;

    public HubGui(MultiverseGamblingPlugin plugin, Player player) {
        this(plugin, player, GameCategory.SOLO);
    }

    public HubGui(MultiverseGamblingPlugin plugin, Player player, GameCategory tab) {
        super(plugin, player, 6, plugin.messages().forSender(player, "gui.hub.title"));
        this.tab = tab;
    }

    private Messages messages() {
        return plugin.messages();
    }

    @Override
    protected void render() {
        clearActions();
        Player viewer = player();
        boolean solo = tab == GameCategory.SOLO;
        ItemStack dark = Items.of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        ItemStack frame = Items.of(solo ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE)
                .name(" ").build();
        ItemStack gold = Items.of(Material.YELLOW_STAINED_GLASS_PANE).name(" ").build();
        fill(dark);
        for (int slot : new int[]{9, 17, 18, 26, 27, 35}) {
            set(slot, frame);
        }
        for (int slot = 36; slot <= 44; slot++) {
            set(slot, frame);
        }
        for (int slot : new int[]{0, 8, 45, 53}) {
            set(slot, gold);
        }

        renderHeader(viewer);
        renderTabs(viewer);
        renderGames(viewer);
        renderShortcuts(viewer);
    }

    private void renderHeader(Player viewer) {
        double balance = plugin.economy().balance(viewer.getUniqueId());
        PlayerStats stats = plugin.stats().peek(viewer.getUniqueId());
        List<String> lore = new ArrayList<>(messages().loreFor(viewer, "gui.hub.balance-lore",
                "provider", plugin.economy().provider().name(),
                "min", plugin.economy().format(plugin.config().minBet()),
                "wallet", java.util.Objects.toString(plugin.economy().wallet(viewer.getUniqueId()), "-")));
        if (stats != null && stats.games > 0) {
            lore.add("");
            lore.add(messages().forSender(viewer, "gui.hub.summary",
                    "games", stats.games,
                    "profit", (stats.profit() >= 0 ? "&a+" : "&c") + plugin.economy().format(stats.profit())));
        }
        ItemStack head = Items.of(Material.PLAYER_HEAD)
                .name(messages().forSender(viewer, "gui.hub.balance",
                        "balance", plugin.economy().format(balance)))
                .lore(lore)
                .build();
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(viewer);
            head.setItemMeta(meta);
        }
        set(4, head, e -> {
            close();
            plugin.guis().openStats(viewer);
        });
    }

    private void renderTabs(Player viewer) {
        for (GameCategory category : GameCategory.values()) {
            boolean selected = category == tab;
            int amount = plugin.games().byCategory(category).size();
            Material icon = category == GameCategory.SOLO ? Material.DIAMOND : Material.EMERALD;
            set(category == GameCategory.SOLO ? SOLO_TAB : GROUP_TAB, Items.of(icon)
                    .name((selected ? "&a&l▶ " : "&7") + categoryName(viewer, category)
                            + (selected ? " &a&l◀" : ""))
                    .lore(messages().loreFor(viewer, "gui.hub.tab-lore",
                            "description", categoryDescription(viewer, category),
                            "amount", amount,
                            "state", messages().forSender(viewer,
                                    selected ? "gui.hub.tab-selected" : "gui.hub.tab-switch")))
                    .glow(selected)
                    .build(), e -> {
                if (tab != category) {
                    tab = category;
                    refresh();
                }
            });
        }
    }

    private void renderGames(Player viewer) {
        List<Game> games = new ArrayList<>(plugin.games().byCategory(tab));
        boolean world = plugin.world() != null && plugin.world().ready();
        int index = 0;
        for (Game game : games) {
            if (index >= GAME_SLOTS.length) {
                break;
            }
            boolean enabled = game.enabled();
            List<String> lore = new ArrayList<>();
            for (String line : game.displayDescription(viewer)) {
                lore.add(line);
            }
            lore.add(messages().forSender(viewer, "gui.hub.divider"));
            lore.addAll(game.statusLore(viewer));
            lore.add(messages().forSender(viewer, "gui.hub.bet",
                    "min", plugin.economy().format(game.minBet()),
                    "max", plugin.economy().format(game.maxBet())));
            if (game.category() == GameCategory.GROUP) {
                lore.add(messages().forSender(viewer, "gui.hub.players",
                        "min", game.minPlayers(), "max", game.maxPlayers()));
                if (game.playableAgainstHouse()) {
                    lore.add(messages().forSender(viewer, "gui.hub.against-house"));
                }
            }
            lore.add(messages().forSender(viewer, "gui.hub.divider"));
            lore.add(messages().forSender(viewer, enabled ? "gui.hub.play" : "gui.hub.disabled"));
            if (enabled && world) {
                lore.add(messages().forSender(viewer, "gui.hub.visit"));
            }

            ItemStack icon = Items.of(enabled ? game.icon() : Material.BARRIER)
                    .name((enabled ? "&6&l" : "&8") + Text.strip(game.displayName(viewer)))
                    .lore(lore)
                    .glow(enabled && game.activePlayers() > 0)
                    .amount(Math.max(1, Math.min(64, game.activePlayers())))
                    .build();
            set(GAME_SLOTS[index], icon, e -> {
                close();
                if (e.isRightClick() && world) {
                    if (!plugin.world().teleportToArena(viewer, game.id())) {
                        game.open(viewer);
                    }
                    return;
                }
                game.open(viewer);
            });
            index++;
        }
    }

    private void renderShortcuts(Player viewer) {
        boolean world = plugin.config().worldEnabled();
        set(46, Items.of(world ? Material.ENDER_PEARL : Material.GRAY_DYE)
                .name(messages().forSender(viewer, "gui.hub.world"))
                .lore(messages().loreFor(viewer, world ? "gui.hub.world-lore" : "gui.hub.world-off"))
                .build(), e -> {
            close();
            viewer.performCommand("mvgam world");
        });
        set(47, Items.of(Material.WRITABLE_BOOK)
                .name(messages().forSender(viewer, "gui.hub.language"))
                .lore(messages().loreFor(viewer, "gui.hub.language-lore",
                        "language", messages().localeOf(viewer)))
                .build(), e -> {
            close();
            viewer.performCommand("mvgam language");
        });
        String wallet = plugin.economy().wallet(viewer.getUniqueId());
        if (wallet != null) {
            set(48, Items.of(Material.GOLD_INGOT)
                    .name(messages().forSender(viewer, "gui.hub.wallet", "wallet", wallet))
                    .lore(messages().loreFor(viewer, "gui.hub.wallet-lore", "wallet", wallet,
                            "balance", plugin.economy().format(plugin.economy().balance(viewer.getUniqueId()))))
                    .build(), e -> {
                String next = plugin.economy().nextWallet(viewer.getUniqueId());
                String refusal = next == null ? "economy.wallet-none" : plugin.economy().chooseWallet(viewer, next);
                if (refusal != null) {
                    messages().send(viewer, refusal, "wallet", String.valueOf(next));
                }
                render();
            });
        }
        set(50, Items.of(Material.KNOWLEDGE_BOOK)
                .name(messages().forSender(viewer, "gui.hub.help"))
                .lore(messages().loreFor(viewer, "gui.hub.help-lore"))
                .build(), e -> {
            close();
            viewer.performCommand("mvgam help");
        });
        set(49, Items.of(Material.BARRIER)
                .name(messages().forSender(viewer, "gui.hub.close"))
                .build(), e -> close());
        set(51, Items.of(Material.ENDER_EYE)
                .name(messages().forSender(viewer, "gui.hub.fairness"))
                .lore(messages().loreFor(viewer, "gui.hub.fairness-lore",
                        "hash", plugin.fair().serverSeedHash().substring(0, 24)))
                .build(), e -> {
            close();
            plugin.guis().sendVerify(viewer);
        });
        set(52, Items.of(Material.BOOK)
                .name(messages().forSender(viewer, "gui.hub.stats"))
                .lore(messages().loreFor(viewer, "gui.hub.stats-lore"))
                .build(), e -> {
            close();
            plugin.guis().openStats(viewer);
        });
    }

    private String categoryName(Player viewer, GameCategory category) {
        return messages().forSenderOr(viewer, "gui.category." + category.key() + ".name",
                category.label());
    }

    private String categoryDescription(Player viewer, GameCategory category) {
        return messages().forSenderOr(viewer, "gui.category." + category.key() + ".description",
                category.description());
    }

    @Override
    public String sessionId() {
        return "hub";
    }

    @Override
    protected boolean holdsRound() {
        return false;
    }
}
