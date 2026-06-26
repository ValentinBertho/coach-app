package com.coachrun.service;

import com.coachrun.dto.response.AthleteAccessResponse;
import com.coachrun.dto.response.AthleteAccessResponse.PermissionEntry;
import com.coachrun.dto.response.ClubMemberResponse;
import com.coachrun.entity.AthleteCoachPermission;
import com.coachrun.entity.Club;
import com.coachrun.entity.ClubMember;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.AthleteOwnershipType;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.entity.enums.PermissionLevel;
import com.coachrun.entity.enums.UserRole;
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

    /**
     * Ajoute un coach <strong>existant</strong> (compte Darilab) au club avec un rôle donné, et lui
     * accorde l'accès tenant (club additionnel). L'invitation d'un coach <em>sans compte</em>
     * (création + e-mail) est un chantier ultérieur.
     */
    @Transactional
    public ClubMemberResponse addCoach(UUID clubId, String email, ClubRole role, UUID invitedByUserId) {
        User coach = userRepository.findByEmailIgnoreCase(email == null ? "" : email.trim())
                .orElseThrow(() -> new NotFoundException(
                        "Aucun compte avec cet e-mail. Le coach doit déjà avoir un compte Darilab."));
        if (coach.getRole() == UserRole.ATHLETE || coach.getRole() == UserRole.PLATFORM_ADMIN) {
            throw new ConflictException("Ce compte n'est pas un coach.");
        }
        if (memberRepository.findByClubIdAndCoachIdAndActiveTrue(clubId, coach.getId()).isPresent()) {
            throw new ConflictException("Ce coach est déjà membre du club.");
        }
        Club club = clubRepository.getReferenceById(clubId);
        ClubMember member = new ClubMember();
        member.setClub(club);
        member.setCoach(coach);
        member.setClubRole(role != null ? role : ClubRole.COACH_ASSISTANT);
        if (invitedByUserId != null) {
            member.setInvitedBy(userRepository.getReferenceById(invitedByUserId));
        }
        member.setActive(true);
        memberRepository.save(member);

        // Accès tenant au club additionnel (clubAccessValidator) si ce n'est pas son club principal.
        if (coach.getClub() == null || !clubId.equals(coach.getClub().getId())) {
            coach.getAdditionalClubs().add(club);
        }
        return ClubMemberResponse.from(member);
    }

    /** Retire un coach du club (désactive l'adhésion). Le propriétaire ne peut pas être retiré. */
    @Transactional
    public void removeCoach(UUID clubId, UUID coachId) {
        ClubMember member = memberRepository.findByClubIdAndCoachIdAndActiveTrue(clubId, coachId)
                .orElseThrow(() -> new NotFoundException("Membre introuvable."));
        if (member.getClubRole() == ClubRole.OWNER) {
            throw new ConflictException("Le propriétaire du club ne peut pas être retiré.");
        }
        member.setActive(false);
        User coach = member.getCoach();
        if (coach.getClub() == null || !clubId.equals(coach.getClub().getId())) {
            coach.getAdditionalClubs().removeIf(c -> c.getId().equals(clubId));
        }
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
