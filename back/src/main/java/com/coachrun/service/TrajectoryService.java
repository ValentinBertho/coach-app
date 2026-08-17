package com.coachrun.service;

import com.coachrun.dto.request.PerformanceRequest;
import com.coachrun.dto.response.TrajectoryResponse;
import com.coachrun.engine.TrajectoryEngine;
import com.coachrun.engine.VdotEngine;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthletePerformance;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.enums.RaceObjectiveStatus;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthletePerformanceRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * La trajectoire vers l'objectif — et le chrono de course qui referme la boucle.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>{@code RaceObjective} était un post-it : une date, une distance, un chrono visé, et aucun
 * calcul. Le produit connaissait pourtant le VDOT de l'athlète et savait inverser Daniels. Rien ne
 * reliait l'effort du jour au but de la saison — et le lendemain de la course, l'objectif passait
 * « passé » sans que personne n'ait jamais enregistré ce qui s'était réellement passé.</p>
 *
 * <h2>Le chrono de course</h2>
 *
 * <p>{@link #recordResult} n'écrit pas un champ décoratif : il enregistre une
 * <b>performance de référence</b>, ce qui déclenche la cascade complète — VDOT, allures
 * d'équivalence, resynchronisation des zones, cibles des séances à venir. Une course est la
 * meilleure mesure de niveau qu'un athlète produise dans l'année ; la traiter autrement serait
 * gâcher la seule donnée que personne n'a besoin de motiver.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrajectoryService {

    private final RaceObjectiveRepository raceRepository;
    private final AthletePerformanceRepository performanceRepository;
    private final AthleteRepository athleteRepository;
    private final AthleteLoadService loadService;
    private final AthletePhysioService physioService;
    private final TrajectoryEngine engine;
    private final VdotEngine vdotEngine;
    private final ClockService clock;

    /** Trajectoire vers la prochaine course A ou B, ou {@code null} s'il n'y en a pas. */
    public TrajectoryResponse forAthlete(UUID athleteId) {
        LocalDate today = clock.today();
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));

        RaceObjective race = nextRace(athleteId, today);
        if (race == null) {
            return null;
        }

        Double currentVdot = athlete.getVdot() == null ? null : athlete.getVdot().doubleValue();
        int daysLeft = (int) ChronoUnit.DAYS.between(today, race.getRaceDate());
        int weeksLeft = Math.max(0, daysLeft / 7);
        int distanceM = race.getDistanceM() == null ? 0 : race.getDistanceM();

        var trajectory = engine.compute(currentVdot, distanceM, race.getTargetTimeS(),
                weeksLeft, weeklyProgress(athleteId));

        return TrajectoryResponse.from(race.getId(), race.getName(), race.getRaceDate(),
                daysLeft, race.getDistanceM(), race.getTargetTimeS(), trajectory,
                loadNote(athlete, trajectory));
    }

    /** Même trajectoire, vue par le coach (scopée club). */
    public TrajectoryResponse forCoach(UUID clubId, UUID athleteId) {
        if (athleteRepository.findByIdAndClubMembership(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        return forAthlete(athleteId);
    }

    /**
     * Enregistre le chrono réalisé sur une course : la course passe {@code COMPLETED} et le temps
     * devient une performance de référence.
     *
     * <p>La distance n'est reprise que si elle correspond à une distance de référence — un trail de
     * 27 km n'a pas de VDOT, et l'inscrire comme un semi fausserait le profil. Le résultat est
     * quand même mémorisé sur la course.</p>
     */
    @Transactional
    public TrajectoryResponse recordResult(UUID clubId, UUID raceId, int timeSeconds) {
        RaceObjective race = raceRepository.findByIdAndClubId(raceId, clubId)
                .orElseThrow(() -> new NotFoundException("Course introuvable."));
        if (timeSeconds <= 0) {
            throw new ConflictException("Le chrono doit être positif.");
        }
        race.setResultTimeS(timeSeconds);
        race.setStatus(RaceObjectiveStatus.DONE);

        var distance = referenceDistance(race.getDistanceM());
        if (distance != null) {
            physioService.addPerformance(clubId, race.getAthlete().getId(),
                    new PerformanceRequest(distance, timeSeconds, race.getRaceDate()));
            log.info("Chrono de course enregistré comme performance ({} en {} s) — athlète {}",
                    distance.code(), timeSeconds, race.getAthlete().getId());
        }
        return forAthlete(race.getAthlete().getId());
    }

    // ---------------------------------------------------------------------
    // Détail
    // ---------------------------------------------------------------------

    /**
     * La prochaine course à venir. Priorité A d'abord — c'est celle sur laquelle l'athlète est
     * jugé ; une course B intercalée n'est pas l'objectif de la saison.
     */
    private RaceObjective nextRace(UUID athleteId, LocalDate today) {
        List<RaceObjective> upcoming = raceRepository.findByAthleteIdOrderByRaceDateAsc(athleteId).stream()
                .filter(r -> r.getStatus() == RaceObjectiveStatus.UPCOMING)
                .filter(r -> !r.getRaceDate().isBefore(today))
                .filter(r -> r.getDistanceM() != null && r.getDistanceM() > 0)
                .toList();
        if (upcoming.isEmpty()) {
            return null;
        }
        return upcoming.stream()
                .filter(r -> r.getPriority() == com.coachrun.entity.enums.RacePriority.A)
                .findFirst()
                .orElse(upcoming.get(0));
    }

    /**
     * Progression observée, en points de VDOT par semaine, sur l'historique de l'athlète.
     *
     * <p>C'est <b>sa</b> pente, jamais une table générique : un débutant gagne trois points en un
     * mois là où un coureur installé en gagne un en six. Prédire avec la moyenne des autres serait
     * la meilleure façon de décourager le premier et de bercer le second.</p>
     */
    private Double weeklyProgress(UUID athleteId) {
        List<AthletePerformance> perfs = performanceRepository
                .findByAthleteIdOrderByDateSetDescCreatedAtDesc(athleteId).stream()
                .filter(p -> p.getDistance().hasFixedDistance() && p.getTimeSeconds() > 0)
                .sorted(Comparator.comparing(AthletePerformance::getDateSet))
                .toList();
        if (perfs.size() < 2) {
            return null;
        }
        AthletePerformance oldest = perfs.get(0);
        AthletePerformance latest = perfs.get(perfs.size() - 1);
        double oldestVdot = vdotEngine.vdot(oldest.getDistance().meters(), oldest.getTimeSeconds());
        double latestVdot = vdotEngine.vdot(latest.getDistance().meters(), latest.getTimeSeconds());
        long days = ChronoUnit.DAYS.between(oldest.getDateSet(), latest.getDateSet());
        return engine.weeklyProgress(oldestVdot, latestVdot, days);
    }

    /**
     * Ce que la charge dit de la trajectoire — et rien quand elle ne dit rien.
     *
     * <p>C'est la ligne qui distingue « il manque 9 minutes » d'une information exploitable :
     * l'écart a une cause, et le plus souvent elle est là.</p>
     */
    private String loadNote(Athlete athlete, TrajectoryEngine.Trajectory t) {
        if (t.outlook() == TrajectoryEngine.Outlook.UNKNOWN
                || t.outlook() == TrajectoryEngine.Outlook.AHEAD) {
            return null;
        }
        try {
            var load = loadService.load(athlete.getClub().getId(), athlete.getId());
            if (!load.ratioReady() || load.ratio() == null) {
                return null;
            }
            if (load.ratio() < 0.8) {
                return "Ta charge est en baisse (" + fr(load.ratio())
                        + ") : à ce rythme, le niveau ne montera pas d'ici la course.";
            }
            if (load.ratio() > 1.5) {
                return "Ta charge monte vite (" + fr(load.ratio())
                        + ") : gagner du niveau ne sert à rien si la blessure arrive avant.";
            }
            if (load.monotony() != null && load.monotony() >= 2.0) {
                return "Toutes tes semaines se ressemblent (monotonie " + fr(load.monotony())
                        + ") : c'est le premier levier à actionner.";
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Distance de référence exacte, à 2 % près — sinon pas de VDOT. */
    private com.coachrun.entity.enums.RunDistance referenceDistance(Integer distanceM) {
        if (distanceM == null || distanceM <= 0) {
            return null;
        }
        for (var d : com.coachrun.entity.enums.RunDistance.values()) {
            if (d.hasFixedDistance() && Math.abs(d.meters() - distanceM) / (double) d.meters() <= 0.02) {
                return d;
            }
        }
        return null;
    }

    private String fr(double v) {
        return String.valueOf(v).replace('.', ',');
    }
}
