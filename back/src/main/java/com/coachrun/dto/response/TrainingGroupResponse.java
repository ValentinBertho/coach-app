package com.coachrun.dto.response;

import com.coachrun.entity.TrainingGroup;

import java.util.UUID;

public record TrainingGroupResponse(UUID id, String name, long athleteCount) {

    public static TrainingGroupResponse of(TrainingGroup g, long athleteCount) {
        return new TrainingGroupResponse(g.getId(), g.getName(), athleteCount);
    }
}
