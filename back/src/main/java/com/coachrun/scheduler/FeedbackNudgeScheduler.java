package com.coachrun.scheduler;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Relance « Ta séance est finie ? », envoyée quelques heures après l'heure habituelle de séance
 * de chaque athlète.
 *
 * <p>Le rappel J-1 existant dit « tu as une séance demain » ; celui-ci dit « tu l'as faite, dis-moi
 * comment ». C'est le second qui manque : un ressenti se donne dans l'heure qui suit, pas le
 * lendemain — et un retour non rempli est un bug produit, pas une négligence de l'athlète.</p>
 *
 * <p>Le scheduler tourne toutes les heures et ne retient que les athlètes dont l'heure habituelle
 * + le décalage tombe sur l'heure courante : chacun est relancé au bon moment plutôt que tout le
 * monde en même temps. On ne relance jamais une séance déjà notée.</p>
 *
 * <p>(Mono-instance pour le MVP, comme les autres schedulers ; passer à ShedLock en cas de
 * scale-out, sinon chaque instance enverrait sa propre relance.)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeedbackNudgeScheduler {

    private final WorkoutRepository workoutRepository;
    private final NotificationService notificationService;

    /** Décalage après l'heure habituelle de séance. */
    @Value("${app.feedback-nudge.delay-hours:2}")
    private int delayHours;

    /** Heure habituelle par défaut, pour les athlètes qui n'ont rien déclaré. */
    @Value("${app.feedback-nudge.default-hour:18}")
    private int defaultHour;

    @Value("${app.feedback-nudge.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.feedback-nudge.cron:0 5 * * * *}")
    @Transactional(readOnly = true)
    public void nudgePendingFeedback() {
        if (!enabled) {
            return;
        }
        int hourNow = LocalTime.now().getHour();
        LocalDate today = LocalDate.now();

        // PLANNED seulement : une séance déjà déclarée partielle a reçu un retour, la relancer
        // reviendrait à demander deux fois la même chose.
        List<Workout> candidates = workoutRepository.findByScheduledDateAndStatus(today, WorkoutStatus.PLANNED);
        int sent = 0;
        for (Workout w : candidates) {
            if (w.getRpe() != null || w.getFatigue() != null) {
                continue; // déjà noté sans changement de statut
            }
            if (nudgeHour(w.getAthlete()) != hourNow) {
                continue;
            }
            notificationService.notifyFeedbackNudge(w);
            sent++;
        }
        if (sent > 0) {
            log.info("Relances de ressenti envoyées à {}h : {}", hourNow, sent);
        }
    }

    /** Heure de relance d'un athlète, ramenée dans [0,23] (une heure habituelle tardive déborde). */
    private int nudgeHour(Athlete athlete) {
        Integer usual = athlete.getUsualSessionHour();
        int base = usual != null ? usual : defaultHour;
        return Math.floorMod(base + delayHours, 24);
    }
}
