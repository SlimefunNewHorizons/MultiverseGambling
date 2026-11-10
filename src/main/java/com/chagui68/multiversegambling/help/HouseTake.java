package com.chagui68.multiversegambling.help;

import java.util.Set;

/**
 * How each game makes the house win, so help texts and holograms say it plainly.
 */
public final class HouseTake {

    public enum Kind {
        /** Payouts are cut by the house edge. */
        EDGE,
        /** The winner of a pot gets it minus the house commission. */
        POT_CUT,
        /** The house keeps a rake of every pot. */
        RAKE
    }

    private static final Set<String> POT_GAMES =
            Set.of("jackpot", "dice-poker", "duel", "hot-bomb", "russian-roulette", "bomb-board");

    private HouseTake() {
    }

    public static Kind kindOf(String gameId) {
        if ("poker".equals(gameId)) {
            return Kind.RAKE;
        }
        return POT_GAMES.contains(gameId) ? Kind.POT_CUT : Kind.EDGE;
    }

    /** The share (0..1) the house keeps for that kind of game. */
    public static double share(Kind kind, double houseEdge, double groupCut, double rake) {
        return switch (kind) {
            case EDGE -> houseEdge;
            case POT_CUT -> groupCut;
            case RAKE -> rake;
        };
    }

    /** Language key of the sentence that explains the take. */
    public static String key(Kind kind) {
        return switch (kind) {
            case EDGE -> "help.take-edge";
            case POT_CUT -> "help.take-pot";
            case RAKE -> "help.take-rake";
        };
    }
}
