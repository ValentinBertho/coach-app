package com.coachrun.dto.response;

import com.coachrun.entity.MailLog;

import java.time.Instant;

/** Une ligne du journal des envois, pour le diagnostic individuel (« untel a-t-il reçu ? »). */
public record MailLogResponse(
        String recipient,
        String subject,
        String kind,
        String label,
        String audience,
        boolean sent,
        String errorMessage,
        Instant sentAt
) {

    public static MailLogResponse from(MailLog log) {
        return new MailLogResponse(log.getRecipient(), log.getSubject(),
                log.getKind().name(), log.getKind().label(), log.getAudience(),
                log.isSent(), log.getErrorMessage(), log.getSentAt());
    }
}
