package com.coachrun.dto.response;

import com.coachrun.entity.RunDrill;
import com.coachrun.entity.enums.RunDrillCategory;

import java.util.UUID;

public record RunDrillResponse(
        UUID id,
        String name,
        RunDrillCategory category,
        String description,
        String videoUrl
) {
    public static RunDrillResponse of(RunDrill d) {
        return new RunDrillResponse(d.getId(), d.getName(), d.getCategory(), d.getDescription(), d.getVideoUrl());
    }
}
