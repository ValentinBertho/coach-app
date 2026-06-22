package com.coachrun.util;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limiteur de débit à fenêtre fixe, en mémoire (par clé = IP + catégorie).
 * Suffisant pour protéger l'auth/les invitations en mono-instance ; pur et testable.
 */
public class FixedWindowRateLimiter {

    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int maxRequests, Duration window) {
        this.maxRequests = maxRequests;
        this.windowMs = window.toMillis();
    }

    /** @return true si la requête est autorisée, false si la limite est atteinte. */
    public boolean tryAcquire(String key, long nowMs) {
        Window w = windows.compute(key, (k, existing) -> {
            if (existing == null || nowMs - existing.start >= windowMs) {
                return new Window(nowMs);
            }
            return existing;
        });
        return w.count.incrementAndGet() <= maxRequests;
    }

    public boolean tryAcquire(String key) {
        return tryAcquire(key, System.currentTimeMillis());
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long start) {
            this.start = start;
        }
    }
}
