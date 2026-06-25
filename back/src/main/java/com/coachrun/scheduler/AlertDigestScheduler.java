package com.coachrun.scheduler;

import com.coachrun.dto.response.CoachAlertResponse;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.repository.ClubRepository;
import com.coachrun.service.CoachDashboardService;
import com.coachrun.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Digest quotidien d'alertes : pour chaque club, regroupe les alertes actionnables par coach
 * <strong>référent</strong> et lui envoie un récapitulatif (push + email, sans donnée de santé).
 * C'est le branchement des signaux du tableau de bord (Chantier 3) sur le canal de notification.
 * No-op tant que {@code MAIL_ENABLED=false} (email) ; le push dépend des souscriptions.
 * Mono-instance pour le MVP (passer à ShedLock en cas de scale-out).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDigestScheduler {

    private final ClubRepository clubRepository;
    private final CoachDashboardService dashboardService;
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.alerts.digest-cron:0 0 7 * * *}")
    @Transactional(readOnly = true)
    public void sendDailyAlertDigests() {
        int clubsWithAlerts = 0;
        for (Club club : clubRepository.findAll()) {
            List<CoachAlertResponse> alerts = dashboardService.alerts(club.getId(), "all", null);
            if (alerts.isEmpty()) {
                continue;
            }
            clubsWithAlerts++;

            Map<UUID, List<CoachAlertResponse>> byCoach = new LinkedHashMap<>();
            Map<UUID, User> coaches = new HashMap<>();
            for (CoachAlertResponse a : alerts) {
                notificationService.referentCoach(a.athleteId(), club.getId()).ifPresent(coach -> {
                    byCoach.computeIfAbsent(coach.getId(), k -> new ArrayList<>()).add(a);
                    coaches.putIfAbsent(coach.getId(), coach);
                });
            }
            byCoach.forEach((coachId, list) ->
                    notificationService.notifyCoachAlertDigest(coaches.get(coachId), list));
        }
        if (clubsWithAlerts > 0) {
            log.info("Digest d'alertes envoyé pour {} club(s).", clubsWithAlerts);
        }
    }
}
