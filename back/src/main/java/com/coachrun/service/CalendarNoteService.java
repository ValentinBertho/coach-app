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
    private final com.coachrun.repository.UserRepository userRepository;

    public List<CalendarNoteResponse> list(UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        return noteRepository.findOverlapping(clubId, athleteId, from, to)
                .stream().map(n -> CalendarNoteResponse.from(n, authorName(n))).toList();
    }

    /** Nom de l'auteur, quand la note en porte un — les notes antérieures n'en ont pas. */
    private String authorName(CalendarNote note) {
        return note.getAuthorUserId() == null ? null
                : userRepository.findById(note.getAuthorUserId())
                        .map(com.coachrun.entity.User::getFullName).orElse(null);
    }

    /**
     * Ce que l'athlète voit sur son calendrier : les cycles, les notes que le coach lui a
     * <b>adressées</b>, et les siennes.
     *
     * <p>Pas le reste. Une note d'un jour non partagée est le carnet de travail du coach —
     * « relancer sur le sommeil », « surveiller ce genou » — écrite en le croyant privé. Ouvrir
     * l'écriture à l'athlète ne change rien à cela : c'est le drapeau de partage qui décide, pas
     * la table.</p>
     */
    public List<CalendarNoteResponse> listForAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        return noteRepository.findOverlappingForAthlete(athleteId, from, to).stream()
                .filter(CalendarNote::isVisibleToAthlete)
                .map(n -> CalendarNoteResponse.from(n, authorName(n)))
                .toList();
    }

    /**
     * L'athlète pose un mot sur son calendrier.
     *
     * <p>Toujours partagé : une note qu'un athlète écrit n'a de sens que lue par son coach —
     * « je finis tard mardi », « piste fermée jeudi ». Toujours d'un jour, aussi : décrire un
     * bloc d'entraînement est le travail du coach, et un athlète qui poserait un cycle
     * brouillerait la lecture de sa propre préparation.</p>
     */
    @Transactional
    public CalendarNoteResponse createByAthlete(UUID athleteId, UUID authorUserId, CalendarNoteRequest req) {
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        CalendarNote note = new CalendarNote();
        note.setClub(athlete.getClub());
        note.setAthlete(athlete);
        note.setNoteDate(req.noteDate());
        note.setEndDate(null);
        note.setText(req.text());
        note.setShared(true);
        note.setAuthorUserId(authorUserId);
        note.setAuthorRole(com.coachrun.entity.enums.UserRole.ATHLETE);
        return CalendarNoteResponse.from(noteRepository.save(note), null);
    }

    /**
     * Suppression par l'athlète, bornée à <b>ses</b> notes : effacer le mot de son coach n'est pas
     * la même chose que retirer le sien.
     */
    @Transactional
    public void deleteByAthlete(UUID athleteId, UUID noteId, UUID authorUserId) {
        CalendarNote note = noteRepository.findById(noteId)
                .filter(n -> n.getAthlete().getId().equals(athleteId))
                .filter(n -> authorUserId.equals(n.getAuthorUserId()))
                .orElseThrow(() -> new NotFoundException("Note introuvable."));
        noteRepository.delete(note);
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
    public CalendarNoteResponse create(UUID clubId, UUID athleteId, UUID authorUserId,
                                       CalendarNoteRequest req) {
        Athlete athlete = athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        CalendarNote note = new CalendarNote();
        note.setClub(athlete.getClub());
        note.setAthlete(athlete);
        note.setNoteDate(req.noteDate());
        note.setEndDate(endDateOf(req));
        note.setText(req.text());
        note.setShared(Boolean.TRUE.equals(req.shared()));
        note.setAuthorUserId(authorUserId);
        note.setAuthorRole(com.coachrun.entity.enums.UserRole.COACH);
        return CalendarNoteResponse.from(noteRepository.save(note), null);
    }

    @Transactional
    public CalendarNoteResponse update(UUID clubId, UUID noteId, CalendarNoteRequest req) {
        CalendarNote note = require(clubId, noteId);
        note.setNoteDate(req.noteDate());
        note.setEndDate(endDateOf(req));
        note.setText(req.text());
        if (req.shared() != null) {
            note.setShared(req.shared());
        }
        return CalendarNoteResponse.from(note, authorName(note));
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
