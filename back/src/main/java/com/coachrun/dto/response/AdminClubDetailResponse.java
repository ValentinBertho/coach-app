package com.coachrun.dto.response;

import com.coachrun.entity.enums.ClubStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fiche club : qui l'anime, qui s'y entraîne, et ce que sa suppression détruirait.
 *
 * <p><b>Pourquoi l'aperçu d'impact.</b> La suppression d'un club efface en cascade ses coachs,
 * ses athlètes, leurs séances et leurs sorties importées — un historique d'entraînement qui ne se
 * reconstitue pas. La modale disait « et toutes ses données » sans jamais dire combien. Les
 * compteurs ci-dessous sont exactement ce qu'on veut lire avant de recopier le mot de
 * confirmation.</p>
 */
public record AdminClubDetailResponse(
        UUID id,
        String name,
        String slug,
        ClubStatus status,
        Instant createdAt,
        long coaches,
        long athletes,
        long athletesActive,
        long athletesPaused,
        long athletesArchived,
        long pendingInvitations,
        long workouts,
        long activities,
        long activities30d,
        long deviceConnections,
        /** Dernière sortie enregistrée : dit d'un coup d'œil si le club est encore vivant. */
        LocalDate lastActivityDate,
        List<Member> members) {

    /** Un encadrant du club, avec ce qu'il faut pour décider s'il faut le contacter. */
    public record Member(
            UUID id,
            String fullName,
            String email,
            String role,
            String roleLabel,
            String status,
            boolean primaryClub,
            Instant lastSeenAt) {
    }
}
