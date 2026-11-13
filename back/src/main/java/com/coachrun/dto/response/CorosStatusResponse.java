package com.coachrun.dto.response;

/** État de la connexion COROS d'un athlète. */
public record CorosStatusResponse(
        /** L'intégration est ouverte sur ce serveur ({@code app.coros.enabled}). */
        boolean configured,
        boolean connected,
        /** Identifiant COROS de l'utilisateur, opaque ; null si COROS ne l'a pas fourni. */
        String providerUserId,
        /** Serveur régional COROS du compte : « EU », « US », « CN », ou null si inconnu. */
        String region,
        /** Séances à venir actuellement dans son calendrier COROS (hors annulées). */
        long workoutsOnCoros,
        /** Séances à venir dont l'envoi a échoué (elles seront retentées). */
        long workoutsInError,
        /** Sorties créées dans Darilab depuis son compte COROS. */
        long activitiesImported
) {
}
