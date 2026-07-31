package com.coachrun.service;

import com.coachrun.dto.response.LoadResponse;
import com.coachrun.dto.response.LoadSeriesResponse;
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
    private final com.coachrun.repository.StrengthLoadTrackingRepository strengthLoadRepository;
    private final LoadEngine loadEngine;
    private final ClockService clock;

    /** Charge — variante athlète-scopée (portail /me) : résout le club de l'athlète. */
    public LoadResponse loadForAthlete(UUID athleteId) {
        com.coachrun.entity.Athlete a = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return load(a.getClub().getId(), athleteId);
    }

    public LoadResponse load(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        LocalDate today = clock.today();
        return LoadResponse.from(loadEngine.compute(
                collectSessions(clubId, athleteId, today.minusDays(27), today),
                today, firstLoadedSessionDate(athleteId)));
    }

    /**
     * Date de la première séance chargée de l'athlète (course ou force), toutes périodes
     * confondues. Le moteur en a besoin pour ne pas prendre un athlète neuf pour un athlète
     * installé — la fenêtre de calcul, elle, ne voit que 28 jours.
     */
    private LocalDate firstLoadedSessionDate(UUID athleteId) {
        LocalDate fromWorkouts = workoutRepository.findFirstLoadedSessionDate(athleteId).orElse(null);
        LocalDate fromStrength = strengthLoadRepository
                .findFirstByAthleteIdOrderBySessionDateAsc(athleteId)
                .map(t -> t.getSessionDate()).orElse(null);
        if (fromWorkouts == null) {
            return fromStrength;
        }
        if (fromStrength == null) {
            return fromWorkouts;
        }
        return fromWorkouts.isBefore(fromStrength) ? fromWorkouts : fromStrength;
    }

    /**
     * Série temporelle de charge : ATL, CTL et ACWR jour par jour sur les {@code weeks} dernières
     * semaines. Chaque point réutilise exactement le moteur de charge — la courbe et la valeur
     * instantanée ne peuvent pas diverger.
     */
    public LoadSeriesResponse series(UUID clubId, UUID athleteId, int weeks) {
        requireAthlete(clubId, athleteId);
        int window = Math.max(4, Math.min(weeks, 26));
        LocalDate today = clock.today();
        LocalDate from = today.minusWeeks(window);

        // On remonte 27 jours avant le début de la fenêtre : le premier point a besoin de son
        // propre historique 28 jours, sinon la courbe démarre artificiellement bas.
        List<SessionLoad> sessions = collectSessions(clubId, athleteId, from.minusDays(27), today);
        LocalDate firstSession = firstLoadedSessionDate(athleteId);

        List<LoadSeriesResponse.Point> points = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            var m = loadEngine.compute(sessions, d, firstSession);
            points.add(new LoadSeriesResponse.Point(
                    d, m.acuteLoad7d(), m.chronicLoad28dWeekly(), m.ratio()));
        }
        return new LoadSeriesResponse(from, today, SAFE_RATIO_MIN, SAFE_RATIO_MAX, points);
    }

    /** Bande de sécurité ACWR (cf. DARI Lab) : en dessous on se désentraîne, au-dessus on risque. */
    private static final double SAFE_RATIO_MIN = 0.8;
    private static final double SAFE_RATIO_MAX = 1.3;

    private void requireAthlete(UUID clubId, UUID athleteId) {
        if (athleteRepository.findByIdAndClubMembership(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
    }

    /**
     * Charges de séance sur une plage, <strong>course et force confondues</strong> (invariant
     * DARI Lab : un seul score sRPE, jamais deux compteurs séparés).
     */
    private List<SessionLoad> collectSessions(UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        List<SessionLoad> sessions = new ArrayList<>();
        for (Workout w : workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, from, to)) {
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

        // Fusion de la charge de force (charge métabolique, UA) dans le score sRPE global, pour
        // que l'ACWR/monotonie reflètent course ET renforcement (cf. DARI Lab — charge unifiée).
        for (var t : strengthLoadRepository
                .findByAthleteIdAndSessionDateBetweenOrderBySessionDateAsc(athleteId, from, to)) {
            if (t.getMetabolicLoad() == null) {
                continue;
            }
            double load = t.getMetabolicLoad().doubleValue();
            if (load > 0) {
                sessions.add(new SessionLoad(t.getSessionDate(), load, 6)); // force ≈ domaine 2
            }
        }
        return sessions;
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
