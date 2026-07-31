package com.coachrun.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Décodeur de tracé « Google Encoded Polyline Algorithm Format », le format du champ
 * {@code map.summary_polyline} des activités Strava.
 *
 * <p>Chaque point est encodé en delta par rapport au précédent, en 1e-5 degré, sur des groupes de
 * 5 bits décalés de 63 (« ? ») pour rester en ASCII imprimable. Une quarantaine de lignes suffisent
 * à le lire : aucune dépendance supplémentaire n'est nécessaire.</p>
 *
 * <p>Le tracé produit a le même format que celui de {@link GpxParser} — une liste de
 * {@code [latitude, longitude]} — pour que la carte n'ait pas à distinguer la source.</p>
 */
public final class PolylineDecoder {

    /** Alignement sur le lissage du parser GPX : au-delà, la carte n'y gagne rien. */
    private static final int MAX_POINTS = 400;

    private PolylineDecoder() {
    }

    /** Décode un tracé encodé en liste de {@code [lat, lon]}. Chaîne vide ou nulle → liste vide. */
    public static List<double[]> decode(String encoded) {
        List<double[]> points = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) {
            return points;
        }
        int index = 0;
        int lat = 0;
        int lon = 0;
        while (index < encoded.length()) {
            int[] latResult = decodeValue(encoded, index);
            if (latResult == null) {
                break;
            }
            lat += latResult[0];
            index = latResult[1];

            int[] lonResult = decodeValue(encoded, index);
            if (lonResult == null) {
                break;
            }
            lon += lonResult[0];
            index = lonResult[1];

            points.add(new double[] {lat / 1e5, lon / 1e5});
        }
        return downsample(points);
    }

    /**
     * Lit une valeur delta à partir de {@code index}.
     *
     * @return {@code [delta, indexSuivant]}, ou {@code null} si la chaîne est tronquée
     */
    private static int[] decodeValue(String encoded, int index) {
        int result = 0;
        int shift = 0;
        int b;
        do {
            if (index >= encoded.length()) {
                return null;
            }
            b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        // Bit de poids faible = signe (encodage en zigzag).
        return new int[] {(result & 1) != 0 ? ~(result >> 1) : (result >> 1), index};
    }

    private static List<double[]> downsample(List<double[]> points) {
        if (points.size() <= MAX_POINTS) {
            return points;
        }
        int step = points.size() / MAX_POINTS + 1;
        List<double[]> sampled = new ArrayList<>();
        for (int i = 0; i < points.size(); i += step) {
            sampled.add(points.get(i));
        }
        // Le dernier point ferme le tracé : sans lui la carte s'arrête avant l'arrivée.
        double[] last = points.get(points.size() - 1);
        if (sampled.get(sampled.size() - 1) != last) {
            sampled.add(last);
        }
        return sampled;
    }
}
