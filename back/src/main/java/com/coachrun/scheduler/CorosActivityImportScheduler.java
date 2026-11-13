package com.coachrun.scheduler;

import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.integration.CorosMcpClient;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.DeviceConnectionRepository.AthleteClubIds;
import com.coachrun.service.CorosActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Import horaire des sorties COROS de chaque athlète relié : COROS n'envoie aucune notification,
 * il faut aller voir. Verrou ShedLock : une seule instance à la fois.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CorosActivityImportScheduler {

    private final CorosMcpClient client;
    private final DeviceConnectionRepository connectionRepository;
    private final CorosActivityService importService;

    @Scheduled(cron = "${app.coros.activity-import-cron:0 7 * * * *}")
    @SchedulerLock(name = "corosActivityImport", lockAtLeastFor = "PT1M", lockAtMostFor = "PT50M")
    public void importConnectedAthletes() {
        if (!client.isConfigured()) {
            return;
        }
        int imported = 0;
        int failed = 0;
        for (AthleteClubIds c : connectionRepository.findAthleteClubIdsByProvider(DeviceProvider.COROS)) {
            try {
                CorosActivityService.Result r = importService.importAthlete(c.getAthleteId());
                imported += r.imported();
                failed += r.failed();
            } catch (RuntimeException ex) {
                failed++;
                log.warn("Import COROS échoué pour l'athlète {} : {}", c.getAthleteId(), ex.getMessage());
            }
        }
        if (imported + failed > 0) {
            log.info("Import COROS : {} sortie(s) importée(s), {} échec(s).", imported, failed);
        }
    }
}
