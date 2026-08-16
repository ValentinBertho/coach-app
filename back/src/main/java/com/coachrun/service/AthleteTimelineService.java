package com.coachrun.service;

import com.coachrun.dto.InjuryReport;
import com.coachrun.dto.response.TimelineResponse;
import com.coachrun.dto.response.TimelineResponse.Event;
import com.coachrun.entity.AthletePerformance;
import com.coachrun.entity.AthleteUnavailability;
import com.coachrun.entity.LactateTest;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.Workout;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthletePerformanceRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteUnavailabilityRepository;
import com.coachrun.repository.LactateTestRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.repository.StrengthTestRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.util.InjuryCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Le fil de l'athlète — six mois de suivi rassemblés en une frise.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>Tout existait, réparti sur six écrans : les tests lactate d'un côté, les records de l'autre,
 * les courses ailleurs, les blessures déclarées au débrief enfouies dans les séances
 * ({@code injuriesJson} porte pourtant zone, type et côté, en structuré), les coupures dans la
 * fiche. Aucun endroit ne racontait l'histoire, et le coach n'avait rien à remettre à l'athlète en
 * fin de cycle.</p>
 *
 * <h2>Ce que la frise vaut</h2>
 *
 * <p>Deux choses différentes : la mémoire, pour le coach qui reprend un athlète après l'été ; et le
 * livrable, pour l'athlète qui paie et veut voir ce qu'il a fait. C'est aussi, accessoirement, ce
 * qui ne se reconstitue pas par un export CSV — donc ce qui rend un départ coûteux.</p>
 *
 * <p>Assemblage pur : aucune donnée n'est créée ici, aucune n'est stockée.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteTimelineService {

    private final AthleteRepository athleteRepository;
    private final AthletePerformanceRepository performanceRepository;
    private final LactateTestRepository lactateTestRepository;
    private final StrengthTestRepository strengthTestRepository;
    private final RaceObjectiveRepository raceRepository;
    private final AthleteUnavailabilityRepository unavailabilityRepository;
    private final WorkoutRepository workoutRepository;
    private final ClockService clock;

    /** Fenêtre par défaut : six mois, la durée d'un cycle de préparation vu de loin. */
    public static final int DEFAULT_MONTHS = 6;

    public TimelineResponse forCoach(UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        if (athleteRepository.findByIdAndClubMembership(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        return build(clubId, athleteId, from, to);
    }

    /** Mon fil — portail athlète. */
    public TimelineResponse forAthlete(UUID athleteId, LocalDate from, LocalDate to) {
        var athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        return build(athlete.getClub().getId(), athleteId, from, to);
    }

    private TimelineResponse build(UUID clubId, UUID athleteId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? clock.today() : to;
        LocalDate start = from == null ? end.minusMonths(DEFAULT_MONTHS) : from;
        List<Event> events = new ArrayList<>();

        addPerformances(athleteId, start, end, events);
        addLactateTests(clubId, athleteId, start, end, events);
        addStrengthTests(athleteId, start, end, events);
        addRaces(clubId, athleteId, start, end, events);
        addUnavailabilities(clubId, athleteId, start, end, events);
        addInjuries(clubId, athleteId, start, end, events);

        events.sort(Comparator.comparing(Event::date).reversed());
        return new TimelineResponse(start, end, events);
    }

    // ---------------------------------------------------------------------

    private void addPerformances(UUID athleteId, LocalDate from, LocalDate to, List<Event> out) {
        for (AthletePerformance p : performanceRepository
                .findByAthleteIdOrderByDateSetDescCreatedAtDesc(athleteId)) {
            if (outside(p.getDateSet(), from, to)) {
                continue;
            }
            out.add(new Event(p.getDateSet(), "RECORD", "good",
                    p.getDistance().code() + " en " + formatTime(p.getTimeSeconds()),
                    "Performance de référence — le VDOT et les allures ont suivi.",
                    p.getId(), null));
        }
    }

    private void addLactateTests(UUID clubId, UUID athleteId, LocalDate from, LocalDate to, List<Event> out) {
        for (LactateTest t : lactateTestRepository
                .findByClubIdAndAthleteIdOrderByTestDateDesc(clubId, athleteId)) {
            if (outside(t.getTestDate(), from, to)) {
                continue;
            }
            StringBuilder detail = new StringBuilder();
            if (t.getLt1Ms() != null) {
                detail.append("LT1 ").append(pace(t.getLt1Ms().doubleValue()));
            }
            if (t.getLt2Ms() != null) {
                if (detail.length() > 0) {
                    detail.append(" · ");
                }
                detail.append("LT2 ").append(pace(t.getLt2Ms().doubleValue()));
            }
            out.add(new Event(t.getTestDate(), "TEST", "neutral",
                    "Test " + (t.getTestType() == null ? "physiologique" : t.getTestType().name().toLowerCase()),
                    detail.length() == 0 ? null : detail.toString(),
                    t.getId(), "/app/athletes/" + athleteId + "/tests"));
        }
    }

    private void addStrengthTests(UUID athleteId, LocalDate from, LocalDate to, List<Event> out) {
        strengthTestRepository.findByAthleteIdOrderByTestDateDesc(athleteId).stream()
                .filter(t -> !outside(t.getTestDate(), from, to))
                .forEach(t -> out.add(new Event(t.getTestDate(), "STRENGTH_TEST", "neutral",
                        "Test de force",
                        t.getComputedE1rmKg() == null ? null
                                : "1RM estimé " + t.getComputedE1rmKg().stripTrailingZeros().toPlainString() + " kg",
                        t.getId(), null)));
    }

    private void addRaces(UUID clubId, UUID athleteId, LocalDate from, LocalDate to, List<Event> out) {
        for (RaceObjective r : raceRepository.findByClubIdAndAthleteIdOrderByRaceDateAsc(clubId, athleteId)) {
            if (outside(r.getRaceDate(), from, to)) {
                continue;
            }
            String detail = r.getResultTimeS() != null
                    ? "Réalisé en " + formatTime(r.getResultTimeS())
                    : r.getTargetTimeS() != null ? "Objectif " + formatTime(r.getTargetTimeS()) : null;
            out.add(new Event(r.getRaceDate(), "RACE", "good", r.getName(), detail,
                    r.getId(), "/app/athletes/" + athleteId + "/races"));
        }
    }

    private void addUnavailabilities(UUID clubId, UUID athleteId, LocalDate from, LocalDate to, List<Event> out) {
        for (AthleteUnavailability u : unavailabilityRepository
                .findByClubIdAndAthleteIdOrderByStartDateDesc(clubId, athleteId)) {
            if (u.getEndDate().isBefore(from) || u.getStartDate().isAfter(to)) {
                continue;
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(u.getStartDate(), u.getEndDate()) + 1;
            out.add(new Event(u.getStartDate(), "UNAVAILABILITY",
                    u.getReason() == com.coachrun.entity.enums.UnavailabilityReason.INJURY ? "alert" : "watch",
                    reasonLabel(u.getReason()) + " — " + days + " jour" + (days > 1 ? "s" : ""),
                    u.getNotes(), u.getId(), null));
        }
    }

    /**
     * Les blessures <b>nommées</b> au débrief d'une séance.
     *
     * <p>C'est la donnée la plus précieuse de la frise et la plus enfouie du produit : elle est
     * structurée (zone, nature, côté) depuis toujours, et n'apparaissait nulle part ailleurs que
     * dans la séance où elle a été saisie. Une gêne au mollet déclarée trois fois en six semaines
     * ne se voyait donc pas.</p>
     */
    private void addInjuries(UUID clubId, UUID athleteId, LocalDate from, LocalDate to, List<Event> out) {
        for (Workout w : workoutRepository
                .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        clubId, athleteId, from, to)) {
            List<InjuryReport> injuries = InjuryCodec.read(w.getInjuriesJson());
            for (InjuryReport i : injuries) {
                out.add(new Event(w.getScheduledDate(), "INJURY", "alert",
                        injuryLabel(i),
                        (w.getPain() != null ? "Douleur " + w.getPain() + "/10 · " : "") + w.getTitle(),
                        w.getId(), "/app/athletes/" + athleteId + "/workouts/" + w.getId()));
            }
        }
    }

    // ---------------------------------------------------------------------

    private boolean outside(LocalDate d, LocalDate from, LocalDate to) {
        return d == null || d.isBefore(from) || d.isAfter(to);
    }

    private String injuryLabel(InjuryReport i) {
        String area = i.area() == null ? "" : i.area().name().toLowerCase().replace('_', ' ');
        String side = i.side() == null ? "" : " (" + i.side().name().toLowerCase() + ")";
        return "Blessure — " + area + side;
    }

    private String reasonLabel(com.coachrun.entity.enums.UnavailabilityReason r) {
        return switch (r) {
            case INJURY -> "Blessure";
            case ILLNESS -> "Maladie";
            case VACATION -> "Vacances";
            case PERSONAL -> "Indisponible";
            case OTHER -> "Coupure";
        };
    }

    /** m/s → « 4:15 /km ». */
    private String pace(double ms) {
        if (ms <= 0) {
            return "—";
        }
        int secPerKm = (int) Math.round(1000.0 / ms);
        return String.format("%d:%02d /km", secPerKm / 60, secPerKm % 60);
    }

    private String formatTime(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }
}
