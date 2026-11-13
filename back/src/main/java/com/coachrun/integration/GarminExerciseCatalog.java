package com.coachrun.integration;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Nom d'exercice Darilab → exercice Garmin ({@code exerciseCategory} / {@code exerciseName},
 * annexe B de la Training API).
 *
 * <p>Nos exercices sont nommés librement par chaque coach, en français (« Fente bulgare »,
 * « Soulevé de terre roumain »), quand Garmin attend un couple fermé de constantes. On reconnaît
 * donc les mouvements courants par mots-clés, du plus précis au plus général. Chaque couple
 * ci-dessous a été vérifié dans l'annexe B.</p>
 *
 * <p>Un exercice non reconnu part <b>sans</b> catégorie : la montre affiche alors le nom écrit
 * par le coach (dans la description de l'étape), avec ses séries, répétitions et charge. C'est
 * moins riche qu'un exercice reconnu, jamais faux — un mauvais rapprochement afficherait un
 * autre mouvement que celui demandé.</p>
 */
public final class GarminExerciseCatalog {

    private GarminExerciseCatalog() {
    }

    /** Un exercice Garmin. */
    public record Exercise(String category, String name) {
    }

    private record Rule(Pattern pattern, Exercise exercise) {
    }

    private static Rule rule(String regex, String category, String name) {
        return new Rule(Pattern.compile("\\b(?:" + regex + ")\\b"), new Exercise(category, name));
    }

    /** Ordre significatif : la première règle qui reconnaît le nom l'emporte. */
    private static final List<Rule> RULES = List.of(
            // Charnière de hanche
            rule("hip thrust", "HIP_RAISE", "BARBELL_HIP_THRUST_ON_FLOOR"),
            rule("pont fessier (?:unilateral|une jambe)|single leg (?:glute )?bridge", "HIP_RAISE", "SINGLE_LEG_HIP_RAISE"),
            rule("pont fessier|glute bridge|releve de bassin", "HIP_RAISE", "HIP_RAISE"),
            rule("swing", "HIP_RAISE", "KETTLEBELL_SWING"),
            rule("souleve de terre (?:unipodal|unilateral|une jambe)|single leg (?:romanian )?deadlift|single leg rdl",
                    "DEADLIFT", "SINGLE_LEG_ROMANIAN_DEADLIFT_WITH_DUMBBELL"),
            rule("souleve de terre roumain|romanian deadlift|rdl", "DEADLIFT", "ROMANIAN_DEADLIFT"),
            rule("trap bar", "DEADLIFT", "TRAP_BAR_DEADLIFT"),
            rule("souleve de terre|deadlift", "DEADLIFT", "BARBELL_DEADLIFT"),
            rule("good morning", "LEG_CURL", "GOOD_MORNING"),
            rule("nordic|leg curl|curl ischio", "LEG_CURL", "LEG_CURL"),
            // Fentes et squats
            rule("fentes? sautees?|jump lunges?", "PLYO", "ALTERNATING_JUMP_LUNGE"),
            rule("fentes? bulgares?|bulgarian split squat", "LUNGE", "DUMBBELL_BULGARIAN_SPLIT_SQUAT"),
            rule("fentes? arrieres?|reverse lunges?", "LUNGE", "DUMBBELL_REVERSE_LUNGE"),
            rule("fentes? laterales?|side lunges?|lateral lunges?", "LUNGE", "DUMBBELL_SIDE_LUNGE"),
            rule("fentes? croisees?|curtsy", "LUNGE", "CURTSY_LUNGE"),
            rule("fentes? marchees?|walking lunges?", "LUNGE", "WALKING_LUNGE"),
            rule("fentes?|lunges?|split squat", "LUNGE", "LUNGE"),
            rule("squats? sautes?|jump squats?|squat jumps?", "PLYO", "JUMP_SQUAT"),
            rule("goblet", "SQUAT", "GOBLET_SQUAT"),
            rule("squat avant|front squat", "SQUAT", "BARBELL_FRONT_SQUAT"),
            rule("squat arriere|back squat", "SQUAT", "BARBELL_BACK_SQUAT"),
            rule("pistol", "SQUAT", "PISTOL_SQUAT"),
            rule("chaise|wall sit|isometrique quadriceps au mur", "SQUAT", "BODY_WEIGHT_WALL_SQUAT"),
            rule("step[ -]?ups?|montees? sur (?:box|banc|marche)", "SQUAT", "DUMBBELL_STEP_UP"),
            rule("presse a cuisses|leg press", "SQUAT", "LEG_PRESS"),
            rule("squats?", "SQUAT", "SQUAT"),
            // Mollets
            rule("(?:mollets?|calf raises?)\\b.*\\b(?:unilateral|une jambe|unipodal)", "CALF_RAISE", "SINGLE_LEG_STANDING_CALF_RAISE"),
            rule("mollets?|calf raises?", "CALF_RAISE", "STANDING_CALF_RAISE"),
            // Pliométrie
            rule("drop jumps?|depth jumps?", "PLYO", "DEPTH_JUMP"),
            rule("box jumps?|sauts? sur (?:box|caisse)", "PLYO", "BOX_JUMP"),
            rule("burpees?", "TOTAL_BODY", "BURPEE"),
            rule("jumping jacks?", "CARDIO", "JUMPING_JACKS"),
            // Tronc
            rule("gainage lateral|planche laterale|side plank", "PLANK", "SIDE_PLANK"),
            rule("mountain climbers?", "PLANK", "MOUNTAIN_CLIMBER"),
            rule("gainage|planche|plank", "PLANK", "PLANK"),
            rule("dead ?bug", "HIP_STABILITY", "DEAD_BUG"),
            rule("russian twist", "CORE", "RUSSIAN_TWIST"),
            rule("superman", "HYPEREXTENSION", "SUPERMAN_FROM_FLOOR"),
            rule("crunch", "CRUNCH", "CRUNCH"),
            rule("releves? de jambes|leg raises?", "LEG_RAISE", "HANGING_LEG_RAISE"),
            rule("sit[ -]?ups?|redressements? assis", "SIT_UP", "SIT_UP"),
            // Hanche, stabilité
            rule("monster walk|marche laterale", "HIP_STABILITY", "LATERAL_WALKS_WITH_BAND_AT_ANKLES"),
            rule("abduction", "HIP_STABILITY", "STANDING_HIP_ABDUCTION"),
            rule("fire hydrant", "HIP_STABILITY", "FIRE_HYDRANT_KICKS"),
            // Haut du corps
            rule("tirage vertical|lat pull ?down", "PULL_UP", "LAT_PULLDOWN"),
            rule("tractions? supination|chin[ -]?ups?", "PULL_UP", "CHIN_UP"),
            rule("tractions?|pull[ -]?ups?", "PULL_UP", "PULL_UP"),
            rule("rowing inverse|inverted rows?|tirage australien", "ROW", "INVERTED_ROW"),
            rule("rowing barre|barbell rows?", "ROW", "BARBELL_ROW"),
            rule("rowing|rows?|tirage horizontal", "ROW", "DUMBBELL_ROW"),
            rule("pompes?|push[ -]?ups?", "PUSH_UP", "PUSH_UP"),
            rule("developpe couche|bench press", "BENCH_PRESS", "BARBELL_BENCH_PRESS"),
            rule("developpe (?:militaire|epaules?)|overhead press|shoulder press", "SHOULDER_PRESS", "OVERHEAD_DUMBBELL_PRESS"),
            rule("dips?", "TRICEPS_EXTENSION", "BENCH_DIP"),
            rule("elevations? laterales?|lateral raises?", "LATERAL_RAISE", "DUMBBELL_LATERAL_RAISE"),
            rule("curl biceps|biceps curl", "CURL", "DUMBBELL_BICEPS_CURL"),
            rule("marche du fermier|farmers? (?:walk|carry)", "CARRY", "FARMERS_WALK"));

    /** L'exercice Garmin reconnu dans ce nom, s'il y en a un. */
    public static Optional<Exercise> match(String exerciseName) {
        if (exerciseName == null || exerciseName.isBlank()) {
            return Optional.empty();
        }
        String text = normalize(exerciseName);
        for (Rule r : RULES) {
            if (r.pattern().matcher(text).find()) {
                return Optional.of(r.exercise());
            }
        }
        return Optional.empty();
    }

    /** Minuscules, sans accents, ponctuation réduite à des espaces. */
    static String normalize(String s) {
        String noAccents = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return noAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 -]+", " ").replaceAll("\\s+", " ").trim();
    }
}
