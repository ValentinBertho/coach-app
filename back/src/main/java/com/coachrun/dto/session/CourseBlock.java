package com.coachrun.dto.session;

/**
 * Bloc d'une séance course (cf. DARI Lab). Ex. « 6 × 1000 m à 98–103 % allure 5 km, récup trot 90 s ».
 * {@code type} : ex. « intervals », « tempo », « easy », « recovery », « run ».
 *
 * <p>Deux niveaux de répétition, et ils ne disent pas la même chose : {@code reps} compte les
 * répétitions <b>dans</b> une série (« 6 × 400 m »), {@code sets} compte les séries elles-mêmes
 * (« 2 × (6 × 400 m) »). Le second manquait : un coach qui écrivait un bloc à doubler devait le
 * saisir deux fois, et le retoucher deux fois à chaque ajustement.</p>
 *
 * <h2>Bloc simple ou enchaînement</h2>
 *
 * <p>{@code steps} vide (le cas de toute séance écrite jusqu'ici) : le bloc est <b>simple</b>, il
 * répète {@code reps} fois le même effort, décrit par {@code distanceM}/{@code durationS} et
 * {@code prescription}, avec {@code recovery} entre les répétitions.</p>
 *
 * <p>{@code steps} renseigné : le bloc est un <b>enchaînement</b>. Chaque répétition parcourt les
 * étapes dans l'ordre, chacune avec sa propre allure et sa propre récupération — « 8 × (200 m /
 * 400 m), 100 m de récup entre chaque ». Le volume et la prescription du bloc lui-même ne sont
 * alors pas lus : ce sont les étapes qui les portent.</p>
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
        CourseRecovery setRecovery,
        /**
         * Étapes enchaînées <b>à l'intérieur</b> de chaque répétition, ou vide pour un bloc simple.
         * Voir {@link CourseStep} : c'est ce qui permet d'écrire « 8 × (200 m / 400 m) » à deux
         * allures distinctes en un seul bloc.
         */
        java.util.List<CourseStep> steps
) {

    /**
     * Étapes du bloc, jamais {@code null}. Vide = bloc simple, et c'est ce que vaut toute séance
     * écrite avant l'existence des enchaînements : le repli garantit que leurs totaux et leur
     * affichage ne bougent pas.
     */
    public java.util.List<CourseStep> stepList() {
        return steps == null ? java.util.List.of() : steps;
    }

    /** Ce bloc enchaîne-t-il plusieurs efforts par répétition ? */
    public boolean isChain() {
        return !stepList().isEmpty();
    }

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
