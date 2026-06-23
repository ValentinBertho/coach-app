package com.coachrun.dto.session;

import com.coachrun.entity.enums.PrescriptionRef;

/**
 * Prescription d'allure en fourchette (cf. DARI Lab) : référentiel + bornes min/max en %.
 * Jamais de valeur sèche — toujours une fourchette.
 */
public record CoursePrescription(
        PrescriptionRef ref,
        Double minPct,
        Double maxPct
) {
}
