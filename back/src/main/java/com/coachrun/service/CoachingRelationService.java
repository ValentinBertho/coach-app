package com.coachrun.service;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteAccountRepository;
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
    private final AthleteAccountRepository accountRepository;
    private final PersonalSpaceService personalSpace;
    // Fournisseurs paresseux : les services de montre tirent eux-mêmes une longue chaîne de
    // dépendances, et la fin d'un coaching n'en a besoin qu'à l'instant où elle se produit.
    private final ObjectProvider<StravaService> strava;
    private final ObjectProvider<GarminService> garmin;
    private final ObjectProvider<CorosService> coros;

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
        close(relation, userId, reason);
        detach(athleteUser, athlete);

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
            detach(athleteUser, athlete);
            notificationService.notifyCoachingEnded(athleteUser,
                    relation.getCoach().getFullName(), false, reason);
        });
        log.info("Coaching terminé à l'initiative du coach (relation={})", relation.getId());
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
        detach(athleteUser, athlete);
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
    private void detach(User athleteUser, Athlete athlete) {
        releaseDevices(athlete);
        AthleteAccount account = ensureAccount(athleteUser, athlete);
        // Il retrouve son espace personnel — sa fiche, ses objectifs, son historique d'avant —
        // plutôt qu'un compte vide qui n'aurait plus que l'annuaire.
        personalSpace.attach(athleteUser, account);
        athleteUser.setSessionsInvalidatedAt(Instant.now());
    }

    /**
     * Débranche les montres de la fiche que l'athlète quitte.
     *
     * <p>La connexion est attachée à la fiche, pas à la personne : laissée en place, elle
     * continuait d'importer chaque sortie de l'athlète chez un coach qu'il venait de quitter —
     * l'exact contraire de ce qu'il a demandé. Les déconnexions passent par les gestes
     * existants : jetons effacés, accès révoqué chez le fournisseur quand il le permet, et
     * séances à venir retirées de la montre Garmin. L'athlète rebranchera sa montre là où il ira.</p>
     *
     * <p>Une panne chez un fournisseur ne doit pas bloquer la sortie : chaque débranchement est
     * tenté isolément, et l'échec est journalisé plutôt que remonté.</p>
     */
    private void releaseDevices(Athlete athlete) {
        UUID athleteId = athlete.getId();
        disconnectQuietly("Strava", () -> strava.getObject().disconnectForAthlete(athleteId));
        disconnectQuietly("Garmin", () -> garmin.getObject().disconnectForAthlete(athleteId));
        disconnectQuietly("COROS", () -> coros.getObject().disconnectForAthlete(athleteId));
    }

    private void disconnectQuietly(String provider, Runnable disconnect) {
        try {
            disconnect.run();
        } catch (RuntimeException e) {
            log.warn("Débranchement {} impossible à la fin du coaching : {}", provider, e.getMessage());
        }
    }

    /**
     * Garantit qu'un athlète qui part a un compte athlète.
     *
     * <p>Un athlète invité par son coach (lien magique) entrait avec un accès au portail mais sans
     * compte : une fois parti, il ne pouvait ni chercher un coach ni envoyer une demande — les
     * deux supposent ce compte. On le crée ici, à partir de ce que la fiche sait de lui. Le
     * consentement santé ne voyage que s'il est actif sur la fiche.</p>
     */
    private AthleteAccount ensureAccount(User athleteUser, Athlete athlete) {
        AthleteAccount existing = accountRepository.findByUserId(athleteUser.getId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        AthleteAccount account = new AthleteAccount();
        account.setUser(athleteUser);
        account.setFirstName(athlete.getFirstName());
        account.setLastName(athlete.getLastName());
        account.setBirthDate(athlete.getBirthDate());
        account.setSex(athlete.getSex());
        account.setLevel(athlete.getLevel());
        account.setDiscipline(athlete.getDiscipline());
        account.setLookingForCoach(true);
        account.setTermsAcceptedAt(athleteUser.getTermsAcceptedAt());
        if (athlete.isHealthDataConsentActive()) {
            account.setHealthDataConsentAt(athlete.getHealthDataConsentAt());
        }
        account = accountRepository.save(account);
        // La fiche garde la trace de la personne, comme pour un athlète venu du hub.
        if (athlete.getAccount() == null) {
            athlete.setAccount(account);
        }
        return account;
    }
}
