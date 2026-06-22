package com.coachrun.dto.response;

import com.coachrun.entity.TrainingPlan;

import java.util.List;
import java.util.UUID;

public record TrainingPlanResponse(
        UUID id,
        String name,
        String description,
        int durationWeeks,
        List<PlanItem> items) {

    public record PlanItem(int weekIndex, int dayOfWeek, UUID templateId, String templateName) {
    }

    public static TrainingPlanResponse of(TrainingPlan p, List<PlanItem> items) {
        return new TrainingPlanResponse(p.getId(), p.getName(), p.getDescription(),
                p.getDurationWeeks(), items);
    }
}
