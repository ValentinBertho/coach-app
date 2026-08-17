package com.coachrun.scheduler;

import com.coachrun.service.ProposalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Périme chaque nuit les propositions que le temps a dépassées.
 *
 * <h2>Pourquoi ce n'est pas un refus</h2>
 *
 * <p>Une proposition d'alléger la séance de mardi n'a plus d'objet mercredi : personne ne l'a
 * refusée, le jour est simplement passé. La marquer « refusée » salirait une information qu'on
 * veut garder propre — trois refus de la même suggestion doivent vouloir dire que le coach n'en
 * veut pas, pas qu'il a oublié de répondre trois fois.</p>
 *
 * <p>La laisser en attente serait pire : la file du matin se remplirait de décisions sans objet, et
 * une file qu'on ne peut pas vider cesse d'être lue.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProposalExpiryScheduler {

    private final ProposalService proposalService;

    /** Avant le digest de 7 h : la file que le coach ouvre le matin est déjà à jour. */
    @Scheduled(cron = "${app.proposals.expiry-cron:0 15 4 * * *}")
    @SchedulerLock(name = "expireStaleProposals", lockAtMostFor = "PT10M")
    public void expireStaleProposals() {
        int expired = proposalService.expireStale();
        if (expired > 0) {
            log.info("Propositions périmées : {}", expired);
        }
    }
}
