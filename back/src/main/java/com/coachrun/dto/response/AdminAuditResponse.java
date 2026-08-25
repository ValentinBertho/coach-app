package com.coachrun.dto.response;

import com.coachrun.entity.AdminAuditLog;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;

import java.time.Instant;
import java.util.UUID;

/** Une ligne du journal d'administration, prête à afficher. */
public record AdminAuditResponse(
        UUID id,
        UUID actorUserId,
        String actorEmail,
        AdminAuditAction action,
        String actionLabel,
        boolean sensitive,
        AdminAuditTarget targetType,
        String targetTypeLabel,
        UUID targetId,
        String targetLabel,
        String summary,
        String ipAddress,
        Instant occurredAt) {

    public static AdminAuditResponse from(AdminAuditLog a) {
        AdminAuditAction action = a.getAction();
        AdminAuditTarget target = a.getTargetType();
        return new AdminAuditResponse(
                a.getId(),
                a.getActorUserId(),
                a.getActorEmail(),
                action,
                action != null ? action.label() : null,
                action != null && action.sensitive(),
                target,
                target != null ? target.label() : null,
                a.getTargetId(),
                a.getTargetLabel(),
                a.getSummary(),
                a.getIpAddress(),
                a.getOccurredAt());
    }
}
