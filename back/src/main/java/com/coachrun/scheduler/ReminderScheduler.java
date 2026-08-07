package com.coachrun.scheduler;

import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.ClockService;
import com.coachrun.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rappel quotidien J-1 : notifie les athlètes des séances prévues le lendemain.
 * Protégé par un verrou distribué : à deux instances, l'athlète recevrait son rappel en double.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    /**
     * Fenêtre au-delà de laquelle un athlète n'est plus considéré comme suivi : sans séance ni
     * avant ni après, il n'attend pas de point de programme quotidien.
     */
    private static final int LIVE_PROGRAM_DAYS = 14;

    private final WorkoutRepository workoutRepository;
    private final com.coachrun.repository.AthleteRepository athleteRepository;
    private final NotificationService notificationService;
    private final ClockService clock;
    /** Passage par le proxy : sans lui, {@code @Transactional} ne s'appliquerait pas (cf. plus bas). */
    private final ObjectProvider<ReminderScheduler> self;

    /**
     * Balayage du lendemain, une transaction par séance.
     *
     * <p>Deux défauts corrigés ici. Le premier était silencieux et coûtait une fonctionnalité
     * entière : la méthode portait {@code @Transactional(readOnly = true)}, or
     * {@code NotificationService.record()} y <b>écrit</b> la notification in-app. Une transaction
     * en lecture seule met Hibernate en {@code FlushMode.MANUAL} et ne vide jamais le contexte de
     * persistance au commit — l'insertion était donc perdue, sans erreur, et le rappel « Séance
     * demain » n'apparaissait jamais dans le centre de notifications. Seul le push partait, ce qui
     * rendait le défaut invisible pour qui avait accepté les notifications système.</p>
     *
     * <p>Le second est de robustesse : une seule transaction couvrait toutes les séances de tous
     * les clubs. Un athlète en erreur emportait le lot entier. Chaque rappel est maintenant
     * indépendant — c'est un service quotidien, il vaut mieux qu'il soit partiel qu'absent.</p>
     */
    @Scheduled(cron = "${app.reminders.cron:0 0 21 * * *}", zone = "${app.timezone:Europe/Paris}")
    @SchedulerLock(name = "sendTomorrowReminders", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    public void sendTomorrowReminders() {
        LocalDate tomorrow = clock.today().plusDays(1);
        // Groupé par athlète : deux séances demain font un rappel, pas deux notifications
        // consécutives disant la même chose.
        Map<UUID, List<UUID>> byAthlete = new LinkedHashMap<>();
        for (Workout w : workoutRepository.findByScheduledDateAndStatus(tomorrow, WorkoutStatus.PLANNED)) {
            byAthlete.computeIfAbsent(w.getAthlete().getId(), k -> new ArrayList<>()).add(w.getId());
        }
        List<UUID> resting = restingAthletes(byAthlete.keySet());
        log.info("Point de programme : {} athlète(s) avec séance, {} au repos, pour le {}",
                byAthlete.size(), resting.size(), tomorrow);

        int failures = 0;
        for (Map.Entry<UUID, List<UUID>> entry : byAthlete.entrySet()) {
            try {
                self.getObject().remindAthlete(entry.getValue());
            } catch (RuntimeException ex) {
                failures++;
                log.error("Rappel J-1 en échec pour l'athlète {} — les autres continuent",
                        entry.getKey(), ex);
                io.sentry.Sentry.captureException(ex);
            }
        }
        for (UUID athleteId : resting) {
            try {
                self.getObject().announceRestDay(athleteId);
            } catch (RuntimeException ex) {
                failures++;
                log.error("Annonce de repos en échec pour l'athlète {} — les autres continuent",
                        athleteId, ex);
                io.sentry.Sentry.captureException(ex);
            }
        }
        if (failures > 0) {
            log.warn("Point de programme : {} échec(s) sur {}",
                    failures, byAthlete.size() + resting.size());
        }
    }

    /**
     * Athlètes à prévenir qu'ils ne s'entraînent pas demain.
     *
     * <p>Deux filtres, et chacun évite une nuisance. On ne retient que les athlètes ayant un
     * programme <b>vivant</b> — au moins une séance dans la quinzaine écoulée ou à venir : sans
     * cela, chaque compte dormant recevrait « Repos demain » toutes les nuits, indéfiniment. Et on
     * retire évidemment ceux qui ont déjà une séance annoncée pour demain. Le statut de l'athlète
     * est vérifié au moment de l'envoi, quand son dossier est chargé.</p>
     */
    private List<UUID> restingAthletes(java.util.Set<UUID> withSessionTomorrow) {
        LocalDate today = clock.today();
        return workoutRepository
                .findAthleteIdsWithWorkoutsBetween(today.minusDays(LIVE_PROGRAM_DAYS),
                        today.plusDays(LIVE_PROGRAM_DAYS))
                .stream()
                .filter(id -> !withSessionTomorrow.contains(id))
                .toList();
    }

    /**
     * Rappel des séances d'un athlète, dans sa propre transaction <b>en écriture</b> : la
     * notification in-app est persistée, le push part après commit, et l'e-mail de repli aussi.
     */
    @Transactional
    public void remindAthlete(List<UUID> workoutIds) {
        List<Workout> workouts = workoutRepository.findAllById(workoutIds);
        if (!workouts.isEmpty()) {
            notificationService.notifyWorkoutReminder(workouts);
        }
    }

    /**
     * Annonce de repos, dans sa propre transaction comme les rappels de séance. Un athlète en
     * pause ou archivé ne reçoit rien : son programme s'est arrêté, pas seulement sa journée.
     */
    @Transactional
    public void announceRestDay(UUID athleteId) {
        athleteRepository.findById(athleteId)
                .filter(a -> a.getStatus() == com.coachrun.entity.enums.AthleteStatus.ACTIVE)
                .ifPresent(notificationService::notifyRestDay);
    }
}
