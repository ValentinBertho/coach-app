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
        UUID sourceTemplateId,
        String snapshotJson,
        String calculatedJson
) {
}
