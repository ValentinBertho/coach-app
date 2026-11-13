package com.coachrun.integration;

import com.coachrun.dto.response.ActivityLapsResponse;
import com.coachrun.entity.enums.ActivitySport;
import com.coachrun.util.ActivityTrack;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Activité reçue de Garmin (Activity API, notifications push) → sortie Darilab.
 *
 * <p>Deux formes arrivent pour une même sortie, chacune par sa propre notification :</p>
 * <ul>
 *   <li>{@code activities} : le résumé seul (distance, durée, FC…), à plat ;</li>
 *   <li>{@code activityDetails} : le même résumé sous la clé {@code summary}, plus les
 *       échantillons (tracé, FC, vitesse) et les débuts de tours.</li>
 * </ul>
 * <p>Elles partagent l'{@code activityId} de Garmin Connect, qui sert d'identifiant externe : le
 * {@code summaryId} diffère d'une forme à l'autre (« 123 » / « 123-detail ») et ne permettrait
 * pas de reconnaître la même sortie.</p>
 *
 * <p>Classe pure : aucun appel, aucune base. Ce qui est délicat ici (date locale, cadence, tours
 * reconstitués) se teste sur des notifications écrites à la main.</p>
 */
public final class GarminActivityMapper {

    private GarminActivityMapper() {
    }

    /** Une sortie Garmin, prête à être importée. */
    public record Parsed(
            String externalId,
            LocalDate date,
            String title,
            String activityType,
            ActivitySport sport,
            Integer distanceM,
            Integer durationS,
            Integer avgHr,
            Integer maxHr,
            Integer avgCadence,
            Integer avgPowerW,
            Integer calories,
            Integer elevationGainM,
            /** Activité « parente » d'un multisport : ses enfants arrivent séparément. */
            boolean parent,
            List<double[]> route,
            List<int[]> stream,
            List<ActivityLapsResponse.Lap> laps) {

        public boolean hasDetails() {
            return !stream.isEmpty() || !route.isEmpty() || !laps.isEmpty();
        }
    }

    /** Élément d'une notification {@code activities} (résumé à plat). */
    public static Parsed fromSummary(Map<String, Object> item) {
        return parse(item, item, List.of(), List.of());
    }

    /** Élément d'une notification {@code activityDetails} (résumé + échantillons + tours). */
    @SuppressWarnings("unchecked")
    public static Parsed fromDetails(Map<String, Object> item) {
        Object s = item.get("summary");
        Map<String, Object> summary = s instanceof Map<?, ?> m ? (Map<String, Object>) m : item;
        return parse(item, summary, maps(item.get("samples")), maps(item.get("laps")));
    }

    private static Parsed parse(Map<String, Object> envelope, Map<String, Object> s,
                                List<Map<String, Object>> samples, List<Map<String, Object>> lapStarts) {
        String externalId = externalId(envelope, s);
        Long start = longOf(s.get("startTimeInSeconds"));
        if (externalId == null || start == null) {
            return null;
        }
        long offset = orZero(longOf(s.get("startTimeOffsetInSeconds")));
        // La date de la sortie est celle qu'affichait la montre, pas celle d'UTC : un footing à
        // 23 h à Paris est un footing du jour, pas du lendemain.
        LocalDate date = Instant.ofEpochSecond(start + offset).atOffset(ZoneOffset.UTC).toLocalDate();

        String type = text(s.get("activityType"));
        ActivitySport sport = ActivitySport.fromGarmin(type);

        List<ActivityTrack.Point> points = points(samples);
        List<ActivityTrack.Point> positioned = ActivityTrack.positioned(points);
        return new Parsed(
                externalId,
                date,
                text(s.get("activityName")),
                type,
                sport,
                round(doubleOf(s.get("distanceInMeters"))),
                intOf(s.get("durationInSeconds")),
                round(doubleOf(s.get("averageHeartRateInBeatsPerMinute"))),
                round(doubleOf(s.get("maxHeartRateInBeatsPerMinute"))),
                cadence(s, sport),
                null,
                round(doubleOf(s.get("activeKilocalories"))),
                round(doubleOf(s.get("totalElevationGainInMeters"))),
                Boolean.TRUE.equals(s.get("isParent")),
                positioned.isEmpty() ? List.of() : ActivityTrack.downsample(positioned),
                points.isEmpty() ? List.of() : ActivityTrack.buildStream(points),
                laps(samples, lapStarts, sport));
    }

    /**
     * Cadence de course en pas/min. Garmin la donne <b>par jambe</b> (l'exemple de sa doc :
     * 84 « pas/min » pour 5 022 pas en 30 min, soit 168 réels) ; on la double, comme pour Strava.
     */
    private static Integer cadence(Map<String, Object> s, ActivitySport sport) {
        Double run = doubleOf(s.get("averageRunCadenceInStepsPerMinute"));
        if (run != null && run > 0 && (sport == null || sport.isFootborne())) {
            return (int) Math.round(run * 2);
        }
        Double bike = doubleOf(s.get("averageBikeCadenceInRoundsPerMinute"));
        return bike != null && bike > 0 ? (int) Math.round(bike) : null;
    }

    private static String externalId(Map<String, Object> envelope, Map<String, Object> s) {
        Object id = envelope.get("activityId") != null ? envelope.get("activityId") : s.get("activityId");
        String text = numberText(id);
        if (text != null) {
            return text;
        }
        // Sans activityId, le summaryId en tient lieu, débarrassé du suffixe des détails.
        String summaryId = text(envelope.get("summaryId"));
        return summaryId == null ? null : summaryId.replaceFirst("-detail$", "");
    }

