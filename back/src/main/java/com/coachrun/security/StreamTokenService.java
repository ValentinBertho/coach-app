package com.coachrun.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jetons dédiés aux deux seules requêtes du produit qui ne peuvent pas porter d'en-tête
 * {@code Authorization} : l'ouverture d'un flux {@code EventSource} et l'ouverture d'une pièce
 * jointe dans un onglet.
 *
 * <h2>Ce que cela remplace</h2>
 *
 * <p>Ces routes acceptaient jusqu'ici le <b>jeton de session</b> en paramètre d'URL
 * ({@code ?access_token=…}). Un JWT d'accès vaut une heure et ouvre <i>toute</i> l'API ; placé
 * dans une URL, il se retrouve dans les journaux d'accès du relais, dans l'historique du
 * navigateur — {@code EventSource} et une pièce jointe ouverte dans un onglet créent une entrée
 * de navigation — et dans l'en-tête {@code Referer} de la page suivante. Un journal partagé avec
 * l'hébergeur suffisait donc à rejouer une session complète.</p>
 *
 * <h2>Ce que ces jetons-ci valent</h2>
 *
 * <ul>
 *   <li><b>Une minute.</b> Le jeton est demandé juste avant d'être utilisé ; il n'a pas à
 *       survivre au flux qu'il ouvre.</li>
 *   <li><b>Un seul usage.</b> {@link #consume} retire l'entrée avant de répondre : rejouer une
 *       URL glanée dans un journal ne donne rien, même dans la minute.</li>
 *   <li><b>Une portée.</b> Un jeton émis pour un flux n'ouvre pas une pièce jointe, et
 *       réciproquement.</li>
 *   <li><b>Jamais l'API.</b> {@link JwtAuthenticationFilter} ne les accepte que sur un
 *       {@code GET} de flux ou de pièce jointe. Partout ailleurs, ils n'existent pas.</li>
 * </ul>
 *
 * <h2>Forme du jeton</h2>
 *
 * <p>{@code <étiquette>.<secret>}. L'étiquette est une empreinte tronquée et non réversible du
 * compte ; elle ne sert qu'au comptage des requêtes ({@link RateLimitFilter} ne peut pas décoder
 * un jeton opaque, et sans elle les flux redeviendraient comptés par adresse IP — donc partagés
 * entre tous les athlètes d'un club derrière une même box). Le secret fait 256 bits.</p>
 *
 * <p>Ce n'est <b>pas</b> l'étiquette qui autorise : seul le secret est vérifié, et le stockage se
 * fait sous l'empreinte SHA-256 du jeton complet — la carte en mémoire ne contient donc aucune
 * valeur rejouable.</p>
 *
 * <h2>Limite connue</h2>
 *
 * <p>Le registre vit dans l'instance, comme {@link TokenBlacklist}. À deux instances derrière un
 * répartiteur, un jeton émis par l'une et présenté à l'autre serait refusé : le flux se
 * rouvrirait (le client réessaie avec un jeton frais) mais une ouverture sur deux échouerait.
 * Le déploiement est mono-instance ; externaliser le registre est le prérequis d'un scale-out,
 * au même titre que la liste noire.</p>
 */
@Slf4j
@Service
public class StreamTokenService {

    /** Ce à quoi un jeton donne droit. Un jeton hors de sa portée est refusé comme s'il était faux. */
    public enum Scope {
        /** Ouverture d'un flux SSE ({@code GET …/stream}). */
        STREAM,
        /** Téléchargement d'une pièce jointe ({@code GET …/attachment}). */
        ATTACHMENT
    }

    /** Longueur du secret aléatoire, en octets. */
    private static final int SECRET_BYTES = 32;
    /** Longueur de l'étiquette de comptage, en caractères Base64. */
    private static final int TAG_LENGTH = 12;
    /**
     * Sel de l'étiquette. Elle voyage en clair dans l'URL : sans sel, deux journaux différents
     * permettraient de recouper « ces requêtes viennent du même compte » à partir d'un
     * identifiant devinable.
     */
    private static final String TAG_SALT = "darilab-stream-tag:";
    /**
     * Au-delà, on passe un coup de balai avant d'émettre. L'émission est plafonnée par requête et
     * par compte ({@link RateLimitFilter}, bucket des canaux de présence), et un jeton vit une
     * minute : la carte reste petite. Ce garde-fou ne sert qu'à ce qu'elle ne puisse pas croître
     * sans borne si ce plafond venait à être desserré.
     */
    private static final int PURGE_THRESHOLD = 20_000;

    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public StreamTokenService(
            @Value("${app.security.stream-token-ttl-seconds:60}") long ttlSeconds) {
        this(Duration.ofSeconds(Math.max(1, ttlSeconds)), Clock.systemUTC());
    }

    /** Pour les tests : durée de vie et horloge maîtrisées. */
    StreamTokenService(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    /** Ce qu'un jeton porte : à qui il appartient, ce qu'il ouvre, jusqu'à quand. */
    private record Grant(AuthPrincipal principal, Scope scope, Instant expiresAt) {
    }

    /** Durée de vie annoncée au client, en secondes. */
    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /**
     * Émet un jeton pour ce compte et cette portée. La valeur retournée est la seule occasion de
     * la connaître : seule son empreinte est conservée.
     */
    public String issue(AuthPrincipal principal, Scope scope) {
        if (grants.size() >= PURGE_THRESHOLD) {
            purgeExpired();
        }
        byte[] secret = new byte[SECRET_BYTES];
        random.nextBytes(secret);
        String token = tagFor(principal.userId()) + "." + encoder.encodeToString(secret);
        grants.put(fingerprint(token),
                new Grant(principal, scope, clock.instant().plus(ttl)));
        return token;
    }

    /**
     * Consomme un jeton. Retourne le compte auquel il appartenait, ou {@link Optional#empty()} si
     * le jeton est inconnu, périmé, déjà utilisé, ou émis pour une autre portée.
     *
     * <p>L'entrée est retirée <b>avant</b> toute vérification : un jeton présenté hors de sa
     * portée est aussi un jeton brûlé, sinon il suffirait de le présenter deux fois pour
     * distinguer « portée invalide » de « jeton inconnu ».</p>
     */
    public Optional<AuthPrincipal> consume(String token, Scope scope) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Grant grant = grants.remove(fingerprint(token));
        if (grant == null || grant.scope() != scope || grant.expiresAt().isBefore(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(grant.principal());
    }

    /**
     * Étiquette de comptage d'un compte : stable d'un jeton à l'autre, non réversible, et sans
     * rapport avec l'identifiant qu'elle représente.
     */
    public String tagFor(UUID userId) {
        return sha256Base64(TAG_SALT + userId).substring(0, TAG_LENGTH);
    }

    /**
     * Étiquette portée par un jeton, ou {@code null} s'il n'en porte pas. Lue par le filtre de
     * plafonnement, qui s'exécute avant toute authentification : elle n'autorise rien.
     */
    public static String tagOf(String token) {
        if (token == null) {
            return null;
        }
        int dot = token.indexOf('.');
        return dot > 0 ? token.substring(0, dot) : null;
    }

    /** Retire les jetons périmés que personne n'est venu consommer. */
    @Scheduled(fixedDelay = 300_000L)
    void purgeExpired() {
        Instant now = clock.instant();
        grants.values().removeIf(g -> g.expiresAt().isBefore(now));
    }

    /** Nombre de jetons en attente. Pour les tests et le diagnostic. */
    int outstanding() {
        return grants.size();
    }

    private String fingerprint(String token) {
        return sha256Base64(token);
    }

    private String sha256Base64(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return encoder.encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 est exigé de toute implémentation de la plateforme Java.
            throw new IllegalStateException("SHA-256 indisponible", ex);
        }
    }
}
