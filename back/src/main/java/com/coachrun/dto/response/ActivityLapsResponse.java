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
 * <p>{@code availableKinds} annonce les découpes que <b>cette</b> sortie sait produire, pour que
 * l'écran propose des onglets « Tours montre / Tours 1 km » sans avoir à deviner : une sortie avec
 * tours de montre <em>et</em> flux GPS offre les deux lectures, et comparer les répétitions aux
 * kilomètres est précisément ce qu'on fait sur un fractionné en côte. Nul ou absent (anciennes
 * lignes stockées) = seule la découpe rendue est disponible.</p>
 *
 * @param kind           provenance des tours rendus, jamais masquée à l'affichage
 * @param laps           tours dans l'ordre chronologique
 * @param availableKinds découpes proposables pour cette sortie
 */
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityLapsResponse(Kind kind, List<Lap> laps, List<Kind> availableKinds) {

    /** Forme courte : la découpe rendue est la seule disponible. */
    public ActivityLapsResponse(Kind kind, List<Lap> laps) {
        this(kind, laps, laps == null || laps.isEmpty() ? List.of() : List.of(kind));
    }

    /** Même contenu, avec la liste des découpes proposables recalculée par le service. */
    public ActivityLapsResponse withAvailable(List<Kind> kinds) {
        return new ActivityLapsResponse(kind, laps, kinds);
    }

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
