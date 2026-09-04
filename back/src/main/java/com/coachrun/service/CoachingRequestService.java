package com.coachrun.service;

import com.coachrun.dto.request.CoachingRequestSubmission;
import com.coachrun.dto.response.CoachingRequestResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.CoachOffer;
import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.CoachingRequest;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.CoachingRequestStatus;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.CoachOfferRepository;
import com.coachrun.repository.CoachProfileRepository;
import com.coachrun.repository.CoachingRequestRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * La mise en relation : demander, répondre, commencer à travailler.
 *
 * <h2>Ce que l'acceptation déclenche</h2>
 *
 * <p>C'est la charnière du produit, et elle est <b>atomique</b>. Une acceptation à moitié faite —
 * la demande passée à {@code ACCEPTED} mais la fiche jamais créée — laisserait un athlète persuadé
 * d'avoir un coach et un coach sans athlète, sans que rien ne signale l'écart. Dans une seule
 * transaction : la demande est tranchée, la fiche naît dans l'espace du coach, le compte de
 * l'athlète y est rattaché, la relation référente est posée <b>en privé</b>, et le consentement
 * santé est reporté.</p>
 *
 * <h2>Pourquoi la relation est privée</h2>
 *
 * <p>{@code club = null} sur la relation. Un athlète venu du hub a choisi <b>un coach</b>, pas une
 * organisation : dans un espace qui peut accueillir d'autres coachs, le rendre visible de tous
 * reviendrait à partager sans le lui demander ce qu'il a confié à une personne.</p>
 *
 * <h2>Le consentement, et pourquoi il voyage</h2>
 *
 * <p>Sans report du consentement santé du compte vers la fiche, {@code HealthDataConsentValidator}
 * refuserait la première mesure — et le coach découvrirait la règle en butant dessus, sur un
 * athlète qui a pourtant consenti à l'inscription.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoachingRequestService {

    /** Aligné sur la validité d'une invitation athlète : deux semaines. */
    private static final int VALIDITY_DAYS = 14;

    /** Demandes simultanément en attente. Au-delà, l'athlète arrose plutôt qu'il ne choisit. */
    private static final int MAX_PENDING = 5;

    /** Demandes sur 24 h. Le plafond glissant, contre l'envoi en rafale. */
    private static final int MAX_PER_DAY = 10;

    private final CoachingRequestRepository requestRepository;
    private final CoachProfileRepository profileRepository;
    private final CoachOfferRepository offerRepository;
    private final AthleteRepository athleteRepository;
    private final CoachAthleteRelationRepository relationRepository;
    private final UserRepository userRepository;
    private final AthleteAccountService accountService;
    private final NotificationService notificationService;

    // ---------------------------------------------------------------- côté athlète

    /**
     * Envoie une demande à un coach.
     *
     * <p>Trois gardes, dans l'ordre où elles protègent quelqu'un : l'e-mail vérifié (le coach ne
     * doit pas recevoir de demande d'une adresse que personne ne contrôle), les plafonds (sa file
     * doit rester lisible), et l'unicité (une demande en attente à la fois par couple).</p>
     */
    @Transactional
    public CoachingRequestResponse submit(UUID userId, CoachingRequestSubmission submission,
                                          String ip, String userAgent) {
        AthleteAccount account = accountService.require(userId);
        User athleteUser = account.getUser();
        if (!athleteUser.isEmailVerified()) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Vérifiez votre adresse e-mail avant d'envoyer une demande : le coach doit "
                            + "pouvoir vous répondre.");
        }

        CoachProfile profile = profileRepository.findBySlug(submission.coachSlug())
                .orElseThrow(() -> new NotFoundException("Ce coach n'existe pas ou n'est plus publié."));
        if (!profile.getStatus().acceptsRequests()) {
            throw new ConflictException(
                    profile.getCoach().getFullName() + " ne prend pas de nouveaux athlètes en ce moment.");
        }

        Instant now = Instant.now();
        requestRepository.expireOverdue(now);

        if (requestRepository.existsByAthleteAccountIdAndCoachIdAndStatus(
                account.getId(), profile.getCoach().getId(), CoachingRequestStatus.PENDING)) {
            throw new ConflictException(
                    "Vous avez déjà une demande en attente auprès de ce coach. Laissez-lui le temps "
                            + "de vous répondre.");
        }
        if (requestRepository.countByAthleteAccountIdAndStatus(
                account.getId(), CoachingRequestStatus.PENDING) >= MAX_PENDING) {
            throw new ConflictException(
                    "Vous avez déjà " + MAX_PENDING + " demandes en attente. Attendez une réponse, "
                            + "ou retirez-en une avant d'en envoyer une autre.");
        }
        if (requestRepository.countByAthleteAccountIdAndCreatedAtAfter(
                account.getId(), now.minus(1, ChronoUnit.DAYS)) >= MAX_PER_DAY) {
            throw new ConflictException(
                    "Vous avez envoyé beaucoup de demandes aujourd'hui. Réessayez demain.");
        }

        CoachingRequest request = new CoachingRequest();
        request.setAthleteAccount(account);
        request.setCoach(profile.getCoach());
        request.setStatus(CoachingRequestStatus.PENDING);
        request.setMessage(submission.message().trim());
        request.setExpiresAt(now.plus(VALIDITY_DAYS, ChronoUnit.DAYS));
        request.setIpAddress(truncate(ip, 64));
        request.setUserAgent(truncate(userAgent, 512));
        applyOffer(request, profile, submission.offerId());

        requestRepository.save(request);
        notificationService.notifyCoachingRequestReceived(profile.getCoach(),
                account.getFirstName() + " " + account.getLastName());
        log.info("Demande de coaching {} déposée (coach={})", request.getId(), profile.getCoach().getId());
        return render(request);
    }

    /**
     * Recopie la formule choisie, libellé et montant compris.
     *
     * <p>L'instantané, et pas seulement la clé : le coach peut changer sa grille, et l'accord
     * conclu doit rester lisible tel qu'il l'a été.</p>
     */
    private void applyOffer(CoachingRequest request, CoachProfile profile, UUID offerId) {
        if (offerId == null) {
            return;
        }
        CoachOffer offer = offerRepository.findByIdAndProfileId(offerId, profile.getId())
                .orElseThrow(() -> new NotFoundException("Cette formule n'existe pas chez ce coach."));
        request.setOffer(offer);
        request.setOfferLabel(offer.getName());
        request.setOfferAmountCents(offer.getAmountCents());
    }

    public List<CoachingRequestResponse> myRequests(UUID userId) {
        AthleteAccount account = accountService.require(userId);
        requestRepository.expireOverdue(Instant.now());
        return requestRepository.findByAthleteAccountIdOrderByCreatedAtDesc(account.getId())
                .stream().map(this::render).toList();
    }

    /** L'athlète retire sa demande. Retirée n'est pas refusée : l'historique le distingue. */
    @Transactional
    public CoachingRequestResponse withdraw(UUID userId, UUID requestId) {
        CoachingRequest request = mineAsAthlete(userId, requestId);
        requireOpen(request);
        request.setStatus(CoachingRequestStatus.WITHDRAWN);
        request.setDecidedAt(Instant.now());
        return render(request);
    }

    /** L'unique réponse de l'athlète à l'unique question du coach. */
    @Transactional
    public CoachingRequestResponse answer(UUID userId, UUID requestId, String answer) {
        CoachingRequest request = mineAsAthlete(userId, requestId);
        requireOpen(request);
        if (!StringUtils.hasText(request.getCoachQuestion())) {
            throw new ConflictException("Le coach ne vous a pas posé de question.");
        }
        if (StringUtils.hasText(request.getAthleteAnswer())) {
            throw new ConflictException(
                    "Vous avez déjà répondu. La discussion se poursuivra dans la messagerie si le "
                            + "coach accepte votre demande.");
        }
        request.setAthleteAnswer(answer == null ? null : answer.trim());
        return render(request);
    }

    // ---------------------------------------------------------------- côté coach

    public List<CoachingRequestResponse> received(UUID coachId) {
        requestRepository.expireOverdue(Instant.now());
        return requestRepository.findByCoachIdOrderByCreatedAtDesc(coachId)
                .stream().map(this::render).toList();
    }

    public long pendingCount(UUID coachId) {
        return requestRepository.countByCoachIdAndStatus(coachId, CoachingRequestStatus.PENDING);
    }

    /** L'unique question du coach avant de décider. */
    @Transactional
    public CoachingRequestResponse ask(UUID coachId, UUID requestId, String question) {
        CoachingRequest request = mineAsCoach(coachId, requestId);
        requireOpen(request);
        if (StringUtils.hasText(request.getCoachQuestion())) {
            throw new ConflictException(
                    "Vous avez déjà posé une question. Acceptez la demande pour poursuivre dans la "
                            + "messagerie.");
        }
        if (!StringUtils.hasText(question)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La question ne peut pas être vide.");
        }
        request.setCoachQuestion(question.trim());
        notificationService.notifyCoachingRequestQuestion(request.getAthleteAccount().getUser(),
                request.getCoach().getFullName());
        return render(request);
    }

    @Transactional
    public CoachingRequestResponse decline(UUID coachId, UUID requestId, String reason) {
        CoachingRequest request = mineAsCoach(coachId, requestId);
        requireOpen(request);
        request.setStatus(CoachingRequestStatus.DECLINED);
        request.setDecidedAt(Instant.now());
        request.setDeclineReason(StringUtils.hasText(reason) ? reason.trim() : null);
        notificationService.notifyCoachingRequestDeclined(request.getAthleteAccount().getUser(),
                request.getCoach().getFullName(), request.getDeclineReason());
        log.info("Demande de coaching {} refusée", requestId);
        return render(request);
    }

    /**
     * Accepte : crée la fiche, la relation, et fait entrer l'athlète dans l'outil.
     *
     * <p>Tout ou rien. Les autres demandes en attente de cet athlète ne sont <b>pas</b> annulées :
     * il peut légitimement attendre deux réponses, et c'est à lui de retirer celles qui n'ont plus
     * d'objet — les fermer d'office déciderait à sa place.</p>
     */
    @Transactional
    public CoachingRequestResponse accept(UUID coachId, UUID requestId) {
        CoachingRequest request = mineAsCoach(coachId, requestId);
        requireOpen(request);

        User coach = request.getCoach();
        if (coach.getClub() == null) {
            // Impossible en pratique — tout coach ouvre un espace à l'inscription — mais la fiche
            // ne peut pas naître sans, et un NPE ici laisserait une demande à moitié acceptée.
            throw new ConflictException(
                    "Votre espace de travail est introuvable : contactez l'équipe avant d'accepter.");
        }

        AthleteAccount account = request.getAthleteAccount();
        Athlete athlete = new Athlete();
        athlete.setClub(coach.getClub());
        athlete.setAccount(account);
        athlete.setFirstName(account.getFirstName());
        athlete.setLastName(account.getLastName());
        athlete.setEmail(account.getUser().getEmail());
        athlete.setBirthDate(account.getBirthDate());
        athlete.setSex(account.getSex());
        athlete.setLevel(account.getLevel());
        if (account.getDiscipline() != null) {
            athlete.setDiscipline(account.getDiscipline());
        }
        athlete.setStatus(AthleteStatus.ACTIVE);
        // Le consentement voyage avec la personne : sans lui, la première mesure serait refusée
        // par HealthDataConsentValidator, sur un athlète qui a pourtant consenti à l'inscription.
        athlete.setHealthDataConsentAt(account.getHealthDataConsentAt());
        athlete = athleteRepository.save(athlete);

        CoachAthleteRelation relation = new CoachAthleteRelation();
        relation.setAthlete(athlete);
        relation.setCoach(coach);
        // club = null : athlète PRIVÉ. Il a choisi un coach, pas une organisation.
        relation.setClub(null);
        relation.setReferent(true);
        relation.setActive(true);
        relation = relationRepository.save(relation);

        // Le compte de l'athlète rejoint l'espace du coach : les fils de discussion en dépendent,
        // Conversation.club étant non nullable.
        User athleteUser = account.getUser();
        athleteUser.setClub(coach.getClub());
        athleteUser.setAthlete(athlete);

        request.setStatus(CoachingRequestStatus.ACCEPTED);
        request.setDecidedAt(Instant.now());
        request.setCreatedAthleteId(athlete.getId());
        request.setCreatedRelationId(relation.getId());

        notificationService.notifyCoachingRequestAccepted(athleteUser, coach.getFullName());
        log.info("Demande de coaching {} acceptée (athlète {} créé, relation {})",
                requestId, athlete.getId(), relation.getId());
        return render(request);
    }

    // ---------------------------------------------------------------- interne

    private CoachingRequest mineAsAthlete(UUID userId, UUID requestId) {
        AthleteAccount account = accountService.require(userId);
        return requestRepository.findByIdAndAthleteAccountId(requestId, account.getId())
                .orElseThrow(() -> new NotFoundException("Demande introuvable."));
    }

    private CoachingRequest mineAsCoach(UUID coachId, UUID requestId) {
        return requestRepository.findByIdAndCoachId(requestId, coachId)
                .orElseThrow(() -> new NotFoundException("Demande introuvable."));
    }

    /**
     * Refuse d'agir sur une demande déjà tranchée ou périmée.
     *
     * <p>Le message nomme l'état réel plutôt que de dire « impossible » : deux écrans ouverts côte
     * à côte suffisent à tomber ici, et l'utilisateur doit comprendre que ce n'est pas une panne.</p>
     */
    private void requireOpen(CoachingRequest request) {
        if (!request.getStatus().isOpen()) {
            throw new ConflictException(
                    "Cette demande est « " + request.getStatus().label() + " » : elle ne peut plus "
                            + "être modifiée.");
        }
        if (!request.getExpiresAt().isAfter(Instant.now())) {
            request.setStatus(CoachingRequestStatus.EXPIRED);
            throw new ConflictException("Cette demande a expiré, faute de réponse dans les "
                    + VALIDITY_DAYS + " jours.");
        }
    }

    private CoachingRequestResponse render(CoachingRequest r) {
        String slug = profileRepository.findByCoachId(r.getCoach().getId())
                .map(CoachProfile::getSlug).orElse(null);
        return CoachingRequestResponse.of(r, slug);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
