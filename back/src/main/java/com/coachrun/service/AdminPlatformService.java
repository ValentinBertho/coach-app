package com.coachrun.service;

import com.coachrun.config.VapidKeys;
import com.coachrun.dto.response.AdminPlatformResponse;
import com.coachrun.dto.response.AdminPlatformResponse.Setting;
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

    @Value("${app.demo.reset.enabled:false}")
    private boolean demoResetEnabled;

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

    private List<Setting> settings() {
        boolean stravaConfigured = stravaClient.isConfigured();
        boolean webhookReady = StringUtils.hasText(stravaCallbackUrl)
                && StringUtils.hasText(stravaVerifyToken);

        return List.of(
                new Setting("mail", "Envoi d'e-mails",
                        mailEnabled && StringUtils.hasText(resendApiKey) ? Setting.ON
                                : mailEnabled ? Setting.PARTIAL : Setting.OFF,
                        mailEnabled && StringUtils.hasText(resendApiKey)
                                ? "Invitations, vérifications et réinitialisations partent normalement."
                                : mailEnabled
                                ? "Envoi activé mais aucune clé de fournisseur : rien ne part."
                                : "Désactivé : aucun e-mail ne quitte cette instance.",
                        "MAIL_ENABLED / RESEND_API_KEY"),

                new Setting("strava", "Application Strava",
                        stravaConfigured ? Setting.ON : Setting.OFF,
                        stravaConfigured
                                ? "Les athlètes peuvent connecter leur compte Strava."
                                : "Non configurée : aucune connexion de montre possible.",
                        "STRAVA_CLIENT_ID / STRAVA_CLIENT_SECRET"),

                new Setting("strava-webhook", "Webhook Strava",
                        !stravaConfigured ? Setting.OFF
                                : webhookReady ? Setting.ON : Setting.PARTIAL,
                        !stravaConfigured
                                ? "Sans application Strava configurée, l'abonnement n'a pas d'objet."
                                : webhookReady
                                ? "Remontée des activités en quelques secondes."
                                : "Remontée horaire seulement. Un seul abonnement par application "
                                + "Strava : il se pose depuis l'instance qui doit recevoir le flux.",
                        "STRAVA_WEBHOOK_CALLBACK_URL / STRAVA_WEBHOOK_VERIFY_TOKEN"),

                new Setting("push", "Notifications push",
                        vapidKeys.isConfigured() ? Setting.ON : Setting.OFF,
                        vapidKeys.isConfigured()
                                ? "Identité VAPID posée : les appareils peuvent s'abonner."
                                : "Sans identité VAPID, le push est inerte — le navigateur n'a "
                                + "même pas de clé publique à présenter.",
                        "VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY"),

                new Setting("registration", "Inscription",
                        "invite".equalsIgnoreCase(registrationMode)
                                ? (StringUtils.hasText(registrationInviteCode) ? Setting.ON : Setting.PARTIAL)
                                : Setting.OFF,
                        "invite".equalsIgnoreCase(registrationMode)
                                ? (StringUtils.hasText(registrationInviteCode)
                                ? "Cohorte fermée : un code est exigé à l'inscription."
                                : "Mode « invite » sans code configuré : plus personne ne peut s'inscrire.")
                                : "Inscription libre : n'importe qui peut créer un club sur cette instance.",
                        "REGISTRATION_MODE / REGISTRATION_INVITE_CODE"),

                new Setting("debrief", "Rappels de débriefing",
                        debriefEnabled ? Setting.ON : Setting.OFF,
                        debriefEnabled
                                ? "« Ta séance est finie ? », 2 h après l'heure habituelle."
                                : "Désactivés sur cette instance.",
                        "DEBRIEF_REMINDERS_ENABLED"),

                new Setting("sentry", "Remontée d'erreurs",
                        StringUtils.hasText(sentryDsn) ? Setting.ON : Setting.OFF,
                        StringUtils.hasText(sentryDsn)
                                ? "Les erreurs serveur sont remontées avec leur identifiant de corrélation."
                                : "Aucune remontée : un incident ne se voit que dans les journaux.",
                        "SENTRY_DSN"),

                new Setting("demo-reset", "Réinitialisation démo",
                        demoResetEnabled ? Setting.ON : Setting.OFF,
                        demoResetEnabled
                                ? "Disponible : efface TOUTES les données au profit du jeu de démonstration."
                                : "Interdite sur cette instance — c'est le réglage attendu en production.",
                        "DEMO_RESET_ENABLED"));
    }
}
