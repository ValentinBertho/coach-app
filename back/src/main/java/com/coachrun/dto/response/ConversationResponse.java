package com.coachrun.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * Une conversation de la boîte de réception coach : l'athlète, le dernier message échangé et
 * le nombre de messages de l'athlète que le coach n'a pas encore ouverts.
 */
public record ConversationResponse(
        UUID athleteId,
        String firstName,
        String lastName,
        String lastMessage,
        /** Auteur du dernier message : COACH ou ATHLETE (pour préfixer l'aperçu). */
        String lastSenderRole,
        Instant lastMessageAt,
        long unreadCount) {
}
