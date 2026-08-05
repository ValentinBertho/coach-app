package com.coachrun.scheduler;

import com.coachrun.dto.response.CoachAlertResponse;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.repository.ClubRepository;
import com.coachrun.service.CoachDashboardService;
import com.coachrun.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Digest quotidien d'alertes : pour chaque club, regroupe les alertes actionnables par coach
 * <strong>référent</strong> et lui envoie un récapitulatif (push + email, sans donnée de santé).
 * C'est le branchement des signaux du tableau de bord (Chantier 3) sur le canal de notification.
 * No-op tant que {@code MAIL_ENABLED=false} (email) ; le push dépend des souscriptions.
 * Protégé par un verrou distribué : un digest envoyé deux fois est un digest qu'on cesse de lire.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDigestScheduler {

    private final ClubRepository clubRepository;
    private final CoachDashboardService dashboardService;
    private final NotificationService notificationService;
    private final com.coachrun.repository.AlertDigestLogRepository digestLogRepository;

    /**
     * Référence sur soi <b>via le proxy</b>. {@code @Transactional} est appliqué par un proxy :
     * un appel direct {@code this.digestForClub(...)} depuis la méthode planifiée le
     * court-circuiterait, et la transaction par club n'existerait tout simplement pas — un défaut
     * qui ne se voit ni à la compilation, ni dans un test qui appelle la méthode directement.
     * {@code @Lazy} rompt le cycle de construction.
     */
    private final org.springframework.beans.factory.ObjectProvider<AlertDigestScheduler> self;

    /**
     * Balayage quotidien. <b>Sans transaction</b> : c'est {@link #digestForClub(UUID)} qui en
     * ouvre une, par club.
     *
     * <p>La méthode entière était {@code @Transactional} : une seule transaction couvrait tous
     * les clubs, du premier au dernier. Trois conséquences, toutes mauvaises à mesure que la
     * cohorte grandit — une connexion Hikari retenue pendant tout le balayage ; un échec sur le
     * dernier club annulant les notifications in-app de tous les autres ; et surtout, les e-mails
     * de digest étant enregistrés en {@code afterCommit}, un seul club en difficulté suffisait à
     * ce qu'<em>aucun</em> e-mail ne parte de la journée.</p>
     *
     * <p>Un club en échec est désormais journalisé et le balayage continue : le digest est un
     * service quotidien, il vaut mieux qu'il soit partiel que absent.</p>
     */
    @Scheduled(cron = "${app.alerts.digest-cron:0 0 7 * * *}")
    @SchedulerLock(name = "sendDailyAlertDigests", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void sendDailyAlertDigests() {
        int clubsWithAlerts = 0;
        for (UUID clubId : clubRepository.findAll().stream().map(Club::getId).toList()) {
            try {
                if (self.getObject().digestForClub(clubId)) {
                    clubsWithAlerts++;
                }
            } catch (RuntimeException ex) {
                log.error("Digest d'alertes en échec pour le club {} — les autres continuent", clubId, ex);
                io.sentry.Sentry.captureException(ex);
            }
        }
        if (clubsWithAlerts > 0) {
            log.info("Digest d'alertes envoyé pour {} club(s).", clubsWithAlerts);
        }
    }

    /**
     * Digest d'un club, dans sa propre transaction (écriture : persiste les notifications in-app).
     *
     * <p>Public et appelée via le proxy Spring ({@code self}) pour que {@code @Transactional}
     * s'applique réellement : un appel direct depuis la méthode planifiée passerait à côté du
     * proxy et la transaction n'existerait pas.</p>
     *
     * @return vrai si le club avait des alertes.
     */
    @Transactional
    public boolean digestForClub(UUID clubId) {
        List<CoachAlertResponse> alerts = dashboardService.alerts(clubId, "all", null);
        if (alerts.isEmpty()) {
            return false;
        }

        Map<UUID, List<CoachAlertResponse>> byCoach = new LinkedHashMap<>();
        Map<UUID, User> coaches = new HashMap<>();
        for (CoachAlertResponse a : alerts) {
            notificationService.referentCoach(a.athleteId(), clubId).ifPresent(coach -> {
                byCoach.computeIfAbsent(coach.getId(), k -> new ArrayList<>()).add(a);
                coaches.putIfAbsent(coach.getId(), coach);
            });
        }

        boolean sent = false;
        for (Map.Entry<UUID, List<CoachAlertResponse>> entry : byCoach.entrySet()) {
            List<CoachAlertResponse> fresh = withoutRecentlySent(entry.getKey(), entry.getValue());
            if (fresh.isEmpty()) {
                continue;
            }
            notificationService.notifyCoachAlertDigest(coaches.get(entry.getKey()), fresh);
            sent = true;
        }
        return sent;
    }

    /**
     * Délai avant de rappeler une alerte déjà signalée et toujours vraie.
     *
     * <p>Sept jours : assez long pour qu'une blessure de trois semaines ne produise pas vingt et un
     * digests identiques, assez court pour qu'une situation qui s'installe revienne se rappeler au
     * coach une fois par semaine.</p>
     */
    private static final Duration REMINDER_DELAY = Duration.ofDays(7);

    /**
     * Retire les alertes déjà envoyées à ce coach et toujours dans leur délai de rappel, et note
     * les autres comme envoyées.
     *
     * <p>La clé est (coach, athlète, type) : une <em>nouvelle</em> alerte sur un athlète déjà
     * signalé passe donc immédiatement — c'est bien un fait nouveau. Une alerte qui disparaît puis
     * revient repart de zéro, sa ligne ayant été purgée entre-temps.</p>
     */
    private List<CoachAlertResponse> withoutRecentlySent(UUID coachId, List<CoachAlertResponse> alerts) {
        Instant now = Instant.now();
        Instant reminderCutoff = now.minus(REMINDER_DELAY);

        Map<String, com.coachrun.entity.AlertDigestLog> seen = new HashMap<>();
        for (var row : digestLogRepository.findByCoachId(coachId)) {
            seen.put(key(row.getAthleteId(), row.getAlertType()), row);
        }

        List<CoachAlertResponse> fresh = new ArrayList<>();
        for (CoachAlertResponse a : alerts) {
            var previous = seen.get(key(a.athleteId(), a.type()));
            if (previous != null && previous.getLastSentAt().isAfter(reminderCutoff)) {
                continue; // déjà dit, et toujours dans le délai de rappel
            }
            fresh.add(a);
            if (previous != null) {
                previous.setLastSentAt(now);
            } else {
                var row = new com.coachrun.entity.AlertDigestLog();
                row.setCoachId(coachId);
                row.setAthleteId(a.athleteId());
                row.setAlertType(a.type());
                row.setLastSentAt(now);
                digestLogRepository.save(row);
            }
        }
        return fresh;
    }

    private static String key(UUID athleteId, String type) {
        return athleteId + "|" + type;
    }

    /**
     * Purge quotidienne des traces plus anciennes que le délai de rappel : passé ce délai, elles
     * ne filtrent plus rien et une alerte qui reviendrait doit repartir de zéro.
     */
    @Scheduled(cron = "${app.alerts.digest-purge-cron:0 30 3 * * *}")
    @SchedulerLock(name = "purgeStaleDigestLog", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeStaleDigestLog() {
        digestLogRepository.deleteByLastSentAtBefore(Instant.now().minus(REMINDER_DELAY));
    }
}
