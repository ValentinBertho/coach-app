package com.coachrun.service;

import com.coachrun.dto.response.TimeInZoneResponse;
import com.coachrun.entity.Activity;
import com.coachrun.entity.AthleteZoneValue;
import com.coachrun.entity.MetricType;
import com.coachrun.entity.TrainingZone;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteZoneValueRepository;
import com.coachrun.repository.MetricTypeRepository;
import com.coachrun.repository.TrainingZoneRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Calcule le <b>temps passé par zone</b> (temps-en-zone) d'une activité réalisée à partir de son
 * flux échantillonné ({@code stream_json} : [elapsedS, hr, paceSecPerKm]) et des valeurs de zones
 * de l'athlète. Une barre par métrique (Allure, FC…), façon Nolio. Calcul à la lecture (les zones
 * de l'athlète peuvent avoir évolué depuis l'import). Cf. PROPOSITION-ZONES §3.7 / E7 (V2-7).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeInZoneService {

    /** Métriques pour lesquelles on sait situer une valeur du flux dans une zone. */
    private static final List<String> STREAM_METRICS = List.of("HR", "PACE");

    private final ActivityRepository activityRepository;
    private final AthleteZoneValueRepository valueRepository;
    private final TrainingZoneRepository zoneRepository;
    private final MetricTypeRepository metricTypeRepository;
    private final ObjectMapper objectMapper;

    /** Temps-en-zone pour une activité d'un club (anti-IDOR : activité scopée au club). */
    public TimeInZoneResponse forActivity(UUID clubId, UUID activityId) {
        Activity activity = activityRepository.findByIdAndClubId(activityId, clubId)
                .orElseThrow(() -> new NotFoundException("Activité introuvable."));
        return compute(activity);
    }

    /** Variante portail athlète : l'activité doit lui appartenir. */
    public TimeInZoneResponse forActivityOfAthlete(UUID athleteId, UUID activityId) {
        Activity activity = activityRepository.findById(activityId)
                .filter(a -> a.getAthlete().getId().equals(athleteId))
                .orElseThrow(() -> new NotFoundException("Activité introuvable."));
        return compute(activity);
    }

    private TimeInZoneResponse compute(Activity activity) {
        List<int[]> stream = parseStream(activity.getStreamJson());
        if (stream.size() < 2) {
            return TimeInZoneResponse.empty();
        }
        UUID clubId = activity.getClub().getId();
        UUID athleteId = activity.getAthlete().getId();

        Map<String, MetricType> metricByCode = new java.util.HashMap<>();
        for (MetricType m : metricTypeRepository.findVisibleForClub(clubId)) {
            if (m.isBuiltin()) {
                metricByCode.putIfAbsent(m.getCode(), m);
            }
        }
        List<TrainingZone> zones = zoneRepository.findByClubIdOrderBySortOrderAscNameAsc(clubId);
        List<AthleteZoneValue> values = valueRepository.findByAthleteId(athleteId);

        List<TimeInZoneResponse.Scale> scales = new ArrayList<>();
        for (String code : STREAM_METRICS) {
            MetricType metric = metricByCode.get(code);
            if (metric == null) {
                continue;
            }
            TimeInZoneResponse.Scale scale = scaleFor(code, metric, zones, values, stream);
            if (scale != null) {
                scales.add(scale);
            }
        }
        return new TimeInZoneResponse(scales);
    }

    /** Distribution du temps pour une métrique donnée, ou null si aucune donnée exploitable. */
    private TimeInZoneResponse.Scale scaleFor(String code, MetricType metric, List<TrainingZone> zones,
                                              List<AthleteZoneValue> values, List<int[]> stream) {
        int valueIdx = "HR".equals(code) ? 1 : 2; // colonne du flux : [elapsedS, hr, pace]

        // Bandes (zone + fourchette de l'athlète pour cette métrique), triées par ordre de zone.
        List<Band> bands = new ArrayList<>();
        for (TrainingZone zone : zones) {
            AthleteZoneValue v = values.stream()
                    .filter(x -> x.getZone().getId().equals(zone.getId())
                            && x.getMetricType().getId().equals(metric.getId()))
                    .findFirst().orElse(null);
            if (v != null && v.getValueMin() != null && v.getValueMax() != null) {
                double lo = Math.min(v.getValueMin(), v.getValueMax());
                double hi = Math.max(v.getValueMin(), v.getValueMax());
                bands.add(new Band(zone, lo, hi, 0));
            }
        }
        if (bands.isEmpty()) {
            return null;
        }

        int total = 0;
        for (int i = 0; i < stream.size() - 1; i++) {
            int dt = stream.get(i + 1)[0] - stream.get(i)[0];
            if (dt <= 0 || dt > 120) {
                continue; // pause / trou de données → non compté
            }
            int val = stream.get(i)[valueIdx];
            if (val < 0) {
                continue;
            }
            Band b = bestBand(bands, val);
            if (b != null) {
                b.seconds += dt;
                total += dt;
            }
        }
        if (total == 0) {
            return null;
        }

        List<TimeInZoneResponse.Bucket> buckets = new ArrayList<>();
        for (Band b : bands) {
            if (b.seconds > 0) {
                double pct = Math.round(1000.0 * b.seconds / total) / 10.0;
                buckets.add(new TimeInZoneResponse.Bucket(
                        b.zone.getId(), b.zone.getName(), b.zone.getColor(), b.seconds, pct));
            }
        }
        return new TimeInZoneResponse.Scale(code, metric.getName(), total, buckets);
    }

    /**
     * Bande contenant la valeur ; à défaut la plus proche (le flux peut sortir des bornes des zones).
     * Garantit que chaque échantillon exploitable est attribué à une zone (somme des % = 100).
     */
    private Band bestBand(List<Band> bands, double val) {
        Band nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (Band b : bands) {
            if (val >= b.lo && val <= b.hi) {
                return b;
            }
            double d = val < b.lo ? b.lo - val : val - b.hi;
            if (d < bestDist) {
                bestDist = d;
                nearest = b;
            }
        }
        return nearest;
    }

    private List<int[]> parseStream(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<int[]>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Bande de travail mutable (accumulateur de secondes). */
    private static final class Band {
        final TrainingZone zone;
        final double lo;
        final double hi;
        int seconds;

        Band(TrainingZone zone, double lo, double hi, int seconds) {
            this.zone = zone;
            this.lo = lo;
            this.hi = hi;
            this.seconds = seconds;
        }
    }
}
