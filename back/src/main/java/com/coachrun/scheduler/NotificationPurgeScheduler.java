package com.coachrun.scheduler;

import com.coachrun.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Purge du centre de notifications.
 *
 * <p>La table {@code notifications} n'avait aucune rétention, alors que tout le reste du produit
 * en a une — les traces du digest sont purgées quotidiennement, les jetons expirent. Chaque
 * séance planifiée, chaque rappel, chaque message y laisse une ligne : pour un club de 240
 * athlètes, l'ordre de grandeur est de plusieurs dizaines de milliers de lignes par mois, gardées
 * indéfiniment, et relues par la pagination de chaque ouverture de la cloche.</p>
 *
 * <p>Quatre-vingt-dix jours : au-delà, une notification n'est plus consultée — elle porte un lien
 * vers une séance passée ou un fil déjà lu. Ce n'est pas l'historique d'entraînement, qui vit dans
 * ses propres tables et n'est pas concerné ; c'est la liste des interruptions passées.</p>
 *
 * <p>Mono-instance comme les autres planificateurs (passer à ShedLock en cas de scale-out — ici le
 * risque est bénin : deux instances supprimeraient les mêmes lignes).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPurgeScheduler {

    private final NotificationRepository notificationRepository;

    /** Âge au-delà duquel une notification est retirée du centre. */
    @Value("${app.notifications.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "${app.notifications.purge-cron:0 45 3 * * *}")
    @SchedulerLock(name = "purgeOldNotifications", lockAtMostFor = "PT10M")
    @Transactional
    public void purgeOldNotifications() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(1, retentionDays)));
        int removed = notificationRepository.deleteByCreatedAtBefore(cutoff);
        if (removed > 0) {
            log.info("Centre de notifications purgé : {} ligne(s) antérieure(s) à {}", removed, cutoff);
        }
    }
}
