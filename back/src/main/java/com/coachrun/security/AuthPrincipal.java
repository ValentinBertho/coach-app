package com.coachrun.security;

import com.coachrun.entity.enums.UserRole;

import java.util.UUID;

/**
 * Identité authentifiée placée dans le SecurityContext (principal). Porte le scoping tenant
 * ({@code clubId}) et, pour un compte ATHLETE, son {@code athleteId}.
 *
 * <p>{@code impersonatorUserId} dit qu'on agit depuis une session <b>empruntée</b>, et par qui.
 * Le jeton d'impersonation portait ce renseignement (claim {@code imp}) depuis l'origine sans que
 * personne ne le lise : les actions faites au nom d'un utilisateur étaient donc indiscernables des
 * siennes, y compris au journal d'audit. Le porter sur le principal le rend disponible partout où
 * la question « qui, vraiment ? » se pose.</p>
 */
public record AuthPrincipal(UUID userId, UUID clubId, UUID athleteId, String email, UserRole role,
                            UUID impersonatorUserId) {

    /** Session ordinaire : personne derrière le volant que le titulaire du compte. */
    public AuthPrincipal(UUID userId, UUID clubId, UUID athleteId, String email, UserRole role) {
        this(userId, clubId, athleteId, email, role, null);
    }

    /** Vrai quand ce principal vient d'un jeton d'impersonation. */
    public boolean impersonated() {
        return impersonatorUserId != null;
    }
}
