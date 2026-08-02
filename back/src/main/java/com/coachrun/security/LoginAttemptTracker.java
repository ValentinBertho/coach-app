package com.coachrun.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compteur d'échecs de connexion <strong>par compte</strong>, avec délai progressif.
 *
 * <p>Le rate limiting du {@link RateLimitFilter} est par IP : il n'arrête pas un attaquant qui
 * répartit ses essais sur plusieurs adresses pour forcer un compte précis. Ce compteur suit la
 * cible plutôt que la source, et le verrou s'allonge à chaque échec supplémentaire.</p>
 *
 * <p>En mémoire et mono-instance, comme les autres garde-fous du MVP. Un redémarrage remet les
 * compteurs à zéro : c'est un ralentisseur, pas un coffre-fort — mais un ralentisseur suffit à
 * rendre le brute-force en ligne inexploitable.</p>
 */
@Slf4j
@Component
public class LoginAttemptTracker {

    /** Échecs tolérés avant le premier verrou. */
    private static final int FREE_ATTEMPTS = 5;
    /** Délai de base, doublé à chaque échec au-delà du seuil (30 s, 1 min, 2 min…). */
    private static final Duration BASE_DELAY = Duration.ofSeconds(30);
    /** Plafond : au-delà, l'utilisateur légitime serait puni plus que l'attaquant. */
    private static final Duration MAX_DELAY = Duration.ofMinutes(15);
    /** Un compte sans échec récent repart de zéro. */
    private static final Duration ATTEMPT_TTL = Duration.ofHours(1);

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    /** Délai restant avant la prochaine tentative autorisée, ou {@code null} si le compte est libre. */
    public Duration lockRemaining(String email) {
        if (!enabled || email == null) {
            return null;
        }
        Attempts a = attempts.get(key(email));
        if (a == null || a.lockedUntil == null) {
            return null;
        }
        Duration remaining = Duration.between(Instant.now(), a.lockedUntil);
        return remaining.isNegative() || remaining.isZero() ? null : remaining;
    }

    /**
     * Échec d'authentification : incrémente le compteur et rallonge le verrou.
     *
     * <p>Tout se fait <b>dans</b> le {@code compute()} : l'incrément et l'horodatage étaient
     * auparavant appliqués après coup, sur des champs non volatiles. Deux essais simultanés sur
     * le même compte lisaient donc la même valeur et n'en écrivaient qu'une — un attaquant qui
     * parallélise ses tentatives voyait son compteur avancer moins vite que ses essais, et le
     * verrou progressif ne se déclenchait jamais.</p>
     */
    public void recordFailure(String email) {
        if (!enabled || email == null) {
            return;
        }
        Attempts updated = attempts.compute(key(email), (k, existing) -> {
            Attempts a = (existing == null || existing.isExpired()) ? new Attempts() : existing;
            a.count++;
            a.lastFailure = Instant.now();
            if (a.count > FREE_ATTEMPTS) {
                long factor = 1L << Math.min(a.count - FREE_ATTEMPTS - 1, 10);
                Duration delay = BASE_DELAY.multipliedBy(factor);
                if (delay.compareTo(MAX_DELAY) > 0) {
                    delay = MAX_DELAY;
                }
                a.lockedUntil = Instant.now().plus(delay);
            }
            return a;
        });
        if (updated.lockedUntil != null && updated.count > FREE_ATTEMPTS) {
            // Jamais l'adresse en clair dans les journaux : c'est une donnée personnelle.
            log.warn("Connexion verrouillée {} s après {} échecs consécutifs",
                    Duration.between(Instant.now(), updated.lockedUntil).toSeconds(), updated.count);
        }
    }

    /**
     * Purge les compteurs sans échec récent. La table est indexée sur l'adresse e-mail soumise,
     * donc sur une valeur choisie par l'appelant : sans purge, une salve de connexions sur des
     * adresses inventées la faisait croître sans borne.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 900_000L)
    void purgeExpired() {
        attempts.values().removeIf(Attempts::isExpired);
    }

    /** Nombre de comptes suivis (supervision et tests de purge). */
    public int trackedAccounts() {
        return attempts.size();
    }

    /** Connexion réussie : le compteur repart de zéro. */
    public void recordSuccess(String email) {
        if (email != null) {
            attempts.remove(key(email));
        }
    }

    private String key(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * État d'un compte. Les champs ne sont mutés que sous le {@code compute()} de la map, qui
     * verrouille l'entrée : c'est lui qui garantit l'atomicité, pas les champs eux-mêmes. Ils
     * sont tout de même {@code volatile} pour que {@code lockRemaining()}, qui lit hors du
     * compute, voie bien la dernière valeur écrite.
     */
    private static final class Attempts {
        volatile int count;
        volatile Instant lastFailure = Instant.now();
        volatile Instant lockedUntil;

        boolean isExpired() {
            return Instant.now().isAfter(lastFailure.plus(ATTEMPT_TTL));
        }
    }
}
