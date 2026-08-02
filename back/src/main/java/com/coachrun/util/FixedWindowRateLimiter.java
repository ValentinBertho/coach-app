package com.coachrun.util;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limiteur de débit à fenêtre fixe, en mémoire (par clé = IP + catégorie).
 * Suffisant pour protéger l'auth/les invitations en mono-instance ; pur et testable.
 *
 * <p>La table des fenêtres est <b>purgée</b> ({@link #purgeExpired()}) : chaque clé inédite y crée
 * une entrée, et une clé dérivée de l'IP cliente est par nature sous le contrôle de l'appelant.
 * Sans purge, la table croissait sans borne — un simple balayage d'adresses la faisait enfler
 * jusqu'à épuiser la mémoire.</p>
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

    /**
     * Retire les fenêtres closes. Une fenêtre expirée ne porte plus aucune information : la
     * prochaine requête sur la même clé en ouvre une neuve de toute façon.
     *
     * @return nombre d'entrées retirées.
     */
    public int purgeExpired(long nowMs) {
        int before = windows.size();
        windows.values().removeIf(w -> nowMs - w.start >= windowMs);
        return before - windows.size();
    }

    public int purgeExpired() {
        return purgeExpired(System.currentTimeMillis());
    }

    /** Nombre de clés suivies (supervision et tests de purge). */
    public int trackedKeys() {
        return windows.size();
    }

    private static final class Window {
        final long start;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long start) {
            this.start = start;
        }
    }
}
