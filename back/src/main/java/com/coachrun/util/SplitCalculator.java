package com.coachrun.util;

import com.coachrun.dto.response.ActivityLapsResponse.Lap;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits kilométriques calculés depuis le flux d'une activité, quand la montre n'a pas découpé la
 * sortie en tours.
 *
 * <p>Le flux est celui déjà stocké pour le temps-en-zone : {@code [elapsedS, hr, paceSecPerKm]},
 * {@code -1} pour une valeur absente. La distance n'y figure pas — on l'intègre depuis l'allure,
 * puis on <b>recale le total sur la distance connue de l'activité</b>. Sans ce recalage, les
 * arrêts (allure {@code -1}, donc distance nulle) et l'échantillonnage feraient dériver la somme
 * de plusieurs centaines de mètres, et le dernier kilomètre tomberait au mauvais endroit.</p>
 */
public final class SplitCalculator {

    private static final int SPLIT_M = 1000;
    /** Au-delà, ce n'est plus une lecture de séance : un ultra afficherait 170 lignes. */
    private static final int MAX_SPLITS = 100;

    private SplitCalculator() {
    }

    /**
     * @param stream    flux {@code [[elapsedS, hr, paceSecPerKm], …]}, tel que stocké
     * @param distanceM distance totale de l'activité, qui fait autorité sur l'intégration
     * @return un tour par kilomètre entamé (le dernier peut être partiel), vide si le flux ne
     *         permet rien de fiable
     */
    public static List<Lap> perKilometer(List<int[]> stream, Integer distanceM) {
        if (stream == null || stream.size() < 2) {
            return List.of();
        }

        double[] cumulative = integrate(stream);
        double raw = cumulative[cumulative.length - 1];
        if (raw < SPLIT_M) {
            return List.of(); // moins d'un kilomètre exploitable : rien à découper
        }
        // Recalage sur la distance réelle quand elle est connue (elle vient de la montre).
        double scale = distanceM != null && distanceM > 0 ? distanceM / raw : 1.0;

        double total = raw * scale;
        List<Lap> laps = new ArrayList<>();
        int startIdx = 0;
        double startTime = stream.get(0)[0];
        for (double target = SPLIT_M; target <= total && laps.size() < MAX_SPLITS; target += SPLIT_M) {
            int i = firstIndexAtOrAfter(cumulative, scale, target);
            // Le temps du passage au kilomètre est interpolé entre deux échantillons : sans ça,
            // un point toutes les dix secondes suffit à afficher « 1 033 m en 5:10 » là où
            // l'athlète attend « km 2 — 5:00 ».
            double crossing = interpolateTime(stream, cumulative, scale, i, target);
            laps.add(lap(laps.size() + 1, stream, startIdx, i, SPLIT_M,
                    (int) Math.round(crossing - startTime)));
            startIdx = i;
            startTime = crossing;
        }

        // Fin de sortie : le kilomètre entamé compte aussi (« 10,4 km » se lit sur 11 lignes).
        double tail = total - laps.size() * (double) SPLIT_M;
        int last = stream.size() - 1;
        if (tail >= 50 && laps.size() < MAX_SPLITS) {
            laps.add(lap(laps.size() + 1, stream, startIdx, last, (int) Math.round(tail),
                    (int) Math.round(stream.get(last)[0] - startTime)));
        }
        return laps;
    }

    /** Premier échantillon dont la distance cumulée atteint la borne visée. */
    private static int firstIndexAtOrAfter(double[] cumulative, double scale, double target) {
        for (int i = 1; i < cumulative.length; i++) {
            if (cumulative[i] * scale >= target) {
                return i;
            }
        }
        return cumulative.length - 1;
    }

    /** Instant (s) où la distance {@code target} est atteinte, interpolé entre i-1 et i. */
    private static double interpolateTime(List<int[]> stream, double[] cumulative, double scale,
                                          int i, double target) {
        if (i <= 0) {
            return stream.get(0)[0];
        }
        double before = cumulative[i - 1] * scale;
        double after = cumulative[i] * scale;
        double span = after - before;
        double ratio = span > 0 ? Math.max(0, Math.min(1, (target - before) / span)) : 1;
        return stream.get(i - 1)[0] + ratio * (stream.get(i)[0] - stream.get(i - 1)[0]);
    }

    /** Distance cumulée (m) à chaque point du flux, intégrée depuis l'allure instantanée. */
    private static double[] integrate(List<int[]> stream) {
        double[] cumulative = new double[stream.size()];
        for (int i = 1; i < stream.size(); i++) {
            int[] prev = stream.get(i - 1);
            int[] cur = stream.get(i);
            int dt = cur[0] - prev[0];
            int pace = prev.length > 2 ? prev[2] : -1;
            // Allure absente = athlète à l'arrêt ou valeur aberrante : aucune distance parcourue.
            double dm = dt > 0 && pace > 0 ? dt * 1000.0 / pace : 0;
            cumulative[i] = cumulative[i - 1] + dm;
        }
        return cumulative;
    }

    /** Agrège une tranche du flux en un tour : FC moyenne et max sur les échantillons couverts. */
    private static Lap lap(int index, List<int[]> stream, int fromIdx, int toIdx, int distanceM,
                           int durationS) {
        long hrSum = 0;
        int hrCount = 0;
        int hrMax = 0;
        for (int i = fromIdx; i <= toIdx; i++) {
            int hr = stream.get(i).length > 1 ? stream.get(i)[1] : -1;
            if (hr > 0) {
                hrSum += hr;
                hrCount++;
                hrMax = Math.max(hrMax, hr);
            }
        }
        return Lap.of(index, distanceM, durationS > 0 ? durationS : null,
                hrCount > 0 ? (int) Math.round((double) hrSum / hrCount) : null,
                hrMax > 0 ? hrMax : null, null, null);
    }
}
