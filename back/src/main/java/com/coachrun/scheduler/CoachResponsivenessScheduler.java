package com.coachrun.scheduler;

import com.coachrun.service.CoachResponsivenessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Recalcule chaque nuit la réactivité affichée des coachs.
 *
 * <p>La nuit, et non à chaque décision : c'est une donnée d'affichage, et la recalculer dans la
 * transaction d'acceptation ferait payer au geste le plus important du produit le coût d'une
 * statistique. Un délai affiché avec un jour de retard n'a jamais trompé personne.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoachResponsivenessScheduler {

    private final CoachResponsivenessService service;

    @Scheduled(cron = "${app.coach-responsiveness.cron:0 40 4 * * *}")
    @SchedulerLock(name = "refreshCoachResponsiveness", lockAtMostFor = "PT15M")
    public void refresh() {
        service.refreshAll();
    }
}
