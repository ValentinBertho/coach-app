package com.coachrun.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Export RGPD (portabilité) des données d'un athlète : profil + séances + activités
 * + traçabilité du consentement.
 */
public record AthleteExportResponse(
        Instant exportedAt,
        Instant healthDataConsentAt,
        AthleteResponse profile,
        List<WorkoutResponse> workouts,
        List<ActivityResponse> activities) {
}
