package com.chagui68.multiversegambling.economy;

import java.util.List;
import java.util.UUID;

/**
 * Money source of the casino. It exists so the plugin behaves the same with the
 * economy of the server (Vault) or with its own wallet when there is none.
 */
public interface EconomyProvider {

    /**
     * Human readable name of the active provider, for /mvgam info.
     */
    String name();

    double balance(UUID playerId);

    /**
     * @return true when the change was applied.
     */
    boolean set(UUID playerId, double amount);

    /**
     * @return true when there was enough balance and it was withdrawn.
     */
    boolean withdraw(UUID playerId, double amount);

    boolean deposit(UUID playerId, double amount);

    default boolean has(UUID playerId, double amount) {
        return balance(playerId) >= amount;
    }

    /**
     * Grants a welcome balance when the player never had an account.
     */
    default boolean createAccountIfMissing(UUID playerId, double startingBalance) {
        return false;
    }

    /**
     * Wallet (sBank modality) the player gambles with, or {@code null} when the engine has
     * a single wallet.
     */
    default String wallet(UUID playerId) {
        return null;
    }

    /**
     * Wallets the player can choose from: only those with an existing bank.
     */
    default List<String> wallets(UUID playerId) {
        return List.of();
    }

    /**
     * @return true when the wallet exists and is now the one the player gambles with
     */
    default boolean setWallet(UUID playerId, String wallet) {
        return false;
    }

    /**
     * Balance of one specific wallet, 0 when it does not exist.
     */
    default double balance(UUID playerId, String wallet) {
        return balance(playerId);
    }
}
