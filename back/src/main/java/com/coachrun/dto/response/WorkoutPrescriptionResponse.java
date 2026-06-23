package com.coachrun.dto.response;

import com.coachrun.dto.session.SessionStructure;

/**
 * Prescription figée d'une séance planifiée : snapshot des blocs (au moment de l'assignation)
 * et cibles calculées pour l'athlète.
 */
public record WorkoutPrescriptionResponse(
        SessionStructure snapshot,
        CalculatedSessionResponse calculated
) {
}
