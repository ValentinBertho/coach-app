package com.coachrun.security;

import com.coachrun.entity.RefreshToken;
import com.coachrun.entity.User;
import com.coachrun.exception.UnauthorizedException;
import com.coachrun.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Jetons de rafraîchissement opaques, enregistrés en base.
 *
 * <p><b>Pourquoi plus de JWT.</b> Le jeton de rafraîchissement était un JWT de trente jours, et
 * la seule trace de ceux déjà consommés vivait dans une liste en mémoire : à chaque redémarrage,
 * tout jeton volé redevenait rejouable. Ici, le jeton est une valeur aléatoire de 256 bits dont
 * seule l'empreinte est stockée, et son état (utilisé, révoqué, expiré) survit à tout.</p>
 *
 * <p><b>Rotation et rejeu.</b> Chaque rafraîchissement consomme le jeton présenté et en émet un
 * nouveau dans la même famille. Si un jeton déjà consommé revient, deux personnes détiennent la
 * même session : on ne peut pas savoir laquelle est légitime, donc toute la famille est révoquée
 * et les deux devront se reconnecter.</p>
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final TokenFreshnessValidator tokenFreshness;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository,
                               TokenFreshnessValidator tokenFreshness,
                               @Value("${app.security.jwt.refresh-token-ttl-seconds:2592000}") long ttlSeconds) {
        this.repository = repository;
        this.tokenFreshness = tokenFreshness;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Duration ttl() {
        return ttl;
    }

    /** Ouvre une session : nouvelle famille, premier jeton. Retourne la valeur en clair (jamais stockée). */
    @Transactional
    public String issue(User user) {
        return issue(user.getId(), UUID.randomUUID());
    }

    /** Résultat d'une rotation : le compte et le nouveau jeton en clair. */
    public record Rotation(UUID userId, String refreshToken) {
    }

    /**
     * Consomme le jeton présenté et en émet un nouveau dans la même session.
     *
     * @throws UnauthorizedException si le jeton est inconnu, expiré, révoqué, rejoué, ou antérieur
     *                               à la dernière déconnexion ou au dernier changement de mot de passe
     */
    // Transaction propre et validée même en cas de refus : la révocation d'une session rejouée
    // doit tenir quoi que fasse l'appelant de l'exception.
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW,
            noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Session expirée, reconnectez-vous.");
        }
        Instant now = Instant.now();
        RefreshToken token = repository.findByTokenHash(hash(rawToken)).orElse(null);
        if (token == null) {
            log.info("Refresh refusé : jeton inconnu");
            throw new UnauthorizedException("Session expirée, reconnectez-vous.");
        }
        if (token.getUsedAt() != null) {
            // Rejeu : quelqu'un d'autre a eu ce jeton entre les mains.
            repository.revokeFamily(token.getFamilyId(), now);
            log.warn("Refresh refusé : jeton déjà utilisé, session révoquée (user={})", token.getUserId());
            throw new UnauthorizedException("Session révoquée par sécurité, reconnectez-vous.");
        }
        if (token.getRevokedAt() != null) {
            log.info("Refresh refusé : session révoquée (user={})", token.getUserId());
            throw new UnauthorizedException("Session expirée, reconnectez-vous.");
        }
        if (token.getExpiresAt().isBefore(now)) {
            log.info("Refresh refusé : jeton expiré (user={})", token.getUserId());
            throw new UnauthorizedException("Session expirée, reconnectez-vous.");
        }
        if (tokenFreshness.isStale(token.getUserId(), token.getCreatedAt())) {
            log.info("Refresh refusé : session antérieure à la dernière révocation (user={})", token.getUserId());
            throw new UnauthorizedException("Le mot de passe a changé ou la session a été fermée, reconnectez-vous.");
        }
        token.setUsedAt(now);
        repository.saveAndFlush(token);
        return new Rotation(token.getUserId(), issue(token.getUserId(), token.getFamilyId()));
    }

    /** Ferme la session à laquelle appartient ce jeton (déconnexion de cet appareil). */
    @Transactional
    public void revokeSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    /** Ferme toutes les sessions du compte (déconnexion, mot de passe changé ou réinitialisé). */
    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId, Instant.now());
    }

    /** Les jetons expirés depuis plus d'un jour ne servent plus à rien, pas même à détecter un rejeu. */
    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpired() {
        int removed = repository.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purge : {} jetons de rafraîchissement expirés supprimés", removed);
        }
    }

    private String issue(UUID userId, UUID familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setFamilyId(familyId);
        t.setTokenHash(hash(raw));
        t.setExpiresAt(Instant.now().plus(ttl));
        repository.save(t);
        return raw;
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
