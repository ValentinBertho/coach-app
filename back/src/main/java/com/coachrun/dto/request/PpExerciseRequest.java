package com.coachrun.dto.request;

import com.coachrun.entity.enums.EquipmentType;
import com.coachrun.entity.enums.ExerciseCategory;
import com.coachrun.entity.enums.ExerciseLevel;
import com.coachrun.entity.enums.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Création / mise à jour d'un exercice de préparation physique. */
public record PpExerciseRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull ExerciseCategory category,
        ExerciseLevel level,
        String objective,
        List<MuscleGroup> muscleGroups,
        List<EquipmentType> equipment,
        @Size(max = 1024) String videoUrl,
        @Size(max = 1024) String imageUrl,
        @Size(max = 4096) String instructions,
        @Size(max = 4096) String technicalNotes,
        @Size(max = 2048) String contraindications,
        UUID progressionId,
        UUID regressionId
) {
}
