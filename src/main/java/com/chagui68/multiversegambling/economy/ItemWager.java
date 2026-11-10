package com.chagui68.multiversegambling.economy;

import java.util.UUID;
import java.util.function.DoubleSupplier;

import org.bukkit.inventory.ItemStack;

/**
 * A bet staked with items instead of money.
 *
 * <p>Its "amount" is the number of items staked, so every game multiplies it exactly as
 * it would multiply money; what changes is the payout, which is handed back as copies of
 * the same item (custom data, names and enchantments included). A fractional payout is
 * settled with {@link ItemMath#roundByChance(double, double)}, using a provably fair roll
 * of the player.</p>
 */
public final class ItemWager extends Wager {

    private final ItemStack item;
    private final ItemBank bank;
    private final DoubleSupplier rolls;
    private int paid;

    ItemWager(Ledger ledger, UUID playerId, ItemStack item, int count, ItemBank bank,
              DoubleSupplier rolls, String id) {
        super(ledger, playerId, count, id, null);
        this.item = item.asOne();
        this.bank = bank;
        this.rolls = rolls;
    }

    /**
     * One unit of the item staked.
     */
    public ItemStack item() {
        return item.clone();
    }

    /**
     * How many items were staked.
     */
    public int count() {
        return (int) Math.round(amount());
    }

    /**
     * Items actually handed back when the bet was settled.
     */
    public int paid() {
        return paid;
    }

    @Override
    protected boolean deliver(double payout) {
        int amount = ItemMath.roundByChance(payout, rolls.getAsDouble());
        paid = amount;
        if (amount > 0) {
            bank.give(playerId(), item, amount);
        }
        return true;
    }
}
