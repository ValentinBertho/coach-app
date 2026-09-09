package com.coachrun.entity.enums;

import java.util.Locale;

/**
 * Sport d'une activité réalisée, tel que la montre ou Strava le déclare.
 *
 * <p><b>Pourquoi cette colonne existe.</b> Une sortie n'avait, en base, qu'une date, une distance
 * et une durée. Trois nombres où l'on cherchait à reconnaître une séance de course à pied — et
 * qu'une séance de musculation, un trajet à vélo ou une sortie en natation remplissent tout aussi
 * bien. Le rapprochement automatique comparait donc des volumes sans savoir de quel sport il
 * parlait : une séance de renforcement de 29 minutes, mise en face d'un fractionné prescrit de
 * 59 minutes, obtenait un score honorable (la distance nulle était traitée comme <i>absente</i>,
 * pas comme incompatible) et emportait la séance devant le fractionné réellement couru.</p>
 *
 * <p><b>Ce que la liste retient.</b> Des familles, pas le catalogue Strava : le rapprochement n'a
 * besoin de savoir que ce qui l'empêche de confondre deux entraînements. Un type inconnu — comme
 * une saisie manuelle, ou toute sortie importée avant l'existence de cette colonne — vaut
 * {@code null} : l'absence n'affirme rien et laisse le rapprochement se comporter comme avant.</p>
 */
public enum ActivitySport {
    RUN,
    RIDE,
    SWIM,
    /** Renforcement, musculation, cross-training en salle : de la charge, sans distance. */
    STRENGTH,
    WALK,
    /** Sport identifié, mais qui n'est aucun des précédents (ski, rameur, escalade…). */
    OTHER;

    /** Ce sport peut-il tenir lieu de course à pied ? La marche compte : elle se court aussi. */
    public boolean isFootborne() {
        return this == RUN || this == WALK;
    }

    /**
     * Sport déclaré par Strava ({@code sport_type} de préférence, {@code type} sinon).
     *
     * <p>La correspondance est volontairement tolérante : Strava ajoute des types au fil de l'eau
     * (« GravelRide » est arrivé après « Ride »), et un type inconnu doit rester {@code null} —
     * « je ne sais pas » — plutôt que de devenir {@link #OTHER}, qui affirmerait à tort que ce
     * n'est pas de la course.</p>
     */
    public static ActivitySport fromStrava(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String t = type.toLowerCase(Locale.ROOT);
        if (t.contains("run") || t.equals("treadmill")) {
            return RUN;
        }
        if (t.contains("ride") || t.contains("cycl") || t.contains("bike") || t.equals("handcycle")) {
            return RIDE;
        }
        if (t.contains("swim")) {
            return SWIM;
        }
        if (t.contains("weight") || t.contains("crossfit") || t.contains("strength")) {
            return STRENGTH;
        }
        if (t.contains("walk") || t.contains("hike")) {
            return WALK;
        }
        // « Workout », « Yoga », « Elliptical »… : identifiés, et ce ne sont pas des courses.
        return KNOWN_NON_RUN.stream().anyMatch(t::contains) ? OTHER : null;
    }

    private static final java.util.List<String> KNOWN_NON_RUN = java.util.List.of(
            "workout", "yoga", "pilates", "elliptical", "stairstepper", "rowing", "row",
            "ski", "snowboard", "skate", "surf", "kayak", "canoe", "sail", "golf",
            "climb", "soccer", "tennis", "badminton", "squash", "wheelchair", "windsurf",
            "kitesurf", "velomobile", "inlineskate", "iceskate", "rockclimbing", "snowshoe");

    /**
     * Sport déclaré dans un fichier FIT (champ {@code sport} du message {@code session}).
     * {@code 0} (generic) reste {@code null} : la montre n'a rien déclaré de plus qu'un fichier.
     */
    public static ActivitySport fromFit(Integer sport) {
        if (sport == null) {
            return null;
        }
        return switch (sport) {
            case 1 -> RUN;
            case 2 -> RIDE;
            case 5 -> SWIM;
            case 4, 10 -> STRENGTH;   // fitness_equipment, training
            case 11, 17 -> WALK;      // walking, hiking
            case 0 -> null;           // generic : la montre n'a rien dit
            default -> OTHER;
        };
    }

    /**
     * Sport déclaré dans un fichier XML : attribut {@code Sport} d'un TCX
     * ({@code Running} / {@code Biking} / {@code Other}) ou balise {@code <type>} d'un GPX.
     */
    public static ActivitySport fromXmlLabel(String label) {
        if (label == null || label.isBlank()) {
            return null;
        }
        String t = label.toLowerCase(Locale.ROOT);
        if (t.contains("run") || t.equals("9")) {
            return RUN;
        }
        if (t.contains("bik") || t.contains("cycl") || t.contains("ride") || t.equals("1")) {
            return RIDE;
        }
        if (t.contains("swim")) {
            return SWIM;
        }
        if (t.contains("walk") || t.contains("hik")) {
            return WALK;
        }
        // « Other » d'un TCX ne dit rien de plus que l'absence : la montre range là tout ce
        // qu'elle ne sait pas nommer, course à pied comprise.
        return null;
    }
}
