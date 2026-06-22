package com.coachrun.dto.response;

import com.coachrun.entity.TrainingPlan;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record TrainingPlanResponse(
        UUID id,
        String name,
        String description,
        int durationWeeks,
        List<PlanItem> items,
        List<RefResponse> athletes) {

    public record PlanItem(int weekIndex, int dayOfWeek, UUID templateId, String templateName) {
    }

    public static TrainingPlanResponse of(TrainingPlan p, List<PlanItem> items) {
        List<RefResponse> athletes = p.getAthletes().stream()
                .map(a -> new RefResponse(a.getId(), a.getFirstName() + " " + a.getLastName()))
                .sorted(Comparator.comparing(RefResponse::name, Comparator.nullsLast(String::compareTo)))
                .toList();
        return new TrainingPlanResponse(p.getId(), p.getName(), p.getDescription(),
                p.getDurationWeeks(), items, athletes);
    }
}
