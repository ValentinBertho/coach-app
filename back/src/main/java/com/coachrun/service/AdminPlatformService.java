package com.coachrun.service;

import com.coachrun.config.VapidKeys;
import com.coachrun.dto.response.AdminPlatformResponse;
import com.coachrun.dto.response.AdminPlatformResponse.Setting;
import com.coachrun.entity.enums.RegistrationMode;
import com.coachrun.integration.StravaClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Configuration de la plateforme, telle qu'un administrateur peut la consulter.
 *
 * <p><b>La règle qui gouverne cette classe :</b> on rapporte qu'un secret <i>est posé</i>, jamais
 * sa valeur. {@link StringUtils#hasText} sur une propriété rend un booléen — c'est tout ce dont
 * un diagnostic a besoin, et c'est la seule chose qu'un écran a le droit d'afficher.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminPlatformService {

    private final Environment environment;
    private final StravaClient stravaClient;
    private final VapidKeys vapidKeys;
    private final ClockService clock;

    @Value("${app.version:dev}")
    private String version;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    @Value("${app.registration.mode:open}")
    private String registrationMode;

    @Value("${app.registration.invite-code:}")
    private String registrationInviteCode;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.quota.daily:100}")
    private long mailDailyQuota;

    @Value("${app.mail.quota.monthly:3000}")
    private long mailMonthlyQuota;

    @Value("${app.mail.log-retention-days:180}")
    private int mailLogRetentionDays;

    @Value("${app.mail.resend-api-key:}")
    private String resendApiKey;

    @Value("${app.strava.webhook-callback-url:}")
    private String stravaCallbackUrl;

    @Value("${app.strava.webhook-verify-token:}")
    private String stravaVerifyToken;

    @Value("${app.debrief.enabled:true}")
    private boolean debriefEnabled;

    @Value("${sentry.dsn:}")
    private String sentryDsn;

    public AdminPlatformResponse platform() {
        String[] profiles = environment.getActiveProfiles();
        return new AdminPlatformResponse(
                profiles.length > 0 ? String.join(", ", profiles) : "default",
                version,
                clock.zone().getId(),
                frontendUrl,
                registrationMode,
                mailDailyQuota,
                mailMonthlyQuota,
                mailLogRetentionDays,
                settings());
    }

    /**
     * Le régime d'inscription, dit tel qu'il se comporte réellement.
     *
     * <p>« Libre » n'est pas « inactif » : l'inscription fonctionne, elle n'est simplement pas
     * restreinte — d'où un libellé propre à ce réglage plutôt qu'un booléen. Les deux états
     * rouges sont ceux où l'instance ne fait pas ce qu'on croit : un mode {@code invite} sans
     * code n'accepte plus personne, et une valeur mal orthographiée ne correspond à aucun
     * régime.</p>
     */
    private Setting registrationSetting() {
        RegistrationMode mode = RegistrationMode.parse(registrationMode);
        if (mode == null) {
            return new Setting("registration", "Inscription", Setting.OFF,
                    "Valeur inconnue",
                    "REGISTRATION_MODE=« " + registrationMode + " » ne correspond à aucun régime "
                            + "(" + RegistrationMode.accepted() + "). L'application se replie sur "
                            + "le plus fermé : les demandes de club.",
                    "REGISTRATION_MODE");
        }
        boolean inviteCodeSet = StringUtils.hasText(registrationInviteCode);
        return switch (mode) {
            case OPEN -> new Setting("registration", "Inscription", Setting.PARTIAL,
                    "Libre",
                    "N'importe qui peut créer un club sur cette instance.",
                    "REGISTRATION_MODE / REGISTRATION_INVITE_CODE");
            case INVITE -> new Setting("registration", "Inscription",
                    inviteCodeSet ? Setting.ON : Setting.OFF,
                    inviteCodeSet ? "Sur code" : "Fermée par erreur",
                    inviteCodeSet
                            ? "Cohorte fermée : un code est exigé à l'inscription."
                            : "Mode « invite » sans code configuré : plus personne ne peut s'inscrire.",
                    "REGISTRATION_MODE / REGISTRATION_INVITE_CODE");
            case REQUEST -> new Setting("registration", "Inscription", Setting.ON,
                    "Sur demande validée",
                    "Le formulaire est ouvert à tous, mais il dépose une demande : rien n'est "
                            + "créé avant validation depuis « Demandes de club ».",
                    "REGISTRATION_MODE");
        };
    }

    private List<Setting> settings() {
        boolean stravaConfigured = stravaClient.isConfigured();
        boolean webhookReady = StringUtils.hasText(stravaCallbackUrl)
                && StringUtils.hasText(stravaVerifyToken);
        boolean mailReady = mailEnabled && StringUtils.hasText(resendApiKey);

        return List.of(
                new Setting("mail", "Envoi d'e-mails",
                        mailReady ? Setting.ON : mailEnabled ? Setting.PARTIAL : Setting.OFF,
                        mailReady ? "Actif" : mailEnabled ? "Sans fournisseur" : "Désactivé",
                        mailReady
                                ? "Invitations, vérifications et réinitialisations partent normalement."
                                : mailEnabled
                                ? "Envoi activé mais aucune clé de fournisseur : rien ne part."
                                : "Aucun e-mail ne quitte cette instance.",
                        "MAIL_ENABLED / RESEND_API_KEY"),

                new Setting("strava", "Application Strava",
                        stravaConfigured ? Setting.ON : Setting.OFF,
                        stravaConfigured ? "Configurée" : "Non configurée",
                        stravaConfigured
                                ? "Les athlètes peuvent connecter leur compte Strava."
                                : "Aucune connexion de montre possible.",
                        "STRAVA_CLIENT_ID / STRAVA_CLIENT_SECRET"),

                new Setting("strava-webhook", "Webhook Strava",
                        !stravaConfigured ? Setting.OFF : webhookReady ? Setting.ON : Setting.PARTIAL,
                        !stravaConfigured ? "Sans objet" : webhookReady ? "Configuré" : "Non configuré",
                        !stravaConfigured
                                ? "Sans application Strava configurée, l'abonnement n'a pas d'objet."
                                : webhookReady
                                ? "Remontée des activités en quelques secondes."
                                : "Remontée horaire seulement. Un seul abonnement par application "
                                + "Strava : il se pose depuis l'instance qui doit recevoir le flux.",
                        "STRAVA_WEBHOOK_CALLBACK_URL / STRAVA_WEBHOOK_VERIFY_TOKEN"),

                new Setting("push", "Notifications push",
                        vapidKeys.isConfigured() ? Setting.ON : Setting.OFF,
                        vapidKeys.isConfigured() ? "Identité posée" : "Sans identité",
                        vapidKeys.isConfigured()
                                ? "Les appareils peuvent s'abonner."
                                : "Sans identité VAPID, le push est inerte — le navigateur n'a "
                                + "même pas de clé publique à présenter.",
                        "VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY"),

                registrationSetting(),

                new Setting("debrief", "Rappels de débriefing",
                        debriefEnabled ? Setting.ON : Setting.OFF,
                        debriefEnabled ? "Actifs" : "Désactivés",
                        debriefEnabled
                                ? "« Ta séance est finie ? », 2 h après l'heure habituelle."
                                : "Aucun rappel de débriefing sur cette instance.",
                        "DEBRIEF_REMINDERS_ENABLED"),

                new Setting("sentry", "Remontée d'erreurs",
                        StringUtils.hasText(sentryDsn) ? Setting.ON : Setting.OFF,
                        StringUtils.hasText(sentryDsn) ? "Active" : "Absente",
                        StringUtils.hasText(sentryDsn)
                                ? "Les erreurs serveur sont remontées avec leur identifiant de corrélation."
                                : "Un incident ne se voit que dans les journaux.",
                        "SENTRY_DSN"));
    }
}
