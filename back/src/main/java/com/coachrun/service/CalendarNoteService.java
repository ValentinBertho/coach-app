package com.coachrun.service;

import com.coachrun.dto.request.CalendarNoteRequest;
import com.coachrun.dto.response.CalendarNoteResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.CalendarNote;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.CalendarNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Notes libres du coach sur le calendrier d'un athlète (CDC §8). Scoping tenant. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarNoteService {

    private final CalendarNoteRepository noteRepository;
    private final AthleteRepository athleteRepository;

    public List<CalendarNoteResponse> list(UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        return noteRepository.findByClubIdAndAthleteIdAndNoteDateBetweenOrderByNoteDateAsc(clubId, athleteId, from, to)
                .stream().map(CalendarNoteResponse::from).toList();
    }

    /** Variante athlète-scopée (portail /me) : l'athlète lit ses propres notes. */
    public List<CalendarNoteResponse> listForAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        return noteRepository.findByAthleteIdAndNoteDateBetweenOrderByNoteDateAsc(athleteId, from, to)
                .stream().map(CalendarNoteResponse::from).toList();
    }

    @Transactional
    public CalendarNoteResponse create(UUID clubId, UUID athleteId, CalendarNoteRequest req) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        CalendarNote note = new CalendarNote();
        note.setClub(athlete.getClub());
        note.setAthlete(athlete);
        note.setNoteDate(req.noteDate());
        note.setText(req.text());
        return CalendarNoteResponse.from(noteRepository.save(note));
    }

    @Transactional
    public CalendarNoteResponse update(UUID clubId, UUID noteId, CalendarNoteRequest req) {
        CalendarNote note = require(clubId, noteId);
        note.setNoteDate(req.noteDate());
        note.setText(req.text());
        return CalendarNoteResponse.from(note);
    }

    @Transactional
    public void delete(UUID clubId, UUID noteId) {
        noteRepository.delete(require(clubId, noteId));
    }

    private CalendarNote require(UUID clubId, UUID noteId) {
        return noteRepository.findByIdAndClubId(noteId, clubId)
                .orElseThrow(() -> new NotFoundException("Note introuvable."));
    }
}
