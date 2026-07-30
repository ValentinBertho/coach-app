package com.coachrun.dto.session;

/**
 * Bloc d'une séance course (cf. DARI Lab). Ex. « 6 × 1000 m à 98–103 % allure 5 km, récup trot 90 s ».
 * {@code type} : ex. « intervals », « tempo », « easy », « recovery », « run ».
 */
public record CourseBlock(
        String id,
        String type,
        Integer reps,
        Integer distanceM,
        Integer durationS,
        CoursePrescription prescription,
        CourseRecovery recovery,
        /** Effort perçu visé pour ce bloc (RPE 1–10) — saisi sur le contenu de la séance. */
        Integer rpe,
        String note,
        /** Éducatifs de course (gammes) attachés au bloc — ex. échauffement (CDC §8/§9). */
        java.util.List<java.util.UUID> drillIds
) {
}
