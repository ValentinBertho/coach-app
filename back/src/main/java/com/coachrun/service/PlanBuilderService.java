package com.coachrun.service;

import com.coachrun.dto.request.PlanFromRaceRequest;
import com.coachrun.dto.response.PlanPreviewResponse;
import com.coachrun.engine.PlanBuilderEngine;
import com.coachrun.engine.PlanBuilderEngine.Plan;
import com.coachrun.engine.PlanBuilderEngine.PlanWeek;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.Workout;
import com.coachrun.entity.WorkoutStep;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteUnavailabilityRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le plan qui part de la course.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>{@code generateMesocycle} recopiait une semaine type en la multipliant par un facteur. Il
 * ignorait la date de course, les indisponibilités déclarées et la charge de l'athlète, et démarrait
 * toujours « la semaine prochaine ». Le geste fondateur du métier — <i>quatorze semaines jusqu'à
 * Paris, dont deux d'affûtage</i> — n'existait nulle part.</p>
 *
 * <h2>Deux gestes, jamais un seul</h2>
 *
 * <p>{@link #preview} ne touche à rien : il rend la trame, semaine par semaine, pour que le coach la
 * lise avant de décider. {@link #apply} matérialise, et seulement sur demande explicite. Poser
 * quatorze semaines de séances dans le calendrier de quelqu'un est un acte trop lourd pour être un
 * effet de bord.</p>
 *
 * <p>Une fois posé, le plan est un plan ordinaire : chaque séance s'édite, se déplace et se supprime
 * comme les autres. La machine pose la structure, le coach garde chaque séance.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanBuilderService {

    private final RaceObjectiveRepository raceRepository;
    private final WorkoutRepository workoutRepository;
    private final AthleteUnavailabilityRepository unavailabilityRepository;
    private final AthleteLoadService loadService;
    private final PlanBuilderEngine engine;
    private final ClockService clock;

    /** Aperçu de la trame, sans rien écrire. */
    public PlanPreviewResponse preview(UUID clubId, UUID raceId, PlanFromRaceRequest req) {
        RaceObjective race = require(clubId, raceId);
        Plan plan = buildPlan(clubId, race, req);
        LocalDate sourceWeek = sourceWeekStart(req);

        int sourceSessions = countWeek(clubId, race.getAthlete().getId(), sourceWeek);
        return PlanPreviewResponse.from(plan, race, sourceWeek, sourceSessions,
                sourceSessions == 0
                        ? "La semaine de référence est vide : construis-la d'abord, le plan la déclinera."
                        : null);
    }

    /**
     * Matérialise la trame : chaque semaine reçoit une copie de la semaine de référence, mise à
     * l'échelle par son facteur de volume.
     *
     * <p>Les semaines couvertes par une indisponibilité déclarée sont <b>sautées</b> : c'est la
     * différence entre un plan et une multiplication. Idem pour les semaines qui portent déjà des
     * séances, sauf demande explicite d'écrasement — un plan ne détruit pas le travail existant
     * sans le dire.</p>
     */
    @Transactional
    public PlanPreviewResponse apply(UUID clubId, UUID raceId, PlanFromRaceRequest req) {
        RaceObjective race = require(clubId, raceId);
        Athlete athlete = race.getAthlete();
        Plan plan = buildPlan(clubId, race, req);
        LocalDate sourceWeek = sourceWeekStart(req);

        List<Workout> source = workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athlete.getId(), sourceWeek, sourceWeek.plusDays(6));
        if (source.isEmpty()) {
            throw new ConflictException(
                    "La semaine de référence est vide : construis-la d'abord, le plan la déclinera.");
        }

        int created = 0;
        int skipped = 0;
        for (PlanWeek week : plan.weeks()) {
            if (week.skipped()) {
                skipped++;
                continue;
            }
            if (week.monday().equals(sourceWeek)) {
                continue; // ne pas réécrire la semaine qui sert de modèle
            }
            if (!Boolean.TRUE.equals(req.overwrite())
                    && countWeek(clubId, athlete.getId(), week.monday()) > 0) {
                skipped++;
                continue;
            }
            created += copyWeek(clubId, athlete, source, week);
        }

        // La course elle-même : une séance de type RACE, le jour dit. C'est elle qui rend le plan
        // lisible dans le calendrier — sans elle, l'affûtage descend vers rien de visible.
        created += ensureRaceSession(clubId, athlete, race);

        log.info("Plan depuis la course {} appliqué à l'athlète {} : {} séances, {} semaines ignorées",
                raceId, athlete.getId(), created, skipped);
        return PlanPreviewResponse.applied(plan, race, sourceWeek, created, skipped);
    }

    // ---------------------------------------------------------------------
    // Détail
    // ---------------------------------------------------------------------

    private Plan buildPlan(UUID clubId, RaceObjective race, PlanFromRaceRequest req) {
        LocalDate start = req.firstWeekStart() != null
                ? req.firstWeekStart()
                : clock.today().plusWeeks(1).with(DayOfWeek.MONDAY);

        List<LocalDate[]> unavailable = unavailabilityRepository
                .findByClubIdAndAthleteIdOrderByStartDateDesc(clubId, race.getAthlete().getId())
                .stream()
                .filter(u -> !u.getEndDate().isBefore(start))
                .map(u -> new LocalDate[]{u.getStartDate(), u.getEndDate()})
                .toList();

        return engine.build(start, race.getRaceDate(),
                req.peakFactorOrDefault(), req.taperWeeksOrDefault(), req.deloadEveryOrDefault(),
                unavailable);
    }

    /**
     * Semaine de référence : celle que le coach désigne, sinon la semaine courante.
     *
     * <p>C'est la semaine type de l'athlète — celle qui porte sa répartition habituelle (jours de
     * sortie, fractionné du mardi, longue du dimanche). Le plan la décline ; il ne l'invente pas.</p>
     */
    private LocalDate sourceWeekStart(PlanFromRaceRequest req) {
        LocalDate raw = req.sourceWeekStart() != null ? req.sourceWeekStart() : clock.today();
        return raw.with(DayOfWeek.MONDAY);
    }

    private int countWeek(UUID clubId, UUID athleteId, LocalDate monday) {
        return workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, monday, monday.plusDays(6))
                .size();
    }

    /**
     * Recopie la semaine de référence sur une semaine cible en mettant le volume à l'échelle.
     *
     * <p>Le snapshot et les cibles calculées ne suivent qu'à l'identique (facteur 1) : les
     * fourchettes ont été calculées pour un volume donné, les plaquer sur un volume différent
     * produirait une prescription qui ne correspond plus à ce qu'elle annonce. La séance mise à
     * l'échelle garde donc son type, son titre et ses étapes — le coach en règle le détail.</p>
     */
    private int copyWeek(UUID clubId, Athlete athlete, List<Workout> source, PlanWeek week) {
        LocalDate sourceMonday = source.get(0).getScheduledDate().with(DayOfWeek.MONDAY);
        int created = 0;
        for (Workout w : source) {
            long offset = ChronoUnit.DAYS.between(sourceMonday, w.getScheduledDate());
            Workout copy = new Workout();
            copy.setClub(w.getClub());
            copy.setAthlete(athlete);
            copy.setStatus(WorkoutStatus.PLANNED);
            copy.setScheduledDate(week.monday().plusDays(offset));
            copy.setType(w.getType());
            copy.setTitle(w.getTitle());
            copy.setNotes(w.getNotes());
            copy.setTargetDistanceM(scale(w.getTargetDistanceM(), week.volumeFactor()));
            copy.setTargetDurationS(scale(w.getTargetDurationS(), week.volumeFactor()));
            copy.setSourceTemplateId(w.getSourceTemplateId());
            if (week.volumeFactor() == 1.0) {
                copy.setSessionSnapshot(w.getSessionSnapshot());
                copy.setCalculatedPaces(w.getCalculatedPaces());
                copy.setPlannedLoadUa(w.getPlannedLoadUa());
            }
            for (WorkoutStep s : w.getSteps()) {
                WorkoutStep ns = new WorkoutStep();
                ns.setWorkout(copy);
                ns.setOrderIndex(s.getOrderIndex());
                ns.setStepType(s.getStepType());
                ns.setRepetitions(s.getRepetitions());
                ns.setZone(s.getZone());
                ns.setDistanceM(scale(s.getDistanceM(), week.volumeFactor()));
                ns.setDurationS(scale(s.getDurationS(), week.volumeFactor()));
                ns.setNotes(s.getNotes());
                copy.getSteps().add(ns);
            }
            workoutRepository.save(copy);
            created++;
        }
        return created;
    }

    /** Pose la course dans le calendrier si elle n'y est pas déjà. */
    private int ensureRaceSession(UUID clubId, Athlete athlete, RaceObjective race) {
        boolean exists = workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athlete.getId(), race.getRaceDate(), race.getRaceDate())
                .stream().anyMatch(w -> w.getType() == WorkoutType.RACE);
        if (exists) {
            return 0;
        }
        Workout w = new Workout();
        w.setClub(athlete.getClub());
        w.setAthlete(athlete);
        w.setStatus(WorkoutStatus.PLANNED);
        w.setScheduledDate(race.getRaceDate());
        w.setType(WorkoutType.RACE);
        w.setTitle(race.getName());
        w.setTargetDistanceM(race.getDistanceM());
        w.setTargetDurationS(race.getTargetTimeS());
        workoutRepository.save(w);
        return 1;
    }

    private Integer scale(Integer value, double factor) {
        if (value == null || factor == 1.0) {
            return value;
        }
        return (int) Math.round(value * factor);
    }

    private RaceObjective require(UUID clubId, UUID raceId) {
        return raceRepository.findByIdAndClubId(raceId, clubId)
                .orElseThrow(() -> new NotFoundException("Course introuvable."));
    }
}
