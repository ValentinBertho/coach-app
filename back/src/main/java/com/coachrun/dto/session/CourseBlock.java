package com.coachrun.dto.session;

/**
 * Bloc d'une séance course (cf. DARI Lab). Ex. « 6 × 1000 m à 98–103 % allure 5 km, récup trot 90 s ».
 * {@code type} : ex. « intervals », « tempo », « easy », « recovery », « run ».
 *
 * <p>Deux niveaux de répétition, et ils ne disent pas la même chose : {@code reps} compte les
 * répétitions <b>dans</b> une série (« 6 × 400 m »), {@code sets} compte les séries elles-mêmes
 * (« 2 × (6 × 400 m) »). Le second manquait : un coach qui écrivait un bloc à doubler devait le
 * saisir deux fois, et le retoucher deux fois à chaque ajustement.</p>
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
        java.util.List<java.util.UUID> drillIds,
        /** Nombre de séries : le bloc entier (répétitions et récupération) est répété d'autant. */
        Integer sets,
        /** Récupération entre deux séries — plus longue que celle entre répétitions. */
        CourseRecovery setRecovery
) {

    /**
     * Nombre de séries effectif. Une séance écrite avant l'existence des séries n'en porte aucune,
     * et vaut exactement une série : c'est ce repli qui garantit que les totaux d'une ancienne
     * séance ne bougent pas d'un mètre.
     */
    public int setCount() {
        return sets == null || sets < 1 ? 1 : sets;
    }

    /** Nombre de répétitions effectif, séries exclues. */
    public int repCount() {
        return reps == null || reps < 1 ? 1 : reps;
    }
}
