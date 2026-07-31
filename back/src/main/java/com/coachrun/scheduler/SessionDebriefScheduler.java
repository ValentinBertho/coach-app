package com.coachrun.scheduler;

import com.coachrun.entity.ScheduledStrengthSession;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.ClockService;
import com.coachrun.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Rappel de débriefing : « Ta séance est finie ? », 2 h après l'heure habituelle de séance de
 * l'athlète, avec le RPE en actions rapides.
 *
 * <p>Thèse du produit : <em>un retour non rempli est un bug produit, pas une négligence de
 * l'athlète</em>. Le rattrapage à 7 jours répare l'oubli après coup ; ce rappel s'adresse au
 * moment où le ressenti est encore juste — juste après l'effort, quand l'athlète a son
 * téléphone en main.</p>
 *
 * <p>Tourne toutes les heures et ne retient que les athlètes dont l'heure habituelle + 2 h
 * tombe dans l'heure courante : chacun est notifié à son propre rythme, pas à une heure de
 * club unique. Un athlète qui a déjà donné son ressenti n'est jamais relancé.</p>
 *
 * <p>Mono-instance pour le MVP, comme les autres schedulers (passer à ShedLock en cas de
 * scale-out : deux instances enverraient le rappel en double).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionDebriefScheduler {

    /** Délai entre la fin supposée de la séance et le rappel. */
    private static final int DEBRIEF_DELAY_HOURS = 2;

    private final WorkoutRepository workoutRepository;
    private final ScheduledStrengthSessionRepository strengthRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ClockService clock;

    @Value("${app.debrief.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${app.debrief.cron:0 5 * * * *}")
    @Transactional(readOnly = true)
    public void sendDebriefReminders() {
        if (!enabled) {
            return;
        }
        LocalDate today = clock.today();
        int currentHour = clock.now().getHour();
        Set<UUID> notifiedUsers = new HashSet<>();
        int sent = 0;

        // Course : la feuille de ressenti attend un RPE, d'où les actions rapides.
        for (Workout w : workoutRepository.findByScheduledDateAndStatus(today, WorkoutStatus.PLANNED)) {
            User athleteUser = debriefTarget(w.getAthlete().getId(), currentHour);
            if (athleteUser == null || !notifiedUsers.add(athleteUser.getId())) {
                continue;
            }
            notificationService.notifySessionDebrief(athleteUser, w.getTitle(),
                    "/athlete/today?feedback=" + w.getId());
            sent++;
        }

        // Force : même rappel, mais il ouvre le mode séance — c'est là que se saisissent les
        // séries, et le ressenti se donne à la fin du parcours guidé.
        for (ScheduledStrengthSession s : strengthRepository.findByScheduledDateAndCompletedFalse(today)) {
            User athleteUser = debriefTarget(s.getAthlete().getId(), currentHour);
            if (athleteUser == null || !notifiedUsers.add(athleteUser.getId())) {
                continue;
            }
            notificationService.notifySessionDebrief(athleteUser, s.getTitle(),
                    "/athlete/session/" + s.getId() + "?debrief=1");
            sent++;
        }

        if (sent > 0) {
            log.info("Rappels de débriefing envoyés : {} (heure {}h)", sent, currentHour);
        }
    }

    /**
     * Compte athlète à relancer maintenant, ou {@code null}. Une heure habituelle nulle vaut
     * opt-out, et le push coupé vaut refus explicite : on ne contourne ni l'un ni l'autre.
     */
    private User debriefTarget(UUID athleteId, int currentHour) {
        User user = userRepository.findByAthleteId(athleteId).orElse(null);
        if (user == null || !user.isNotifyPushEnabled() || user.getUsualSessionTime() == null) {
            return null;
        }
        int debriefHour = (user.getUsualSessionTime().getHour() + DEBRIEF_DELAY_HOURS) % 24;
        return debriefHour == currentHour ? user : null;
    }
}
