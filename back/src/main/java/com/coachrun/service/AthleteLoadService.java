package com.coachrun.service;

import com.coachrun.dto.response.LoadResponse;
import com.coachrun.engine.LoadEngine;
import com.coachrun.engine.LoadEngine.SessionLoad;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Calcule la charge d'entraînement d'un athlète (ACWR/monotonie) à partir des retours de séance
 * (RPE × durée). Course aujourd'hui ; la force s'additionnera au même score (Sprint 3) via le
 * même moteur. Scoping tenant systématique.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteLoadService {

    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final LoadEngine loadEngine;

    /** Charge — variante athlète-scopée (portail /me) : résout le club de l'athlète. */
    public LoadResponse loadForAthlete(UUID athleteId) {
        com.coachrun.entity.Athlete a = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return load(a.getClub().getId(), athleteId);
    }

    public LoadResponse load(UUID clubId, UUID athleteId) {
        if (athleteRepository.findByIdAndClubId(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(27);

        List<SessionLoad> sessions = new ArrayList<>();
        for (Workout w : workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, from, today)) {
            if (w.getRpe() == null) {
                continue;
            }
            Integer durationS = durationSeconds(w);
            if (durationS == null || durationS <= 0) {
                continue;
            }
            double load = w.getRpe() * (durationS / 60.0);
            sessions.add(new SessionLoad(w.getScheduledDate(), load, w.getRpe()));
        }

        return LoadResponse.from(loadEngine.compute(sessions, today));
    }

    /** Durée de séance : cible globale, sinon somme des étapes. */
    private Integer durationSeconds(Workout w) {
        if (w.getTargetDurationS() != null && w.getTargetDurationS() > 0) {
            return w.getTargetDurationS();
        }
        int sum = 0;
        for (WorkoutStep step : w.getSteps()) {
            if (step.getDurationS() != null) {
                sum += step.getDurationS() * Math.max(1, step.getRepetitions());
            }
        }
        return sum > 0 ? sum : null;
    }
}
