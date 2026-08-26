package com.coachrun.dto.response;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Fiche complète d'un compte, telle qu'on en a besoin pour traiter un ticket de support.
 *
 * <p><b>Ce qu'elle ajoute à {@code AdminUserResponse}.</b> Les trois quarts des demandes de
 * support portent sur l'un de ces champs, qu'aucun écran n'exposait : l'adresse est-elle vérifiée,
 * quand cette personne s'est-elle connectée pour la dernière fois, à quels clubs est-elle
 * rattachée, a-t-elle un appareil abonné aux notifications, sa montre est-elle connectée. Il
 * fallait ouvrir la base pour chacun.</p>
 *
 * <p><b>Aucune donnée de santé.</b> La fiche renvoie l'identifiant de l'athlète associé, jamais
 * ses valeurs physiologiques ni ses notes médicales : celles-ci ne se consultent que depuis
 * l'écran athlète, qui porte ses propres gardes.</p>
 */
public record AdminUserDetailResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        UserStatus status,
        UUID clubId,
        String clubName,
        UUID athleteId,
        List<RefResponse> additionalClubs,
        boolean emailVerified,
        boolean invitePending,
        Instant inviteExpiresAt,
        Instant termsAcceptedAt,
        Instant lastLoginAt,
        Instant lastSeenAt,
        Instant passwordChangedAt,
        Instant sessionsInvalidatedAt,
        boolean hasPassword,
        /** Compte athlète créé par lien magique : pas d'adresse réelle, donc pas d'e-mail possible. */
        boolean realEmail,
        long pushSubscriptions,
        long coachedAthletes,
        Instant createdAt,
        List<AdminAuditResponse> history) {

    /** Adresse technique des comptes athlète créés par lien magique — jamais délivrable. */
    private static final String SYNTHETIC_EMAIL_SUFFIX = "@athlete.coachrun.local";

    public static AdminUserDetailResponse from(User u,
                                               long pushSubscriptions,
                                               long coachedAthletes,
                                               List<AdminAuditResponse> history) {
        List<RefResponse> additionalClubs = u.getAdditionalClubs().stream()
                .map(c -> new RefResponse(c.getId(), c.getName()))
                .sorted(Comparator.comparing(RefResponse::name, Comparator.nullsLast(String::compareTo)))
                .toList();
        String email = u.getEmail();
        return new AdminUserDetailResponse(
                u.getId(),
                email,
                u.getFullName(),
                u.getRole(),
                u.getStatus(),
                u.getClub() != null ? u.getClub().getId() : null,
                u.getClub() != null ? u.getClub().getName() : null,
                u.getAthlete() != null ? u.getAthlete().getId() : null,
                additionalClubs,
                u.isEmailVerified(),
                u.getInviteToken() != null,
                u.getInviteExpiresAt(),
                u.getTermsAcceptedAt(),
                u.getLastLoginAt(),
                u.getLastSeenAt(),
                u.getPasswordChangedAt(),
                u.getSessionsInvalidatedAt(),
                u.getPasswordHash() != null,
                email != null && !email.endsWith(SYNTHETIC_EMAIL_SUFFIX),
                pushSubscriptions,
                coachedAthletes,
                u.getCreatedAt(),
                history);
    }
}
