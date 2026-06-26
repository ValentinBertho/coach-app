package com.coachrun.dto.response;

import com.coachrun.entity.Notification;

import java.time.Instant;
import java.util.UUID;

/** Notification in-app (centre de notifications). {@code read} dérivé de {@code readAt}. */
public record NotificationResponse(
        UUID id,
        String type,
        String title,
        String body,
        String link,
        boolean read,
        Instant createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getLink(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
