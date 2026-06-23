package com.coachrun.dto.session;

/**
 * Récupération entre répétitions d'un bloc course (cf. DARI Lab).
 * {@code type} : ex. « jog », « walk », « static ». Allure optionnelle (récup active).
 */
public record CourseRecovery(
        String type,
        Integer durationS,
        Integer distanceM,
        CoursePrescription prescription
) {
}
