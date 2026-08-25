package com.coachrun.dto.response;

import com.coachrun.entity.CalendarNote;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Note de calendrier. {@code endDate} nul = note d'un jour ; sinon la note couvre la période
 * {@code noteDate}…{@code endDate} incluse — ce que l'interface appelle un cycle.
 */
public record CalendarNoteResponse(UUID id, UUID athleteId, LocalDate noteDate,
                                   LocalDate endDate, String text,
                                   boolean shared, String authorRole, String authorName) {

    public static CalendarNoteResponse from(CalendarNote n) {
        return from(n, null);
    }

    /**
     * @param authorName nom de l'auteur, quand l'appelant a pu le résoudre. Dans un calendrier à
     *                   deux voix, « qui l'a écrit » fait partie du message : une note signée du
     *                   coach ne se lit pas comme un mot laissé par l'athlète.
     */
    public static CalendarNoteResponse from(CalendarNote n, String authorName) {
        return new CalendarNoteResponse(n.getId(), n.getAthlete().getId(), n.getNoteDate(),
                n.getEndDate(), n.getText(), n.isShared(),
                n.getAuthorRole() == null ? "COACH" : n.getAuthorRole().name(), authorName);
    }
}
