package com.coachrun.dto.response;

/** État de la connexion Strava d'un athlète. */
public record StravaStatusResponse(
        boolean configured,
        boolean connected,
        String providerAthleteId,
        Long lastImportEpoch
) {
}
