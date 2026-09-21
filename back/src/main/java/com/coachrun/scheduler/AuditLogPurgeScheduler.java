package com.coachrun.scheduler;

import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditScope;
import com.coachrun.repository.AdminAuditLogRepository;
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
 * Purge des <b>traces d'accès</b> du journal au-delà de la durée de conservation.
 *
 * <h2>Pourquoi cette tâche est arrivée avec les connexions</h2>
 *
 * <p>Le journal n'avait jamais eu besoin de purge : quelques gestes d'administration par semaine
 * tiennent des années sans peser. Consigner les connexions change l'ordre de grandeur — une seule
 * journée d'usage produit plus de lignes qu'une année de back-office. Garder indéfiniment qui
 * s'est connecté, quand et depuis quelle adresse, c'est constituer un historique de présence dont
 * personne n'a l'usage passé quelques mois, et que la CNIL recommande justement de borner (six
 * mois à un an pour des données de connexion). Un an par défaut : de quoi couvrir un contrôle ou
 * un litige, pas de quoi tracer une vie.</p>
 *
 * <h2>Ce qui n'est pas purgé, et pourquoi</h2>
 *
 * <p>Seule la portée {@link AdminAuditScope#SECURITY} est concernée. Les gestes d'administration,
 * les exercices de droits RGPD et les gestes de coaching restent : ils sont rares, ils ne pèsent
 * rien, et ce sont eux qu'on vient chercher des années plus tard — « qui a supprimé ce club ? »,
 * « quand ce consentement a-t-il été retiré ? ». Effacer la réponse à ces questions au bout d'un
 * an rendrait le journal inutile au moment précis où il sert.</p>
 *
 * <p>Réglage à zéro = aucune purge, pour qui a d'autres obligations de conservation. Tourne la
 * nuit et sous verrou, comme les autres purges : plusieurs instances ne doivent pas s'en charger
 * en même temps.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogPurgeScheduler {

    private final AdminAuditLogRepository repository;
    private final ClockService clock;

    @Value("${app.audit.access-retention-days:365}")
    private int retentionDays;

    @Scheduled(cron = "${app.audit.purge-cron:0 15 4 * * *}")
    @SchedulerLock(name = "purgeAuditLog", lockAtLeastFor = "PT1M", lockAtMostFor = "PT15M")
    @Transactional
    public void purge() {
        if (retentionDays <= 0) {
            return;
        }
        Instant cutoff = clock.today().minusDays(retentionDays)
                .atStartOfDay(clock.zone()).toInstant();
        long removed = repository.deleteByActionInAndOccurredAtBefore(
                AdminAuditAction.inScope(AdminAuditScope.SECURITY), cutoff);
        if (removed > 0) {
            log.info("Journal d'accès purgé : {} ligne(s) antérieure(s) à {}", removed, cutoff);
        }
    }
}