    private static List<ActivityTrack.Point> points(List<Map<String, Object>> samples) {
        List<ActivityTrack.Point> out = new ArrayList<>();
        for (Map<String, Object> p : samples) {
            Long t = longOf(p.get("startTimeInSeconds"));
            if (t == null) {
                continue;
            }
            Integer hr = intOf(p.get("heartRate"));
            out.add(new ActivityTrack.Point(
                    doubleOf(p.get("latitudeInDegree")),
                    doubleOf(p.get("longitudeInDegree")),
                    doubleOf(p.get("elevationInMeters")),
                    Instant.ofEpochSecond(t),
                    hr != null && hr > 0 ? hr : null,
                    doubleOf(p.get("speedMetersPerSecond"))));
        }
        return out;
    }

    /**
     * Tours de la montre. Garmin n'en donne que l'heure de début : distance, durée et FC de chaque
     * tour se reconstituent à partir des échantillons qui tombent entre deux débuts. Un seul tour
     * n'en est pas un (la sortie entière) : liste vide, et l'écran retombe sur des splits.
     */
    private static List<ActivityLapsResponse.Lap> laps(List<Map<String, Object>> samples,
                                                       List<Map<String, Object>> lapStarts,
                                                       ActivitySport sport) {
        List<Long> starts = new ArrayList<>();
        for (Map<String, Object> l : lapStarts) {
            Long t = longOf(l.get("startTimeInSeconds"));
            if (t != null) {
                starts.add(t);
            }
        }
        starts.sort(Long::compare);
        if (starts.size() < 2 || samples.isEmpty()) {
            return List.of();
        }
        List<ActivityLapsResponse.Lap> out = new ArrayList<>();
        Double distanceAtLapStart = null;
        Long timerAtLapStart = null;
        for (int i = 0; i < starts.size(); i++) {
            long from = starts.get(i);
            long to = i + 1 < starts.size() ? starts.get(i + 1) : Long.MAX_VALUE;
            Double lastDistance = null;
            Long lastTimer = null;
            Long firstTime = null;
            Long lastTime = null;
            long hrSum = 0;
            int hrCount = 0;
            int hrMax = 0;
            double cadSum = 0;
            int cadCount = 0;
            double gain = 0;
            Double prevEle = null;
            for (Map<String, Object> p : samples) {
                Long t = longOf(p.get("startTimeInSeconds"));
                if (t == null || t < from || t >= to) {
                    continue;
                }
                firstTime = firstTime == null ? t : firstTime;
                lastTime = t;
                Double d = doubleOf(p.get("totalDistanceInMeters"));
                if (d != null) {
                    lastDistance = d;
                    distanceAtLapStart = distanceAtLapStart == null ? d : distanceAtLapStart;
                }
                Long timer = longOf(p.get("timerDurationInSeconds"));
                if (timer != null) {
                    lastTimer = timer;
                    timerAtLapStart = timerAtLapStart == null ? timer : timerAtLapStart;
                }
                Integer hr = intOf(p.get("heartRate"));
                if (hr != null && hr > 0) {
                    hrSum += hr;
                    hrCount++;
                    hrMax = Math.max(hrMax, hr);
                }
                Double cad = doubleOf(p.get("stepsPerMinute"));
                if (cad != null && cad > 0) {
                    cadSum += cad;
                    cadCount++;
                }
                Double ele = doubleOf(p.get("elevationInMeters"));
                if (ele != null) {
                    if (prevEle != null && ele > prevEle) {
                        gain += ele - prevEle;
                    }
                    prevEle = ele;
                }
            }
            if (firstTime == null) {
                continue; // tour sans échantillon (pause, ou tour fantôme en fin de sortie)
            }
            Integer distance = lastDistance != null && distanceAtLapStart != null
                    ? (int) Math.round(lastDistance - distanceAtLapStart) : null;
            Integer duration = lastTimer != null && timerAtLapStart != null
                    ? (int) (lastTimer - timerAtLapStart) : (int) (lastTime - firstTime);
            // Le tour suivant part de là où celui-ci s'est arrêté, pas de son propre premier
            // échantillon : sinon l'écart entre deux échantillons se perdrait à chaque tour.
            distanceAtLapStart = lastDistance;
            timerAtLapStart = lastTimer;
            Integer cadence = cadCount == 0 ? null : (int) Math.round(cadSum / cadCount);
            out.add(ActivityLapsResponse.Lap.of(out.size() + 1, distance, duration,
                    hrCount == 0 ? null : (int) Math.round(hrSum / (double) hrCount),
                    hrMax == 0 ? null : hrMax,
                    cadence,
                    (int) Math.round(gain)));
        }
        return out.size() < 2 ? List.of() : out;
    }

    // --- Lecture tolérante du JSON ---

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> maps(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    /** Un identifiant numérique sans « .0 » : Jackson peut le livrer en Long comme en Double. */
    private static String numberText(Object value) {
        if (value instanceof Number n) {
            return String.valueOf(n.longValue());
        }
        return text(value);
    }

    private static Double doubleOf(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static Long longOf(Object value) {
        Double d = doubleOf(value);
        return d == null ? null : (long) Math.floor(d);
    }

    private static Integer intOf(Object value) {
        Double d = doubleOf(value);
        return d == null ? null : (int) Math.round(d);
    }

    private static Integer round(Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
