package com.coachrun.scheduler;

import com.coachrun.service.InactiveAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Applique la durée de conservation annoncée : un compte resté inactif vingt-quatre mois est
 * supprimé, après un e-mail de préavis (L-20 du plan de conformité).
 *
 * <h2>Deux passes, dans cet ordre</h2>
 *
 * <ol>
 *   <li><b>Prévenir</b> — les comptes qui approchent de l'échéance, à un préavis près, reçoivent
 *       un e-mail et la date d'envoi est notée. Rien d'autre ne se produit ce jour-là.</li>
 *   <li><b>Supprimer</b> — les comptes qui ont dépassé l'échéance <i>et</i> dont le préavis
 *       remonte à au moins le délai annoncé sont effacés.</li>
 * </ol>
 *
 * <p>Séparer les deux garantit ce que la seconde ne pourrait pas garantir seule : personne n'est
 * supprimé sans avoir été prévenu, <b>même si le planificateur est resté à l'arrêt des mois</b> —
 * le préavis ne commence à courir qu'une fois envoyé, pas quand il aurait dû l'être.</p>
 *
 * <p><b>Se connecter suffit à tout annuler.</b> Une visite repousse {@code last_seen_at} au-delà
 * de la date de préavis, et le compte cesse d'être candidat des deux côtés. Rien à effacer, rien
 * à réarmer — donc aucun chemin où l'oublier.</p>
 *
 * <p>Sous verrou comme les autres planificateurs : deux instances ne doivent pas envoyer deux
 * préavis, ni se disputer les mêmes suppressions.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InactiveAccountPurgeScheduler {

    private final InactiveAccountService accounts;

    /**
     * Interrupteur d'exploitation. À {@code false}, la tâche ne lit ni n'écrit rien — de quoi
     * suspendre le dispositif le temps d'un incident, sans redéployer.
     */
    @Value("${app.accounts.inactivity.enabled:true}")
    private boolean enabled;

    /** Durée d'inactivité au-delà de laquelle un compte est supprimé. 730 jours = 24 mois. */
    @Value("${app.accounts.inactivity.retention-days:730}")
    private int retentionDays;

    /** Délai entre le préavis et la suppression. */
    @Value("${app.accounts.inactivity.notice-days:30}")
    private int noticeDays;

    /**
     * Plafond de comptes traités par passage, pour chaque phase.
     *
     * <p>Le premier passage en production rattrape d'un coup tout l'arriéré : sans plafond, il
     * enverrait des centaines d'e-mails en quelques secondes — sur un plan d'envoi qui en compte
     * cent par jour, partagé avec les réinitialisations de mot de passe. Le reliquat est repris
     * le lendemain, et les comptes sont pris du plus ancien au plus récent : l'ordre est stable,
     * personne n'est laissé indéfiniment de côté.</p>
     */
    @Value("${app.accounts.inactivity.max-per-run:100}")
    private int maxPerRun;

    /** Bilan d'un passage. Retourné pour les tests, journalisé en production. */
    public record Outcome(int warned, int purged, int kept) {
    }

    @Scheduled(cron = "${app.accounts.inactivity.cron:0 20 4 * * *}")
    @SchedulerLock(name = "purgeInactiveAccounts", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void run() {
        runAt(Instant.now());
    }

    /**
     * Le passage, à une date donnée. Séparé de {@link #run()} pour que les tests le jouent sans
     * attendre 4 h 20 : l'instant est la seule chose qui distingue un passage de nuit d'un
     * passage de test.
     */
    public Outcome runAt(Instant now) {
        if (!enabled) {
            return new Outcome(0, 0, 0);
        }
        int retention = Math.max(1, retentionDays);
        // Un préavis plus long que la durée de conservation elle-même n'a pas de sens : il
        // prévient un compte avant même qu'il ait commencé à dormir.
        int notice = Math.min(Math.max(1, noticeDays), retention - 1);

        int warned = 0;
        for (UUID id : accounts.findToWarn(now, retention, notice, maxPerRun)) {
            try {
                if (accounts.warn(id, now, retention, notice)) {
                    warned++;
                }
            } catch (RuntimeException ex) {
                log.warn("Préavis d'inactivité non envoyé au compte {} : {}", id, ex.getMessage());
            }
        }

        int purged = 0;
        int kept = 0;
        for (UUID id : accounts.findToPurge(now, retention, notice, maxPerRun)) {
            try {
                if (accounts.purge(id)) {
                    purged++;
                } else {
                    kept++;
                }
            } catch (RuntimeException ex) {
                kept++;
                log.warn("Compte inactif {} non supprimé : {}", id, ex.getMessage());
            }
        }

        if (warned > 0 || purged > 0 || kept > 0) {
            log.info("Comptes inactifs : {} préavis, {} suppression(s), {} conservé(s) "
                            + "(seuil {} j, préavis {} j)",
                    warned, purged, kept, retention, notice);
        }
        return new Outcome(warned, purged, kept);
    }
}
