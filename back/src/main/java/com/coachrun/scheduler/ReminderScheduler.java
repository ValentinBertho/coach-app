package com.coachrun.scheduler;

import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.ClockService;
import com.coachrun.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Rappel quotidien J-1 : notifie les athlètes des séances prévues le lendemain.
 * (Mono-instance pour le MVP ; passer à ShedLock en cas de scale-out.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final WorkoutRepository workoutRepository;
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
    @Scheduled(cron = "${app.reminders.cron:0 0 18 * * *}")
    public void sendTomorrowReminders() {
        LocalDate tomorrow = clock.today().plusDays(1);
        List<UUID> workoutIds = workoutRepository
                .findByScheduledDateAndStatus(tomorrow, WorkoutStatus.PLANNED)
                .stream().map(Workout::getId).toList();
        log.info("Rappels J-1 : {} séance(s) prévue(s) le {}", workoutIds.size(), tomorrow);

        int failures = 0;
        for (UUID workoutId : workoutIds) {
            try {
                self.getObject().remindOne(workoutId);
            } catch (RuntimeException ex) {
                failures++;
                log.error("Rappel J-1 en échec pour la séance {} — les autres continuent", workoutId, ex);
                io.sentry.Sentry.captureException(ex);
            }
        }
        if (failures > 0) {
            log.warn("Rappels J-1 : {} échec(s) sur {}", failures, workoutIds.size());
        }
    }

    /**
     * Rappel d'une séance, dans sa propre transaction <b>en écriture</b> : la notification in-app
     * est persistée, le push part après commit, et l'e-mail de repli aussi.
     */
    @Transactional
    public void remindOne(UUID workoutId) {
        workoutRepository.findById(workoutId).ifPresent(notificationService::notifyWorkoutReminder);
    }
}
