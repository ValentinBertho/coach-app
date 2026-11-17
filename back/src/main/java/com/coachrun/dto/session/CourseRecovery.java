package com.coachrun.dto.session;

/**
 * Récupération entre répétitions d'un bloc course (cf. DARI Lab).
 * {@code type} : ex. « jog », « walk », « static ». Allure optionnelle (récup active).
 */
public record CourseRecovery(
        String type,
        Integer durationS,
        Integer distanceM,
        CoursePrescription prescription,
        /** Récup libre : l'athlète repart quand il le veut (bouton Lap). Valeur saisie = indicative. */
        Boolean open
) {
    public CourseRecovery(String type, Integer durationS, Integer distanceM, CoursePrescription prescription) {
        this(type, durationS, distanceM, prescription, null);
    }

    /** Durée libre (bouton Lap) ? */
    public boolean endsOnLap() {
        return Boolean.TRUE.equals(open);
    }
}
