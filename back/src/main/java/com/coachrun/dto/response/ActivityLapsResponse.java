package com.coachrun.dto.response;

import java.util.List;

/**
 * Tours d'une activité réalisée — de quoi décortiquer une séance plutôt que de la lire en un
 * seul chiffre moyen.
 *
 * <p>La <b>nature</b> des tours compte autant que leur contenu, d'où {@link Kind} : les tours pris
 * par la montre sont les répétitions telles qu'elles ont été courues (« 10 × 400 »), alors que des
 * splits kilométriques n'en sont qu'une découpe régulière. Afficher les seconds sous le nom des
 * premiers ferait lire « 4:12 au km 3 » à un athlète qui cherche l'allure de sa 3<sup>e</sup>
 * répétition.</p>
 *
 * @param kind provenance des tours, jamais masquée à l'affichage
 * @param laps tours dans l'ordre chronologique
 */
public record ActivityLapsResponse(Kind kind, List<Lap> laps) {

    public enum Kind {
        /** Tours relevés par la montre (bouton lap, ou structure de séance). */
        DEVICE,
        /** Splits kilométriques calculés : la montre n'a pas découpé la sortie. */
        SPLIT
    }

    /**
     * Un tour. L'allure est calculée ici plutôt que côté client pour que tous les écrans
     * affichent la même valeur (même règle que {@link ActivityResponse#paceSPerKm}).
     *
     * @param index          numéro du tour, à partir de 1
     * @param distanceM      distance parcourue (m)
     * @param durationS      temps en mouvement (s)
     * @param paceSPerKm     allure moyenne du tour (s/km), nulle si indéterminable
     * @param avgHr          FC moyenne du tour (bpm)
     * @param maxHr          FC maximale du tour (bpm)
     * @param avgCadence     cadence moyenne (pas/min)
     * @param elevationGainM dénivelé positif du tour (m)
     */
    public record Lap(
            int index,
            Integer distanceM,
            Integer durationS,
            Integer paceSPerKm,
            Integer avgHr,
            Integer maxHr,
            Integer avgCadence,
            Integer elevationGainM) {

        public static Lap of(int index, Integer distanceM, Integer durationS, Integer avgHr,
                             Integer maxHr, Integer avgCadence, Integer elevationGainM) {
            return new Lap(index, distanceM, durationS, pace(distanceM, durationS),
                    avgHr, maxHr, avgCadence, elevationGainM);
        }

        private static Integer pace(Integer distanceM, Integer durationS) {
            if (distanceM == null || durationS == null || distanceM <= 0 || durationS <= 0) {
                return null;
            }
            return (int) Math.round(durationS * 1000.0 / distanceM);
        }
    }

    public static ActivityLapsResponse empty() {
        return new ActivityLapsResponse(Kind.SPLIT, List.of());
    }
}
