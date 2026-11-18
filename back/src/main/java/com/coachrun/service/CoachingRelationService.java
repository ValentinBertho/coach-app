package com.coachrun.service;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

/**
 * La fin d'un coaching.
 *
 * <h2>Pourquoi cette porte devait exister avant l'ouverture</h2>
 *
 * <p>Une place de marché où l'on entre sans pouvoir sortir est un piège. Le socle technique de la
 * révocation était posé depuis le premier lot de ce chantier — clore une relation retire réellement
 * l'accès, et y survit au redémarrage — mais aucun geste ne l'exposait : ni le coach ni l'athlète
 * ne pouvaient mettre fin à quoi que ce soit.</p>
 *
 * <h2>Ce que la fin d'une relation fait, et ne fait pas</h2>
 *
 * <p><b>Elle ne détruit rien.</b> La fiche, les séances, les tests et les ressentis restent dans
 * l'espace du coach : il les a écrits, et l'athlète garde par ailleurs son droit d'export et
 * d'effacement, qui sont des gestes distincts et explicites. Ce qui change est le <b>droit</b> :
 * le coach passe en lecture seule sur cette fiche, l'athlète redevient un compte sans coach, libre
 * de repartir dans l'annuaire.</p>
 *
 * <p><b>Les deux parties peuvent l'exercer</b>, sans motif obligatoire. Exiger un motif pour
 * partir transformerait une décision personnelle en justification à fournir à quelqu'un dont on
 * veut précisément se détacher. L'athlète part sans préavis ; le coach annonce la fin
 * {@value #NOTICE_DAYS} jours à l'avance, le temps pour l'athlète de se retourner.</p>
 *
 * <p>À la fin, l'athlète retrouve son espace personnel avec sa montre et une copie de ses
 * sorties ; les séances à venir sont retirées (le coach peut choisir de les laisser).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachingRelationService {

    private final CoachAthleteRelationRepository relationRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AthleteAccountService accountService;
    private final PersonalSpaceService personalSpace;
    private final AthleteCarryOverService carryOver;
    // Fournisseurs paresseux : les services de montre tirent eux-mêmes une longue chaîne de
    // dépendances, et la fin d'un coaching n'en a besoin qu'à l'instant où elle se produit.
    private final ObjectProvider<GarminService> garmin;
    private final ObjectProvider<ProposalService> proposals;
    private final com.coachrun.repository.WorkoutRepository workoutRepository;
    private final ClockService clock;
    private final com.coachrun.repository.AthleteCoachPermissionRepository permissionRepository;

    /** Préavis du coach : le temps pour l'athlète de se retourner. */
    public static final int NOTICE_DAYS = 7;

    /**
     * L'athlète met fin à son coaching.
     *
     * <p>Il n'a qu'une relation référente : c'est celle qui le lie à son coach, et la seule que la
     * mise en relation par le hub sache créer.</p>
     */
    @Transactional
    public void endByAthlete(UUID userId, String reason) {
        User athleteUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Compte introuvable."));
        Athlete athlete = athleteUser.getAthlete();
        if (athlete == null || PersonalSpaceService.isPersonal(athlete)) {
            throw new ConflictException("Vous n'avez pas de coach actuellement.");
        }
        CoachAthleteRelation relation = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(athlete.getId())
                .orElseThrow(() -> new ConflictException("Vous n'avez pas de coach actuellement."));

        String coachName = relation.getCoach().getFullName();
        // Ses séances à venir n'ont plus de coach pour les ajuster : elles sont retirées.
        finish(relation, athleteUser, userId, reason, false);

        notificationService.notifyCoachingEnded(relation.getCoach(),
                athlete.getFirstName() + " " + athlete.getLastName(), true, reason);
        log.info("Coaching terminé à l'initiative de l'athlète (relation={})", relation.getId());
        log.debug("L'athlète quitte {}", coachName);
    }

    /**
     * Le coach annonce la fin du coaching d'un de ses athlètes.
     *
     * <p>Avec un préavis de {@value #NOTICE_DAYS} jours quand l'athlète utilise l'application :
     * c'est le temps de se retourner, et le coach peut revenir sur sa décision d'ici là. La fin
     * elle-même est prononcée par {@link #endDue}. Un athlète saisi par le coach et qui n'utilise
     * pas l'application n'a personne à prévenir : la fin est immédiate.</p>
     *
     * @param keepFutureWorkouts laisser les séances à venir sur la fiche (sinon retirées)
     * @return la date de fin effective
     */
    @Transactional
    public Instant endByCoach(UUID coachId, UUID athleteId, String reason, boolean keepFutureWorkouts) {
        CoachAthleteRelation relation = relationRepository
                .findByCoachIdAndAthleteIdAndActiveTrue(coachId, athleteId)
                .filter(CoachAthleteRelation::isReferent)
                .orElseThrow(() -> new ConflictException(
                        "Vous n'êtes pas le coach référent de cet athlète."));

        Athlete athlete = relation.getAthlete();
        User athleteUser = userRepository.findByAthleteId(athlete.getId()).orElse(null);
        String note = StringUtils.hasText(reason) ? reason.trim() : null;
        if (athleteUser == null) {
            finish(relation, null, coachId, note, keepFutureWorkouts);
            log.info("Coaching terminé à l'initiative du coach (relation={}, sans compte)", relation.getId());
            return clock.instant();
        }
        Instant endsAt = clock.instant().plus(NOTICE_DAYS, java.time.temporal.ChronoUnit.DAYS);
        relation.setPlannedEndAt(endsAt);
        relation.setPlannedEndBy(coachId);
        relation.setPlannedEndReason(note);
        relation.setKeepFutureWorkouts(keepFutureWorkouts);
        relation.setEndReminderSentAt(null);
        notificationService.notifyCoachingEndPlanned(athleteUser, relation.getCoach().getFullName(), endsAt, note);
        log.info("Fin de coaching annoncée pour le {} (relation={})", endsAt, relation.getId());
        return endsAt;
    }

    /** Le coach revient sur la fin annoncée. */
    @Transactional
    public void cancelPlannedEnd(UUID coachId, UUID athleteId) {
        CoachAthleteRelation relation = relationRepository
                .findByCoachIdAndAthleteIdAndActiveTrue(coachId, athleteId)
                .filter(r -> r.getPlannedEndAt() != null)
                .orElseThrow(() -> new ConflictException("Aucune fin de suivi n'est prévue pour cet athlète."));
        relation.setPlannedEndAt(null);
        relation.setPlannedEndBy(null);
        relation.setPlannedEndReason(null);
        relation.setKeepFutureWorkouts(false);
        relation.setEndReminderSentAt(null);
        userRepository.findByAthleteId(athleteId).ifPresent(u ->
                notificationService.notifyCoachingEndCancelled(u, relation.getCoach().getFullName()));
    }

    /** La fin prévue d'une relation, vue par le coach ; vide si rien n'est prévu. */
    @Transactional(readOnly = true)
    public java.util.Optional<CoachAthleteRelation> plannedEnd(UUID coachId, UUID athleteId) {
        return relationRepository.findByCoachIdAndAthleteIdAndActiveTrue(coachId, athleteId)
                .filter(r -> r.getPlannedEndAt() != null);
    }

    /** Le coaching en cours de l'athlète connecté (coach, depuis quand, fin annoncée). */
    @Transactional(readOnly = true)
    public java.util.Optional<CoachAthleteRelation> current(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getAthlete() == null || PersonalSpaceService.isPersonal(user.getAthlete())) {
            return java.util.Optional.empty();
        }
        return relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(user.getAthlete().getId())
                .map(r -> {
                    r.getCoach().getFullName(); // chargé pendant la transaction
                    return r;
                });
    }

    /** Prononce les fins annoncées arrivées à échéance. */
    @Transactional
    public int endDue(Instant now) {
        int ended = 0;
        for (CoachAthleteRelation relation : relationRepository.findByActiveTrueAndPlannedEndAtLessThanEqual(now)) {
            Athlete athlete = relation.getAthlete();
            User athleteUser = userRepository.findByAthleteId(athlete.getId()).orElse(null);
            finish(relation, athleteUser, relation.getPlannedEndBy(), relation.getPlannedEndReason(),
                    relation.isKeepFutureWorkouts());
            if (athleteUser != null) {
                notificationService.notifyCoachingEnded(athleteUser, relation.getCoach().getFullName(), false,
                        relation.getPlannedEndReason());
            }
            ended++;
        }
        return ended;
    }

    /** Le rappel de la veille, pour chaque fin annoncée qui tombe dans les 24 heures. */
    @Transactional
    public int remindDue(Instant now) {
        int sent = 0;
        for (CoachAthleteRelation relation : relationRepository
                .findByActiveTrueAndEndReminderSentAtIsNullAndPlannedEndAtLessThanEqual(
                        now.plus(1, java.time.temporal.ChronoUnit.DAYS))) {
            userRepository.findByAthleteId(relation.getAthlete().getId()).ifPresent(u ->
                    notificationService.notifyCoachingEndReminder(u, relation.getCoach().getFullName()));
            relation.setEndReminderSentAt(now);
            sent++;
        }
        return sent;
    }

    /**
     * Clôt une relation pour la déplacer ailleurs (coach qui quitte son club avec ses athlètes
     * privés) : l'athlète retrouve un instant son espace personnel, avec sa montre et ses sorties,
     * avant d'entrer dans le nouvel espace. Pas de récapitulatif : le suivi continue.
     */
    @Transactional
    public void closeForMove(CoachAthleteRelation relation, User athleteUser, UUID byUserId, String reason) {
        Athlete athlete = relation.getAthlete();
        close(relation, byUserId, reason);
        relation.setPlannedEndAt(null);
        removeFutureWorkouts(athlete);
        relationRepository.saveAndFlush(relation);
        if (athleteUser != null && athleteUser.getAthlete() != null
                && athleteUser.getAthlete().getId().equals(athlete.getId())) {
            detach(athleteUser, athlete, null);
        }
    }

    /**
     * L'athlète quitte le club mais garde son coach : il devient son athlète privé. Les accès
     * accordés à d'autres coachs du club tombent, puisqu'un athlète privé n'est pas partagé.
     */
    @Transactional
    public void leaveClubKeepCoach(UUID userId) {
        User athleteUser = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Compte introuvable."));
        Athlete athlete = athleteUser.getAthlete();
        CoachAthleteRelation relation = athlete == null ? null : relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(athlete.getId()).orElse(null);
        if (relation == null || relation.getClub() == null) {
            throw new ConflictException("Vous n'êtes rattaché à aucun club.");
        }
        String clubName = relation.getClub().getName();
        relation.setClub(null);
        permissionRepository.deleteAll(permissionRepository.findByAthleteId(athlete.getId()));
        notificationService.notifyLeftClubKeptCoach(relation.getCoach(),
                athlete.getFirstName() + " " + athlete.getLastName(), clubName);
        log.info("Athlète {} : quitte le club, garde son coach", athlete.getId());
    }

    /**
     * Changement de coach : le nouveau coach vient d'accepter, le suivi en cours s'arrête.
     *
     * <p>L'athlète l'a voulu en envoyant sa demande de changement : pas de préavis, mais l'ancien
     * coach est prévenu. La fiche lui reste, en lecture, comme pour toute fin de coaching.</p>
     */
    @Transactional
    public void endForSwitch(User athleteUser, String newCoachName) {
        Athlete athlete = athleteUser.getAthlete();
        if (athlete == null || PersonalSpaceService.isPersonal(athlete)) {
            return;
        }
        relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(athlete.getId()).ifPresent(relation -> {
            finish(relation, athleteUser, athleteUser.getId(), "Changement de coach", false);
            notificationService.notifyCoachingEnded(relation.getCoach(),
                    athlete.getFirstName() + " " + athlete.getLastName(), true,
                    "a choisi de continuer avec un autre coach");
            log.info("Changement de coach : relation {} close", relation.getId());
        });
    }

    /**
     * L'archivage d'une fiche dont la personne se connecte à l'application.
     *
     * <p>Archiver retirait la fiche des listes du coach sans rien dire à l'athlète, qui gardait
     * son portail, sa montre branchée sur un espace que plus personne n'animait, et aucun moyen
     * de chercher quelqu'un d'autre. Pour lui, archiver <b>est</b> une fin de coaching : on la
     * traite comme telle. Une fiche sans accès à l'application n'est pas concernée — ses relations
     * restent en place, pour qu'un désarchivage rende la fiche au coach telle qu'il l'a laissée.</p>
     *
     * @return {@code true} si une personne a été détachée
     */
    @Transactional
    public boolean endOnArchive(UUID byUserId, Athlete athlete) {
        User athleteUser = userRepository.findByAthleteId(athlete.getId()).orElse(null);
        if (athleteUser == null) {
            return false;
        }
        String coachName = null;
        for (CoachAthleteRelation relation : relationRepository.findByAthleteIdAndActiveTrue(athlete.getId())) {
            if (relation.isReferent()) {
                coachName = relation.getCoach().getFullName();
            }
            close(relation, byUserId, "Fiche archivée");
        }
        removeFutureWorkouts(athlete);
        detach(athleteUser, athlete, null);
        notificationService.notifyCoachingEnded(athleteUser,
                coachName != null ? coachName : athlete.getClub().getName(), false, null);
        log.info("Fiche {} archivée : l'athlète redevient un compte libre", athlete.getId());
        return true;
    }

    private void close(CoachAthleteRelation relation, UUID byUserId, String reason) {
        relation.end(byUserId, StringUtils.hasText(reason) ? reason.trim() : null);
    }

    /**
     * Rend l'athlète à lui-même : plus de fiche courante, plus d'espace.
     *
     * <p>C'est ce qui le renvoie à l'état dans lequel il est arrivé — un compte qui peut chercher
     * un coach — plutôt que de le laisser dans un espace d'entraînement que plus personne n'anime.
     * La fiche, elle, n'est pas touchée : elle reste chez le coach, et son
     * {@code athlete_account_id} continue de la relier à cette personne. C'est par ce lien qu'il
     * relit le fil de discussion qu'il a lui-même alimenté (cf. {@code ConversationService}).</p>
     *
     * <h2>Pourquoi les sessions sont fermées</h2>
     *
     * <p>Le jeton porte {@code athleteId} et {@code clubId} : il les <b>affirme</b> pendant toute
     * sa durée de vie, une heure, sans jamais les relire en base. Sans cette invalidation, un
     * athlète qui vient de partir continuait donc de se présenter comme membre du club de son
     * ancien coach — et, côté messagerie, de pouvoir écrire dans un fil que la relation venait de
     * clore.</p>
     *
     * <p>La reconnexion est le prix de cette fermeture, et il est juste : c'est le moment où le
     * compte change de nature. Le mécanisme est celui qui existe déjà pour « fermer les sessions »
     * du back-office, honoré par {@code TokenFreshnessValidator}.</p>
     */
    private int detach(User athleteUser, Athlete athlete, String coachName) {
        AthleteAccount account = ensureAccount(athleteUser, athlete);
        // Il retrouve son espace personnel — sa fiche, ses objectifs, son historique d'avant —
        // plutôt qu'un compte vide qui n'aurait plus que l'annuaire.
        Athlete personal = personalSpace.attach(athleteUser, account);
        returnDevices(athlete, personal);
        // Son profil (records, seuils, FC, VMA, objectifs) le suit : c'est le sien, et c'est de lui
        // que dépendent ses zones chez le prochain coach.
        carryOver.carryProfile(athlete, personal);
        // Les deux gardent une copie : la fiche reste chez le coach, les sorties et le calendrier
        // passé suivent l'athlète.
        int copied = personalSpace.copyHistory(athlete, personal);
        athleteUser.setSessionsInvalidatedAt(Instant.now());
        if (coachName != null) {
            notificationService.notifyCoachingRecap(athleteUser, coachName,
                    workoutRepository.countByAthleteIdAndStatus(athlete.getId(),
                            com.coachrun.entity.enums.WorkoutStatus.COMPLETED), copied);
        }
        return copied;
    }

    /**
     * Prononce la fin : relation close, séances à venir retirées (sauf choix du coach), compte
     * rendu à son espace personnel avec sa montre et ses sorties.
     */
    private void finish(CoachAthleteRelation relation, User athleteUser, UUID byUserId, String reason,
                        boolean keepFutureWorkouts) {
        Athlete athlete = relation.getAthlete();
        close(relation, byUserId, reason);
        relation.setPlannedEndAt(null);
        if (!keepFutureWorkouts) {
            removeFutureWorkouts(athlete);
        }
        if (athleteUser != null && athleteUser.getAthlete() != null
                && athleteUser.getAthlete().getId().equals(athlete.getId())) {
            detach(athleteUser, athlete, relation.getCoach().getFullName());
        }
    }

    /**
     * Les séances à venir de la fiche quittée : sans coach pour les ajuster, elles n'ont plus
     * d'objet. Retirées de la fiche, et de la montre Garmin où nous les avions poussées.
     */
    private void removeFutureWorkouts(Athlete athlete) {
        java.util.List<com.coachrun.entity.Workout> future = workoutRepository
                .findByAthleteIdAndStatusAndScheduledDateGreaterThanEqual(athlete.getId(),
                        com.coachrun.entity.enums.WorkoutStatus.PLANNED, clock.today());
        ProposalService proposalService = proposals.getIfAvailable();
        for (com.coachrun.entity.Workout w : future) {
            if (proposalService != null) {
                try {
                    proposalService.expireForTarget(w.getId());
                } catch (RuntimeException e) {
                    log.warn("Propositions non périmées pour la séance {} : {}", w.getId(), e.getMessage());
                }
            }
        }
        workoutRepository.deleteAll(future);
    }

    /**
     * Les montres suivent l'athlète dans son espace personnel.
     *
     * <p>La connexion est attachée à la fiche, pas à la personne : laissée en place, elle
     * continuait d'importer chaque sortie de l'athlète chez un coach qu'il venait de quitter. Elle
     * est déplacée vers sa fiche personnelle — pas besoin de rebrancher sa montre. Les séances que
     * nous avions poussées sur sa montre Garmin pour l'ancien coach en sont d'abord retirées.</p>
     *
     * <p>Une panne chez un fournisseur ne doit pas bloquer la sortie : le retrait est tenté, et
     * l'échec est journalisé plutôt que remonté.</p>
     */
    private void returnDevices(Athlete from, Athlete personal) {
        disconnectQuietly("Garmin", () -> garmin.getObject().releaseWorkoutsForAthlete(from.getId()));
        personalSpace.moveDevices(from, personal);
    }

    private void disconnectQuietly(String provider, Runnable disconnect) {
        try {
            disconnect.run();
        } catch (RuntimeException e) {
            log.warn("Retrait des séances {} impossible à la fin du coaching : {}", provider, e.getMessage());
        }
    }

    /**
     * Garantit qu'un athlète qui part a un compte athlète.
     *
     * <p>Un athlète invité par son coach (lien magique) entrait avec un accès au portail mais sans
     * compte : une fois parti, il ne pouvait ni chercher un coach ni envoyer une demande — les
     * deux supposent ce compte.</p>
     */
    private AthleteAccount ensureAccount(User athleteUser, Athlete athlete) {
        return accountService.ensure(athleteUser, athlete);
    }
}
