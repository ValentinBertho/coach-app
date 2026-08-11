package com.coachrun.service;

import com.coachrun.dto.response.AnalyticsResponse;
import com.coachrun.entity.Activity;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
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
    private final AthleteRepository athleteRepository;
    private final EndurancePaceService endurancePaceService;
    private final ClockService clock;

    /**
     * Distance prévue d'une séance pour les totaux : la cible si elle décrit bien une séance,
     * sinon une estimation depuis la durée et l'allure d'endurance de l'athlète.
     *
     * <p><b>Pourquoi ne pas se contenter de la cible.</b> Une séance dont la prescription est
     * écrite en durée, sans allure calculable, ne totalise que ses éducatifs — quelques centaines
     * de mètres — voire rien du tout. Elle comptait donc pour zéro kilomètre dans le volume
     * hebdomadaire prévu et dans l'adhérence : le coach lisait « 12 km prévus, 47 réalisés » sur
     * une semaine parfaitement suivie, et l'athlète un taux d'adhérence absurde. Zéro n'est pas
     * une valeur prudente ici, c'est une valeur fausse.</p>
     */
    private Integer plannedDistanceM(Workout w, Integer referencePace) {
        return com.coachrun.util.PlannedVolume.distanceOrEstimate(
                w.getTargetDistanceM(), w.getTargetDurationS(), referencePace);
    }

    /** Analytics — variante athlète-scopée (portail /me) : résout le club de l'athlète. */
    public AnalyticsResponse computeForAthlete(UUID athleteId, int weeks) {
        com.coachrun.entity.Athlete a = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return compute(a.getClub().getId(), athleteId, weeks);
    }

    /**
     * Récapitulatif de la semaine en cours (lundi → dimanche) : volume prévu/réalisé et séances
     * faites sur séances prévues. C'est le seul chiffre qu'un athlète regarde vraiment en semaine.
     */
    public com.coachrun.dto.response.WeekSummaryResponse weekSummary(UUID athleteId) {
        com.coachrun.entity.Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        UUID clubId = athlete.getClub().getId();
        LocalDate monday = clock.today().with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        Integer referencePace = endurancePaceService.referencePace(athleteId);
        double plannedKm = 0;
        int plannedSessions = 0;
        int completedSessions = 0;
        for (Workout w : workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, monday, sunday)) {
            // Une séance de repos n'est pas une séance à faire : la compter fausserait le « 3/5 ».
            if (w.getType() == com.coachrun.entity.enums.WorkoutType.REST) {
                continue;
            }
            plannedSessions++;
            Integer distanceM = plannedDistanceM(w, referencePace);
            if (distanceM != null) {
                plannedKm += distanceM / 1000.0;
            }
            if (w.getStatus() == com.coachrun.entity.enums.WorkoutStatus.COMPLETED
                    || w.getStatus() == com.coachrun.entity.enums.WorkoutStatus.PARTIAL) {
                completedSessions++;
            }
        }

        double realizedKm = 0;
        for (Activity a : activityRepository
                .findByClubIdAndAthleteIdOrderByActivityDateDesc(clubId, athleteId)) {
            if (a.getDistanceM() != null
                    && !a.getActivityDate().isBefore(monday) && !a.getActivityDate().isAfter(sunday)) {
                realizedKm += a.getDistanceM() / 1000.0;
            }
        }

        return new com.coachrun.dto.response.WeekSummaryResponse(
                monday,
                Math.round(plannedKm * 10) / 10.0,
                Math.round(realizedKm * 10) / 10.0,
                plannedSessions,
                completedSessions);
    }

    public AnalyticsResponse compute(UUID clubId, UUID athleteId, int weeks) {
        int n = Math.max(1, Math.min(weeks, 26));
        LocalDate monday = clock.today().with(DayOfWeek.MONDAY).minusWeeks(n - 1L);
        LocalDate end = monday.plusWeeks(n);

        List<Workout> workouts = workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, monday, end);
        List<Activity> activities = activityRepository
                .findByClubIdAndAthleteIdOrderByActivityDateDesc(clubId, athleteId);

        // Volume hebdo prévu/réalisé
        double[] planned = new double[n];
        double[] realized = new double[n];
        Integer referencePace = endurancePaceService.referencePace(athleteId);
        for (Workout w : workouts) {
            int idx = weekIndex(monday, w.getScheduledDate(), n);
            Integer distanceM = idx >= 0 ? plannedDistanceM(w, referencePace) : null;
            if (distanceM != null) {
                planned[idx] += distanceM / 1000.0;
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
