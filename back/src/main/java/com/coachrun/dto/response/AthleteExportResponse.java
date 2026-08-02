package com.coachrun.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Export RGPD (portabilité, art. 20) des données d'un athlète.
 *
 * <p>L'export ne couvrait que le profil, les séances et les activités — trois familles sur les
 * vingt-deux rattachées à un athlète. Un dossier de portabilité incomplet ne remplit pas
 * l'obligation : tout ce que l'athlète a déclaré ou produit doit s'y retrouver, à commencer par
 * ses données de santé (check-ins quotidiens, tests lactate, ressenti des séances de force).</p>
 *
 * <p>Les pièces jointes de messagerie sont exportées <b>par leurs métadonnées</b> (identifiant,
 * nom, type MIME) : le binaire se récupère par l'API dédiée, l'inclure ici rendrait le JSON
 * inexploitable.</p>
 */
public record AthleteExportResponse(
        Instant exportedAt,
        Instant healthDataConsentAt,
        AthleteResponse profile,
        List<WorkoutResponse> workouts,
        List<ActivityResponse> activities,
        /** Check-ins quotidiens : sommeil, fatigue, douleur — déclarés par l'athlète. */
        List<DailyCheckInResponse> dailyCheckIns,
        /** Tests lactate, paliers compris. */
        List<LactateTestResponse> lactateTests,
        /** Records par distance (à l'origine du VDOT). */
        List<PerformanceResponse> performances,
        /** Objectifs de course (A/B/C) et chronos visés. */
        List<RaceObjectiveResponse> raceObjectives,
        /** Indisponibilités déclarées (blessure, maladie, vacances). */
        List<UnavailabilityResponse> unavailabilities,
        /** Messagerie coach ↔ athlète, métadonnées de pièces jointes comprises (sans le binaire). */
        List<MessageResponse> messages,
        /** Tests de force (1RM direct, rep-test, AMRAP, isométrie). */
        List<StrengthTestResponse> strengthTests,
        /** Séries de force réalisées, avec ressenti et commentaires. */
        List<StrengthResultExportResponse> strengthResults,
        /** Valeurs de zones personnelles (cibles d'allure, de FC…). */
        List<AthleteZoneValueResponse> zoneValues) {
}
