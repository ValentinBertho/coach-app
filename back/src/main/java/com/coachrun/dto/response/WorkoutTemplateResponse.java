package com.coachrun.dto.response;

import com.coachrun.dto.request.WorkoutStepRequest;
import com.coachrun.entity.SessionCategory;
import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.entity.enums.WorkoutType;

import java.util.List;
import java.util.UUID;

public record WorkoutTemplateResponse(
        UUID id,
        String name,
        WorkoutType type,
        String title,
        String notes,
        Integer targetDistanceM,
        Integer targetDurationS,
        UUID categoryId,
        String categoryName,
        boolean favorite,
        int useCount,
        List<WorkoutStepRequest> steps) {

    public static WorkoutTemplateResponse of(WorkoutTemplate t, List<WorkoutStepRequest> steps) {
        SessionCategory cat = t.getCategory();
        return new WorkoutTemplateResponse(
                t.getId(), t.getName(), t.getType(), t.getTitle(), t.getNotes(),
                t.getTargetDistanceM(), t.getTargetDurationS(),
                cat == null ? null : cat.getId(),
                cat == null ? null : cat.getName(),
                t.isFavorite(), t.getUseCount(), steps);
    }
}
