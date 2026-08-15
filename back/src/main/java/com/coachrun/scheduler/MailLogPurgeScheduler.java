package com.coachrun.scheduler;

import com.coachrun.repository.MailLogRepository;
import com.coachrun.service.ClockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Purge du journal des e-mails au-delà de la durée de conservation.
 *
 * <p><b>Pourquoi c'est une tâche et pas une option.</b> Le journal porte des adresses e-mail,
 * donc des données personnelles. Un journal d'exploitation se garde le temps de servir à quelque
 * chose — diagnostiquer un envoi, expliquer une facture — et pas au-delà. Six mois couvrent
 * largement les deux ; la valeur reste réglable pour qui a d'autres obligations.</p>
 *
 * <p>Tourne la nuit, quand rien d'autre ne travaille, et sous verrou : plusieurs instances ne
 * doivent pas purger en même temps.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailLogPurgeScheduler {

    private final MailLogRepository repository;
    private final ClockService clock;

    @Value("${app.mail.log-retention-days:180}")
    private int retentionDays;

    @Scheduled(cron = "${app.mail.log-purge-cron:0 30 3 * * *}")
    @SchedulerLock(name = "purgeMailLog", lockAtLeastFor = "PT1M", lockAtMostFor = "PT15M")
    @Transactional
    public void purge() {
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = clock.today().minusDays(retentionDays)
                .atStartOfDay(clock.zone()).toInstant();
        long removed = repository.deleteBySentAtBefore(cutoff);
        if (removed > 0) {
            log.info("Journal e-mail purgé : {} ligne(s) antérieure(s) à {}", removed, cutoff);
        }
    }
}
