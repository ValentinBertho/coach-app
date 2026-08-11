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
        return noteRepository.findOverlapping(clubId, athleteId, from, to)
                .stream().map(CalendarNoteResponse::from).toList();
    }

    /** Variante athlète-scopée (portail /me) : l'athlète lit ses propres notes. */
    public List<CalendarNoteResponse> listForAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        return noteRepository.findOverlappingForAthlete(athleteId, from, to)
                .stream().map(CalendarNoteResponse::from).toList();
    }

    /**
     * Les <b>cycles</b> de l'athlète sur une fenêtre — et eux seuls.
     *
     * <p><b>Pourquoi ce filtre, et pas la liste complète.</b> Le calendrier du coach porte deux
     * choses sous la même entité. Les <b>cycles</b> décrivent le bloc d'entraînement en cours
     * (« spécifique », « affûtage ») : ils s'adressent à l'athlète, qui a besoin de savoir où il
     * en est dans sa préparation, et c'est justement ce qui lui manquait. Les <b>notes d'un
     * jour</b>, elles, sont le carnet de travail du coach — « relancer sur le sommeil »,
     * « surveiller ce genou » — écrites en le croyant privé. Les exposer d'un bloc parce qu'elles
     * partagent une table serait une rupture de confiance, et le coach cesserait d'en écrire.</p>
     *
     * <p>Un cycle se reconnaît à sa période : une date de fin postérieure à sa date de début.</p>
     */
    public List<CalendarNoteResponse> cyclesForAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        return noteRepository.findOverlappingForAthlete(athleteId, from, to).stream()
                .filter(n -> n.getEndDate() != null && n.getEndDate().isAfter(n.getNoteDate()))
                .map(CalendarNoteResponse::from)
                .toList();
    }

    @Transactional
    public CalendarNoteResponse create(UUID clubId, UUID athleteId, CalendarNoteRequest req) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        CalendarNote note = new CalendarNote();
        note.setClub(athlete.getClub());
        note.setAthlete(athlete);
        note.setNoteDate(req.noteDate());
        note.setEndDate(endDateOf(req));
        note.setText(req.text());
        return CalendarNoteResponse.from(noteRepository.save(note));
    }

    @Transactional
    public CalendarNoteResponse update(UUID clubId, UUID noteId, CalendarNoteRequest req) {
        CalendarNote note = require(clubId, noteId);
        note.setNoteDate(req.noteDate());
        note.setEndDate(endDateOf(req));
        note.setText(req.text());
        return CalendarNoteResponse.from(note);
    }

    @Transactional
    public void delete(UUID clubId, UUID noteId) {
        noteRepository.delete(require(clubId, noteId));
    }

    /**
     * Fin de période validée. Une fin antérieure au début est refusée plutôt que corrigée : la
     * corriger en silence rendrait une saisie fautive indiscernable d'une saisie juste.
     * Une fin égale au début vaut note d'un jour — on la ramène à {@code null}.
     */
    private LocalDate endDateOf(CalendarNoteRequest req) {
        if (!req.hasValidRange()) {
            throw new com.coachrun.exception.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "La fin d'un cycle ne peut pas précéder son début.");
        }
        return req.endDate() == null || req.endDate().isEqual(req.noteDate()) ? null : req.endDate();
    }

    private CalendarNote require(UUID clubId, UUID noteId) {
        return noteRepository.findByIdAndClubId(noteId, clubId)
                .orElseThrow(() -> new NotFoundException("Note introuvable."));
    }
}
