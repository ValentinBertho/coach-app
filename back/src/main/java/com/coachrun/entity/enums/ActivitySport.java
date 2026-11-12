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
     * Nom canonique FIT de la catégorie, sous-sport compris — « trail_running »,
     * « lap_swimming », « indoor_cycling ».
     *
     * <p>Un FIT ne déclare pas un libellé mais deux entiers, et c'est le second qui porte le
     * plus d'information : {@code sport=1} dit « course », {@code sub_sport=3} dit que c'était
     * du trail. Ne garder que la famille revenait à jeter la moitié de ce que la montre avait
     * pris soin d'écrire.</p>
     *
     * <p>Un code que cette table ne nomme pas rend {@code null} plutôt qu'un « sport 47 » :
     * l'écran retombe alors sur la famille, qui dit au moins quelque chose de juste. Inventer un
     * libellé à partir d'un numéro ne serait pas conserver l'information, ce serait la déguiser.</p>
     */
    public static String fitLabel(Integer sport, Integer subSport) {
        // Un fichier sans message de session ne déclare ni l'un ni l'autre, et une table
        // immuable refuse qu'on l'interroge sur un nul.
        String refined = subSport == null ? null : SUB_SPORTS.get(subSport);
        if (refined != null) {
            return refined;
        }
        return sport == null ? null : SPORTS.get(sport);
    }

    /** Sports FIT nommés (profil Garmin) — les familles, quand le sous-sport ne précise rien. */
    private static final java.util.Map<Integer, String> SPORTS = java.util.Map.ofEntries(
            java.util.Map.entry(1, "running"),
            java.util.Map.entry(2, "cycling"),
            java.util.Map.entry(4, "fitness_equipment"),
            java.util.Map.entry(5, "swimming"),
            java.util.Map.entry(10, "training"),
            java.util.Map.entry(11, "walking"),
            java.util.Map.entry(12, "cross_country_skiing"),
            java.util.Map.entry(13, "alpine_skiing"),
            java.util.Map.entry(14, "snowboarding"),
            java.util.Map.entry(15, "rowing"),
            java.util.Map.entry(16, "mountaineering"),
            java.util.Map.entry(17, "hiking"),
            java.util.Map.entry(19, "paddling"),
            java.util.Map.entry(21, "e_biking"),
            java.util.Map.entry(25, "golf"),
            java.util.Map.entry(30, "inline_skating"),
            java.util.Map.entry(31, "rock_climbing"),
            java.util.Map.entry(32, "sailing"),
            java.util.Map.entry(33, "ice_skating"),
            java.util.Map.entry(35, "snowshoeing"),
            java.util.Map.entry(37, "stand_up_paddleboarding"),
            java.util.Map.entry(38, "surfing"),
            java.util.Map.entry(41, "kayaking"),
            java.util.Map.entry(43, "windsurfing"),
            java.util.Map.entry(44, "kitesurfing"),
            java.util.Map.entry(47, "boxing"),
            java.util.Map.entry(53, "diving"),
            java.util.Map.entry(62, "hiit"));

    /**
     * Sous-sports FIT nommés : ce sont eux qui distinguent un trail d'un 10 km sur piste, ou une
     * séance en bassin d'une traversée en eau libre. Quand l'un d'eux est reconnu, il prime sur
     * la famille — il en dit strictement plus.
     */
    private static final java.util.Map<Integer, String> SUB_SPORTS = java.util.Map.ofEntries(
            java.util.Map.entry(1, "treadmill"),
            java.util.Map.entry(2, "street_running"),
            java.util.Map.entry(3, "trail_running"),
            java.util.Map.entry(4, "track_running"),
            java.util.Map.entry(5, "spin"),
            java.util.Map.entry(6, "indoor_cycling"),
            java.util.Map.entry(7, "road_cycling"),
            java.util.Map.entry(8, "mountain_biking"),
            java.util.Map.entry(9, "downhill"),
            java.util.Map.entry(11, "cyclocross"),
            java.util.Map.entry(12, "hand_cycling"),
            java.util.Map.entry(13, "track_cycling"),
            java.util.Map.entry(14, "indoor_rowing"),
            java.util.Map.entry(15, "elliptical"),
            java.util.Map.entry(16, "stair_climbing"),
            java.util.Map.entry(17, "lap_swimming"),
            java.util.Map.entry(18, "open_water"),
            java.util.Map.entry(19, "flexibility_training"),
            java.util.Map.entry(20, "strength_training"),
            java.util.Map.entry(26, "cardio_training"),
            java.util.Map.entry(27, "indoor_walking"),
            java.util.Map.entry(28, "e_bike_fitness"),
            java.util.Map.entry(29, "bmx"),
            java.util.Map.entry(30, "casual_walking"),
            java.util.Map.entry(31, "speed_walking"),
            java.util.Map.entry(46, "indoor_running"),
            java.util.Map.entry(58, "virtual_activity"),
            java.util.Map.entry(65, "gravel_cycling"));

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
