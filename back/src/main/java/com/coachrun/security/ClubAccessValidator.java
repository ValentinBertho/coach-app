package com.coachrun.security;

import com.coachrun.entity.enums.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Validateur de scoping multi-tenant utilisé dans les @PreAuthorize :
 * {@code @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")}.
 * Un utilisateur n'accède qu'aux ressources de son propre club (anti-IDOR).
 * Le PLATFORM_ADMIN a accès transverse.
 */
@Component("clubAccessValidator")
public class ClubAccessValidator {

    public boolean hasAccess(Authentication authentication, UUID clubId) {
        if (authentication == null || clubId == null
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            return false;
        }
        if (principal.role() == UserRole.PLATFORM_ADMIN) {
            return true;
        }
        return clubId.equals(principal.clubId());
    }
}
