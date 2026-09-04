package com.coachrun.dto.response;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;

import java.util.UUID;

/** Profil utilisateur courant exposé au front. */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        UUID clubId,
        String clubName,
        UUID athleteId,
        boolean emailVerified,
        /** Unité d'affichage des allures préférée (PACE = min/km, SPEED = km/h). */
        com.coachrun.entity.enums.PaceUnit paceUnit,
        /**
         * L'espace est celui d'un coach indépendant : l'interface cesse de lui parler de « club ».
         *
         * <p>Champ <b>ajouté</b>, jamais retiré ni renommé : les clients encore servis par un
         * service worker antérieur l'ignorent simplement et gardent le vocabulaire d'avant, ce qui
         * est un affichage démodé et non un écran cassé.</p>
         */
        boolean soloPractice) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getClub() != null ? user.getClub().getId() : null,
                user.getClub() != null ? user.getClub().getName() : null,
                user.getAthlete() != null ? user.getAthlete().getId() : null,
                user.isEmailVerified(),
                user.getPaceUnit(),
                user.getClub() != null && user.getClub().isSoloPractice());
    }
}
