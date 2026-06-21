package com.coachrun.dto.response;

import com.coachrun.entity.Club;
import com.coachrun.entity.enums.ClubStatus;

import java.time.Instant;
import java.util.UUID;

public record ClubResponse(UUID id, String name, String slug, ClubStatus status, Instant createdAt) {

    public static ClubResponse from(Club c) {
        return new ClubResponse(c.getId(), c.getName(), c.getSlug(), c.getStatus(), c.getCreatedAt());
    }
}
