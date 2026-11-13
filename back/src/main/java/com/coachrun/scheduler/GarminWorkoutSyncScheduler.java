package com.coachrun.scheduler;

import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.integration.GarminClient;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.DeviceConnectionRepository.AthleteClubIds;
import com.coachrun.service.GarminWorkoutSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Passe planifiée d'envoi des séances vers Garmin : pour chaque athlète relié, met son calendrier
 * Garmin en accord avec ses séances course à venir (cf. {@link GarminWorkoutSyncService}).
 *
 * <p>Toutes les 15 minutes par défaut : une séance assignée par le coach est sur la montre à la
 * synchronisation suivante de celle-ci. La passe ne coûte aucun appel Garmin tant que rien n'a
 * changé. Verrou distribué : deux instances enverraient deux fois la même séance.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GarminWorkoutSyncScheduler {

    private final GarminClient client;
    private final DeviceConnectionRepository connectionRepository;
    private final GarminWorkoutSyncService syncService;

    @Scheduled(cron = "${app.garmin.workout-sync-cron:0 */15 * * * *}")
    @SchedulerLock(name = "garminWorkoutSync", lockAtLeastFor = "PT1M", lockAtMostFor = "PT14M")
    public void syncConnectedAthletes() {
        if (!client.isConfigured()) {
            return;
        }
        int created = 0;
        int updated = 0;
        int removed = 0;
        int failed = 0;
        for (AthleteClubIds c : connectionRepository.findAthleteClubIdsByProvider(DeviceProvider.GARMIN)) {
            try {
                GarminWorkoutSyncService.Result r = syncService.syncAthlete(c.getAthleteId());
                created += r.created();
                updated += r.updated();
                removed += r.removed();
                failed += r.failed();
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Envoi Garmin échoué pour l'athlète {} : {}", c.getAthleteId(), ex.getMessage());
            }
        }
        if (created + updated + removed + failed > 0) {
            log.info("Envoi Garmin : {} créée(s), {} mise(s) à jour, {} retirée(s), {} échec(s).",
                    created, updated, removed, failed);
        }
    }
}
