package com.coachrun.dto.response;

import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.IntensityZone;
import com.coachrun.entity.enums.WorkoutStepType;

import java.util.UUID;

public record WorkoutStepResponse(
        UUID id,
        int orderIndex,
        WorkoutStepType stepType,
        int repetitions,
        IntensityZone zone,
        Integer distanceM,
        Integer durationS,
        String notes) {

    public static WorkoutStepResponse from(WorkoutStep s) {
        return new WorkoutStepResponse(
                s.getId(), s.getOrderIndex(), s.getStepType(), s.getRepetitions(),
                s.getZone(), s.getDistanceM(), s.getDurationS(), s.getNotes());
    }
}
