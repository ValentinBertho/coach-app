package com.coachrun.service;

import com.coachrun.dto.response.AnalyticsResponse;
import com.coachrun.entity.Activity;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agrégation des données de charge côté serveur (le front reçoit du prêt-à-tracer,
 * pas des milliers de samples — cf. Techno.md §4).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    private final WorkoutRepository workoutRepository;
    private final ActivityRepository activityRepository;

    public AnalyticsResponse compute(UUID clubId, UUID athleteId, int weeks) {
        int n = Math.max(1, Math.min(weeks, 26));
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(n - 1L);
        LocalDate end = monday.plusWeeks(n);

        List<Workout> workouts = workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, monday, end);
        List<Activity> activities = activityRepository
                .findByClubIdAndAthleteIdOrderByActivityDateDesc(clubId, athleteId);

        // Volume hebdo prévu/réalisé
        double[] planned = new double[n];
        double[] realized = new double[n];
        for (Workout w : workouts) {
            int idx = weekIndex(monday, w.getScheduledDate(), n);
            if (idx >= 0 && w.getTargetDistanceM() != null) {
                planned[idx] += w.getTargetDistanceM() / 1000.0;
            }
        }
        for (Activity a : activities) {
            int idx = weekIndex(monday, a.getActivityDate(), n);
            if (idx >= 0 && a.getDistanceM() != null) {
                realized[idx] += a.getDistanceM() / 1000.0;
            }
        }
        List<AnalyticsResponse.WeekPoint> weekly = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            weekly.add(new AnalyticsResponse.WeekPoint(
                    monday.plusWeeks(i),
                    Math.round(planned[i] * 10) / 10.0,
                    Math.round(realized[i] * 10) / 10.0));
        }

        // Répartition par zone (nb d'étapes, pondéré par répétitions)
        Map<String, Integer> zones = new LinkedHashMap<>();
        for (String z : List.of("Z1", "Z2", "Z3", "Z4", "Z5")) {
            zones.put(z, 0);
        }
        for (Workout w : workouts) {
            for (WorkoutStep s : w.getSteps()) {
                if (s.getZone() != null) {
                    zones.merge(s.getZone().name(), Math.max(1, s.getRepetitions()), Integer::sum);
                }
            }
        }

        // Adhérence (statuts)
        Map<String, Integer> statuses = new LinkedHashMap<>();
        for (Workout w : workouts) {
            statuses.merge(w.getStatus().name(), 1, Integer::sum);
        }

        return new AnalyticsResponse(weekly, zones, statuses);
    }

    private int weekIndex(LocalDate monday, LocalDate date, int n) {
        long weeks = java.time.temporal.ChronoUnit.WEEKS.between(monday, date.with(DayOfWeek.MONDAY));
        return (weeks >= 0 && weeks < n) ? (int) weeks : -1;
    }
}
