package com.coachrun.dto.session;

/**
 * Une allure d'un <b>enchaînement</b> : l'un des efforts qui se succèdent à l'intérieur d'une
 * même répétition (cf. {@link CourseBlock#steps()}).
 *
 * <h2>Ce qui manquait</h2>
 *
 * <p>Un bloc portait <b>une</b> distance et <b>une</b> prescription. « 8 × (200 m / 400 m) », où
 * les 200 se courent à 2'48–3'04 et les 400 à 3'20–3'26, ne rentrait donc pas : les deux allures
 * étant différentes, il fallait seize blocs saisis un par un — et seize retouches à chaque
 * ajustement. Un athlète pouvait courir cette séance et sa montre l'enregistrer ; son coach ne
 * pouvait pas l'écrire.</p>
 *
 * <p>Une étape est délibérément plus pauvre qu'un bloc : ni répétitions ni séries (c'est le bloc
 * qui les porte), ni éducatifs (ils s'attachent à l'échauffement, pas à une fraction de série).
 * Elle a en revanche <b>sa</b> récupération, parce que la récup entre 200 et 400 n'est pas
 * forcément celle qui suit les 400.</p>
 */
public record CourseStep(
        String id,
        Integer distanceM,
        Integer durationS,
        CoursePrescription prescription,
        /** Récupération qui suit <b>cette</b> étape — 100 m de trot entre le 200 et le 400. */
        CourseRecovery recovery,
        /** Effort perçu visé pour cette étape (RPE 1–10). */
        Integer rpe,
        String note
) {
}
