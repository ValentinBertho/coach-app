package com.coachrun.dto.response;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
        List<RefResponse> additionalClubs,
        Instant createdAt) {

    public static AdminUserResponse from(User u) {
        List<RefResponse> additionalClubs = u.getAdditionalClubs().stream()
                .map(c -> new RefResponse(c.getId(), c.getName()))
                .sorted(Comparator.comparing(RefResponse::name, Comparator.nullsLast(String::compareTo)))
                .toList();
        return new AdminUserResponse(
                u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getStatus(),
                u.getClub() != null ? u.getClub().getId() : null,
                u.getClub() != null ? u.getClub().getName() : null,
                u.getAthlete() != null ? u.getAthlete().getId() : null,
                additionalClubs,
                u.getCreatedAt());
    }
}
