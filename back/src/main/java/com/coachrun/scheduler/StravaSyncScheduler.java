package com.coachrun.scheduler;

import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.integration.StravaClient;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.DeviceConnectionRepository.AthleteClubIds;
import com.coachrun.service.StravaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Synchronisation Strava planifiée : importe périodiquement les nouvelles activités de chaque
 * athlète connecté (import incrémental déjà géré par {@link StravaService} via le watermark
 * {@code lastImportEpoch} + rafraîchissement de jeton). Évite l'import manuel et fiabilise les
 * données réelles (charge/analytics). No-op si Strava n'est pas configuré. Mono-instance (MVP).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StravaSyncScheduler {

    private final StravaClient client;
    private final DeviceConnectionRepository connectionRepository;
    private final StravaService stravaService;

    @Scheduled(cron = "${app.strava.sync-cron:0 30 * * * *}")
    public void syncConnectedAthletes() {
        if (!client.isConfigured()) {
            return; // intégration non configurée → rien à faire
        }
        List<AthleteClubIds> connections =
                connectionRepository.findAthleteClubIdsByProvider(DeviceProvider.STRAVA);
        int total = 0;
        int failures = 0;
        for (AthleteClubIds c : connections) {
            try {
                // Tx par athlète (méthode du bean StravaService) : un échec n'affecte pas les autres.
                total += stravaService.importActivities(c.getClubId(), c.getAthleteId());
            } catch (RuntimeException ex) {
                failures++;
                log.warn("Synchro Strava échouée pour l'athlète {} : {}", c.getAthleteId(), ex.getMessage());
            }
        }
        if (!connections.isEmpty()) {
            log.info("Synchro Strava : {} activité(s) importée(s) pour {} athlète(s) ({} échec(s)).",
                    total, connections.size(), failures);
        }
    }
}
