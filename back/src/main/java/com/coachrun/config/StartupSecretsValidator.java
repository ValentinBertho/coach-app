package com.coachrun.config;

import com.coachrun.controller.StravaWebhookPaths;
import com.coachrun.entity.enums.RegistrationMode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Garde-fou de démarrage en profil {@code prod} : l'application refuse de booter si les
 * secrets et réglages critiques sont manquants ou laissés à leurs valeurs de développement
 * (cf. Techno.md §5).
 *
 * <p>Le périmètre couvre désormais les réglages dont l'absence produit une panne <b>silencieuse</b>
 * plutôt qu'une erreur : une URL de front restée sur {@code localhost} fabrique des liens
 * d'invitation et de réinitialisation inutilisables ; un envoi d'e-mail activé sans clé Resend
 * échoue à chaque fois dans les logs ; un CORS ouvert sur {@code localhost} laisse une origine de
 * développement parler à la production ; et des clés VAPID absentes rendent le push inerte —
 * or « séance planifiée » et « commentaire du coach » n'ont plus de repli e-mail.</p>
 */
@Slf4j
@Component
@Profile("prod")
public class StartupSecretsValidator {

    private static final int MIN_JWT_SECRET_BYTES = 64; // HS512 → 512 bits
    private static final String DEV_JWT_SECRET_PREFIX = "dev-";
    private static final String DEV_ENCRYPTION_KEY = "0".repeat(64);

