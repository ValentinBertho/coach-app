package com.coachrun.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trace d'une erreur que le code choisit de ne pas propager.
 *
 * <p>Une trentaine de {@code catch} renvoyaient {@code null} ou une liste vide sans rien écrire :
 * une prescription enregistrée devenue illisible s'affichait comme une séance vide, un calcul de
 * charge en échec retirait une alerte du tableau de bord, et personne ne le savait. Le repli
 * reste — une donnée secondaire illisible ne doit pas faire tomber l'écran — mais il laisse
 * désormais une ligne au journal et un événement Sentry.</p>
 *
 * <p>Les mêmes écrans se rechargent souvent : la même erreur (même endroit, même contexte, même
 * cause) n'est tracée qu'une fois par fenêtre de dix minutes, pour qu'une donnée corrompue lue à
 * chaque ouverture du tableau de bord ne noie pas le journal.</p>
 */
public final class SwallowedError {

    static final Duration WINDOW = Duration.ofMinutes(10);
    private static final int MAX_KEYS = 2_000;
    private static final ConcurrentHashMap<String, Instant> SEEN = new ConcurrentHashMap<>();

    private SwallowedError() {
    }

    /**
     * Trace une erreur avalée.
     *
     * @param where   classe qui avale l'erreur (son journal reçoit la ligne)
     * @param what    ce qui a échoué et ce qui est renvoyé à la place, avec les identifiants
     *                utiles pour retrouver la donnée (jamais son contenu)
     * @param error   l'erreur d'origine
     * @return {@code true} si l'erreur a été tracée, {@code false} si elle l'avait déjà été
     *         dans la fenêtre courante
     */
    public static boolean report(Class<?> where, String what, Throwable error) {
        Instant now = Instant.now();
        String key = where.getName() + '|' + what + '|' + error.getClass().getName();
        if (SEEN.size() > MAX_KEYS) {
            SEEN.entrySet().removeIf(e -> e.getValue().plus(WINDOW).isBefore(now));
            if (SEEN.size() > MAX_KEYS) {
                SEEN.clear();
            }
        }
        boolean[] fresh = {false};
        SEEN.compute(key, (k, last) -> {
            if (last == null || last.plus(WINDOW).isBefore(now)) {
                fresh[0] = true;
                return now;
            }
            return last;
        });
        if (!fresh[0]) {
            return false;
        }
        Logger log = LoggerFactory.getLogger(where);
        log.error("Erreur non propagée — {}", what, error);
        io.sentry.Sentry.withScope(scope -> {
            scope.setTag("swallowed", "true");
            scope.setExtra("what", what);
            io.sentry.Sentry.captureException(error);
        });
        return true;
    }

    /** Pour les tests : oublie ce qui a déjà été tracé. */
    static void reset() {
        SEEN.clear();
    }
}
