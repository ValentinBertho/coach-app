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
 *
 * <p><b>Relation close et athlète historique ne sont pas la même chose.</b> Un athlète créé avant
 * le modèle multi-coach n'a aucune relation référente et reste joignable par les coachs de son club
 * — sans ce repli, tout athlète non backfillé serait devenu inaccessible. Un athlète dont la
 * relation référente a été <em>close</em> se présentait exactement pareil, et bénéficiait donc du
 * même repli : le coach qu'on venait d'en détacher récupérait l'écriture par l'accès club. Pour un
 * coach indépendant, dont le club porte la fiche de tous ses athlètes, la fin de relation n'aurait
 * ainsi rien retiré du tout. Les deux cas se distinguent par l'existence d'une relation référente,
 * active ou close ({@code existsByAthleteIdAndReferentTrue}).</p>
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

        // Pas de relation référente ACTIVE : deux situations très différentes se présentent ici
        // de la même façon, et les confondre revenait à ne jamais pouvoir retirer un coach.
        //
        //   1. L'athlète n'a JAMAIS eu de référent (données antérieures au modèle multi-coach,
        //      avant backfill). On retombe sur l'accès club historique pour ne pas le verrouiller.
        //   2. Sa relation référente a été CLOSE. Le coach détaché ne doit plus rien voir — or le
        //      repli lui rendait l'écriture par l'accès club, et le lui rendait précisément dans
        //      le cas nominal du coach indépendant, dont le club est celui qui porte la fiche de
        //      tous ses athlètes. Clore une relation n'aurait alors retiré aucun accès.
        //
        // C'est l'existence d'une relation référente — active ou close — qui les sépare.
        if (referent == null) {
            return relationRepository.existsByAthleteIdAndReferentTrue(athleteId)
                    ? Optional.empty()                          // relation close ⇒ refus
                    : clubLevelFallback(coachId, athleteId);    // jamais de référent ⇒ repli
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

        // 4. Athlète club : accès par défaut de tout coach ayant accès au club. Un athlète
        //    « club » est partagé au sein du club — la confidentialité passe par le statut privé,
        //    qui reste étanche y compris au propriétaire (traité plus haut).
        //
        //    Le niveau dépend du rôle club. Le propriétaire et le coach principal écrivent
        //    d'emblée sur tout le club : ils n'obtenaient que la lecture, si bien que le coach
        //    principal ne pouvait pas prescrire à un athlète du club sans se faire accorder une
        //    permission athlète par athlète — sur son propre club. L'assistant, lui, garde la
        //    lecture : c'est la définition même de son rôle, et l'écriture s'obtient par une
        //    permission explicite ou par la relation référente.
        UUID clubId = referent.getClub().getId();
        if (hasClubAccess(coachId, clubId)) {
            level = PermissionLevel.max(level, clubDefaultLevel(coachId, clubId));
        }

        return Optional.ofNullable(level);
    }

    /**
     * Niveau accordé d'office à un coach sur les athlètes <b>club</b>, selon son rôle au club :
     * écriture pour le propriétaire et le coach principal, lecture pour les autres.
     *
     * <p>Un coach ayant accès au club sans y être membre déclaré (club additionnel, données
     * antérieures) retombe sur la lecture : le rôle club est ce qui distingue, et à défaut de
     * rôle on ne suppose pas le plus fort.</p>
     */
    private PermissionLevel clubDefaultLevel(UUID coachId, UUID clubId) {
        ClubRole role = clubMemberRepository.findByClubIdAndCoachIdAndActiveTrue(clubId, coachId)
                .map(ClubMember::getClubRole)
                .orElse(null);
        return (role == ClubRole.OWNER || role == ClubRole.COACH_PRINCIPAL)
                ? PermissionLevel.WRITE
                : PermissionLevel.READ;
    }

    /** Le coach a-t-il accès au club (club principal ou club additionnel) ? */
    private boolean hasClubAccess(UUID coachId, UUID clubId) {
        User coach = userRepository.findById(coachId).orElse(null);
        if (coach != null && coach.getClub() != null && clubId.equals(coach.getClub().getId())) {
            return true;
        }
        return userRepository.hasClubAccess(coachId, clubId);
    }

    /**
     * Accès de repli pour un athlète qui n'a <b>jamais</b> eu de relation référente (données
     * antérieures au modèle multi-coach) : un coach du même club conserve l'accès complet qu'il
     * avait avant ce durcissement. Sans cela, tout athlète non backfillé serait devenu inaccessible.
     *
     * <p>L'appelant doit avoir vérifié qu'aucune relation référente n'a jamais existé. Appliqué à
     * un athlète dont la relation a été close, ce repli rendrait l'écriture au coach qu'on vient
     * d'en détacher — c'était le défaut, et la garde est chez l'appelant parce que c'est là que la
     * distinction est disponible.</p>
     */
    private Optional<PermissionLevel> clubLevelFallback(UUID coachId, UUID athleteId) {
        Athlete athlete = athleteRepository.findById(athleteId).orElse(null);
        if (athlete == null || athlete.getClub() == null) {
            return Optional.empty();
        }
        return hasClubAccess(coachId, athlete.getClub().getId())
                ? Optional.of(PermissionLevel.WRITE)
                : Optional.empty();
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
