package com.coachrun.service;

import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.repository.ClubRepository;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Supprime les comptes d'athlète inscrits seuls et jamais confirmés.
 *
 * <p>L'inscription libre d'un athlète crée un compte <b>avant</b> la vérification de l'adresse —
 * c'est voulu, pour ne pas perdre la personne le temps qu'elle relève ses e-mails. Le revers : un
 * robot, ou quelqu'un qui saisit l'adresse d'un autre, laisse derrière lui un compte que personne
 * ne confirmera jamais. Ces comptes ne servent à rien, retiennent une adresse e-mail (que son vrai
 * titulaire ne pourrait plus utiliser pour s'inscrire) et des données personnelles sans
 * consentement vérifié. Passé le délai du lien de vérification, ils sont supprimés.</p>
 *
 * <p>Seuls les comptes sans autre fiche que leur espace personnel (qui part avec eux), sans connexion récente, sans demande de coaching ni
 * signalement sont visés (cf. {@link AthleteAccountRepository#findAbandonedUnverifiedUserIds}).
 * Les comptes de coach ne le sont jamais : ils portent un club.</p>
 */
@Slf4j
@Service
public class UnverifiedAccountPurgeService {

    private final AthleteAccountRepository accountRepository;
    private final ClubRepository clubRepository;
    private final TransactionTemplate tx;
    private final int retentionDays;

    public UnverifiedAccountPurgeService(
            AthleteAccountRepository accountRepository,
            ClubRepository clubRepository,
            PlatformTransactionManager txManager,
            @Value("${app.account.unverified-retention-days:7}") int retentionDays) {
        this.accountRepository = accountRepository;
        this.clubRepository = clubRepository;
        this.tx = new TransactionTemplate(txManager);
        this.retentionDays = Math.max(1, retentionDays);
    }

    @Scheduled(cron = "${app.account.unverified-purge-cron:0 23 4 * * *}")
    @SchedulerLock(name = "purgeUnverifiedAccounts", lockAtLeastFor = "PT1M", lockAtMostFor = "PT30M")
    public void purgeScheduled() {
        purge(Instant.now());
    }

    /** @return nombre de comptes supprimés. */
    public int purge(Instant now) {
        Instant cutoff = now.minus(retentionDays, ChronoUnit.DAYS);
        List<UUID> abandoned = accountRepository.findAbandonedUnverifiedUserIds(cutoff, now);
        int deleted = 0;
        for (UUID userId : abandoned) {
            try {
                // Une transaction par compte : un compte qui résiste n'empêche pas les autres.
                tx.executeWithoutResult(status -> {
                    accountRepository.deletePushSubscriptionsOf(userId);
                    // L'espace personnel d'abord : sa fiche pointe vers le compte.
                    List<UUID> personal = accountRepository.findPersonalClubIdsOfUser(userId);
                    accountRepository.detachUserNative(userId);
                    personal.forEach(clubRepository::hardDelete);
                    accountRepository.deleteByUserIdNative(userId);
                    accountRepository.deleteUserNative(userId);
                });
                deleted++;
            } catch (RuntimeException e) {
                log.warn("Suppression impossible du compte non vérifié {} : {}", userId, e.getMessage());
            }
        }
        if (deleted > 0) {
            log.info("{} compte(s) d'athlète jamais confirmé(s) supprimé(s) après {} jours.",
                    deleted, retentionDays);
        }
        return deleted;
    }
}
