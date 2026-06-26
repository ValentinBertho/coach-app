package com.coachrun.service;

import com.coachrun.dto.request.AthleteRequest;
import com.coachrun.dto.response.AthleteInvitationResponse;
import com.coachrun.dto.response.AthleteResponse;
import com.coachrun.dto.response.AthleteSummaryResponse;
import com.coachrun.dto.response.InvitationInfoResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.RefResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Gestion des athlètes : CRUD scopé par club (anti-IDOR), archivage et invitation
 * par lien magique. Les données de santé sont chiffrées au repos via l'entité.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteService {

    private static final int INVITE_VALIDITY_DAYS = 14;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AthleteRepository athleteRepository;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final com.coachrun.repository.TrainingGroupRepository groupRepository;
    private final com.coachrun.repository.CoachAthleteRelationRepository relationRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public PageResponse<AthleteSummaryResponse> list(UUID clubId, AthleteStatus status,
                                                     UUID groupId, String query, Pageable pageable) {
        String q = StringUtils.hasText(query) ? query.trim() : "";
        return PageResponse.from(
                athleteRepository.search(clubId, status, groupId, q, pageable),
                AthleteSummaryResponse::from);
    }

    public AthleteResponse get(UUID clubId, UUID athleteId) {
        return AthleteResponse.from(requireAthlete(clubId, athleteId));
    }

    // ---------------------------------------------------------------------
    // Relations many-to-many : coachs et clubs additionnels de l'athlète
    // ---------------------------------------------------------------------

    /** Coachs du club assignables à un athlète (pour l'UI de rattachement). */
    public List<RefResponse> assignableCoaches(UUID clubId) {
        return userRepository.findCoachesByClub(clubId).stream()
                .map(u -> new RefResponse(u.getId(), u.getFullName()))
                .toList();
    }

    @Transactional
    public AthleteResponse assignCoach(UUID clubId, UUID athleteId, UUID coachId) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new NotFoundException("Coach introuvable."));
        if (coach.getRole() != UserRole.HEAD_COACH && coach.getRole() != UserRole.COACH) {
            throw new ConflictException("Seul un coach peut être rattaché à un athlète.");
        }
        athlete.getCoaches().add(coach);
        log.info("Coach {} rattaché à l'athlète {}", coachId, athleteId);
        return AthleteResponse.from(athlete);
    }

    @Transactional
    public AthleteResponse removeCoach(UUID clubId, UUID athleteId, UUID coachId) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        athlete.getCoaches().removeIf(c -> c.getId().equals(coachId));
        return AthleteResponse.from(athlete);
    }

    @Transactional
    public AthleteResponse addClub(UUID clubId, UUID athleteId, UUID targetClubId) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        if (targetClubId.equals(athlete.getClub().getId())) {
            throw new ConflictException("Ce club est déjà le club principal de l'athlète.");
        }
        Club target = clubRepository.findById(targetClubId)
                .orElseThrow(() -> new NotFoundException("Club introuvable."));
        athlete.getAdditionalClubs().add(target);
        log.info("Athlète {} rattaché au club additionnel {}", athleteId, targetClubId);
        return AthleteResponse.from(athlete);
    }

    @Transactional
    public AthleteResponse removeClub(UUID clubId, UUID athleteId, UUID targetClubId) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        athlete.getAdditionalClubs().removeIf(c -> c.getId().equals(targetClubId));
        return AthleteResponse.from(athlete);
    }

    @Transactional
    public AthleteResponse create(UUID clubId, AthleteRequest request, UUID creatorCoachId) {
        Club club = clubRepository.getReferenceById(clubId);
        Athlete athlete = new Athlete();
        athlete.setClub(club);
        athlete.setStatus(AthleteStatus.ACTIVE);
        apply(athlete, request);
        athlete = athleteRepository.save(athlete);
        boolean privat = Boolean.TRUE.equals(request.privateAthlete());
        createReferentRelation(athlete, club, creatorCoachId, privat);
        log.info("Athlète créé {} (club={}, référent={}, privé={})", athlete.getId(), clubId, creatorCoachId, privat);
        return AthleteResponse.from(athlete);
    }

    /**
     * Crée la relation référent (coach créateur ↔ athlète) qui rend l'athlète pilotable par le modèle
     * multi-coach. {@code privat} = athlète privé (relation sans club → invisible des autres coachs).
     * Idempotent : ne recrée pas si elle existe déjà.
     */
    private void createReferentRelation(Athlete athlete, Club club, UUID coachId, boolean privat) {
        if (coachId == null) {
            return; // sécurité : pas de référent identifiable → l'athlète reste sur le fallback club.
        }
        if (relationRepository.findByCoachIdAndAthleteIdAndActiveTrue(coachId, athlete.getId()).isPresent()) {
            return;
        }
        User coach = userRepository.findById(coachId).orElse(null);
        if (coach == null) {
            return;
        }
        com.coachrun.entity.CoachAthleteRelation rel = new com.coachrun.entity.CoachAthleteRelation();
        rel.setAthlete(athlete);
        rel.setCoach(coach);
        rel.setClub(privat ? null : club);   // null = privé (coaching hors club)
        rel.setReferent(true);
        rel.setActive(true);
        relationRepository.save(rel);
    }

    @Transactional
    public AthleteResponse update(UUID clubId, UUID athleteId, AthleteRequest request) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        apply(athlete, request);
        return AthleteResponse.from(athlete);
    }

    @Transactional
    public void archive(UUID clubId, UUID athleteId) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        athlete.setStatus(AthleteStatus.ARCHIVED);
    }

    @Transactional
    public AthleteInvitationResponse invite(UUID clubId, UUID athleteId) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(INVITE_VALIDITY_DAYS, ChronoUnit.DAYS);

        athlete.setInviteToken(token);
        athlete.setInviteExpiresAt(expiresAt);
        // Email d'invitation : délégué au NotificationTriggerService quand MAIL_ENABLED (à venir).
        String url = frontendUrl + "/invitation/" + token;
        log.info("Invitation générée pour l'athlète {} (expire {})", athleteId, expiresAt);
        return new AthleteInvitationResponse(url, expiresAt);
    }

    /** Lecture publique des infos d'invitation (page d'acceptation athlète). */
    public InvitationInfoResponse invitationInfo(String token) {
        Athlete athlete = athleteRepository.findByInviteToken(token)
                .filter(a -> a.getInviteExpiresAt() != null
                        && a.getInviteExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new NotFoundException("Invitation invalide ou expirée."));
        return new InvitationInfoResponse(athlete.getFirstName(), athlete.getClub().getName());
    }

    private Athlete requireAthlete(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }

    private void apply(Athlete athlete, AthleteRequest request) {
        athlete.setFirstName(request.firstName());
        athlete.setLastName(request.lastName());
        athlete.setEmail(StringUtils.hasText(request.email()) ? request.email().toLowerCase() : null);
        athlete.setBirthDate(request.birthDate());
        athlete.setSex(request.sex());
        athlete.setLevel(request.level());
        athlete.setHrMax(request.hrMax());
        athlete.setHrRest(request.hrRest());
        athlete.setVma(request.vma());
        athlete.setWeightKg(request.weightKg());
        athlete.setMedicalNotes(StringUtils.hasText(request.medicalNotes()) ? request.medicalNotes() : null);
        if (request.groupId() != null) {
            athlete.setGroup(groupRepository.findByIdAndClubId(request.groupId(), athlete.getClub().getId())
                    .orElseThrow(() -> new NotFoundException("Groupe introuvable.")));
        } else {
            athlete.setGroup(null);
        }
    }
}
