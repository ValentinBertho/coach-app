package com.coachrun.security;

import com.coachrun.dto.response.MfaChallengeResponse;
import com.coachrun.entity.MfaChallenge;
import com.coachrun.entity.MfaCredential;
import com.coachrun.entity.MfaRecoveryCode;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.MfaChallengeRepository;
import com.coachrun.repository.MfaCredentialRepository;
import com.coachrun.repository.MfaRecoveryCodeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Double authentification (TOTP) des comptes à fort pouvoir.
 *
 * <p><b>Qui.</b> Les administrateurs plateforme : ils lisent tous les clubs, empruntent des
 * comptes et suppriment des données. Un mot de passe volé sur l'un d'eux ouvrait toute la base.</p>
 *
 * <p><b>Comment.</b> Mot de passe correct → pas de session, mais un <i>défi</i> de cinq minutes
 * et cinq essais. La session ne s'ouvre qu'avec un code de l'application d'authentification, ou
 * un code de secours. Au premier passage, le défi porte un secret neuf que l'administrateur
 * enregistre dans son application ; le premier code juste confirme l'enrôlement.</p>
 */
@Slf4j
@Service
public class MfaService {

    public static final String ISSUER = "Darilab";
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    static final int MAX_ATTEMPTS_PER_CHALLENGE = 5;
    static final int RECOVERY_CODES = 10;

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Sans 0/O ni 1/I/L : un code de secours se recopie souvent à la main depuis un papier. */
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final MfaCredentialRepository credentials;
    private final MfaChallengeRepository challenges;
    private final MfaRecoveryCodeRepository recoveryCodes;

    public MfaService(MfaCredentialRepository credentials, MfaChallengeRepository challenges,
                      MfaRecoveryCodeRepository recoveryCodes) {
        this.credentials = credentials;
        this.challenges = challenges;
        this.recoveryCodes = recoveryCodes;
    }

    /** Ce compte doit-il présenter un second facteur pour ouvrir une session ? */
    public boolean required(User user) {
        return user.getRole() == UserRole.PLATFORM_ADMIN;
    }

    public boolean enrolled(UUID userId) {
        return credentials.existsById(userId);
    }

    /** Résultat d'une vérification : le compte, et les codes de secours s'il vient de s'enrôler. */
    public record Verified(UUID userId, boolean enrolledNow, boolean usedRecoveryCode, List<String> recoveryCodes) {
    }

    /** Refus de vérification ; {@code userId} sert au compteur d'échecs par compte. */
    public static final class Rejected extends RuntimeException {
        private final UUID userId;
        private final boolean challengeDead;

        Rejected(UUID userId, boolean challengeDead) {
            super(challengeDead ? "challenge-dead" : "bad-code", null, false, false);
            this.userId = userId;
            this.challengeDead = challengeDead;
        }

        public UUID userId() {
            return userId;
        }

        /** Le défi est expiré, épuisé ou inconnu : il faut repasser par le mot de passe. */
        public boolean challengeDead() {
            return challengeDead;
        }
    }

    /** Ouvre un défi après un mot de passe correct. */
    @Transactional
    public MfaChallengeResponse start(User user) {
        // Un seul défi vivant par compte : relancer la connexion n'additionne pas les essais.
        challenges.deleteByUser(user.getId());
        String raw = randomToken();
        MfaChallenge c = new MfaChallenge();
        c.setUserId(user.getId());
        c.setTokenHash(sha256(raw));
        c.setExpiresAt(Instant.now().plus(CHALLENGE_TTL));
        if (!credentials.existsById(user.getId())) {
            String secret = Totp.newSecret();
            c.setPendingSecret(secret);
            challenges.save(c);
            return new MfaChallengeResponse(raw, true, secret,
                    Totp.otpauthUri(ISSUER, user.getEmail(), secret), CHALLENGE_TTL.toSeconds());
        }
        challenges.save(c);
        return new MfaChallengeResponse(raw, false, null, null, CHALLENGE_TTL.toSeconds());
    }

    /** Compte visé par un défi encore en vie (pour vérifier son verrou avant d'essayer un code). */
    @Transactional(readOnly = true)
    public Optional<UUID> challengeOwner(String rawChallenge) {
        if (rawChallenge == null || rawChallenge.isBlank()) {
            return Optional.empty();
        }
        // Sans verrou : la vérification qui suit verrouille la ligne dans sa propre transaction,
        // et un verrou pris ici par la transaction appelante la bloquerait.
        return challenges.findLiveOwner(sha256(rawChallenge), Instant.now());
    }

