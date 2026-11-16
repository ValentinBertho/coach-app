package com.coachrun.service;

import com.coachrun.dto.request.InviteLinkRequest;
import com.coachrun.dto.response.InviteJoinResponse;
import com.coachrun.dto.response.InviteLinkResponse;
import com.coachrun.dto.response.InvitePreviewResponse;
import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.CoachingRequest;
import com.coachrun.entity.InviteLink;
import com.coachrun.entity.TrainingGroup;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.CoachingRequestStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.CoachPhotoRepository;
import com.coachrun.repository.CoachProfileRepository;
import com.coachrun.repository.CoachingRequestRepository;
import com.coachrun.repository.InviteLinkRepository;
import com.coachrun.repository.TrainingGroupRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Les liens d'invitation : un coach envoie un lien, l'athlète le rejoint en un geste.
 *
 * <h2>Les cas couverts</h2>
 * <ul>
 *   <li><b>Coach indépendant</b> : l'athlète devient son athlète privé.</li>
 *   <li><b>Coach de club</b> : au choix, athlète privé du coach, ou athlète du club
 *       ({@code clubMembership}) — et, si le lien le prévoit, directement dans un groupe.</li>
 *   <li><b>Lien personnel</b> (entrée directe) : envoyé à une personne, il la fait entrer sans
 *       validation. <b>Lien public</b> : il dépose une demande, que le coach accepte comme les
 *       autres.</li>
 *   <li>Pas de compte : la page du lien propose de le créer ; le lien est retenu et sert dès que
 *       l'adresse est confirmée. Compte déjà suivi par un autre coach : refus explicite, il faut
 *       d'abord mettre fin au coaching en cours.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteLinkService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final int TOKEN_LENGTH = 16;
    private static final int REQUEST_VALIDITY_DAYS = 14;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final InviteLinkRepository linkRepository;
    private final UserRepository userRepository;
    private final TrainingGroupRepository groupRepository;
    private final CoachProfileRepository profileRepository;
    private final CoachPhotoRepository photoRepository;
    private final AthleteAccountRepository accountRepository;
    private final AthleteAccountService accountService;
    private final CoachingRequestRepository requestRepository;
    private final CoachAthleteRelationRepository relationRepository;
    private final CoachingRequestService coachingRequests;
    private final NotificationService notificationService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ---------------------------------------------------------------- côté coach

    @Transactional
    public InviteLinkResponse create(UUID coachId, UUID clubId, InviteLinkRequest req) {
        User coach = requireCoach(coachId);
        if (clubId == null || coach.getClub() == null) {
            throw new ConflictException("Votre espace de travail est introuvable.");
        }
        InviteLink link = new InviteLink();
        link.setToken(newToken());
        link.setCoach(coach);
        link.setClub(coach.getClub().getId().equals(clubId) ? coach.getClub()
                : coach.getAdditionalClubs().stream().filter(c -> c.getId().equals(clubId)).findFirst()
                        .orElseThrow(() -> new ForbiddenException("Vous n'intervenez pas dans cet espace.")));
        link.setLabel(StringUtils.hasText(req.label()) ? req.label().trim() : null);
        // Un coach indépendant n'a pas de club à faire rejoindre : son athlète est le sien.
        link.setClubMembership(req.clubMembership() && !link.getClub().isSoloPractice());
        link.setDirectEntry(req.directEntry());
        if (req.groupId() != null) {
            TrainingGroup group = groupRepository.findByIdAndClubId(req.groupId(), link.getClub().getId())
                    .orElseThrow(() -> new NotFoundException("Groupe introuvable."));
            link.setGroup(group);
        }
        link.setMaxUses(req.maxUses());
        if (req.validDays() != null) {
            link.setExpiresAt(Instant.now().plus(req.validDays(), ChronoUnit.DAYS));
        }
        link = linkRepository.save(link);
        log.info("Lien d'invitation {} créé par le coach {}", link.getId(), coachId);
        return render(link);
    }

    @Transactional(readOnly = true)
    public List<InviteLinkResponse> mine(UUID coachId) {
        return linkRepository.findByCoachIdOrderByCreatedAtDesc(coachId).stream().map(this::render).toList();
    }

    @Transactional
    public InviteLinkResponse revoke(UUID coachId, UUID linkId) {
        InviteLink link = linkRepository.findByIdAndCoachId(linkId, coachId)
                .orElseThrow(() -> new NotFoundException("Lien introuvable."));
        if (link.getRevokedAt() == null) {
            link.setRevokedAt(Instant.now());
        }
        return render(link);
    }

    // ---------------------------------------------------------------- page publique

    @Transactional(readOnly = true)
    public InvitePreviewResponse preview(String token) {
        InviteLink link = linkRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Ce lien d'invitation n'existe pas."));
        String reason = link.unusableReason(Instant.now());
        User coach = link.getCoach();
        Optional<CoachProfile> profile = profileRepository.findByCoachId(coach.getId())
                .filter(p -> p.getStatus().isVisible());
        return new InvitePreviewResponse(
                reason == null, reason,
                coach.getFullName(),
                profile.map(CoachProfile::getSlug).orElse(null),
                profile.flatMap(p -> photoRepository.findIdByProfileId(p.getId()))
                        .map(id -> "/public/coach-photos/" + id).orElse(null),
                link.getClub().getName(),
                link.getClub().isSoloPractice(),
                link.isClubMembership(),
                link.getGroup() != null ? link.getGroup().getName() : null,
                link.isDirectEntry());
    }

    // ---------------------------------------------------------------- côté athlète

    /** L'athlète connecté rejoint par le lien. */
    @Transactional
    public InviteJoinResponse join(UUID userId, String token, String message) {
        AthleteAccount account = accountService.require(userId);
        InviteLink link = requireUsable(token);
        User coach = link.getCoach();
        User athleteUser = account.getUser();

        if (alreadyCoachedBy(athleteUser, coach)) {
            throw new ConflictException("Vous êtes déjà suivi par " + coach.getFullName() + ".");
        }
        coachingRequests.requireNoCurrentCoach(athleteUser,
                "Vous êtes déjà suivi par un coach. Mettez d'abord fin à ce coaching depuis votre "
                        + "profil, puis rouvrez ce lien.");
        if (!athleteUser.isEmailVerified()) {
            // Le lien est retenu : il servira de lui-même à la confirmation de l'adresse.
            account.setPendingInviteToken(link.getToken());
            return new InviteJoinResponse("VERIFY_EMAIL", coach.getFullName());
        }
        return doJoin(account, link, message);
    }

    /**
     * Rejoint par le lien retenu à l'inscription, une fois l'adresse confirmée. Jamais bloquant :
     * un lien devenu inutilisable entre-temps est simplement oublié.
     *
     * @return le nom du coach rejoint (ou sollicité), s'il y en a un
     */
    @Transactional
    public Optional<String> joinPendingAfterVerification(UUID userId) {
        AthleteAccount account = accountRepository.findByUserId(userId).orElse(null);
        if (account == null || account.getPendingInviteToken() == null
                || !account.getUser().isEmailVerified()) {
            return Optional.empty();
        }
        String token = account.getPendingInviteToken();
        account.setPendingInviteToken(null);
        try {
            return Optional.ofNullable(join(userId, token, null).coachName());
        } catch (RuntimeException e) {
            log.info("Lien d'invitation retenu non utilisé (user={}) : {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    private InviteJoinResponse doJoin(AthleteAccount account, InviteLink link, String message) {
        User coach = link.getCoach();
        String athleteName = (account.getFirstName() + " " + account.getLastName()).trim();
        CoachingRequest request = requestRepository
                .findFirstByAthleteAccountIdAndCoachIdAndStatus(account.getId(), coach.getId(),
                        CoachingRequestStatus.PENDING)
                .orElse(null);
        if (request != null && !link.isDirectEntry()) {
            throw new ConflictException("Vous avez déjà une demande en attente auprès de ce coach.");
        }
        if (request == null) {
            request = new CoachingRequest();
            request.setAthleteAccount(account);
            request.setCoach(coach);
            request.setStatus(CoachingRequestStatus.PENDING);
            request.setExpiresAt(Instant.now().plus(REQUEST_VALIDITY_DAYS, ChronoUnit.DAYS));
        }
        request.setInviteLink(link);
        request.setMessage(StringUtils.hasText(message) ? message.trim()
                : "Arrivé par votre lien d'invitation" + (link.getLabel() != null ? " « " + link.getLabel() + " »." : "."));
        request = requestRepository.save(request);
        link.setUses(link.getUses() + 1);

        if (link.isDirectEntry()) {
            coachingRequests.startCoaching(request);
            notificationService.notifyJoinedByInvite(coach, athleteName, link.getLabel());
            log.info("Entrée directe par le lien {} (relation {})", link.getId(), request.getCreatedRelationId());
            return new InviteJoinResponse("JOINED", coach.getFullName());
        }
        notificationService.notifyCoachingRequestReceived(coach, athleteName);
        log.info("Demande {} déposée par le lien {}", request.getId(), link.getId());
        return new InviteJoinResponse("REQUESTED", coach.getFullName());
    }

    private boolean alreadyCoachedBy(User athleteUser, User coach) {
        if (athleteUser.getAthlete() == null) {
            return false;
        }
        return relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(athleteUser.getAthlete().getId())
                .map(CoachAthleteRelation::getCoach)
                .map(c -> c.getId().equals(coach.getId()))
                .orElse(false);
    }

    private InviteLink requireUsable(String token) {
        InviteLink link = linkRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Ce lien d'invitation n'existe pas."));
        String reason = link.unusableReason(Instant.now());
        if (reason != null) {
            throw new ConflictException(reason);
        }
        return link;
    }

    private User requireCoach(UUID coachId) {
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new NotFoundException("Compte introuvable."));
        if (coach.getRole() != UserRole.HEAD_COACH && coach.getRole() != UserRole.COACH) {
            throw new ForbiddenException("Seul un coach peut créer un lien d'invitation.");
        }
        return coach;
    }

    private InviteLinkResponse render(InviteLink link) {
        return new InviteLinkResponse(link.getId(), link.getToken(), shortUrl(link.getToken()),
                link.getLabel(), link.isClubMembership(), link.isDirectEntry(),
                link.getGroup() != null ? link.getGroup().getId() : null,
                link.getGroup() != null ? link.getGroup().getName() : null,
                link.getMaxUses(), link.getUses(), link.getExpiresAt(), link.getRevokedAt(),
                link.unusableReason(Instant.now()), link.getCreatedAt());
    }

    public String shortUrl(String token) {
        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        return base + "/j/" + token;
    }

    private String newToken() {
        String token;
        do {
            StringBuilder sb = new StringBuilder(TOKEN_LENGTH);
            for (int i = 0; i < TOKEN_LENGTH; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            token = sb.toString();
        } while (linkRepository.existsByToken(token));
        return token;
    }
}
