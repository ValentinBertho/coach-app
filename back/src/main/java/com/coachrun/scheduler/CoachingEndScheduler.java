package com.coachrun.scheduler;

import com.coachrun.service.ClockService;
import com.coachrun.service.CoachingRelationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Les fins de coaching annoncées par un coach : le rappel de la veille, puis la fin elle-même à la
 * date prévue. Toutes les heures, pour qu'une fin annoncée tombe le jour dit.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoachingEndScheduler {

    private final CoachingRelationService relations;
    private final ClockService clock;

    @Scheduled(cron = "${app.coaching.end-cron:0 7 * * * *}")
    @SchedulerLock(name = "coachingPlannedEnds", lockAtLeastFor = "PT1M", lockAtMostFor = "PT20M")
    public void run() {
        int reminded = relations.remindDue(clock.instant());
        int ended = relations.endDue(clock.instant());
        if (reminded + ended > 0) {
            log.info("Fins de coaching : {} rappel(s), {} fin(s) prononcée(s).", reminded, ended);
        }
    }
}
