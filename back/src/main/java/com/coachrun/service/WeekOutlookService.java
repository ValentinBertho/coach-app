package com.coachrun.service;

import com.coachrun.dto.response.WeekOutlookResponse;
import com.coachrun.engine.WeekOutlookEngine;
import com.coachrun.engine.WeekOutlookEngine.PlannedSession;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La semaine en une phrase : ce que la charge prévue fera à l'athlète, dite pendant que le coach
 * construit la semaine.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>{@code Workout.plannedLoadUa} existe depuis longtemps, et le calendrier l'additionne déjà par
 * semaine. Cette somme n'était jamais confrontée à la charge chronique : l'ACWR alertait le lundi
 * suivant, sur une semaine déjà courue. L'information existait <i>avant</i> — elle n'était pas lue.</p>
 *
 * <p>Un coach qui voit « +34 % » pendant qu'il pose ses séances corrige tout de suite. Le même
 * chiffre lundi prochain ne sert plus à rien.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeekOutlookService {

    private final WorkoutRepository workoutRepository;
    private final AthleteRepository athleteRepository;
    private final AthleteLoadService loadService;
    private final WeekOutlookEngine engine;
    private final ObjectMapper objectMapper;
    private final ClockService clock;

    /** Perspective d'une semaine pour un athlète (vue coach). */
    public WeekOutlookResponse forWeek(UUID clubId, UUID athleteId, LocalDate anyDayOfWeek) {
        if (athleteRepository.findByIdAndClubMembership(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        LocalDate monday = (anyDayOfWeek == null ? clock.today() : anyDayOfWeek).with(DayOfWeek.MONDAY);
        return compute(clubId, athleteId, monday);
    }

    /** Même perspective, côté athlète (« semaine chargée » / « semaine de décharge »). */
    public WeekOutlookResponse forMyWeek(UUID athleteId, LocalDate anyDayOfWeek) {
        var athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        LocalDate monday = (anyDayOfWeek == null ? clock.today() : anyDayOfWeek).with(DayOfWeek.MONDAY);
        return compute(athlete.getClub().getId(), athleteId, monday);
    }

    private WeekOutlookResponse compute(UUID clubId, UUID athleteId, LocalDate monday) {
        List<PlannedSession> sessions = new ArrayList<>();
        for (Workout w : workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, monday, monday.plusDays(6))) {
            if (w.getType() == WorkoutType.REST) {
                continue;
            }
            sessions.add(new PlannedSession(w.getScheduledDate(), w.getPlannedLoadUa(),
                    hardestRpe(w)));
        }

        double chronic = 0;
        boolean ready = false;
        try {
            var load = loadService.load(clubId, athleteId);
            chronic = load.chronicLoad28d();
            // On réutilise le garde-fou du moteur de charge : sous 21 jours d'historique, un écart
            // en pourcentage est mécaniquement délirant, et le premier chiffre faux discrédite
            // tous les suivants.
            ready = load.ratioReady();
        } catch (RuntimeException e) {
            log.debug("Charge indisponible pour l'athlète {} : perspective non comparée", athleteId);
        }

        return WeekOutlookResponse.from(monday, engine.compute(sessions, chronic, ready));
    }

    /**
     * RPE le plus élevé de la séance : celui des blocs s'il est saisi, sinon déduit du type.
     *
     * <p>Le type est un repli assumé : un fractionné reste un fractionné même quand personne n'a
     * renseigné de RPE de bloc, et l'ignorer ferait passer une semaine à trois séances dures pour
     * une semaine calme.</p>
     */
    private Integer hardestRpe(Workout w) {
        if (w.getSessionSnapshot() != null && !w.getSessionSnapshot().isBlank()) {
            try {
                var structure = objectMapper.readValue(w.getSessionSnapshot(),
                        com.coachrun.dto.session.SessionStructure.class);
                Integer max = structure.main().stream()
                        .map(com.coachrun.dto.session.CourseBlock::rpe)
                        .filter(java.util.Objects::nonNull)
                        .max(Integer::compareTo).orElse(null);
                if (max != null) {
                    return max;
                }
            } catch (Exception ignored) {
                // Snapshot illisible : on retombe sur le type, comme pour une séance sans structure.
            }
        }
        return switch (w.getType()) {
            case INTERVALS, RACE -> 9;
            case THRESHOLD, TEMPO -> 7;
            case LONG_RUN -> 6;
            default -> 4;
        };
    }
}
