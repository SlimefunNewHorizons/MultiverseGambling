package com.chagui68.multiversegambling.economy;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;
import com.chagui68.multiversegambling.util.Text;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;

/**
 * The single place where casino money comes in and goes out.
 */
public final class EconomyManager {

    private final MultiverseGamblingPlugin plugin;
    private EconomyProvider provider;
    private double startingBalance = 1000;
    private Ledger ledger;

    public EconomyManager(MultiverseGamblingPlugin plugin) {
        this.plugin = plugin;
    }

    public MultiverseGamblingPlugin plugin() {
        return plugin;
    }

    public Ledger ledger() {
        if (ledger == null) {
            var cfg = plugin.config();
            ledger = new Ledger(() -> provider,
                    new Journal(new java.io.File(plugin.getDataFolder(), "bet-journal")),
                    new StakeLimits(cfg.maxStake(), cfg.maxLossPerHour(), 3_600_000L, cfg.maxBetsPer10s(), 10_000L,
                            cfg.winAlertPerHour()),
                    plugin.getLogger(), System::currentTimeMillis);
        }
        return ledger;
    }

    /**
     * Returns to their owners the bets a crash, a kick or a failed payout left in the journal.
     *
     * @param only one player, or {@code null} for everybody
     */
    public void recover(java.util.UUID only) {
        if (provider == null) {
            return;
        }
        int n = ledger().recover(only, (player, encoded, count) ->
                plugin.items().give(player, org.bukkit.inventory.ItemStack.deserializeBytes(
                        java.util.Base64.getDecoder().decode(encoded)), count));
        if (n > 0) {
            plugin.getLogger().warning("Recovered " + n + " unsettled bet(s) from the journal.");
        }
    }

    /**
     * Hooks the economy engine the configuration asks for: the sBank bank accounts, the
     * Vault economy or the internal wallet, in the order {@code economy.auto-order}
     * decides. Called again on /mvgam reload, so a server that installs another economy
     * plugin can switch without restarting.
     */
    public void setup() {
        startingBalance = plugin.config().startingBalance();
        String mode = plugin.config().economyProvider();
        if (mode != null && !mode.isBlank() && !EconomyProviders.isKnown(mode)) {
            plugin.getLogger().warning("Unknown economy provider '" + mode + "'; using the internal wallet.");
        }
        for (String engine : EconomyProviders.candidates(mode, plugin.config().economyAutoOrder())) {
            EconomyProvider hooked = switch (engine) {
                case EconomyProviders.SBANK -> SbankEconomy.tryHook(plugin);
                case EconomyProviders.VAULT -> VaultEconomy.tryHook();
                case EconomyProviders.INTERNAL -> new InternalEconomy(plugin);
                default -> null;
            };
            if (hooked != null) {
                provider = hooked;
                plugin.getLogger().info("Economy engine in use: " + provider.name());
                return;
            }
        }
        provider = new InternalEconomy(plugin);
        plugin.getLogger().info("Economy engine in use: " + provider.name());
    }

    public EconomyProvider provider() {
        return provider;
    }

    public boolean usingVault() {
        return provider instanceof VaultEconomy;
    }

    public double startingBalance() {
        return startingBalance;
    }

    public double balance(UUID playerId) {
        return provider.balance(playerId);
    }

    public boolean has(UUID playerId, double amount) {
        return provider.has(playerId, amount);
    }

    public boolean deposit(UUID playerId, double amount) {
        return provider.deposit(playerId, amount);
    }

    public boolean set(UUID playerId, double amount) {
        return provider.set(playerId, amount);
    }

    public String wallet(UUID playerId) {
        return provider.wallet(playerId);
    }

    public List<String> wallets(UUID playerId) {
        return provider.wallets(playerId);
    }

    /**
     * Changes the wallet the player gambles with.
     *
     * @return {@code null} when done, else the message key that explains the refusal
     */
    public String chooseWallet(Player player, String wallet) {
        if (plugin.games().activeGameOf(player.getUniqueId()).isPresent() || ledger().busy(player.getUniqueId())) {
            return "economy.wallet-busy";
        }
        return provider.setWallet(player.getUniqueId(), wallet) ? null : "economy.wallet-unknown";
    }

    /**
     * Wallet after the current one, for the menu button.
     */
    public String nextWallet(UUID playerId) {
        List<String> all = provider.wallets(playerId);
        if (all.isEmpty()) {
            return null;
        }
        int at = all.indexOf(provider.wallet(playerId));
        return all.get((at + 1) % all.size());
    }

    /**
     * Grants the welcome account the first time a player comes in.
     */
    public void ensureAccount(Player player) {
        if (provider.createAccountIfMissing(player.getUniqueId(), startingBalance)) {
            plugin.messages().send(player, "economy.account-created", "balance", format(startingBalance));
        }
    }

    /**
     * Takes the bet out of the wallet and wraps it in a {@link Wager}.
     *
     * @return {@code null} when the player cannot afford it.
     */
    public Wager stake(Player player, double amount) {
        return ledger().stake(player.getUniqueId(), amount);
    }

    /**
     * Why the last {@link #stake} was refused as a message key ({@code null} when it was only
     * a lack of money).
     */
    public String takeRefusal() {
        return ledger().takeRefusal();
    }

    /**
     * Creates an already charged bet (for internal payouts or tests).
     */
    public Wager wager(UUID playerId, double amount) {
        return new Wager(ledger(), playerId, amount);
    }

    public String format(double amount) {
        String format = plugin.config().currencyFormat();
        String formatted = Text.fill(format,
                "amount", Text.number(amount),
                "currency", amount == 1 ? plugin.config().currencyName() : plugin.config().currencyName());
        return formatted;
    }

    /**
     * Compact format for menu lore.
     */
    public String shortFormat(double amount) {
        if (Math.abs(amount) >= 1_000_000_000) {
            return String.format("%,.1fMM", amount / 1_000_000_000);
        }
        if (Math.abs(amount) >= 1_000_000) {
            return String.format("%,.1fM", amount / 1_000_000);
        }
        if (Math.abs(amount) >= 10_000) {
            return String.format("%,.1fK", amount / 1_000);
        }
        return Text.number(amount);
    }

    public void save() {
        if (provider instanceof InternalEconomy internal) {
            internal.save();
        }
    }

    public void saveAsync() {
        if (provider instanceof InternalEconomy internal) {
            internal.saveAsync();
        }
    }
}
