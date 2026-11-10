package com.chagui68.multiversegambling.economy;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * The plugin, never the player, decides what an item is worth and whether it can be staked.
 * Pure rules, so they are tested without a server.
 */
public final class ItemRules {

    private ItemRules() {
    }

    /**
     * @param unitCents    price the plugin gives one unit (hundredths), or <= 0 when unpriced
     * @param maxItemValue ceiling per item in currency units (0 = off)
     * @param namespaces   namespaces of the data the stack carries (its persistent data keys)
     * @param allowed      namespaces whose data is understood and safe to copy
     * @return a language key explaining the refusal, or {@code null} when it can be staked
     */
    public static String refusal(long unitCents, double maxItemValue, Collection<String> namespaces,
                                 Set<String> allowed) {
        if (unitCents <= 0) {
            return "items.unpriced";
        }
        if (maxItemValue > 0 && unitCents / 100.0 > maxItemValue) {
            return "items.too-valuable";
        }
        for (String ns : namespaces) {
            if (!allowed.contains(ns.toLowerCase(Locale.ROOT))) {
                return "items.blocked";
            }
        }
        return null;
    }

    /** True when the whole stake is worth more than the ceiling (0 = off). */
    public static boolean betTooBig(long count, long unitCents, double maxBetValue) {
        return maxBetValue > 0 && count * (unitCents / 100.0) > maxBetValue;
    }

    /** Share of the payout that survives the entry haircut and the house cut. */
    public static double retention(double depositHaircut, double houseCut) {
        double h = Math.max(0, Math.min(0.9, depositHaircut));
        double c = Math.max(0, Math.min(0.5, houseCut));
        return (1.0 - h) * (1.0 - c);
    }

    /** Chips credited for an item worth {@code fullCents}, after the haircut. */
    public static long credit(long fullCents, double depositHaircut) {
        return (long) Math.floor(fullCents * (1.0 - Math.max(0, Math.min(0.9, depositHaircut))));
    }
}
