package com.coachrun.dto.response;

import java.util.List;

/** État de la connexion Garmin Connect d'un athlète. */
public record GarminStatusResponse(
        /** L'intégration est configurée sur ce serveur (Client ID et secret présents). */
        boolean configured,
        boolean connected,
        /** Identifiant Garmin de l'utilisateur, stable d'une connexion à l'autre. */
        String providerUserId,

        /**
         * Permissions accordées par l'athlète chez Garmin ({@code WORKOUT_IMPORT},
         * {@code ACTIVITY_EXPORT}…). Il peut en refuser au consentement, ou les couper plus tard
         * depuis son compte Garmin Connect : la liste suit alors la notification de Garmin.
         */
        List<String> permissions,

        /** Garmin nous autorise à déposer des séances dans son calendrier ({@code WORKOUT_IMPORT}). */
        boolean canSendWorkouts,

        /** Garmin nous autorise à recevoir ses activités ({@code ACTIVITY_EXPORT}). */
        boolean canImportActivities
) {
}
