package com.coachrun.service;

import com.coachrun.dto.request.ClubCreationRequestSubmission;
import com.coachrun.dto.request.ClubRequestDecision;
import com.coachrun.dto.response.ClubCreationRequestResponse;
import com.coachrun.dto.response.ClubRequestApprovalResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.ClubCreationRequest;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.entity.enums.ClubRequestStatus;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ClubCreationRequestRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Le cycle d'une demande de création de club : dépôt public, arbitrage par un administrateur, et
 * ouverture du club à la validation.
 *
 * <h2>Ce que ce régime résout</h2>
 *
 * <p>La bêta s'ouvre à des dizaines de nouveaux venus, et les deux régimes existants échouaient
 * pour des raisons opposées. En {@code open}, {@code /auth/register} créait un club et un compte
 * propriétaire sur la seule unicité de l'adresse : n'importe qui, y compris un robot, repartait
 * avec un espace complet — et chaque tentative consommait le quota d'envoi d'e-mails, partagé
 * avec les réinitialisations de mot de passe. En {@code invite}, un code partagé fermait la
 * porte, mais il fallait le distribuer à la main, il se transfère, et il ne dit jamais qui s'en
 * est servi.</p>
 *
 * <p>Ici, le formulaire reste ouvert : il dépose une demande, pas un compte. Rien n'est créé
 * avant décision, et la décision est tracée au journal d'audit.</p>
 *
 * <h2>Le mot de passe</h2>
 *
 * <p>La demande n'en porte pas, et le compte créé à la validation reçoit un secret aléatoire que
 * personne ne connaît : le coach pose le sien par le lien d'activation reçu par e-mail. Ce lien
 * fait donc deux choses à la fois — il ouvre le compte, et il <b>prouve</b> que le demandeur est
 * bien le titulaire de l'adresse déposée. C'est ce qui permet de marquer l'adresse comme vérifiée
 * sans envoyer un second e-mail de confirmation que personne ne lirait.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubCreationRequestService {

    /** Durée de vie du lien d'activation. Sept jours : un coach valide un lundi, part en stage. */
    private static final int ACTIVATION_LINK_DAYS = 7;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ClubCreationRequestRepository repository;
    private final UserRepository userRepository;
    private final ClubProvisioningService clubProvisioningService;
    private final NotificationService notificationService;
    private final AdminAuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ------------------------------------------------------------------ dépôt

    /**
     * Enregistre une demande.
     *
     * <p>Rend un booléen plutôt que la demande : rien de ce qui est en base n'a à revenir à un
     * appelant anonyme.</p>
     *
     * @param ipAddress adresse d'appel, pour reconnaître une salve venue d'un même point
     */
    @Transactional
    public void submit(ClubCreationRequestSubmission submission, String ipAddress, String userAgent) {
        String email = submission.email().trim().toLowerCase();

        // Un compte existe déjà : ce n'est pas une demande, c'est une connexion. Le dire
        // explicitement évite le pire scénario — une demande qui dort en file pendant que son
        // auteur attend, alors qu'il a déjà un espace.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException(
                    "Un compte existe déjà avec cette adresse. Connectez-vous, ou utilisez "
                            + "« mot de passe oublié » depuis la page de connexion.");
        }

        // Renvoyer le formulaire parce qu'on n'a pas de nouvelle est le comportement normal : on
        // ne veut ni empiler dix lignes identiques, ni faire croire à un échec.
        var pending = repository.findFirstByEmailIgnoreCaseAndStatus(email, ClubRequestStatus.PENDING);
        if (pending.isPresent()) {
            throw new ConflictException(
                    "Une demande est déjà enregistrée pour cette adresse et attend d'être "
                            + "étudiée. Vous recevrez un e-mail dès qu'elle aura été examinée.");
        }

        ClubCreationRequest request = new ClubCreationRequest();
        request.setEmail(email);
        request.setFullName(submission.fullName().trim());
        request.setClubName(submission.clubName().trim());
        request.setPhone(blankToNull(submission.phone()));
        request.setMessage(blankToNull(submission.message()));
        request.setStatus(ClubRequestStatus.PENDING);
        request.setTermsAcceptedAt(Instant.now());
        request.setIpAddress(truncate(ipAddress, 64));
        request.setUserAgent(truncate(userAgent, 512));
        repository.save(request);

        notificationService.notifyClubRequestReceived(email, request.getFullName(),
                request.getClubName());

        // Journalisé sans le message : il est libre, et peut porter ce que le candidat veut y
        // écrire. Il se lit dans le back-office, pas dans des journaux dont la rétention n'est
        // pas maîtrisée.
        log.info("[inscription] Demande de création de club reçue (club « {} »)",
                request.getClubName());
    }

    // -------------------------------------------------------------- arbitrage

    /** File d'arbitrage du back-office plateforme. */
    public PageResponse<ClubCreationRequestResponse> list(ClubRequestStatus status,
                                                          Pageable pageable) {
        var page = status == null
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return PageResponse.from(page, ClubCreationRequestResponse::from);
    }

    /** Compteur de la pastille « demandes à étudier » du tableau de bord admin. */
    public long countPending() {
        return repository.countByStatus(ClubRequestStatus.PENDING);
    }

    /**
     * Valide une demande : ouvre le club, crée le compte du coach, et lui envoie le lien qui lui
     * permettra d'y entrer.
     *
     * <p>Le lien est aussi rendu à l'administrateur. Ce n'est pas une redondance : l'envoi
     * d'e-mails peut être éteint sur l'instance, ou l'adresse rebondir, et le coach validé
     * resterait alors devant une porte fermée sans que personne ne le sache.</p>
     */
    @Transactional
    public ClubRequestApprovalResponse approve(UUID requestId, ClubRequestDecision decision,
                                               AuthPrincipal actor) {
        ClubCreationRequest request = requirePending(requestId);

        // Contrôlé au moment de la validation, et pas seulement au dépôt : entre les deux, le
        // candidat a pu recevoir une invitation de coach, ou déposer deux demandes dont une a
        // déjà été validée.
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ConflictException(
                    "Un compte existe déjà avec « " + request.getEmail() + " ». Cette demande a "
                            + "probablement déjà été validée, ou le coach a été invité entre-temps.");
        }

        // Secret aléatoire que personne ne connaît : le coach pose le sien par le lien
        // d'activation. Un mot de passe provisoire transmis par un canal quelconque serait, lui,
        // un mot de passe partagé.
        User coach = clubProvisioningService.openClub(
                request.getClubName(), request.getFullName(), request.getEmail(),
                passwordEncoder.encode(randomToken()),
                // Le lien d'activation part à l'adresse déposée : l'ouvrir prouve qu'elle
                // appartient bien au demandeur. Un second e-mail de vérification n'apprendrait
                // rien de plus et retarderait l'entrée.
                true);

        String activationToken = randomToken();
        coach.setResetToken(activationToken);
        coach.setResetExpiresAt(Instant.now().plus(ACTIVATION_LINK_DAYS, ChronoUnit.DAYS));

        request.setStatus(ClubRequestStatus.APPROVED);
        request.setReviewedAt(Instant.now());
        request.setReviewNote(decision == null ? null : decision.trimmedNote());
        request.setCreatedClubId(coach.getClub() == null ? null : coach.getClub().getId());
        request.setCreatedUserId(coach.getId());
        applyActor(request, actor);

        String activationUrl = frontendUrl + "/reset-password/" + activationToken;
        notificationService.notifyClubRequestApproved(request.getEmail(), request.getFullName(),
                request.getClubName(), activationUrl);

        auditService.record(AdminAuditAction.CLUB_REQUEST_APPROVED, AdminAuditTarget.CLUB,
                request.getCreatedClubId(), request.getClubName(),
                "Demande validée pour " + request.getEmail() + " — club et compte coach ouverts.");
        log.info("[inscription] Demande validée : club {} ouvert pour le compte {}",
                request.getCreatedClubId(), coach.getId());

        return new ClubRequestApprovalResponse(
                ClubCreationRequestResponse.from(request),
                activationUrl,
                notificationService.isMailEnabled());
    }

    /** Refuse une demande. Le motif, quand il y en a un, part au demandeur. */
    @Transactional
    public ClubCreationRequestResponse reject(UUID requestId, ClubRequestDecision decision,
                                              AuthPrincipal actor) {
        ClubCreationRequest request = requirePending(requestId);
        String note = decision == null ? null : decision.trimmedNote();

        request.setStatus(ClubRequestStatus.REJECTED);
        request.setReviewedAt(Instant.now());
        request.setReviewNote(note);
        applyActor(request, actor);

        notificationService.notifyClubRequestRejected(request.getEmail(), request.getFullName(),
                request.getClubName(), note);

        auditService.record(AdminAuditAction.CLUB_REQUEST_REJECTED, AdminAuditTarget.PLATFORM,
                request.getId(), request.getClubName(),
                "Demande refusée pour " + request.getEmail()
                        + (note == null ? " (sans motif)." : " — " + note));
        log.info("[inscription] Demande refusée (club « {} »)", request.getClubName());

        return ClubCreationRequestResponse.from(request);
    }

    // ------------------------------------------------------------------ outils

    /**
     * La demande, si elle attend encore.
     *
     * <p>409 et non 404 sur une demande déjà arbitrée : deux administrateurs peuvent ouvrir la
     * même file, et le second doit lire « déjà traitée » plutôt que « introuvable » — sans quoi
     * il cherchera une panne là où il n'y a qu'un collègue plus rapide.</p>
     */
    private ClubCreationRequest requirePending(UUID requestId) {
        ClubCreationRequest request = repository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Demande introuvable."));
        if (!request.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Cette demande a déjà été traitée (" + request.getStatus().label().toLowerCase()
                            + "). Rechargez la liste pour voir son état à jour.");
        }
        return request;
    }

    /** Recopie l'identité de l'arbitre : la trace doit survivre à la suppression de son compte. */
    private void applyActor(ClubCreationRequest request, AuthPrincipal actor) {
        if (actor == null) {
            return;
        }
        request.setReviewedByUserId(actor.userId());
        request.setReviewedByEmail(truncate(actor.email(), 255));
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
