package com.coachrun.security;

import com.coachrun.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entretient {@code users.last_seen_at} — la seule donnée qui permette de répondre à
 * « combien d'utilisateurs actifs ? » et « à quand remonte sa dernière visite ? ».
 *
 * <p><b>Pourquoi ce n'est pas une écriture par requête.</b> Le filtre JWT s'exécute sur toute
 * requête authentifiée : y poser un {@code UPDATE} coûterait une écriture par appel d'API, sur
 * une table lue à chaque authentification. La mesure paierait cent fois le prix de sa précision.
 * On borne donc à une écriture par compte et par quart d'heure ; la granularité obtenue reste
 * très au-delà de ce que les écrans affichent (« vu il y a 3 h »).</p>
 *
 * <p><b>La mémoire n'est pas la source de vérité.</b> La carte des dernières écritures vit dans
 * l'instance : un redéploiement la vide, et au pire on réécrit une fois de plus par compte. Rien
 * ne se perd, c'est la base qui fait foi.</p>
 *
 * <p><b>Transaction ouverte à la main</b> plutôt que par {@code @Transactional} : l'écriture est
 * décidée <i>après</i> le test d'étranglement, dans la même méthode. Une annotation aurait été
 * contournée par l'auto-invocation, et la porter sur {@code touch()} ouvrirait une transaction
 * sur chaque requête — précisément ce que cette classe existe pour éviter.</p>
 *
 * <p><b>Aucune conséquence en cas d'échec.</b> Une écriture ratée est journalisée en {@code debug}
 * et oubliée : une mesure d'usage ne doit jamais transformer une requête valide en erreur.</p>
 */
@Slf4j
@Component
public class UserActivityTracker {

    /** Intervalle minimal entre deux écritures pour un même compte. */
    static final Duration THROTTLE = Duration.ofMinutes(15);

    /**
     * Borne du cache. Au-delà, on repart de zéro plutôt que de laisser une carte croître sans
     * limite : le seul effet est une écriture supplémentaire par compte encore actif.
     */
    private static final int MAX_TRACKED = 20_000;

    private final UserRepository userRepository;
    private final TransactionTemplate transactions;
    private final Map<UUID, Instant> lastWrite = new ConcurrentHashMap<>();

    public UserActivityTracker(UserRepository userRepository,
                               PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.transactions = new TransactionTemplate(transactionManager);
        // L'appel vient d'un filtre, hors de toute transaction métier ; s'il en existait une,
        // cette écriture ne doit ni la rejoindre ni la faire échouer.
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** À appeler sur chaque requête authentifiée. Ne lève jamais. */
    public void touch(UUID userId) {
        if (userId == null) {
            return;
        }
        Instant now = Instant.now();
        Instant previous = lastWrite.get(userId);
        if (previous != null && previous.isAfter(now.minus(THROTTLE))) {
            return;
        }
        if (lastWrite.size() > MAX_TRACKED) {
            lastWrite.clear();
        }
        // Posé avant l'écriture : si celle-ci échoue, on ne retentera qu'au prochain quart
        // d'heure — préférable à un martèlement de la base quand elle est déjà en difficulté.
        lastWrite.put(userId, now);
        try {
            transactions.executeWithoutResult(status -> userRepository.touchLastSeen(userId, now));
        } catch (RuntimeException ex) {
            log.debug("last_seen_at non mis à jour pour {} : {}", userId, ex.getMessage());
        }
    }
}
