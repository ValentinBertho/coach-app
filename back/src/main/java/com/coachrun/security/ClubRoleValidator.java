package com.coachrun.security;

import com.coachrun.entity.ClubMember;
import com.coachrun.entity.enums.ClubRole;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.ClubMemberRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Rôle d'un coach <b>au sein d'un club</b>, pour les {@code @PreAuthorize} :
 * {@code @PreAuthorize("@clubRoleValidator.isOwner(authentication, #clubId)")}.
 *
 * <p><b>Pourquoi ce fichier existe.</b> {@link com.coachrun.entity.enums.ClubRole} décrit depuis le
 * début un modèle à trois niveaux — le propriétaire « invite et retire des coachs », le coach
 * principal gère des athlètes, l'assistant a un accès limité — mais <b>aucune autorisation ne le
 * consultait</b>. La composition du club était donc protégée par le seul contrôle d'appartenance :
 * n'importe quel membre pouvait ajouter un coach, et en retirer n'importe quel autre. Seul le
 * propriétaire était protégé, et encore, par une vérification enfouie dans le service.</p>
 *
 * <p>Concrètement, dans un club à trois coachs, un assistant pouvait éjecter le coach principal et
 * ses collègues — en emportant leur accès à leurs propres athlètes.</p>
 *
 * <p>Le périmètre est volontairement étroit : ce validateur ne garde que la composition du club.
 * L'accès aux athlètes reste l'affaire de {@link AthleteAccessValidator}, qui a son propre modèle
 * (référent, permissions explicites, athlète privé) et n'a pas à être dupliqué ici.</p>
 */
@Component("clubRoleValidator")
public class ClubRoleValidator {

    private final ClubMemberRepository memberRepository;

    public ClubRoleValidator(ClubMemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * Le compte authentifié est-il propriétaire de ce club ?
     *
     * <p>Le {@code PLATFORM_ADMIN} passe : c'est lui qu'on appelle quand un propriétaire perd son
     * accès, et le back-office n'aurait aucun autre moyen de rétablir la situation.</p>
     */
    public boolean isOwner(Authentication authentication, UUID clubId) {
        return hasRole(authentication, clubId, ClubRole.OWNER);
    }

    private boolean hasRole(Authentication authentication, UUID clubId, ClubRole required) {
        if (authentication == null || clubId == null
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return false;
        }
        if (principal.role() == UserRole.PLATFORM_ADMIN) {
            return true;
        }
        if (principal.role() == UserRole.ATHLETE) {
            return false;
        }
        return memberRepository.findByClubIdAndCoachIdAndActiveTrue(clubId, principal.userId())
                .map(ClubMember::getClubRole)
                .filter(required::equals)
                .isPresent();
    }
}
