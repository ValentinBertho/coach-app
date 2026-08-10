package com.coachrun.util;

import com.coachrun.dto.response.ActivityStreamResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Construit la courbe affichable d'une sortie (FC et allure le long de la distance) depuis le flux
 * brut déjà stocké pour le temps-en-zone.
 *
 * <p>Deux traitements, et seulement deux. <b>Le rééchantillonnage</b> d'abord : une sortie longue
 * peut porter plusieurs milliers de points, dont un téléphone ne trace que du bruit — on agrège
 * par fenêtres de distance égales, en moyennant, plutôt que d'en garder un sur dix (qui ferait
 * dépendre la courbe du point tombé au hasard dans la fenêtre). <b>Le lissage de l'allure</b>
 * ensuite : l'allure instantanée d'un GPS oscille de plusieurs dizaines de secondes au kilomètre
 * d'un point à l'autre, et une courbe qui tremble ne se lit pas — la moyenne de fenêtre suffit à
 * la calmer sans déplacer les répétitions.</p>
 */
public final class ActivityCurve {

    /** Assez pour dessiner une dérive cardiaque et des répétitions, sans noyer un écran de 375 px. */
    private static final int MAX_POINTS = 240;

    /** En deçà, la sortie est trop courte pour qu'une courbe apprenne quoi que ce soit. */
    private static final int MIN_DISTANCE_M = 200;

    private ActivityCurve() {
    }

    /**
     * @param stream    flux {@code [[elapsedS, hr, paceSecPerKm], …]}, {@code -1} = valeur absente
     * @param distanceM distance totale de l'activité, qui fait autorité sur l'intégration
     */
    public static ActivityStreamResponse build(List<int[]> stream, Integer distanceM) {
        if (stream == null || stream.size() < 2) {
            return ActivityStreamResponse.empty();
        }
        double[] cumulative = SplitCalculator.cumulativeDistances(stream, distanceM);
        if (cumulative.length == 0) {
            return ActivityStreamResponse.empty();
        }
        double total = cumulative[cumulative.length - 1];
        if (total < MIN_DISTANCE_M) {
            return ActivityStreamResponse.empty();
        }

        int buckets = Math.min(MAX_POINTS, stream.size());
        double width = total / buckets;

        List<ActivityStreamResponse.Point> points = new ArrayList<>(buckets);
        boolean hasHr = false;
        boolean hasPace = false;

        int i = 0;
        for (int b = 0; b < buckets; b++) {
            double edge = (b + 1) * width;
            long hrSum = 0;
            int hrCount = 0;
            long paceSum = 0;
            int paceCount = 0;
            int lastElapsed = stream.get(Math.min(i, stream.size() - 1))[0];

            // Fenêtre de distance : on avance dans le flux tant qu'on reste sous la borne, et on
            // prend toujours au moins un point — un arrêt prolongé ne parcourt aucune distance et
            // laisserait sinon une fenêtre vide au milieu de la courbe.
            do {
                int[] p = stream.get(i);
                lastElapsed = p[0];
                int hr = p.length > 1 ? p[1] : -1;
                int pace = p.length > 2 ? p[2] : -1;
                if (hr > 0) {
                    hrSum += hr;
                    hrCount++;
                }
                if (pace > 0) {
                    paceSum += pace;
                    paceCount++;
                }
                i++;
            } while (i < stream.size() && cumulative[i] <= edge);

            Integer hr = hrCount > 0 ? (int) Math.round((double) hrSum / hrCount) : null;
            Integer pace = paceCount > 0 ? (int) Math.round((double) paceSum / paceCount) : null;
            hasHr |= hr != null;
            hasPace |= pace != null;
            points.add(new ActivityStreamResponse.Point(
                    (int) Math.round(Math.min(edge, total)), lastElapsed, hr, pace));

            if (i >= stream.size()) {
                break;
            }
        }

        return new ActivityStreamResponse(points, (int) Math.round(total), hasHr, hasPace);
    }
}
