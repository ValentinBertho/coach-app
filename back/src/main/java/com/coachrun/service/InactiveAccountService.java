package com.coachrun.service;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * La politique de conservation des comptes, appliquée : vingt-quatre mois d'inactivité, un e-mail
 * de préavis, puis la suppression.
 *
 * <h2>Pourquoi ce service existe</h2>
 *
 * <p>La politique de confidentialité l'annonce depuis l'origine et rien ne l'appliquait (L-20 du
 * plan de conformité). Un texte publié qui promet une durée de conservation que le code ne tient
 * pas n'est pas une imprécision de rédaction : c'est l'engagement lui-même qui est faux, et il
 * est opposable. L'écart ne se voyait pas tant qu'aucun compte ne pouvait atteindre l'échéance ;
 * il ne serait devenu visible qu'au jour où le premier l'atteint — trop tard pour le combler.</p>
 *
 * <h2>Pourquoi il est séparé du planificateur</h2>
 *
 * <p>Chaque compte est traité dans <b>sa propre transaction</b>, et une transaction ne s'ouvre
 * que si l'appel traverse le proxy Spring : une méthode {@code @Transactional} appelée depuis la
 * même classe ne l'est pas. Un e-mail refusé pour une adresse invalide ne doit ni annuler les
 * préavis déjà notés, ni faire recommencer le lot le lendemain — sans quoi les comptes situés
 * derrière le fautif ne seraient jamais prévenus, et finiraient supprimés sans l'avoir été.</p>
 *
 * @see com.coachrun.scheduler.InactiveAccountPurgeScheduler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InactiveAccountService {

    private final UserRepository userRepository;
    private final GdprService gdprService;
    private final NotificationService notificationService;
    private final ClockService clock;

    /** Comptes qui approchent de l'échéance et n'ont pas encore été prévenus pour celle-ci. */
    @Transactional(readOnly = true)
    public List<UUID> findToWarn(Instant now, int retentionDays, int noticeDays, int limit) {
        Instant threshold = now.minus(Duration.ofDays((long) retentionDays - noticeDays));
        return userRepository.findInactiveToWarn(threshold, PageRequest.of(0, limit))
                .stream().map(User::getId).toList();
    }

    /** Comptes au-delà de l'échéance dont le préavis a couru jusqu'au bout. */
    @Transactional(readOnly = true)
    public List<UUID> findToPurge(Instant now, int retentionDays, int noticeDays, int limit) {
        return userRepository.findInactiveToPurge(
                        now.minus(Duration.ofDays(retentionDays)),
                        now.minus(Duration.ofDays(noticeDays)),
                        PageRequest.of(0, limit))
                .stream().map(User::getId).toList();
    }

    /**
     * Envoie le préavis à un compte et note la date d'envoi.
     *
     * <p>La date annoncée dans l'e-mail est celle qui sera <b>réellement</b> appliquée : l'échéance
     * d'inactivité, ou la fin du préavis si celle-ci tombe plus tard. Annoncer la première quand
     * c'est la seconde qui décide ferait mentir le seul avertissement que recevra quelqu'un qui
     * n'ouvre plus l'application depuis deux ans.</p>
     *
     * @return {@code true} si un préavis a été envoyé
     */
    @Transactional
    public boolean warn(UUID userId, Instant now, int retentionDays, int noticeDays) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        Instant deadline = latest(
                user.lastActivityAt().plus(Duration.ofDays(retentionDays)),
                now.plus(Duration.ofDays(noticeDays)));
        ZoneId zone = user.zoneOr(clock.zone());
        notificationService.notifyInactiveAccountWarning(user, LocalDate.ofInstant(deadline, zone));
        user.setInactivityWarnedAt(now);
        userRepository.save(user);
        return true;
    }

    /**
     * Supprime un compte arrivé au bout de la durée de conservation.
     *
     * <p>La suppression emprunte le chemin que le produit expose déjà — celui du profil athlète
     * pour un athlète, celui du back-office pour un coach — plutôt qu'un second chemin qui
     * divergerait en silence le jour où l'un des deux évoluera.</p>
     *
     * @return {@code false} si le compte a été délibérément conservé (cf. {@link #wouldStrandAClub})
     */
    @Transactional
    public boolean purge(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        if (wouldStrandAClub(user)) {
            log.warn("Compte inactif {} conservé : dernier membre du club {}, qui contient encore "
                            + "des données. Suppression à arbitrer manuellement.",
                    user.getId(), user.getClub().getId());
            return false;
        }
        UserRole role = user.getRole();
        if (role == UserRole.ATHLETE && user.getAthlete() != null) {
            // La suppression de l'athlète emporte son compte (users.athlete_id ON DELETE CASCADE)
            // et tout son historique : c'est le geste « supprimer mon compte » du profil athlète.
            gdprService.deleteAthleteData(user.getAthlete().getId());
        } else {
            userRepository.delete(user);
        }
        log.warn("[RGPD] Compte {} ({}) supprimé pour inactivité — durée de conservation atteinte.",
                userId, role);
        return true;
    }

    /**
     * Supprimer ce compte laisserait-il un club sans personne ?
     *
     * <p>Un encadrant isolé dont le club porte encore des athlètes est le cas gênant : l'effacer
     * ne libère rien — les données restent — et il ne reste alors personne pour y accéder, ni
     * pour exercer un droit dessus. Fermer le club entier est une décision, pas une conséquence
     * de calendrier : on le signale, on ne le tranche pas.</p>
     *
     * <p>Un athlète n'est jamais concerné : sa suppression emporte ses propres données, et rien
     * d'autre.</p>
     */
    private boolean wouldStrandAClub(User user) {
        if (user.getRole() == UserRole.ATHLETE || user.getClub() == null) {
            return false;
        }
        return userRepository.countOtherMembersOfClub(user.getClub().getId(), user.getId()) > 0;
    }

    private static Instant latest(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }
}
