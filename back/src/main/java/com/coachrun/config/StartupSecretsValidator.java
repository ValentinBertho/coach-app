package com.coachrun.config;

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
            @Value("${app.registration.invite-code:}") String registrationInviteCode) {
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

        // Inscription fermée sans code : plus personne ne peut créer de compte, et le message
        // d'erreur ne s'affiche qu'au premier candidat qui essaie.
        if ("invite".equalsIgnoreCase(registrationMode) && isBlank(registrationInviteCode)) {
            problems.add("REGISTRATION_MODE=invite sans REGISTRATION_INVITE_CODE : "
                    + "aucune inscription ne serait possible.");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Configuration de production incomplète :\n - "
                    + String.join("\n - ", problems));
        }
        log.info("Validation de la configuration de production OK.");
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
