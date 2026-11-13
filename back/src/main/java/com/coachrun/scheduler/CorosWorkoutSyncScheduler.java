package com.coachrun.scheduler;

import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.integration.CorosMcpClient;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.DeviceConnectionRepository.AthleteClubIds;
import com.coachrun.service.CorosWorkoutSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Passe planifiée d'envoi des séances vers COROS : pour chaque athlète relié, met son calendrier
 * COROS en accord avec ses séances course à venir (cf. {@link CorosWorkoutSyncService}).
 *
 * <p>Toutes les 15 minutes par défaut, comme Garmin. Aucun appel COROS tant que rien n'a changé.
 * Verrou distribué : deux instances enverraient deux fois la même séance.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorosWorkoutSyncScheduler {

    private final CorosMcpClient client;
    private final DeviceConnectionRepository connectionRepository;
    private final CorosWorkoutSyncService syncService;

    @Scheduled(cron = "${app.coros.workout-sync-cron:0 */15 * * * *}")
    @SchedulerLock(name = "corosWorkoutSync", lockAtLeastFor = "PT1M", lockAtMostFor = "PT14M")
    public void syncConnectedAthletes() {
        if (!client.isConfigured()) {
            return;
        }
        int created = 0;
        int updated = 0;
        int cancelled = 0;
        int failed = 0;
        for (AthleteClubIds c : connectionRepository.findAthleteClubIdsByProvider(DeviceProvider.COROS)) {
            try {
                CorosWorkoutSyncService.Result r = syncService.syncAthlete(c.getAthleteId());
                created += r.created();
                updated += r.updated();
                cancelled += r.cancelled();
                failed += r.failed();
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Envoi COROS échoué pour l'athlète {} : {}", c.getAthleteId(), ex.getMessage());
            }
        }
        if (created + updated + cancelled + failed > 0) {
            log.info("Envoi COROS : {} créée(s), {} réécrite(s), {} annulée(s), {} échec(s).",
                    created, updated, cancelled, failed);
        }
    }
}
