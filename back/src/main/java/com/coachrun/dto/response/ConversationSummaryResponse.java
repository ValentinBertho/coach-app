package com.coachrun.dto.response;

import com.coachrun.entity.enums.ConversationKind;

import java.time.Instant;
import java.util.UUID;

/**
 * Une ligne de boîte de réception : de quel fil s'agit-il, avec qui, et qu'y a-t-il de neuf.
 *
 * @param id           le fil
 * @param kind         sa nature (binôme, coach à coach, groupe, club)
 * @param title        ce qu'on lit dans la liste : le nom de l'interlocuteur, du groupe ou du club
 * @param subtitle     précision facultative — le rôle de l'interlocuteur, l'effectif d'un groupe
 * @param athleteId    l'athlète du binôme, pour les écrans qui ouvrent sa fiche
 * @param lastMessage  aperçu du dernier message
 * @param lastSenderName qui l'a écrit (dans un fil de groupe, l'aperçu ne se comprend pas sans)
 * @param unreadCount  ce qui est arrivé depuis le dernier passage de CETTE personne
 * @param canPost      peut-elle y écrire ? (le fil du club est un canal d'annonces)
 */
public record ConversationSummaryResponse(
        UUID id,
        ConversationKind kind,
        String title,
        String subtitle,
        UUID athleteId,
        UUID groupId,
        String lastMessage,
        String lastSenderName,
        Instant lastMessageAt,
        long unreadCount,
        boolean canPost) {
}
