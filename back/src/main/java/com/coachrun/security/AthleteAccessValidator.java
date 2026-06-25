package com.coachrun.security;

import com.coachrun.entity.AthleteCoachPermission;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.ClubMember;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.entity.enums.PermissionLevel;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.AthleteCoachPermissionRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubMemberRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Résout l'accès d'un coach à un athlète selon le modèle DARI Lab (équivalent applicatif des
 * policies RLS Supabase du cahier des charges). À utiliser dans les {@code @PreAuthorize} :
 * {@code @PreAuthorize("@athleteAccessValidator.canWrite(authentication, #athleteId)")}.
 *
 * <p>Règles (par ordre de priorité) :</p>
 * <ol>
 *   <li>coach <strong>référent</strong> (relation active) ⇒ {@link PermissionLevel#WRITE} ;</li>
 *   <li><strong>permission explicite</strong> non expirée ⇒ son niveau ({@code read/comment/write}) ;</li>
 *   <li>athlète <strong>club</strong> + coach {@code OWNER}/{@code COACH_PRINCIPAL} du même club
 *       ⇒ {@link PermissionLevel#READ} par défaut.</li>
 * </ol>
 * Un athlète <strong>privé</strong> ({@code club == null} sur la relation référente) n'est jamais
 * accessible à un autre coach, quelles que soient les permissions ou le rôle club. Le
 * {@code PLATFORM_ADMIN} a un accès transverse ; un compte {@code ATHLETE} n'a aucun accès coach.
 */
@Component("athleteAccessValidator")
public class AthleteAccessValidator {

    private final CoachAthleteRelationRepository relationRepository;
    private final AthleteCoachPermissionRepository permissionRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final AthleteRepository athleteRepository;
    private final UserRepository userRepository;

    public AthleteAccessValidator(CoachAthleteRelationRepository relationRepository,
                                  AthleteCoachPermissionRepository permissionRepository,
                                  ClubMemberRepository clubMemberRepository,
                                  AthleteRepository athleteRepository,
                                  UserRepository userRepository) {
        this.relationRepository = relationRepository;
        this.permissionRepository = permissionRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.athleteRepository = athleteRepository;
        this.userRepository = userRepository;
    }

    /** Niveau effectif du coach authentifié sur l'athlète, ou vide s'il n'y a aucun accès. */
    public Optional<PermissionLevel> effectiveLevel(Authentication authentication, UUID athleteId) {
        if (authentication == null || athleteId == null
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return Optional.empty();
        }
        if (principal.role() == UserRole.PLATFORM_ADMIN) {
            return Optional.of(PermissionLevel.WRITE);
        }
        // Les athlètes n'empruntent jamais les routes coach (ils passent par /me/**).
        if (principal.role() == UserRole.ATHLETE) {
            return Optional.empty();
        }
        return effectiveLevel(principal.userId(), athleteId);
    }

    /** Variante par identifiant de coach (utile hors contexte de sécurité, ex. services). */
    public Optional<PermissionLevel> effectiveLevel(UUID coachId, UUID athleteId) {
        if (coachId == null || athleteId == null) {
            return Optional.empty();
        }

        // 1. Coach référent ⇒ accès complet (privé ou club).
        Optional<CoachAthleteRelation> ownRelation =
                relationRepository.findByCoachIdAndAthleteIdAndActiveTrue(coachId, athleteId);
        if (ownRelation.map(CoachAthleteRelation::isReferent).orElse(false)) {
            return Optional.of(PermissionLevel.WRITE);
        }

        // La relation référente porte le rattachement privé/club de l'athlète.
        CoachAthleteRelation referent =
                relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(athleteId).orElse(null);

        // Athlète hors modèle multi-coach (legacy / avant backfill) : on retombe sur l'accès
        // club-level historique pour ne JAMAIS verrouiller. L'enforcement fin (privé, permissions)
        // s'applique dès qu'une relation référent existe — la création d'athlète et le backfill
        // la créent désormais systématiquement.
        if (referent == null) {
            return clubLevelFallback(coachId, athleteId);
        }

        // Un athlète privé n'est jamais partagé : aucun accès hors référent.
        if (referent.isPrivate()) {
            return Optional.empty();
        }

        PermissionLevel level = null;

        // 2. Coach explicitement assigné (ManyToMany de production) ⇒ écriture.
        if (athleteRepository.existsByIdAndCoaches_Id(athleteId, coachId)) {
            level = PermissionLevel.WRITE;
        }

        // 3. Permission explicite non expirée.
        AthleteCoachPermission permission =
                permissionRepository.findByAthleteIdAndCoachId(athleteId, coachId).orElse(null);
        if (permission != null && permission.isActiveAt(Instant.now())) {
            level = PermissionLevel.max(level, permission.getPermission());
        }

        // 4. Lecture par défaut pour Owner / coach principal du club de l'athlète.
        UUID clubId = referent.getClub().getId();
        ClubMember member =
                clubMemberRepository.findByClubIdAndCoachIdAndActiveTrue(clubId, coachId).orElse(null);
        if (member != null && (member.getClubRole() == ClubRole.OWNER
                || member.getClubRole() == ClubRole.COACH_PRINCIPAL)) {
            level = PermissionLevel.max(level, PermissionLevel.READ);
        }

        return Optional.ofNullable(level);
    }

    /**
     * Accès de repli pour un athlète sans relation référent (données antérieures au modèle
     * multi-coach) : un coach du même club que l'athlète conserve l'accès complet qu'il avait
     * avant ce durcissement. Sans cela, tout athlète non backfillé deviendrait inaccessible.
     */
    private Optional<PermissionLevel> clubLevelFallback(UUID coachId, UUID athleteId) {
        Athlete athlete = athleteRepository.findById(athleteId).orElse(null);
        if (athlete == null || athlete.getClub() == null) {
            return Optional.empty();
        }
        UUID clubId = athlete.getClub().getId();
        User coach = userRepository.findById(coachId).orElse(null);
        boolean sameMainClub = coach != null && coach.getClub() != null
                && clubId.equals(coach.getClub().getId());
        if (sameMainClub || userRepository.hasClubAccess(coachId, clubId)) {
            return Optional.of(PermissionLevel.WRITE);
        }
        return Optional.empty();
    }

    public boolean canRead(Authentication authentication, UUID athleteId) {
        return effectiveLevel(authentication, athleteId).isPresent();
    }

    public boolean canComment(Authentication authentication, UUID athleteId) {
        return effectiveLevel(authentication, athleteId)
                .map(l -> l.atLeast(PermissionLevel.COMMENT)).orElse(false);
    }

    public boolean canWrite(Authentication authentication, UUID athleteId) {
        return effectiveLevel(authentication, athleteId)
                .map(l -> l.atLeast(PermissionLevel.WRITE)).orElse(false);
    }
}
