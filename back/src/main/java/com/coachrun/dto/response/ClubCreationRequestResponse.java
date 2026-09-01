package com.coachrun.dto.response;

import com.coachrun.entity.ClubCreationRequest;
import com.coachrun.entity.enums.ClubRequestStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Une demande de création de club, telle que le back-office plateforme l'affiche.
 *
 * <p>{@code activationUrl} n'est jamais rempli ici : il n'existe qu'une fois, dans la réponse à
 * la validation, pour le cas où l'envoi d'e-mails est éteint. Une liste qui le porterait ferait
 * de chaque consultation un moyen de reprendre la main sur un compte.</p>
 */
public record ClubCreationRequestResponse(
        UUID id,
        String clubName,
        String fullName,
        String email,
        String phone,
        String message,
        ClubRequestStatus status,
        Instant createdAt,
        Instant reviewedAt,
        String reviewedByEmail,
        String reviewNote,
        UUID createdClubId,
        UUID createdUserId) {

    public static ClubCreationRequestResponse from(ClubCreationRequest r) {
        return new ClubCreationRequestResponse(
                r.getId(),
                r.getClubName(),
                r.getFullName(),
                r.getEmail(),
                r.getPhone(),
                r.getMessage(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getReviewedAt(),
                r.getReviewedByEmail(),
                r.getReviewNote(),
                r.getCreatedClubId(),
                r.getCreatedUserId());
    }
}
