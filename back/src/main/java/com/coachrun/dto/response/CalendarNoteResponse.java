package com.coachrun.dto.response;

import com.coachrun.entity.CalendarNote;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Note de calendrier. {@code endDate} nul = note d'un jour ; sinon la note couvre la période
 * {@code noteDate}…{@code endDate} incluse — ce que l'interface appelle un cycle.
 */
public record CalendarNoteResponse(UUID id, UUID athleteId, LocalDate noteDate,
                                   LocalDate endDate, String text) {

    public static CalendarNoteResponse from(CalendarNote n) {
        return new CalendarNoteResponse(n.getId(), n.getAthlete().getId(), n.getNoteDate(),
                n.getEndDate(), n.getText());
    }
}
