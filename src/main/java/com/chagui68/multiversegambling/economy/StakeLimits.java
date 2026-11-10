package com.chagui68.multiversegambling.economy;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player brakes: a cap per bet, a cap on the net loss inside a rolling window and a
 * limit on how fast bets can be started. All state is in memory and time is passed in, so
 * it is deterministic to test.
 */
public final class StakeLimits {

    public static final String OK = null;
    public static final String TOO_BIG = "economy.limit-stake";
    public static final String LOSS = "economy.limit-loss";
    public static final String RATE = "economy.limit-rate";

    private record Move(long at, double delta) {
    }

    private final double maxStake;
    private final double maxLoss;
    private final long window;
    private final int maxBets;
    private final long rateWindow;
    private final double winAlert;
    private final Map<UUID, ArrayDeque<Move>> moves = new HashMap<>();
    private final Map<UUID, ArrayDeque<Long>> starts = new HashMap<>();

    /** A cap of 0 (or less) disables that check. */
    public StakeLimits(double maxStake, double maxLossPerWindow, long windowMillis, int maxBets, long rateWindowMillis,
                       double winAlert) {
        this.winAlert = winAlert;
        this.maxStake = maxStake;
        this.maxLoss = maxLossPerWindow;
        this.window = windowMillis;
        this.maxBets = maxBets;
        this.rateWindow = rateWindowMillis;
    }

    /** @return {@link #OK} (null) or the message key explaining the refusal */
    public synchronized String check(UUID player, double amount, long now) {
        if (maxStake > 0 && amount > maxStake) {
            return TOO_BIG;
        }
        if (maxBets > 0) {
            ArrayDeque<Long> recent = starts.computeIfAbsent(player, id -> new ArrayDeque<>());
            while (!recent.isEmpty() && now - recent.peekFirst() > rateWindow) {
                recent.pollFirst();
            }
            if (recent.size() >= maxBets) {
                return RATE;
            }
        }
        if (maxLoss > 0 && netLoss(player, now) + amount > maxLoss) {
            return LOSS;
        }
        return OK;
    }

    public synchronized void staked(UUID player, double amount, long now) {
        starts.computeIfAbsent(player, id -> new ArrayDeque<>()).addLast(now);
        moves.computeIfAbsent(player, id -> new ArrayDeque<>()).addLast(new Move(now, -amount));
    }

    public synchronized void paid(UUID player, double amount, long now) {
        if (amount > 0) {
            moves.computeIfAbsent(player, id -> new ArrayDeque<>()).addLast(new Move(now, amount));
        }
    }

    /** True when the player is ahead by more than the alert threshold in the window. */
    public synchronized boolean winAlert(UUID player, long now) {
        return winAlert > 0 && -netLoss(player, now) > winAlert;
    }

    /** Net loss inside the window (negative when the player is ahead). */
    public synchronized double netLoss(UUID player, long now) {
        ArrayDeque<Move> list = moves.get(player);
        if (list == null) {
            return 0;
        }
        while (!list.isEmpty() && now - list.peekFirst().at() > window) {
            list.pollFirst();
        }
        double sum = 0;
        for (Move m : list) {
            sum += m.delta();
        }
        return -sum;
    }
}
