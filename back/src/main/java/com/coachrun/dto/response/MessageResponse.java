package com.coachrun.dto.response;

import com.coachrun.entity.Message;
import com.coachrun.entity.enums.UserRole;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        String body,
        UserRole senderRole,
        String senderName,
        UUID senderUserId,
        UUID workoutId,
        Instant createdAt) {

    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(), m.getBody(), m.getSenderRole(), m.getSenderName(),
                m.getSenderUserId(), m.getWorkoutId(), m.getCreatedAt());
    }
}
