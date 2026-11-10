package com.chagui68.multiversegambling.economy;

import com.chagui68.multiversegambling.MultiverseGamblingPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

/**
 * Bridge to the <b>sBank</b> plugin, so a server that keeps its players' money in bank
 * accounts can gamble with that balance instead of the bare Vault wallet.
 *
 * <p>sBank is used through reflection on purpose: the casino must load on servers that do
 * not have it, and the bank keeps its own persistence rules (it rounds to the cent and
 * writes an audit record per movement). Every call is defensive, and the three things the
 * bridge really needs are resolved once, at hook time: nobody pays a reflection cost in
 * the middle of a roll.</p>
 *
 * <p>Writes are persisted immediately with {@code SBank.persistBank}, exactly like sBank
 * does, so a crash cannot resurrect an old in-memory balance. Each movement is also
 * written to the bank audit log, which is what lets an administrator reconcile the casino
 * against the bank afterwards.</p>
 */
public final class SbankEconomy implements EconomyProvider {

    private static final String ACTION_STAKE = "CASINO_BET";
    private static final String ACTION_PAYOUT = "CASINO_PAYOUT";
    private static final String ACTION_ADMIN = "CASINO_ADMIN";

    private final MultiverseGamblingPlugin plugin;
    private final Object bankPlugin;
    private final Method getBanks;
    private final Method getBalance;
    private final Method setBalance;
    private final Method getUsername;
    private final Method persistBank;
    private final Method getDatabase;
    private final Method getBank;
    private final Method bankOf;   // sBank 1.5+: modality-aware lookup for online players (nullable)
    private final boolean bankByModality; // getBank(String,String) instead of getBank(String)
    private final Method getAuditLogger;
    private final Method record;
    private final String version;

    private SbankEconomy(MultiverseGamblingPlugin plugin, Plugin sbank, Members members) {
        this.plugin = plugin;
        this.bankPlugin = sbank;
        this.getBanks = members.getBanks();
        this.getBalance = members.getBalance();
        this.setBalance = members.setBalance();
        this.getUsername = members.getUsername();
        this.persistBank = members.persistBank();
        this.getDatabase = members.getDatabase();
        this.getBank = members.getBank();
        this.bankOf = members.bankOf();
        this.bankByModality = getBank != null && getBank.getParameterCount() == 2;
        this.getAuditLogger = members.getAuditLogger();
        this.record = members.record();
        this.version = String.valueOf(sbank.getDescription().getVersion());
    }

    /**
     * @return the bridge, or {@code null} when sBank is missing, disabled or too different
     * from the version this bridge knows
     */
    public static EconomyProvider tryHook(MultiverseGamblingPlugin plugin) {
        Plugin sbank = Bukkit.getPluginManager().getPlugin("sBank");
        if (sbank == null || !sbank.isEnabled()) {
            return null;
        }
        try {
            Members members = Members.resolve(sbank);
            EconomyProvider provider = new SbankEconomy(plugin, sbank, members);
            plugin.getLogger().info("sBank " + sbank.getDescription().getVersion()
                    + " hooked: the casino plays with bank accounts.");
            return provider;
        } catch (Throwable error) {
            plugin.getLogger().warning("sBank is installed but this bridge could not use it ("
                    + error.getClass().getSimpleName() + ": " + error.getMessage()
                    + "); the casino will try the next economy engine.");
            return null;
        }
    }

    @Override
    public String name() {
        return "sBank " + version + " (bank accounts)";
    }

    @Override
    public double balance(UUID playerId) {
        Object bank = account(playerId);
        if (bank == null) {
            return 0.0;
        }
        try {
            return ((Number) getBalance.invoke(bank)).doubleValue();
        } catch (Throwable error) {
            warn("read a bank balance", error);
            return 0.0;
        }
    }

    @Override
    public boolean set(UUID playerId, double amount) {
        return move(playerId, amount, true, ACTION_ADMIN, "set-balance");
    }

    @Override
    public boolean withdraw(UUID playerId, double amount) {
        if (amount <= 0) {
            return true;
        }
        Object bank = account(playerId);
        if (bank == null || balance(playerId) + 1e-9 < amount) {
            return false;
        }
        return move(playerId, -amount, false, ACTION_STAKE, "casino-stake");
    }

    @Override
    public boolean deposit(UUID playerId, double amount) {
        if (amount <= 0) {
            return true;
        }
        return move(playerId, amount, false, ACTION_PAYOUT, "casino-payout");
    }

    @Override
    public boolean has(UUID playerId, double amount) {
        return balance(playerId) + 1e-9 >= amount;
    }

    /**
     * The bank creates an account for every player who joins, and it is the one that
     * decides the starting money, so the casino never opens one.
     */
    @Override
    public boolean createAccountIfMissing(UUID playerId, double startingBalance) {
        return false;
    }

