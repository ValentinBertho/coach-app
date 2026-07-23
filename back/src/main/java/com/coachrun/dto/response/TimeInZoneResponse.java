package com.coachrun.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Répartition du temps passé par zone pour une activité réalisée (une barre par métrique : allure,
 * FC…), calculée depuis le flux échantillonné de l'activité et les zones de l'athlète.
 * Cf. PROPOSITION-ZONES-ET-EDITEUR-V2 §3.7 / E7 (V2-7).
 */
public record TimeInZoneResponse(List<Scale> scales) {

    /** Distribution pour une métrique (échelle). */
    public record Scale(String metricCode, String metricName, int totalS, List<Bucket> buckets) {
    }

    /** Temps passé dans une zone donnée. */
    public record Bucket(UUID zoneId, String zoneName, String color, int seconds, double pct) {
    }

    public static TimeInZoneResponse empty() {
        return new TimeInZoneResponse(List.of());
    }
}
