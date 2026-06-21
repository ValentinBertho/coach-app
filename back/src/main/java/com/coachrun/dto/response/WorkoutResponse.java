package com.coachrun.dto.response;

import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkoutResponse(
        UUID id,
        UUID athleteId,
        LocalDate scheduledDate,
        WorkoutType type,
        WorkoutStatus status,
        String title,
        String notes,
        Integer targetDistanceM,
        Integer targetDurationS,
        List<WorkoutStepResponse> steps) {

    public static WorkoutResponse from(Workout w) {
        return new WorkoutResponse(
                w.getId(),
                w.getAthlete().getId(),
                w.getScheduledDate(),
                w.getType(),
                w.getStatus(),
                w.getTitle(),
                w.getNotes(),
                w.getTargetDistanceM(),
                w.getTargetDurationS(),
                w.getSteps().stream().map(WorkoutStepResponse::from).toList());
    }
}
