package com.coachrun.dto.response;

import com.coachrun.entity.enums.ClubRole;

import java.util.UUID;

/**
 * Un espace de travail auquel le coach a accès.
 *
 * <p>Le modèle multi-club existait depuis l'origine — {@code User.additionalClubs}, {@code ClubMember}
 * avec son rôle, et un validateur d'accès qui accepte explicitement les clubs additionnels — mais
 * <b>aucune route ne les listait</b>. Le front lisait {@code currentUser().clubId}, c'est-à-dire le
 * seul club principal, et le passait à tous les appels. Un coach invité dans un second club voyait
 * donc son adhésion créée, l'accès autorisé côté serveur… et jamais ce club : sa seule issue était
 * d'ouvrir un second compte.</p>
 */
public record MyClubResponse(
        UUID id,
        String name,
        /** Vrai pour l'espace où le coach a été créé : celui qui sert de défaut. */
        boolean primary,
        /** Son rôle ici, qui n'est pas le même partout : propriétaire chez lui, assistant ailleurs. */
        ClubRole role,
        String roleLabel,
        /** L'espace d'un coach indépendant : on ne lui parle pas de « club ». */
        boolean soloPractice) {

    public static String label(ClubRole role) {
        if (role == null) {
            return "Accès";
        }
        return switch (role) {
            case OWNER -> "Propriétaire";
            case COACH_PRINCIPAL -> "Coach principal";
            case COACH_ASSISTANT -> "Coach assistant";
        };
    }
}
