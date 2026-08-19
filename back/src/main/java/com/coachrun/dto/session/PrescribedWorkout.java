package com.coachrun.dto.session;

import com.coachrun.entity.enums.WorkoutType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Données d'une séance prescrite depuis la bibliothèque (porteur interne) : métadonnées +
 * snapshot figé de la prescription et cibles calculées (JSON), prêtes à persister sur un {@code Workout}.
 */
public record PrescribedWorkout(
        LocalDate date,
        WorkoutType type,
        String title,
        String notes,
        Integer targetDistanceM,
        Integer targetDurationS,
        /** Effort perçu attendu pour la séance entière (1–10), annoncé par le coach. */
        Integer targetRpe,
        UUID sourceTemplateId,
        String snapshotJson,
        String calculatedJson,
        /** Charge prévue (UA) calculée depuis la prescription, nulle si non exploitable. */
        Integer plannedLoadUa
) {
}
