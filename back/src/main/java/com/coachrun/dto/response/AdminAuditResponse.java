package com.coachrun.dto.response;

import com.coachrun.entity.AdminAuditLog;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne du journal d'administration, prête à afficher.
 *
 * <p>Les champs ajoutés répondent aux trois questions que l'écran ne savait pas poser : de quel
 * droit ({@code actorRole}), qui vraiment ({@code impersonatorEmail}) et par où
 * ({@code requestMethod} / {@code requestPath}, {@code userAgent}). Le navigateur était d'ailleurs
 * enregistré depuis l'origine sans jamais ressortir — une information collectée et invisible ne
 * sert personne.</p>
 */
public record AdminAuditResponse(
        UUID id,
        UUID actorUserId,
        String actorEmail,
        /** Rôle de l'acteur au moment du geste — pas celui qu'il a aujourd'hui. */
        String actorRole,
        /** Administrateur derrière une session empruntée ; null dans le cas normal. */
        UUID impersonatorUserId,
        String impersonatorEmail,
        AdminAuditAction action,
        String actionLabel,
        boolean sensitive,
        AdminAuditTarget targetType,
        String targetTypeLabel,
        UUID targetId,
        String targetLabel,
        String summary,
        String ipAddress,
        String userAgent,
        String requestMethod,
        String requestPath,
        Instant occurredAt) {

    public static AdminAuditResponse from(AdminAuditLog a) {
        AdminAuditAction action = a.getAction();
        AdminAuditTarget target = a.getTargetType();
        return new AdminAuditResponse(
                a.getId(),
                a.getActorUserId(),
                a.getActorEmail(),
                a.getActorRole(),
                a.getImpersonatorUserId(),
                a.getImpersonatorEmail(),
                action,
                action != null ? action.label() : null,
                // Un geste fait depuis une session empruntée est signalé quelle que soit
                // l'action : c'est le contexte qui est exceptionnel, pas le geste.
                (action != null && action.sensitive()) || a.getImpersonatorUserId() != null,
                target,
                target != null ? target.label() : null,
                a.getTargetId(),
                a.getTargetLabel(),
                a.getSummary(),
                a.getIpAddress(),
                a.getUserAgent(),
                a.getRequestMethod(),
                a.getRequestPath(),
                a.getOccurredAt());
    }
}
