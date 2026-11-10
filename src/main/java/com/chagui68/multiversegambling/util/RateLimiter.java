package com.chagui68.multiversegambling.util;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** At most {@code max} hits per key inside a sliding window. */
public final class RateLimiter {

    private final int max;
    private final long window;
    private final Map<Object, ArrayDeque<Long>> hits = new HashMap<>();

    public RateLimiter(int max, long windowMillis) {
        this.max = max;
        this.window = windowMillis;
    }

    public synchronized boolean tryAcquire(Object key, long now) {
        ArrayDeque<Long> list = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!list.isEmpty() && now - list.peekFirst() >= window) {
            list.pollFirst();
        }
        if (list.size() >= max) {
            return false;
        }
        list.addLast(now);
        return true;
    }
}
