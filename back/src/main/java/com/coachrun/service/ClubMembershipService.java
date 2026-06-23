package com.coachrun.service;

import com.coachrun.dto.response.AthleteAccessResponse;
import com.coachrun.dto.response.AthleteAccessResponse.PermissionEntry;
import com.coachrun.dto.response.ClubMemberResponse;
import com.coachrun.entity.AthleteCoachPermission;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AthleteOwnershipType;
import com.coachrun.entity.enums.PermissionLevel;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteCoachPermissionRepository;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Gestion multi-coach DARI Lab : membres du club, statut privé/club d'un athlète et permissions
 * graduées accordées aux coachs non référents. Applique les règles du cahier des charges (§4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubMembershipService {

    private final ClubMemberRepository memberRepository;
    private final CoachAthleteRelationRepository relationRepository;
    private final AthleteCoachPermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final ClubRepository clubRepository;

    public List<ClubMemberResponse> members(UUID clubId) {
        return memberRepository.findByClubIdAndActiveTrue(clubId).stream()
                .map(ClubMemberResponse::from)
                .toList();
    }

    public AthleteAccessResponse access(UUID athleteId) {
        CoachAthleteRelation referent = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(athleteId).orElse(null);
        AthleteOwnershipType ownership = (referent == null || referent.isPrivate())
                ? AthleteOwnershipType.PRIVATE : AthleteOwnershipType.CLUB;
        UUID referentId = referent == null ? null : referent.getCoach().getId();
        String referentName = referent == null ? null : referent.getCoach().getFullName();

        List<PermissionEntry> permissions = permissionRepository.findByAthleteId(athleteId).stream()
                .map(p -> new PermissionEntry(
                        p.getCoach().getId(), p.getCoach().getFullName(),
                        p.getPermission().name(),
                        p.getExpiresAt() == null ? null : p.getExpiresAt().toString()))
                .toList();

        return new AthleteAccessResponse(ownership, referentId, referentName, permissions);
    }

    @Transactional
    public AthleteAccessResponse setOwnership(UUID clubId, UUID athleteId, AthleteOwnershipType ownership) {
        CoachAthleteRelation referent = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(athleteId)
                .orElseThrow(() -> new NotFoundException("Relation référente introuvable."));

        if (ownership == AthleteOwnershipType.CLUB) {
            referent.setClub(clubRepository.getReferenceById(clubId));
        } else {
            // Règle : passage club → privé seulement si aucune permission active.
            boolean hasActivePermission = permissionRepository.findByAthleteId(athleteId).stream()
                    .anyMatch(p -> p.isActiveAt(Instant.now()));
            if (hasActivePermission) {
                throw new ConflictException(
                        "Impossible de passer en privé : des permissions actives existent.");
            }
            referent.setClub(null);
        }
        return access(athleteId);
    }

    @Transactional
    public AthleteAccessResponse grant(UUID athleteId, UUID coachId, PermissionLevel level, Instant expiresAt) {
        CoachAthleteRelation referent = relationRepository
                .findByAthleteIdAndReferentTrueAndActiveTrue(athleteId).orElse(null);
        if (referent == null || referent.isPrivate()) {
            throw new ConflictException("Athlète privé : aucune permission ne peut être accordée.");
        }
        User coach = userRepository.findById(coachId)
                .orElseThrow(() -> new NotFoundException("Coach introuvable."));

        AthleteCoachPermission permission = permissionRepository
                .findByAthleteIdAndCoachId(athleteId, coachId)
                .orElseGet(() -> {
                    AthleteCoachPermission p = new AthleteCoachPermission();
                    p.setAthlete(referent.getAthlete());
                    p.setCoach(coach);
                    return p;
                });
        permission.setPermission(level);
        permission.setExpiresAt(expiresAt);
        permissionRepository.save(permission);
        return access(athleteId);
    }

    @Transactional
    public AthleteAccessResponse revoke(UUID athleteId, UUID coachId) {
        permissionRepository.findByAthleteIdAndCoachId(athleteId, coachId)
                .ifPresent(permissionRepository::delete);
        permissionRepository.flush();
        return access(athleteId);
    }
}
