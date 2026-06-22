package com.coachrun.dto.response;

import com.coachrun.dto.request.WorkoutStepRequest;
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
        List<WorkoutStepRequest> steps) {

    public static WorkoutTemplateResponse of(WorkoutTemplate t, List<WorkoutStepRequest> steps) {
        return new WorkoutTemplateResponse(
                t.getId(), t.getName(), t.getType(), t.getTitle(), t.getNotes(),
                t.getTargetDistanceM(), t.getTargetDurationS(), steps);
    }
}
