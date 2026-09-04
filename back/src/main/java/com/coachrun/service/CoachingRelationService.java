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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
 * <p><b>Les deux parties peuvent l'exercer</b>, sans préavis ni motif obligatoire. Exiger un motif
 * pour partir transformerait une décision personnelle en justification à fournir à quelqu'un dont
 * on veut précisément se détacher.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoachingRelationService {

    private final CoachAthleteRelationRepository relationRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

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
        if (athlete == null) {
            throw new ConflictException("Vous n'avez pas de coach actuellement.");
        }
        CoachAthleteRelation relation = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(athlete.getId())
                .orElseThrow(() -> new ConflictException("Vous n'avez pas de coach actuellement."));

        String coachName = relation.getCoach().getFullName();
        close(relation, userId, reason);
        detach(athleteUser);

        notificationService.notifyCoachingEnded(relation.getCoach(),
                athlete.getFirstName() + " " + athlete.getLastName(), true, reason);
        log.info("Coaching terminé à l'initiative de l'athlète (relation={})", relation.getId());
        log.debug("L'athlète quitte {}", coachName);
    }

    /**
     * Le coach met fin au coaching d'un de ses athlètes.
     *
     * <p>Ne concerne que les athlètes venus du hub, qui ont un compte : un athlète saisi par le
     * coach et qui n'utilise pas l'application n'a rien à « quitter » — pour lui, la fin de la
     * relation est un archivage de fiche, qui existe déjà.</p>
     */
    @Transactional
    public void endByCoach(UUID coachId, UUID athleteId, String reason) {
        CoachAthleteRelation relation = relationRepository
                .findByCoachIdAndAthleteIdAndActiveTrue(coachId, athleteId)
                .filter(CoachAthleteRelation::isReferent)
                .orElseThrow(() -> new ConflictException(
                        "Vous n'êtes pas le coach référent de cet athlète."));

        Athlete athlete = relation.getAthlete();
        close(relation, coachId, reason);

        // Seul un athlète qui a un compte est « détaché » : les autres n'en ont jamais eu.
        userRepository.findByAthleteId(athlete.getId()).ifPresent(athleteUser -> {
            detach(athleteUser);
            notificationService.notifyCoachingEnded(athleteUser,
                    relation.getCoach().getFullName(), false, reason);
        });
        log.info("Coaching terminé à l'initiative du coach (relation={})", relation.getId());
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
     * {@code athlete_account_id} continue de la relier à cette personne, ce qui permettra un jour
     * de lui rendre son historique sans le reconstituer.</p>
     */
    private void detach(User athleteUser) {
        athleteUser.setAthlete(null);
        athleteUser.setClub(null);
    }
}
