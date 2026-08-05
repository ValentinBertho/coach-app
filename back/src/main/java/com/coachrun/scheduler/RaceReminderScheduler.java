package com.coachrun.scheduler;

import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.enums.RaceObjectiveStatus;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.service.ClockService;
import com.coachrun.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Rappels de course : J-7 puis J-1.
 *
 * <p>La date d'un objectif dormait dans une fiche que personne n'ouvre la veille. C'est pourtant
 * la seule échéance du produit qui ne se déplace pas : le dossard est pris, la ligne de départ
 * n'attend pas. Deux rappels, et deux seulement — <b>J-7</b>, le moment de l'affûtage et des
 * dernières décisions d'entraînement ; <b>J-1</b>, celui de la logistique.</p>
 *
 * <p>Une course annulée ou déjà courue n'est pas rappelée : seul le statut {@code UPCOMING}
 * compte.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RaceReminderScheduler {

    /** Les deux échéances qui appellent une action, en jours avant la course. */
    private static final int[] LEAD_DAYS = {7, 1};

    private final RaceObjectiveRepository raceRepository;
    private final NotificationService notificationService;
    private final ClockService clock;

    @Scheduled(cron = "${app.races.reminder-cron:0 0 8 * * *}")
    @SchedulerLock(name = "sendRaceReminders", lockAtLeastFor = "PT5M", lockAtMostFor = "PT30M")
    @Transactional
    public void sendRaceReminders() {
        LocalDate today = clock.today();
        int sent = 0;
        for (int lead : LEAD_DAYS) {
            for (RaceObjective race : raceRepository.findByStatusAndRaceDate(
                    RaceObjectiveStatus.UPCOMING, today.plusDays(lead))) {
                try {
                    notificationService.notifyRaceApproaching(race.getAthlete(), race.getName(), lead);
                    sent++;
                } catch (RuntimeException ex) {
                    // Un athlète en erreur n'emporte pas les autres : c'est un service quotidien,
                    // il vaut mieux qu'il soit partiel qu'absent.
                    log.error("Rappel de course en échec pour {} — les autres continuent",
                            race.getId(), ex);
                    io.sentry.Sentry.captureException(ex);
                }
            }
        }
        if (sent > 0) {
            log.info("Rappels de course envoyés : {}", sent);
        }
    }
}
