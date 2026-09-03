package com.coachrun.dto.response;

/** État de la connexion Strava d'un athlète. */
public record StravaStatusResponse(
        boolean configured,
        boolean connected,
        String providerAthleteId,
        Long lastImportEpoch,

        /**
         * L'athlète a demandé que le renommage se répercute sur son compte Strava.
         *
         * <p>Son consentement, et rien d'autre : il peut être vrai alors que {@link
         * #canRenameOnStrava()} est faux, si Strava ne nous a pas accordé l'écriture. L'écran
         * doit alors dire quoi faire (se reconnecter), pas décocher la case à sa place.</p>
         */
        boolean renameOnStrava,

        /** Strava nous a bien accordé le droit d'écrire sur ce compte ({@code activity:write}). */
        boolean canRenameOnStrava
) {
}
