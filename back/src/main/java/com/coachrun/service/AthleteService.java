package com.coachrun.service;

import com.coachrun.dto.request.AthleteRequest;
import com.coachrun.dto.response.AthleteInvitationResponse;
import com.coachrun.dto.response.AthleteResponse;
import com.coachrun.dto.response.AthleteSummaryResponse;
import com.coachrun.dto.response.InvitationInfoResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
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
    private final com.coachrun.repository.TrainingGroupRepository groupRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public PageResponse<AthleteSummaryResponse> list(UUID clubId, AthleteStatus status,
                                                     UUID groupId, String query, Pageable pageable) {
        String q = StringUtils.hasText(query) ? query.trim() : null;
        return PageResponse.from(
                athleteRepository.search(clubId, status, groupId, q, pageable),
                AthleteSummaryResponse::from);
    }

    public AthleteResponse get(UUID clubId, UUID athleteId) {
        return AthleteResponse.from(requireAthlete(clubId, athleteId));
    }

    @Transactional
    public AthleteResponse create(UUID clubId, AthleteRequest request) {
        Athlete athlete = new Athlete();
        athlete.setClub(clubRepository.getReferenceById(clubId));
        athlete.setStatus(AthleteStatus.ACTIVE);
        apply(athlete, request);
        athlete = athleteRepository.save(athlete);
        log.info("Athlète créé {} (club={})", athlete.getId(), clubId);
        return AthleteResponse.from(athlete);
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
