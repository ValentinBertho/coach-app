package com.coachrun.security;

import com.coachrun.entity.LoginAttempt;
import com.coachrun.repository.LoginAttemptRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Compteur d'échecs de connexion <strong>par compte</strong>, avec délai progressif.
 *
 * <p>Le rate limiting du {@link RateLimitFilter} est par IP : il n'arrête pas un attaquant qui
 * répartit ses essais sur plusieurs adresses pour forcer un compte précis. Ce compteur suit la
 * cible plutôt que la source, et le verrou s'allonge à chaque échec supplémentaire.</p>
 *
 * <p><b>En base, plus en mémoire.</b> Un redémarrage remettait tous les compteurs à zéro, et un
 * déploiement par jour suffisait à offrir à un attaquant une fenêtre neuve d'essais gratuits. La
 * ligne est verrouillée en écriture le temps de l'incrément : deux échecs simultanés comptent
 * bien pour deux.</p>
 *
 * <p>Chaque écriture se fait dans <b>sa propre transaction</b>. La connexion qui échoue lève une
 * exception juste après : dans la transaction de l'appelant, l'échec serait annulé avec elle et
 * ne compterait jamais.</p>
 *
 * <p>La clé est l'empreinte SHA-256 de l'adresse normalisée : le compteur s'applique aussi aux
 * adresses inexistantes (sinon un verrou révélerait quels comptes existent) sans conserver ces
 * adresses en clair.</p>
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

    private final LoginAttemptRepository repository;
    private final TransactionTemplate tx;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    public LoginAttemptTracker(LoginAttemptRepository repository, PlatformTransactionManager txManager) {
        this.repository = repository;
        this.tx = new TransactionTemplate(txManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Délai restant avant la prochaine tentative autorisée, ou {@code null} si le compte est libre. */
    public Duration lockRemaining(String email) {
        if (!enabled || email == null) {
            return null;
        }
        Instant lockedUntil = tx.execute(s -> repository.findById(key(email))
                .filter(a -> !isExpired(a))
                .map(LoginAttempt::getLockedUntil)
                .orElse(null));
        if (lockedUntil == null) {
            return null;
        }
        Duration remaining = Duration.between(Instant.now(), lockedUntil);
        return remaining.isNegative() || remaining.isZero() ? null : remaining;
    }

    /** Échec d'authentification : incrémente le compteur et rallonge le verrou. */
    public void recordFailure(String email) {
        if (!enabled || email == null) {
            return;
        }
        String key = key(email);
        LoginAttempt updated;
        try {
            updated = tx.execute(s -> increment(key));
        } catch (DataIntegrityViolationException firstInsertRace) {
            // Deux premiers échecs simultanés ont tenté de créer la ligne : le second la relit,
            // désormais présente, et l'incrémente sous verrou.
            updated = tx.execute(s -> increment(key));
        }
        if (updated != null && updated.getLockedUntil() != null && updated.getFailureCount() > FREE_ATTEMPTS) {
            // Jamais l'adresse en clair dans les journaux : c'est une donnée personnelle.
            log.warn("Connexion verrouillée {} s après {} échecs consécutifs",
                    Duration.between(Instant.now(), updated.getLockedUntil()).toSeconds(),
                    updated.getFailureCount());
        }
    }

    private LoginAttempt increment(String key) {
        Instant now = Instant.now();
        LoginAttempt a = repository.lockByKey(key).orElse(null);
        if (a == null) {
            a = new LoginAttempt();
            a.setAccountKey(key);
        } else if (isExpired(a)) {
            a.setFailureCount(0);
            a.setLockedUntil(null);
        }
        a.setFailureCount(a.getFailureCount() + 1);
        a.setLastFailureAt(now);
        if (a.getFailureCount() > FREE_ATTEMPTS) {
            long factor = 1L << Math.min(a.getFailureCount() - FREE_ATTEMPTS - 1, 10);
            Duration delay = BASE_DELAY.multipliedBy(factor);
            if (delay.compareTo(MAX_DELAY) > 0) {
                delay = MAX_DELAY;
            }
            a.setLockedUntil(now.plus(delay));
        }
        return repository.saveAndFlush(a);
    }

    /** Connexion réussie : le compteur repart de zéro. */
    public void recordSuccess(String email) {
        if (email == null) {
            return;
        }
        String key = key(email);
        tx.executeWithoutResult(s -> repository.findById(key).ifPresent(repository::delete));
    }

    /**
     * Purge les compteurs sans échec récent. La table est indexée sur l'adresse soumise, donc sur
     * une valeur choisie par l'appelant : sans purge, une salve de connexions sur des adresses
     * inventées la ferait croître sans borne.
     */
    @Scheduled(fixedDelay = 900_000L)
    void purgeExpired() {
        tx.executeWithoutResult(s -> repository.deleteStaleBefore(Instant.now().minus(ATTEMPT_TTL)));
    }

    /** Nombre de comptes suivis (supervision et tests de purge). */
    public long trackedAccounts() {
        return repository.count();
    }

    private static boolean isExpired(LoginAttempt a) {
        return Instant.now().isAfter(a.getLastFailureAt().plus(ATTEMPT_TTL));
    }

    static String key(String email) {
        String normalised = email.trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalised.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
