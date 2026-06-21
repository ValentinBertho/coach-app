package com.coachrun.dto.response;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        UserStatus status,
        UUID clubId,
        String clubName,
        UUID athleteId,
        Instant createdAt) {

    public static AdminUserResponse from(User u) {
        return new AdminUserResponse(
                u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getStatus(),
                u.getClub() != null ? u.getClub().getId() : null,
                u.getClub() != null ? u.getClub().getName() : null,
                u.getAthlete() != null ? u.getAthlete().getId() : null,
                u.getCreatedAt());
    }
}