    private final String jwtSecret;
    private final String fieldEncryptionKey;
    private final String frontendUrl;
    private final boolean mailEnabled;
    private final String resendApiKey;
    private final String corsOrigins;
    private final String vapidPublicKey;
    private final String vapidPrivateKey;
    private final String registrationMode;
    private final String registrationInviteCode;
    private final int trustedProxyHops;
    private final String betterStackToken;
    private final String betterStackIngestUrl;
    private final String stravaCallbackUrl;
    private final String stravaWebhookVerifyToken;
    private final String contextPath;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public StartupSecretsValidator(
            @Value("${app.security.jwt.secret:}") String jwtSecret,
            @Value("${app.security.field-encryption-key:}") String fieldEncryptionKey,
            @Value("${app.frontend-url:}") String frontendUrl,
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.resend-api-key:}") String resendApiKey,
            @Value("${app.cors.origins:}") String corsOrigins,
            @Value("${app.vapid.public-key:}") String vapidPublicKey,
            @Value("${app.vapid.private-key:}") String vapidPrivateKey,
            @Value("${app.registration.mode:open}") String registrationMode,
            @Value("${app.registration.invite-code:}") String registrationInviteCode,
            @Value("${app.rate-limit.trusted-proxy-hops:1}") int trustedProxyHops,
            @Value("${app.logs.better-stack.source-token:}") String betterStackToken,
            @Value("${app.logs.better-stack.ingest-url:}") String betterStackIngestUrl,
            @Value("${app.strava.webhook-callback-url:}") String stravaCallbackUrl,
            @Value("${app.strava.webhook-verify-token:}") String stravaWebhookVerifyToken,
            @Value("${server.servlet.context-path:}") String contextPath) {
        this.jwtSecret = jwtSecret;
        this.fieldEncryptionKey = fieldEncryptionKey;
        this.frontendUrl = frontendUrl;
        this.mailEnabled = mailEnabled;
        this.resendApiKey = resendApiKey;
        this.corsOrigins = corsOrigins;
        this.vapidPublicKey = vapidPublicKey;
        this.vapidPrivateKey = vapidPrivateKey;
        this.registrationMode = registrationMode;
        this.registrationInviteCode = registrationInviteCode;
        this.trustedProxyHops = trustedProxyHops;
        this.betterStackToken = betterStackToken;
        this.betterStackIngestUrl = betterStackIngestUrl;
        this.stravaCallbackUrl = stravaCallbackUrl;
        this.stravaWebhookVerifyToken = stravaWebhookVerifyToken;
        this.contextPath = contextPath;
    }

    @PostConstruct
    void validate() {
        List<String> problems = new ArrayList<>();

        if (isBlank(jwtSecret)
                || jwtSecret.startsWith(DEV_JWT_SECRET_PREFIX)
                || jwtSecret.getBytes().length < MIN_JWT_SECRET_BYTES) {
            problems.add("JWT_SECRET manquant ou trop faible (≥ 512 bits requis, valeur de dev interdite).");
        }
        if (isBlank(fieldEncryptionKey) || !fieldEncryptionKey.matches("[0-9a-fA-F]{64}")
                || fieldEncryptionKey.equals(DEV_ENCRYPTION_KEY)) {
            problems.add("FIELD_ENCRYPTION_KEY manquant ou invalide (64 hex requis, valeur de dev interdite).");
        }
        // Les liens d'invitation, de vérification et de réinitialisation sont construits sur cette
        // URL : restée sur localhost, ils partent chez l'utilisateur et n'ouvrent rien.
        if (isBlank(frontendUrl) || isLocal(frontendUrl)) {
            problems.add("FRONTEND_URL manquant ou pointant encore sur localhost : "
                    + "les liens d'invitation et de réinitialisation seraient inutilisables.");
        }
        // Mail activé sans clé : chaque envoi échoue dans les logs, l'utilisateur voit « envoyé ».
        if (mailEnabled && isBlank(resendApiKey)) {
            problems.add("MAIL_ENABLED=true sans RESEND_API_KEY : tous les envois échoueraient silencieusement.");
        }
        if (isBlank(corsOrigins) || isLocal(corsOrigins)) {
            problems.add("CORS_ORIGINS manquant ou contenant localhost : "
                    + "une origine de développement ne doit pas parler à la production.");
        }
        // Push sans VAPID : « séance planifiée » et « commentaire du coach » n'ont plus de repli
        // e-mail — sans clés, ces notifications ne partent nulle part.
        if (isBlank(vapidPublicKey) || isBlank(vapidPrivateKey)) {
            problems.add("VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY manquantes : "
                    + "les notifications push (séance planifiée, commentaire du coach) seraient inertes.");
        }

        // Nombre de relais devant l'API. Doit correspondre à la topologie RÉELLE, et le contrôle
        // ne peut donc pas imposer une valeur : il ne peut qu'exiger qu'elle soit plausible.
        //
        // Il exigeait 2, au motif d'une chaîne « Vercel → Railway ». C'est la topologie du *site*,
        // pas celle de l'API : le navigateur appelle l'API sur son propre domaine, sans passer par
        // Vercel — c'est d'ailleurs pourquoi il faut du CORS. Il n'y a donc qu'un relais, et
        // annoncer 2 fait échouer la lecture de la chaîne à chaque requête : le filtre retombe sur
        // l'adresse TCP, celle du relais, identique pour tout le monde. Toute la plateforme se
        // retrouve dans un compteur unique — vingt requêtes par minute à se partager, et des
        // utilisateurs déconnectés « sans cesse ». Le garde-fou fabriquait la panne qu'il
        // annonçait éviter.
        if (trustedProxyHops < 1) {
            problems.add("RATE_LIMIT_TRUSTED_PROXY_HOPS=" + trustedProxyHops
                    + " : il faut au moins 1 relais. La valeur doit refléter le nombre de proxys "
                    + "traversés par les appels API — 1 si le navigateur atteint l'API "
                    + "directement, davantage si elle est elle-même derrière un relais. "
                    + "Une valeur trop grande fait compter l'adresse du proxy et non celle du "
                    + "client : un seul seau pour tous.");
        }

        // Journalisation centralisée : contrôlée seulement si elle est demandée (un token posé).
        //
        // Sans ce garde-fou, une adresse d'ingestion mal recopiée ne se manifeste QUE par une
        // erreur toutes les trois secondes sur le thread d'envoi, indéfiniment — l'application
        // tourne, les journaux ne partent pas, et rien ne dit pourquoi. On préfère refuser de
        // démarrer sur une erreur de saisie que l'exploitant vient de commettre et peut corriger
        // en trente secondes : c'est la règle appliquée à tout le reste de ce fichier.
        problems.addAll(betterStackProblems());

        problems.addAll(registrationProblems());

        if (!problems.isEmpty()) {
            // Type dédié : c'est lui qui permet à StartupConfigurationFailureAnalyzer d'écrire
            // la liste en clair au lieu d'une trace de deux cents lignes.
            throw new StartupConfigurationException(problems);
        }
        warnings().forEach(w -> log.warn("[configuration] {}", w));
        log.info("Validation de la configuration de production OK.");
    }

    /**
     * Contrôles du mode d'inscription.
     *
     * <p>Trois modes existent, et deux d'entre eux se referment sur eux-mêmes s'ils sont mal
     * posés : {@code invite} sans code n'accepte plus personne, et une valeur inconnue (une
     * faute de frappe, {@code REGISTRATION_MODE=requests}) retomberait silencieusement sur le
     * comportement le plus ouvert. Un mode d'inscription mal orthographié ne doit pas ouvrir la
     * création de club à tout venant : on refuse de démarrer.</p>
     */
    private List<String> registrationProblems() {
        RegistrationMode mode = RegistrationMode.parse(registrationMode);
        if (mode == null) {
            return List.of("REGISTRATION_MODE=« " + registrationMode + " » inconnu. "
                    + "Valeurs acceptées : " + RegistrationMode.accepted()
                    + ". Une valeur non reconnue rouvrirait l'inscription à tout venant.");
        }
        // Inscription fermée sans code : plus personne ne peut créer de compte, et le message
        // d'erreur ne s'affiche qu'au premier candidat qui essaie.
        if (mode == RegistrationMode.INVITE && isBlank(registrationInviteCode)) {
            return List.of("REGISTRATION_MODE=invite sans REGISTRATION_INVITE_CODE : "
                    + "aucune inscription ne serait possible.");
        }
        return List.of();
    }

    /**
     * Réglages douteux qui n'empêchent pas de servir : journalisés, jamais bloquants.
     *
     * <p>La distinction est délibérée. Refuser de démarrer coûte un déploiement raté et un retour
     * arrière ; cela ne se justifie que lorsque l'application <b>servirait mal en silence</b>. Une
     * URL de rappel Strava mal formée, elle, ne casse rien tant qu'on ne crée pas l'abonnement —
     * et à ce moment-là, le contrôle est fait sur place, avec l'adresse exacte à recopier.</p>
     */
    private List<String> warnings() {
        List<String> warnings = new ArrayList<>();
        if (!isBlank(stravaCallbackUrl) && !stravaCallbackUrl.trim().endsWith(webhookPath())) {
            warnings.add("STRAVA_WEBHOOK_CALLBACK_URL (« " + stravaCallbackUrl + " ») ne se termine "
                    + "pas par « " + webhookPath() + " ». L'API est servie derrière le préfixe « "
                    + contextPath + " » : Strava validerait l'adresse sur une page inexistante et "
                    + "refuserait l'abonnement (« callback url not verifiable »).");
        }
        if (!isBlank(stravaCallbackUrl) && isBlank(stravaWebhookVerifyToken)) {
            warnings.add("STRAVA_WEBHOOK_CALLBACK_URL posée sans STRAVA_WEBHOOK_VERIFY_TOKEN : "
                    + "aucune validation d'abonnement ne sera acceptée.");
        }
        return warnings;
    }

    /** Chemin réel du webhook, préfixe de contexte compris. */
    private String webhookPath() {
        String prefix = contextPath == null ? "" : contextPath.trim();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + StravaWebhookPaths.PATH;
    }

    /**
     * Contrôles de la journalisation centralisée. Vide tant qu'aucun token n'est posé : la
     * journalisation est optionnelle, et son absence n'a jamais empêché l'application de servir.
     */
    private List<String> betterStackProblems() {
        if (isBlank(betterStackToken)) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();

        // Le gabarit de la documentation recopié tel quel : « <source token> ». C'est l'erreur
        // la plus fréquente, et la seule qu'on peut nommer précisément.
        if (looksLikePlaceholder(betterStackToken)) {
            problems.add("BETTER_STACK_SOURCE_TOKEN contient encore le gabarit de la documentation "
                    + "(« < » ou « > ») : y recopier le jeton affiché dans les réglages de la source.");
        }
        if (isBlank(betterStackIngestUrl)) {
            problems.add("BETTER_STACK_SOURCE_TOKEN posé sans BETTER_STACK_INGEST_URL : "
                    + "chaque source a SON hôte d'ingestion, à recopier depuis ses réglages.");
        } else if (looksLikePlaceholder(betterStackIngestUrl)) {
            problems.add("BETTER_STACK_INGEST_URL contient encore le gabarit de la documentation "
                    + "(« < » ou « > ») : y recopier l'hôte d'ingestion de la source.");
        } else if (!isAbsoluteHttpUrl(betterStackIngestUrl)) {
            problems.add("BETTER_STACK_INGEST_URL invalide (« " + betterStackIngestUrl + " ») : "
                    + "une URL absolue est attendue, schéma compris — "
                    + "https://sXXXXXX.eu-nbg-2.betterstackdata.com et non l'hôte seul.");
        }
        return problems;
    }

    /** Le gabarit de la documentation, recopié sans être remplacé. */
    private static boolean looksLikePlaceholder(String value) {
        return value.contains("<") || value.contains(">");
    }

    /** URL absolue en http(s), avec un hôte : ce que l'expéditeur de journaux sait appeler. */
    private static boolean isAbsoluteHttpUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value.trim());
            String scheme = uri.getScheme();
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Vrai si la valeur désigne (ou contient) une adresse de développement. */
    private static boolean isLocal(String value) {
        String v = value.toLowerCase(java.util.Locale.ROOT);
        return v.contains("localhost") || v.contains("127.0.0.1");
    }
}
