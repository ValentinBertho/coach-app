package com.coachrun.dto.response;

import com.coachrun.entity.CalendarNote;

import java.time.LocalDate;
import java.util.UUID;

public record CalendarNoteResponse(UUID id, UUID athleteId, LocalDate noteDate, String text) {

    public static CalendarNoteResponse from(CalendarNote n) {
        return new CalendarNoteResponse(n.getId(), n.getAthlete().getId(), n.getNoteDate(), n.getText());
    }
}
