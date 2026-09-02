package com.coachrun.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reconnaît un nom de sortie <b>généré par Strava</b>, par opposition à un nom choisi.
 *
 * <h2>Pourquoi c'est utile</h2>
 *
 * <p>Strava nomme automatiquement toute activité que son auteur ne nomme pas : « Morning Run »,
 * « Sortie à vélo l'après-midi ». Ces noms ne portent aucune information — l'heure et le sport
 * sont déjà des colonnes — et ils remplissent le calendrier du coach de lignes indiscernables.
 * Quand la sortie est rapprochée d'une séance prescrite, le vrai nom est juste à côté.</p>
 *
 * <h2>La règle qui gouverne cette classe</h2>
 *
 * <p><b>On ne touche jamais à un nom que quelqu'un a choisi.</b> La correspondance est donc
 * <b>exacte</b> sur la chaîne entière, jamais partielle : « Morning Run » est un nom généré,
 * « Morning Run avec Paul » est un nom écrit par un athlète et doit survivre intact. En cas de
 * doute, on ne reconnaît rien — le pire cas de cette classe est l'inaction, jamais l'écrasement.</p>
 *
 * <h2>Les langues</h2>
 *
 * <p>Strava nomme dans la langue du compte de l'athlète, pas dans celle de l'application. Les
 * gabarits ci-dessous couvrent l'anglais et le français. Une langue absente n'a aucune
 * conséquence : la sortie garde simplement son nom d'origine. Ajouter une langue consiste à
 * ajouter ses moments et ses sports aux tables ci-dessous — rien d'autre.</p>
 */
public final class StravaAutoName {

    /**
     * Sports reconnus, et le libellé français qui sert à composer un titre de repli.
     *
     * <p>La clé est le mot tel que Strava l'écrit ; la valeur est ce qu'on affichera. Deux
     * entrées peuvent pointer vers le même libellé — « Run » et « Course à pied » désignent la
     * même chose dans deux langues.</p>
     */
    private static final Map<String, String> SPORTS = new LinkedHashMap<>();

    /** Moments de la journée, tels que Strava les préfixe ou les suffixe. */
    private static final String[] EN_MOMENTS = {
            "morning", "lunch", "afternoon", "evening", "night",
    };

    /**
     * Moments en français. Strava les place <b>après</b> le sport (« Course à pied le matin »),
     * là où l'anglais les place avant (« Morning Run ») — d'où les deux formes essayées.
     */
    private static final String[] FR_MOMENTS = {
            "le matin", "le midi", "l'après-midi", "l'apres-midi", "le soir", "la nuit",
            "matinale", "matinal",
    };

    private static final String[] EN_SPORTS = {
            "run", "ride", "swim", "walk", "hike", "workout", "weight training",
            "elliptical", "rowing", "yoga", "e-bike ride", "virtual ride", "virtual run",
    };

    private static final String[] FR_SPORTS = {
            "course à pied", "course a pied", "sortie à vélo", "sortie a velo", "sortie vélo",
            "sortie velo", "natation", "marche", "randonnée", "randonnee", "séance",
            "seance", "musculation", "vélo elliptique", "velo elliptique", "aviron", "yoga",
            "sortie", "footing",
    };

    static {
        SPORTS.put("run", "Course à pied");
        SPORTS.put("virtual run", "Course à pied");
        SPORTS.put("course à pied", "Course à pied");
        SPORTS.put("course a pied", "Course à pied");
        SPORTS.put("footing", "Course à pied");
        SPORTS.put("ride", "Vélo");
        SPORTS.put("e-bike ride", "Vélo");
        SPORTS.put("virtual ride", "Vélo");
        SPORTS.put("sortie à vélo", "Vélo");
        SPORTS.put("sortie a velo", "Vélo");
        SPORTS.put("sortie vélo", "Vélo");
        SPORTS.put("sortie velo", "Vélo");
        SPORTS.put("swim", "Natation");
        SPORTS.put("natation", "Natation");
        SPORTS.put("walk", "Marche");
        SPORTS.put("marche", "Marche");
        SPORTS.put("hike", "Randonnée");
        SPORTS.put("randonnée", "Randonnée");
        SPORTS.put("randonnee", "Randonnée");
        SPORTS.put("workout", "Séance");
        SPORTS.put("séance", "Séance");
        SPORTS.put("seance", "Séance");
        SPORTS.put("sortie", "Sortie");
        SPORTS.put("weight training", "Musculation");
        SPORTS.put("musculation", "Musculation");
        SPORTS.put("elliptical", "Vélo elliptique");
        SPORTS.put("vélo elliptique", "Vélo elliptique");
        SPORTS.put("velo elliptique", "Vélo elliptique");
        SPORTS.put("rowing", "Aviron");
        SPORTS.put("aviron", "Aviron");
        SPORTS.put("yoga", "Yoga");
    }

    private StravaAutoName() {
    }

    /**
     * Le sport, en français, si ce titre est un nom généré par Strava.
     *
     * @return vide dès que le titre n'est pas <b>exactement</b> un gabarit connu — y compris pour
     *     un titre nul, vide, ou qui contiendrait un gabarit sans s'y réduire
     */
    public static Optional<String> sportOf(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String normalized = title.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        for (String sport : EN_SPORTS) {
            for (String moment : EN_MOMENTS) {
                // Anglais : le moment précède le sport (« Morning Run »).
                if (normalized.equals(moment + " " + sport)) {
                    return Optional.ofNullable(SPORTS.get(sport));
                }
            }
        }
        for (String sport : FR_SPORTS) {
            for (String moment : FR_MOMENTS) {
                // Français : le moment suit le sport (« Course à pied le matin »).
                if (normalized.equals(sport + " " + moment)) {
                    return Optional.ofNullable(SPORTS.get(sport));
                }
            }
        }
        return Optional.empty();
    }

    /** Vrai si ce titre a été composé par Strava, et peut donc être remplacé sans rien perdre. */
    public static boolean isAutoGenerated(String title) {
        return sportOf(title).isPresent();
    }

    /**
     * Titre de repli pour une sortie sans séance prescrite en face.
     *
     * <p>Composé des seules données de la sortie : on ne prétend pas savoir ce qu'elle était.
     * Sans distance ni durée, il n'y a rien à dire de plus que le sport lui-même.</p>
     */
    public static String descriptiveTitle(String sportLabel, Integer distanceM, Integer durationS) {
        if (distanceM != null && distanceM >= 100) {
            String km = String.format(Locale.FRANCE, "%.1f", distanceM / 1000.0);
            return sportLabel + " — " + km + " km";
        }
        if (durationS != null && durationS >= 60) {
            return sportLabel + " — " + (durationS / 60) + " min";
        }
        return sportLabel;
    }
}
