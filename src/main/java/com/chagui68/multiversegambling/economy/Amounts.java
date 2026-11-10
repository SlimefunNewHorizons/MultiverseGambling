package com.chagui68.multiversegambling.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Sanity of every amount that touches a wallet. */
public final class Amounts {

    /** Nobody stakes or wins more than this in one operation. */
    public static final double CEILING = 1_000_000_000_000.0;

    private Amounts() {
    }

    /**
     * @return the amount rounded to cents, or {@code NaN} when it is not a usable positive amount
     */
    public static double clean(double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || amount > CEILING) {
            return Double.NaN;
        }
        double rounded = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue();
        return rounded <= 0 ? Double.NaN : rounded;
    }

    /** Same, for a payout, where zero is a valid lost bet. */
    public static double cleanPayout(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            return Double.NaN;
        }
        return Math.min(BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).doubleValue(), CEILING);
    }
}
