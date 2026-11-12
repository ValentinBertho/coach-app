package com.coachrun.service;

import com.coachrun.dto.response.AdminIntegrationResponse;
import com.coachrun.dto.response.AdminOverviewResponse;
import com.coachrun.dto.response.AdminSignalResponse;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.ClubRequestStatus;
import com.coachrun.entity.enums.ClubStatus;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.entity.enums.FeedbackStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.integration.StravaClient;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.BetaFeedbackRepository;
import com.coachrun.repository.ClubCreationRequestRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.MailLogRepository;
import com.coachrun.repository.PushSubscriptionRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Le tableau de bord du back-office : santé, croissance, usage, anomalies.
 *
 * <p><b>Ce qu'il remplace.</b> Sept compteurs bruts — clubs, coachs, athlètes, séances… — dont
 * aucun ne permettait de décider quoi que ce soit. On pouvait passer devant cet écran tous les
 * jours pendant que le plafond d'e-mails se remplissait, que des coachs restaient bloqués sur un
 * lien de vérification jamais reçu, et que des invitations expiraient sans que personne ne le
 * sache.</p>
 *
 * <p><b>Aucun appel sortant.</b> L'état des intégrations est déduit de la configuration et de ce
 * que la base a enregistré. Interroger Strava ici rendrait le tableau de bord tributaire de la
 * disponibilité de Strava, au moment précis où on l'ouvre parce que quelque chose ne va pas.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminOverviewService {

    /** Au-delà, un compte non vérifié n'est plus « en cours d'inscription » mais bloqué. */
    private static final int UNVERIFIED_STALE_DAYS = 3;

    /** Une invitation qui expire dans moins de trois jours mérite d'être renvoyée maintenant. */
    private static final int INVITATION_HORIZON_DAYS = 3;

    /** Part du plafond d'e-mails à partir de laquelle on alerte. */
    private static final int MAIL_WARNING_PCT = 70;
    private static final int MAIL_CRITICAL_PCT = 90;

    private final ClubRepository clubRepository;
    private final ClubCreationRequestRepository clubRequestRepository;
    private final UserRepository userRepository;
    private final AthleteRepository athleteRepository;
    private final CoachAthleteRelationRepository relationRepository;
    private final WorkoutRepository workoutRepository;
    private final ActivityRepository activityRepository;
    private final MailLogRepository mailLogRepository;
    private final BetaFeedbackRepository feedbackRepository;
    private final DeviceConnectionRepository deviceConnectionRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final AdminAuditService auditService;
    private final ClockService clock;
    private final StravaClient stravaClient;
    private final ModerationQueueService moderation;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.quota.daily:100}")
    private long mailDailyQuota;

    @Value("${app.mail.quota.monthly:3000}")
    private long mailMonthlyQuota;

    @Value("${app.strava.webhook-callback-url:}")
    private String stravaCallbackUrl;

    @Value("${app.strava.webhook-verify-token:}")
    private String stravaVerifyToken;

    public AdminOverviewResponse overview() {
        LocalDate today = clock.today();
        Instant now = Instant.now();
        Instant startOfDay = today.atStartOfDay(clock.zone()).toInstant();
        Instant startOfMonth = today.withDayOfMonth(1).atStartOfDay(clock.zone()).toInstant();
        Instant sevenDaysAgo = now.minus(Duration.ofDays(7));
        Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));

        AdminOverviewResponse.Counts counts = counts();
        AdminOverviewResponse.Growth growth = new AdminOverviewResponse.Growth(
                userRepository.countByCreatedAtAfter(sevenDaysAgo),
                userRepository.countByCreatedAtAfter(thirtyDaysAgo),
                clubRepository.countByCreatedAtAfter(thirtyDaysAgo),
                athleteRepository.countByCreatedAtAfter(thirtyDaysAgo));

        AdminOverviewResponse.Engagement engagement = new AdminOverviewResponse.Engagement(
                userRepository.countByLastSeenAtAfter(now.minus(Duration.ofHours(24))),
                userRepository.countByLastSeenAtAfter(sevenDaysAgo),
                userRepository.countByLastSeenAtAfter(thirtyDaysAgo),
                activityRepository.countByActivityDateAfter(today.minusDays(7)),
                workoutRepository.countByScheduledDateBetween(today.minusDays(7), today),
                workoutRepository.countByStatusAndScheduledDateBetween(
                        com.coachrun.entity.enums.WorkoutStatus.COMPLETED,
                        today.minusDays(7), today),
                auditService.countSince(sevenDaysAgo));

        long mailToday = mailLogRepository.countBySentAtAfterAndSentTrue(startOfDay);
        long mailMonth = mailLogRepository.countBySentAtAfterAndSentTrue(startOfMonth);
        long mailFailed7d = mailLogRepository.countBySentAtAfterAndSentFalse(sevenDaysAgo);

        return new AdminOverviewResponse(
                signals(counts, mailToday, mailMonth, mailFailed7d, now),
                counts,
                growth,
                engagement,
                integrations(mailToday, mailFailed7d),
                auditService.latest());
    }

    private AdminOverviewResponse.Counts counts() {
        return new AdminOverviewResponse.Counts(
                clubRepository.count(),
                clubRepository.countByStatus(ClubStatus.ACTIVE),
                clubRepository.countByStatus(ClubStatus.SUSPENDED),
                userRepository.count(),
                userRepository.countByRole(UserRole.PLATFORM_ADMIN),
                userRepository.countByRole(UserRole.HEAD_COACH),
                userRepository.countByRole(UserRole.COACH),
                userRepository.countByRole(UserRole.ATHLETE),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                userRepository.countStaleUnverified(Instant.now()),
                athleteRepository.count(),
                athleteRepository.countByStatus(AthleteStatus.ACTIVE),
                athleteRepository.countByStatus(AthleteStatus.PAUSED),
                athleteRepository.countByStatus(AthleteStatus.ARCHIVED),
                athleteRepository.countByInviteTokenIsNotNull(),
                workoutRepository.count(),
                activityRepository.count());
    }

    /**
     * Les anomalies, de la plus urgente à la moins. Une liste vide est un résultat : l'écran
     * affiche alors « rien à signaler », ce qui est une information utile — pas un vide.
     */
    private List<AdminSignalResponse> signals(AdminOverviewResponse.Counts counts,
                                              long mailToday, long mailMonth, long mailFailed7d,
                                              Instant now) {
        List<AdminSignalResponse> out = new ArrayList<>();

        // --- Canal e-mail : c'est lui qui casse en premier, et en silence. ---
        addQuotaSignal(out, "mail-daily", "quotidien", mailToday, mailDailyQuota);
        addQuotaSignal(out, "mail-monthly", "mensuel", mailMonth, mailMonthlyQuota);
        if (mailFailed7d > 0) {
            out.add(AdminSignalResponse.of("mail-failures", AdminSignalResponse.WARNING,
                    mailFailed7d + " envoi" + plural(mailFailed7d) + " en échec sur 7 jours",
                    "Un e-mail refusé ne repart jamais tout seul : lien de vérification ou de "
                            + "réinitialisation perdu pour son destinataire.",
                    "Voir le journal", "/admin/mail", mailFailed7d));
        }

        // --- Comptes bloqués : ils n'écrivent pas au support, ils abandonnent. ---
        long staleUnverified = userRepository.countStaleUnverified(
                now.minus(Duration.ofDays(UNVERIFIED_STALE_DAYS)));
        if (staleUnverified > 0) {
            out.add(AdminSignalResponse.of("unverified", AdminSignalResponse.WARNING,
                    staleUnverified + " compte" + plural(staleUnverified) + " non vérifié"
                            + plural(staleUnverified) + " depuis plus de "
                            + UNVERIFIED_STALE_DAYS + " jours",
                    "L'e-mail de confirmation n'est probablement jamais arrivé. Le renvoi se fait "
                            + "depuis la fiche du compte.",
                    "Filtrer les comptes", "/admin/users?verified=false", staleUnverified));
        }

        // --- Invitations : périmées, elles ne mènent plus nulle part. ---
        long expired = athleteRepository.countExpiredInvitations(now);
        if (expired > 0) {
            out.add(AdminSignalResponse.of("invitations-expired", AdminSignalResponse.WARNING,
                    expired + " invitation" + plural(expired) + " expirée" + plural(expired),
                    "Le lien ne fonctionne plus. Un renvoi depuis l'écran des invitations en "
                            + "produit un nouveau.",
                    "Voir les invitations", "/admin/invitations", expired));
        }
        long expiring = athleteRepository.countInvitationsExpiringBefore(
                now, now.plus(Duration.ofDays(INVITATION_HORIZON_DAYS)));
        if (expiring > 0) {
            out.add(AdminSignalResponse.of("invitations-expiring", AdminSignalResponse.INFO,
                    expiring + " invitation" + plural(expiring) + " expire"
                            + (expiring > 1 ? "nt" : "") + " sous " + INVITATION_HORIZON_DAYS + " jours",
                    "Les relancer maintenant évite de refaire le tour des athlètes la semaine "
                            + "prochaine.",
                    "Voir les invitations", "/admin/invitations", expiring));
        }

        // --- Clubs sans encadrant : personne n'y prescrit rien. ---
        long clubsWithoutCoach = countClubsWithoutActiveCoach();
        if (clubsWithoutCoach > 0) {
            out.add(AdminSignalResponse.of("clubs-without-coach", AdminSignalResponse.WARNING,
                    clubsWithoutCoach + " club" + plural(clubsWithoutCoach) + " sans coach actif",
                    "Aucun compte actif ne peut y prescrire de séance : club vidé, coach suspendu "
                            + "ou rattachement perdu.",
                    "Voir les clubs", "/admin/clubs", clubsWithoutCoach));
        }

        long athletesWithoutCoach = relationRepository.countActiveAthletesWithoutAnyCoach();
        if (athletesWithoutCoach > 0) {
            out.add(AdminSignalResponse.of("athletes-without-coach", AdminSignalResponse.INFO,
                    athletesWithoutCoach + " athlète" + plural(athletesWithoutCoach)
                            + " actif" + plural(athletesWithoutCoach) + " sans coach",
                    "Ils sont dans la plateforme sans y être suivis : personne ne reçoit leurs "
                            + "retours ni ne leur planifie de séance.",
                    "Voir les athlètes", "/admin/athletes", athletesWithoutCoach));
        }

        // --- Demandes de création de club : personne d'autre ne peut ouvrir la porte. ---
        //
        // En tête des files de travail, et non parmi les « à savoir » : de l'autre côté, un coach
        // attend d'entrer, et il n'a aucun autre moyen de le faire. Une demande oubliée trois
        // jours est un club qui ne s'ouvrira jamais — le candidat aura renoncé.
        long pendingClubRequests = clubRequestRepository.countByStatus(ClubRequestStatus.PENDING);
        if (pendingClubRequests > 0) {
            out.add(AdminSignalResponse.of("club-requests-pending", AdminSignalResponse.WARNING,
                    pendingClubRequests + " demande" + plural(pendingClubRequests)
                            + " de création de club à étudier",
                    "Tant qu'elles ne sont pas arbitrées, ces coachs n'ont aucun moyen d'entrer : "
                            + "la validation ouvre le club et leur envoie leur lien d'accès.",
                    "Étudier les demandes", "/admin/club-requests", pendingClubRequests));
        }

        // --- Fiches coachs à valider : l'autre porte d'entrée de la plateforme. ---
        //
        // Même raisonnement que ci-dessus, et même conséquence : une fiche laissée en attente est
        // un coach qui n'apparaît dans aucun annuaire, donc invisible des athlètes.
        // Un seul relevé des files pour les deux signaux qui suivent : il fait trois comptes en
        // base, et les demander deux fois pour afficher un tableau de bord serait payer le double
        // d'une information qui ne bouge pas entre les deux lignes.
        var queues = moderation.summary();
        long pendingProfiles = queues.pendingCoachProfiles();
        if (pendingProfiles > 0) {
            out.add(AdminSignalResponse.of("coach-profiles-pending", AdminSignalResponse.WARNING,
                    pendingProfiles + " fiche" + plural(pendingProfiles) + " coach à valider",
                    "Tant qu'elle n'est pas publiée, la fiche n'existe pour aucun athlète : "
                            + "ni la recherche ni son adresse directe ne la rendent.",
                    "Étudier les fiches", "/admin/coach-profiles", pendingProfiles));
        }

        // --- Signalements ouverts : la contrepartie de diplômes publiés sans vérification. ---
        //
        // CRITIQUE au-delà de deux jours, et pas par emphase : c'est le seul de ces signaux où
        // l'attente laisse EN LIGNE quelque chose que quelqu'un conteste. Les autres files font
        // patienter ; celle-ci publie.
        long openReports = queues.openReports();
        if (openReports > 0) {
            Long age = queues.oldestReportAgeDays();
            boolean stale = age != null && age >= 2;
            out.add(AdminSignalResponse.of("coach-reports-open",
                    stale ? AdminSignalResponse.CRITICAL : AdminSignalResponse.WARNING,
                    openReports + " signalement" + plural(openReports) + " à traiter"
                            + (stale ? " — le plus ancien depuis " + age + " jours" : ""),
                    "La plateforme ne vérifie pas les diplômes qu'elle publie ; elle lit ce qu'on "
                            + "lui rapporte. Une fiche contestée reste en ligne tant que personne "
                            + "ne l'a regardée.",
                    "Ouvrir les signalements", "/admin/coach-reports", openReports));
        }

        // --- Retours bêta en attente : la file de travail du support. ---
        long openFeedback = feedbackRepository.countByStatus(FeedbackStatus.NEW);
        if (openFeedback > 0) {
            out.add(AdminSignalResponse.of("feedback-open", AdminSignalResponse.INFO,
                    openFeedback + " retour" + plural(openFeedback) + " non traité"
                            + plural(openFeedback),
                    "Chaque retour porte la page, la version et l'identifiant de corrélation de "
                            + "la dernière erreur vue.",
                    "Traiter les retours", "/admin/feedback", openFeedback));
        }

        // --- Intégrations éteintes. ---
        if (stravaClient.isConfigured() && (stravaCallbackUrl.isBlank() || stravaVerifyToken.isBlank())) {
            out.add(AdminSignalResponse.of("strava-webhook", AdminSignalResponse.INFO,
                    "Webhook Strava non configuré",
                    "Les activités remontent par la synchronisation horaire au lieu de quelques "
                            + "secondes. Renseigner l'adresse de rappel et le jeton de validation.",
                    "Voir la configuration", "/admin/platform", 0));
        }
        if (!mailEnabled) {
            out.add(AdminSignalResponse.of("mail-disabled", AdminSignalResponse.INFO,
                    "Envoi d'e-mails désactivé",
                    "Aucune invitation, vérification ni réinitialisation ne part de cette "
                            + "instance. Les liens restent accessibles depuis l'application.",
                    "Voir la configuration", "/admin/platform", 0));
        }

        // --- Comptes suspendus : à savoir, jamais alarmant. ---
        if (counts.clubsSuspended() > 0) {
            out.add(AdminSignalResponse.of("clubs-suspended", AdminSignalResponse.INFO,
                    counts.clubsSuspended() + " club" + plural(counts.clubsSuspended())
                            + " suspendu" + plural(counts.clubsSuspended()),
                    "Leurs données restent intactes ; seul l'accès est fermé.",
                    "Voir les clubs", "/admin/clubs?status=SUSPENDED", counts.clubsSuspended()));
        }

        out.sort(java.util.Comparator.comparingInt(s -> severityRank(s.severity())));
        return out;
    }

    private void addQuotaSignal(List<AdminSignalResponse> out, String key, String scope,
                                long used, long quota) {
        if (quota <= 0) {
            return; // plan sans plafond connu : la jauge n'a pas de sens, l'alerte non plus
        }
        int pct = (int) Math.round(used * 100.0 / quota);
        if (pct < MAIL_WARNING_PCT) {
            return;
        }
        boolean critical = pct >= MAIL_CRITICAL_PCT;
        out.add(AdminSignalResponse.of(key,
                critical ? AdminSignalResponse.CRITICAL : AdminSignalResponse.WARNING,
                "Plafond d'e-mails " + scope + " à " + pct + " %",
                critical
                        ? "Au plafond, plus aucun lien de réinitialisation ni de vérification ne "
                        + "part — l'envoi qu'on ne peut précisément pas perdre."
                        : "Vérifier ce qui consomme avant d'atteindre la limite.",
                "Voir la consommation", "/admin/mail", pct));
    }

    /** Clubs qui n'ont aucun coach actif, rattachement principal ou additionnel. */
    private long countClubsWithoutActiveCoach() {
        Set<UUID> withCoach = new HashSet<>(userRepository.findPrimaryClubIdsWithActiveCoach());
        withCoach.addAll(userRepository.findAdditionalClubIdsWithActiveCoach());
        return clubRepository.count() - withCoach.size();
    }

    private List<AdminIntegrationResponse> integrations(long mailToday, long mailFailed7d) {
        List<AdminIntegrationResponse> out = new ArrayList<>();

        long stravaConnections = deviceConnectionRepository.countByProvider(DeviceProvider.STRAVA);
        boolean stravaLive = stravaClient.isConfigured()
                && !stravaCallbackUrl.isBlank() && !stravaVerifyToken.isBlank();
        out.add(new AdminIntegrationResponse("strava", "Strava",
                !stravaClient.isConfigured() ? AdminIntegrationResponse.OFF
                        : stravaLive ? AdminIntegrationResponse.OK : AdminIntegrationResponse.WARNING,
                !stravaClient.isConfigured()
                        ? "Application Strava non configurée : aucun athlète ne peut connecter sa montre."
                        : stravaLive
                        ? "Remontée en direct possible (webhook configuré)."
                        : "Remontée horaire seulement : le webhook n'est pas configuré.",
                stravaConnections));

        out.add(new AdminIntegrationResponse("mail", "E-mail",
                !mailEnabled ? AdminIntegrationResponse.OFF
                        : mailFailed7d > 0 ? AdminIntegrationResponse.WARNING
                        : AdminIntegrationResponse.OK,
                !mailEnabled
                        ? "Envoi désactivé sur cette instance."
                        : mailFailed7d > 0
                        ? mailFailed7d + " échec" + plural(mailFailed7d) + " sur 7 jours."
                        : "Envois acceptés, aucun échec sur 7 jours.",
                mailToday));

        long pushSubscriptions = pushSubscriptionRepository.count();
        out.add(new AdminIntegrationResponse("push", "Notifications push",
                pushSubscriptions > 0 ? AdminIntegrationResponse.OK : AdminIntegrationResponse.OFF,
                pushSubscriptions > 0
                        ? "Abonnements actifs sur les appareils des utilisateurs."
                        : "Aucun appareil abonné : identité VAPID absente, ou personne n'a encore "
                        + "accepté les notifications.",
                pushSubscriptions));

        return out;
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case AdminSignalResponse.CRITICAL -> 0;
            case AdminSignalResponse.WARNING -> 1;
            default -> 2;
        };
    }

    private static String plural(long n) {
        return n > 1 ? "s" : "";
    }
}
