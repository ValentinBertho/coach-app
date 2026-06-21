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
        UUID athleteId) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getClub() != null ? user.getClub().getId() : null,
                user.getClub() != null ? user.getClub().getName() : null,
                user.getAthlete() != null ? user.getAthlete().getId() : null);
    }
}
