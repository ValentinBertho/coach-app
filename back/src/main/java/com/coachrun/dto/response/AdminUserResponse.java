package com.coachrun.dto.response;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Ligne de la liste des comptes.
 *
 * <p>Les quatre derniers champs ont été <b>ajoutés</b> (jamais substitués) : l'état de
 * vérification et la dernière visite sont ce qu'on regarde en premier sur un ticket de support, et
 * le tableau n'en disait rien. Champs additionnels donc optionnels côté TypeScript — des PWA
 * tournent encore sur la version précédente du front (cf. Claude.md §4 bis).</p>
 */
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
        Instant createdAt,
        boolean emailVerified,
        Instant lastSeenAt,
        Instant lastLoginAt,
        boolean invitePending) {

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
                u.getCreatedAt(),
                u.isEmailVerified(),
                u.getLastSeenAt(),
                u.getLastLoginAt(),
                u.getInviteToken() != null);
    }
}