    /**
     * Vérifie le code présenté pour ce défi.
     *
     * <p>Transaction propre, validée même en cas de refus : l'essai compté doit tenir quoi que
     * fasse l'appelant de l'exception.</p>
     *
     * @throws Rejected code faux, défi inconnu, expiré ou épuisé
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = Rejected.class)
    public Verified verify(String rawChallenge, String presentedCode) {
        if (rawChallenge == null || rawChallenge.isBlank()) {
            throw new Rejected(null, true);
        }
        MfaChallenge c = challenges.findByTokenHash(sha256(rawChallenge)).orElse(null);
        if (c == null) {
            log.info("Second facteur refusé : défi inconnu");
            throw new Rejected(null, true);
        }
        UUID userId = c.getUserId();
        if (c.getExpiresAt().isBefore(Instant.now()) || c.getAttempts() >= MAX_ATTEMPTS_PER_CHALLENGE) {
            log.info("Second facteur refusé : défi expiré ou épuisé (user={})", userId);
            challenges.delete(c);
            throw new Rejected(userId, true);
        }
        String code = presentedCode == null ? "" : presentedCode.replaceAll("[\\s-]", "");
        long nowStep = Totp.stepAt(Instant.now().getEpochSecond());

        if (c.getPendingSecret() != null) {
            long step = Totp.match(c.getPendingSecret(), code, nowStep, -1);
            if (step < 0) {
                throw fail(c);
            }
            MfaCredential cred = credentials.findById(userId).orElseGet(MfaCredential::new);
            cred.setUserId(userId);
            cred.setSecret(c.getPendingSecret());
            cred.setEnabledAt(Instant.now());
            cred.setLastStep(step);
            credentials.save(cred);
            List<String> codes = regenerateRecoveryCodes(userId);
            challenges.delete(c);
            log.info("Double authentification activée (user={})", userId);
            return new Verified(userId, true, false, codes);
        }

        MfaCredential cred = credentials.findForUpdate(userId).orElse(null);
        if (cred == null) {
            // Second facteur réinitialisé entre le mot de passe et le code : on recommence.
            log.info("Second facteur refusé : plus enrôlé (user={})", userId);
            challenges.delete(c);
            throw new Rejected(userId, true);
        }
        long step = Totp.match(cred.getSecret(), code, nowStep, cred.getLastStep());
        if (step >= 0) {
            cred.setLastStep(step);
            challenges.delete(c);
            return new Verified(userId, false, false, List.of());
        }
        if (code.length() >= 12 && recoveryCodes.consume(userId, sha256(normaliseRecovery(code)), Instant.now()) == 1) {
            challenges.delete(c);
            log.warn("Connexion par code de secours (user={}, {} restants)",
                    userId, recoveryCodes.countByUserIdAndUsedAtIsNull(userId));
            return new Verified(userId, false, true, List.of());
        }
        throw fail(c);
    }

    private Rejected fail(MfaChallenge c) {
        c.setAttempts(c.getAttempts() + 1);
        challenges.saveAndFlush(c);
        return new Rejected(c.getUserId(), c.getAttempts() >= MAX_ATTEMPTS_PER_CHALLENGE);
    }

    /** Retire le second facteur (téléphone perdu sans codes de secours) : réenrôlement à la prochaine connexion. */
    @Transactional
    public void reset(UUID userId) {
        credentials.deleteById(userId);
        recoveryCodes.deleteByUser(userId);
        challenges.deleteByUser(userId);
    }

    @Scheduled(fixedDelay = 900_000L)
    @Transactional
    public void purgeExpiredChallenges() {
        challenges.deleteExpiredBefore(Instant.now());
    }

    private List<String> regenerateRecoveryCodes(UUID userId) {
        recoveryCodes.deleteByUser(userId);
        List<String> out = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODES; i++) {
            String code = randomRecoveryCode();
            MfaRecoveryCode r = new MfaRecoveryCode();
            r.setUserId(userId);
            r.setCodeHash(sha256(normaliseRecovery(code)));
            recoveryCodes.save(r);
            out.add(code);
        }
        return out;
    }

    /** 16 caractères (≈ 79 bits), affichés par groupes de quatre : XXXX-XXXX-XXXX-XXXX. */
    private static String randomRecoveryCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append('-');
            }
            sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
        }
        return sb.toString();
    }

    static String normaliseRecovery(String code) {
        return code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String raw) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
