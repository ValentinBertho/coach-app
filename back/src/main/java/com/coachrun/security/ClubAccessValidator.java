package com.coachrun.security;

import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Validateur de scoping multi-tenant utilisé dans les @PreAuthorize :
 * {@code @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")}.
 * Un utilisateur accède aux ressources de son club principal ET de ses clubs
 * additionnels (modèle multi-club, anti-IDOR). Le PLATFORM_ADMIN a accès transverse.
 */
@Component("clubAccessValidator")
public class ClubAccessValidator {

    private final UserRepository userRepository;

    public ClubAccessValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean hasAccess(Authentication authentication, UUID clubId) {
        if (authentication == null || clubId == null
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return false;
        }
        if (principal.role() == UserRole.PLATFORM_ADMIN) {
            return true;
        }
        // Les athlètes passent par /me/** : pas d'accès aux routes club (anti-fuite intra-club).
        if (principal.role() == UserRole.ATHLETE) {
            return false;
        }
        // Voie rapide : club principal porté par le token.
        if (clubId.equals(principal.clubId())) {
            return true;
        }
        // Sinon : appartenance à un club additionnel (modèle multi-club).
        return userRepository.hasClubAccess(principal.userId(), clubId);
    }
}
