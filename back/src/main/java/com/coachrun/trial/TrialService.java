package com.coachrun.trial;

import com.coachrun.dto.response.AuthResponse;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.exception.ApiException;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.service.AuthService;
import com.coachrun.service.DemoSeedService;
import com.coachrun.util.FixedWindowRateLimiter;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Les espaces d'essai jetables : « Essayer sans inscription » sur l'accueil.
 *
 * <h2>Le principe</h2>
 *
 * <p>Chaque visiteur reçoit SON club, rempli comme celui de la démonstration (athlètes, séances,
 * charge, conversations : {@link DemoSeedService#seedTrialClub}), et une session de coach ouverte
 * dessus. Personne d'autre n'y entre, il n'y a aucun serveur de plus, et le club est supprimé
 * avec tout son contenu à l'échéance ({@link #purgeExpired}).</p>
 *
 * <h2>Ce qui empêche un essai de déborder</h2>
 *
 * <ul>
 *   <li>Ses comptes ont des adresses en {@code .invalid} : aucun e-mail ne peut partir
 *       ({@code ResendMailClient} les ignore), et {@link TrialGuardFilter} refuse ce qui
 *       inviterait une vraie personne, relierait Strava ou toucherait au compte ;</li>
 *   <li>il n'a pas de fiche publique : l'annuaire ne le voit pas ;</li>
 *   <li>création limitée par adresse IP et plafond d'essais actifs en même temps.</li>
 * </ul>
 */
@Slf4j
@Service
public class TrialService {

    /** Domaine de toutes les adresses d'essai : c'est lui qui désigne un compte d'essai. */
    public static final String EMAIL_SUFFIX = ".essai.invalid";

    private final DemoSeedService seedService;
    private final AuthService authService;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final boolean enabled;
    private final Duration lifetime;
    private final int maxActive;
    private final FixedWindowRateLimiter perIp;
    private final SecureRandom secureRandom = new SecureRandom();
    private final TransactionTemplate tx;

    public TrialService(DemoSeedService seedService, AuthService authService,
                        ClubRepository clubRepository, UserRepository userRepository,
                        org.springframework.transaction.PlatformTransactionManager txManager,
                        @Value("${app.trial.enabled:true}") boolean enabled,
                        @Value("${app.trial.lifetime-hours:24}") int lifetimeHours,
                        @Value("${app.trial.max-active:200}") int maxActive,
                        @Value("${app.trial.per-ip-per-hour:3}") int perIpPerHour) {
        this.seedService = seedService;
        this.authService = authService;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.tx = new TransactionTemplate(txManager);
        this.enabled = enabled;
        this.lifetime = Duration.ofHours(lifetimeHours);
        this.maxActive = maxActive;
        this.perIp = new FixedWindowRateLimiter(perIpPerHour, Duration.ofHours(1));
    }

    public static boolean isTrialEmail(String email) {
        return email != null && email.toLowerCase().endsWith(EMAIL_SUFFIX);
    }

    public boolean enabled() {
        return enabled;
    }

    /** Crée un espace d'essai et rend la session de sa coach. */
    public AuthResponse start(String clientIp) {
        if (!enabled) {
            throw new ForbiddenException("L'essai sans inscription n'est pas ouvert pour le moment.");
        }
        if (clientIp != null && !perIp.tryAcquire(clientIp)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Vous avez déjà ouvert plusieurs essais cette heure-ci. Réessayez un peu plus tard.");
        }
        if (clubRepository.countByTrialExpiresAtAfter(Instant.now()) >= maxActive) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Beaucoup d'essais sont ouverts en ce moment. Réessayez dans quelques minutes.");
        }
        String tag = HexFormat.of().formatHex(randomBytes(6));
        long t0 = System.nanoTime();
        DemoSeedService.TrialAccounts accounts = seedService.seedTrialClub(tag, Instant.now().plus(lifetime));
        log.info("[essai] Espace d'essai créé en {} ms (club={}, expire dans {} h)",
                (System.nanoTime() - t0) / 1_000_000, accounts.club().getId(), lifetime.toHours());
        return authService.tokensFor(accounts.coach());
    }

    /**
     * Change de côté dans son essai : la coach prend la place de son athlète de démonstration,
     * et inversement. Seulement entre les deux comptes de l'essai.
     */
    @Transactional(readOnly = true)
    public AuthResponse switchSide(UUID userId) {
        User me = userRepository.findById(userId)
                .orElseThrow(() -> new ForbiddenException("Session invalide."));
        Club club = me.getClub();
        if (!isTrialEmail(me.getEmail()) || club == null || !club.isTrial()) {
            throw new ForbiddenException("Réservé aux espaces d'essai.");
        }
        String local = me.getRole() == UserRole.ATHLETE ? "demo" : "athlete";
        String target = local + "@" + me.getEmail().substring(me.getEmail().indexOf('@') + 1);
        User other = userRepository.findByEmailIgnoreCase(target)
                .filter(u -> u.getClub() != null && club.getId().equals(u.getClub().getId()))
                .orElseThrow(() -> new ForbiddenException("Compte d'essai introuvable."));
        return authService.tokensFor(other);
    }

    /** Supprime, avec tout leur contenu, les espaces d'essai arrivés à échéance. */
    @Scheduled(cron = "${app.trial.purge-cron:0 17 * * * *}")
    @SchedulerLock(name = "purgeExpiredTrials", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void purgeExpired() {
        List<UUID> expired = clubRepository.findIdsByTrialExpiresAtBefore(Instant.now());
        for (UUID id : expired) {
            try {
                // Une transaction par club : un essai qui résiste n'empêche pas les autres de partir.
                tx.executeWithoutResult(status -> clubRepository.hardDelete(id));
            } catch (RuntimeException e) {
                log.warn("[essai] Suppression impossible du club d'essai {} : {}", id, e.getMessage());
            }
        }
        if (!expired.isEmpty()) {
            log.info("[essai] {} espace(s) d'essai expiré(s) supprimé(s).", expired.size());
        }
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        secureRandom.nextBytes(b);
        return b;
    }
}
