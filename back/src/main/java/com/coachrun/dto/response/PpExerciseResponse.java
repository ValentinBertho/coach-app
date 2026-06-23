package com.coachrun.dto.response;

import com.coachrun.entity.PpExercise;
import com.coachrun.entity.enums.EquipmentType;
import com.coachrun.entity.enums.ExerciseCategory;
import com.coachrun.entity.enums.ExerciseLevel;
import com.coachrun.entity.enums.MuscleGroup;

import java.util.List;
import java.util.UUID;

/** Exercice de préparation physique. */
public record PpExerciseResponse(
        UUID id,
        String name,
        ExerciseCategory category,
        ExerciseLevel level,
        String objective,
        List<MuscleGroup> muscleGroups,
        List<EquipmentType> equipment,
        String videoUrl,
        String imageUrl,
        String instructions,
        String technicalNotes,
        String contraindications,
        UUID progressionId,
        UUID regressionId,
        boolean favorite,
        int useCount
) {

    public static PpExerciseResponse from(PpExercise e) {
        return new PpExerciseResponse(
                e.getId(), e.getName(), e.getCategory(), e.getLevel(), e.getObjective(),
                e.getMuscleGroups().stream().sorted().toList(),
                e.getEquipment().stream().sorted().toList(),
                e.getVideoUrl(), e.getImageUrl(), e.getInstructions(),
                e.getTechnicalNotes(), e.getContraindications(),
                e.getProgressionId(), e.getRegressionId(), e.isFavorite(), e.getUseCount());
    }
}