    /**
     * Writes the new balance, persists it and records the movement in the bank audit log.
     */
    private boolean move(UUID playerId, double delta, boolean absolute, String action, String note) {
        Object bank = account(playerId);
        if (bank == null) {
            return false;
        }
        try {
            double before = ((Number) getBalance.invoke(bank)).doubleValue();
            double after = normalise(absolute ? delta : before + delta);
            if (after < 0) {
                return false;
            }
            if (after == before) {
                return true;
            }
            setBalance.invoke(bank, after);
            Object persisted = persistBank.invoke(null, bank);
            if (persisted instanceof Boolean ok && !ok) {
                plugin.getLogger().warning("sBank could not persist a casino movement for " + name(playerId));
                return false;
            }
            audit(action, bank, playerId, Math.abs(after - before), before, after, note);
            return true;
        } catch (Throwable error) {
            warn("write a bank movement", error);
            return false;
        }
    }

    /**
     * Records the movement in the bank audit file, exactly like a bank command would.
     */
    private void audit(String action, Object bank, UUID playerId, double amount,
                       double before, double after, String note) {
        if (getAuditLogger == null || record == null) {
            return;
        }
        try {
            Object logger = getAuditLogger.invoke(null);
            if (logger == null) {
                return;
            }
            String username = String.valueOf(getUsername.invoke(bank));
            record.invoke(logger, action, username, playerId.toString(), amount,
                    -1.0, -1.0, before, after, note);
        } catch (Throwable ignored) {
            // The audit log is a bonus: a signature change in sBank must never stop a payout.
        }
    }

    /**
     * The bank of a player: from memory when they are online, from the database when not.
     */
    private Object account(UUID playerId) {
        String username = name(playerId);
        if (username == null) {
            return null;
        }
        try {
            // sBank 1.5+ keys banks by "name@modality": ask it for the online player's own bank.
            if (bankOf != null) {
                org.bukkit.entity.Player online = Bukkit.getPlayer(playerId);
                if (online != null) {
                    Object bank = bankOf.invoke(null, online);
                    if (bank != null) {
                        return bank;
                    }
                }
            }
            Map<?, ?> banks = (Map<?, ?>) getBanks.invoke(null);
            Object bank = banks == null ? null : banks.get(username);
            if (bank == null && banks != null) {
                bank = banks.get(username + "@survival");
            }
            if (bank != null) {
                return bank;
            }
            if (getDatabase == null || getBank == null) {
                return null;
            }
            Object database = getDatabase.invoke(null);
            if (database == null) {
                return null;
            }
            return bankByModality ? getBank.invoke(database, username, "survival") : getBank.invoke(database, username);
        } catch (Throwable error) {
            warn("find a bank account", error);
            return null;
        }
    }

    private String name(UUID playerId) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        return offline.getName();
    }

    /**
     * Same precision the bank stores: two decimals, half up.
     */
    private static double normalise(double amount) {
        if (!Double.isFinite(amount)) {
            return 0.0;
        }
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private void warn(String what, Throwable error) {
        Throwable cause = error instanceof InvocationTargetException && error.getCause() != null
                ? error.getCause() : error;
        plugin.getLogger().warning("Could not " + what + " through sBank: "
                + cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }

    /**
     * Members resolved once, so a partial sBank can be rejected before anything runs.
     */
    private record Members(Method getBanks, Method getBalance, Method setBalance, Method getUsername,
                           Method persistBank, Method getDatabase, Method getBank, Method bankOf,
                           Method getAuditLogger, Method record) {

        static Members resolve(Plugin sbank) throws ReflectiveOperationException {
            // The plugin instance was loaded by sBank's own loader, which is the only one
            // that can see its classes.
            ClassLoader loader = sbank.getClass().getClassLoader();
            Class<?> pluginClass = Class.forName("com.spearforge.sBank.SBank", true, loader);
            Class<?> bankClass = Class.forName("com.spearforge.sBank.model.Bank", true, loader);
            Class<?> databaseClass = Class.forName("com.spearforge.sBank.database.DatabaseConnection", true, loader);

            Method getBanks = pluginClass.getMethod("getBanks");
            Method getBalance = bankClass.getMethod("getBalance");
            Method setBalance = bankClass.getMethod("setBalance", double.class);
            Method getUsername = bankClass.getMethod("getUsername");
            Method persistBank = pluginClass.getMethod("persistBank", bankClass);
            Method getDatabase = pluginClass.getMethod("getDb");
            Method getBank;
            try {
                getBank = databaseClass.getMethod("getBank", String.class);
            } catch (NoSuchMethodException old) {
                getBank = databaseClass.getMethod("getBank", String.class, String.class); // sBank 1.5+
            }
            Method bankOf = null;
            try {
                bankOf = pluginClass.getMethod("bankOf", org.bukkit.entity.Player.class);
            } catch (NoSuchMethodException ignored) {
                // pre-modality sBank
            }

            Method getAuditLogger = null;
            Method record = null;
            try {
                Class<?> auditClass = Class.forName("com.spearforge.sBank.audit.BankAuditLogger", true, loader);
                getAuditLogger = pluginClass.getMethod("getAuditLogger");
                record = auditClass.getMethod("record", String.class, String.class, String.class,
                        double.class, double.class, double.class, double.class, double.class, String.class);
            } catch (ReflectiveOperationException ignored) {
                // Older sBank without the audit module: the casino still works.
            }
            return new Members(getBanks, getBalance, setBalance, getUsername, persistBank,
                    getDatabase, getBank, bankOf, getAuditLogger, record);
        }
    }
}
