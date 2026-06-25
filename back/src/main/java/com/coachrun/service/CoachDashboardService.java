package com.coachrun.service;

import com.coachrun.dto.response.AthleteFormResponse;
import com.coachrun.dto.response.CoachAlertResponse;
import com.coachrun.dto.response.CoachDashboardResponse;
import com.coachrun.dto.response.CoachFormDashboardResponse;
import com.coachrun.dto.response.RaceObjectiveResponse;
import com.coachrun.engine.FormStatusEngine;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.RaceObjectiveStatus;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Agrégation des indicateurs du tableau de bord coach. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoachDashboardService {

    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final RaceObjectiveRepository raceRepository;
    private final FormStatusEngine formStatusEngine;
    private final CoachAthleteRelationRepository relationRepository;
    private final AthleteLoadService loadService;

    public CoachDashboardResponse compute(UUID clubId) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate nextMonday = monday.plusWeeks(1);

        long activeAthletes = athleteRepository.countByClubIdAndStatus(clubId, AthleteStatus.ACTIVE);
        long pending = athleteRepository.countByClubIdAndInviteTokenIsNotNull(clubId);
        // « À valider » : séances passées encore au statut PLANNED.
        long toReview = workoutRepository.countByClubIdAndStatusAndScheduledDateLessThan(
                clubId, WorkoutStatus.PLANNED, today);
        long completedThisWeek = workoutRepository.countByClubIdAndStatusAndScheduledDateBetween(
                clubId, WorkoutStatus.COMPLETED, monday, nextMonday);
        var races = raceRepository
                .findTop5ByClubIdAndStatusAndRaceDateGreaterThanEqualOrderByRaceDateAsc(
                        clubId, RaceObjectiveStatus.UPCOMING, today)
                .stream().map(RaceObjectiveResponse::from).toList();

        return new CoachDashboardResponse(activeAthletes, pending, toReview, completedThisWeek, races);
    }

    /**
     * Tableau de bord « état de forme » : athlètes actifs répartis Route/Trail, chacun avec sa
     * pastille de forme (fatigue + douleur du dernier retour).
     */
    public CoachFormDashboardResponse formDashboard(UUID clubId, String scope, UUID coachId) {
        List<AthleteFormResponse> route = new ArrayList<>();
        List<AthleteFormResponse> trail = new ArrayList<>();

        for (Athlete a : athletesInScope(clubId, scope, coachId)) {
            if (a.getStatus() != AthleteStatus.ACTIVE) {
                continue;
            }
            Workout last = workoutRepository
                    .findFirstByAthleteIdAndFatigueIsNotNullOrderByScheduledDateDescCreatedAtDesc(a.getId())
                    .orElse(null);
            Integer fatigue = last == null ? null : last.getFatigue();
            Integer pain = last == null ? null : last.getPain();
            AthleteFormResponse row = AthleteFormResponse.of(
                    a, formStatusEngine.classify(fatigue, pain),
                    fatigue, pain, last == null ? null : last.getScheduledDate());

            if (a.getDiscipline() == Discipline.TRAIL) {
                trail.add(row);
            } else {
                route.add(row);
            }
        }

        return new CoachFormDashboardResponse(
                route.size() + trail.size(), route.size(), trail.size(), route, trail);
    }

    /** Athlètes d'un périmètre : all = tout le club ; mine/private/club = via les relations du coach. */
    private List<Athlete> athletesInScope(UUID clubId, String scope, UUID coachId) {
        if (scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope)) {
            return athleteRepository.findByClubIdOrderByLastNameAsc(clubId);
        }
        return relationRepository.findByCoachIdAndActiveTrue(coachId).stream()
                .filter(rel -> switch (scope.toLowerCase()) {
                    case "private" -> rel.getClub() == null;
                    case "club" -> rel.getClub() != null;
                    default -> true;            // "mine" = privés + club
                })
                .map(CoachAthleteRelation::getAthlete)
                .filter(a -> a.getClub() != null && clubId.equals(a.getClub().getId()))
                .distinct()
                .sorted(java.util.Comparator.comparing(Athlete::getLastName,
                        java.util.Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    /**
     * File d'alertes actionnables : pour chaque athlète actif du périmètre, détecte douleur,
     * charge à risque (ACWR/monotonie), séances manquées et silence. Triées par gravité (rouge
     * d'abord). Transforme le tableau de bord « descriptif » en outil de pilotage par exception.
     */
    public List<CoachAlertResponse> alerts(UUID clubId, String scope, UUID coachId) {
        LocalDate today = LocalDate.now();
        List<CoachAlertResponse> alerts = new ArrayList<>();

        for (Athlete a : athletesInScope(clubId, scope, coachId)) {
            if (a.getStatus() != AthleteStatus.ACTIVE) {
                continue;
            }
            String name = (a.getFirstName() + " " + a.getLastName()).trim();
            String discipline = a.getDiscipline() == Discipline.TRAIL ? "TRAIL" : "ROUTE";

            // --- Douleur (dernier retour) ---
            Workout lastFeedback = workoutRepository
                    .findFirstByAthleteIdAndFatigueIsNotNullOrderByScheduledDateDescCreatedAtDesc(a.getId())
                    .orElse(null);
            Integer pain = lastFeedback == null ? null : lastFeedback.getPain();
            if (pain != null && pain >= 5) {
                alerts.add(alert(a, name, discipline, "RED", "PAIN",
                        "Douleur élevée", "Douleur " + pain + "/10 au dernier retour."));
            } else if (pain != null && pain >= 3) {
                alerts.add(alert(a, name, discipline, "ORANGE", "PAIN",
                        "Douleur signalée", "Douleur " + pain + "/10 au dernier retour."));
            }

            // --- Charge (ACWR / monotonie) ---
            try {
                var load = loadService.load(clubId, a.getId());
                Double ratio = load.ratio();
                if (ratio != null && ratio > 1.5) {
                    alerts.add(alert(a, name, discipline, "RED", "ACWR_HIGH",
                            "Charge en forte hausse", "ACWR " + ratio + " (> 1,5 : risque de blessure)."));
                } else if (ratio != null && ratio >= 1.3) {
                    alerts.add(alert(a, name, discipline, "ORANGE", "ACWR_HIGH",
                            "Charge en hausse", "ACWR " + ratio + " (zone de vigilance)."));
                } else if (ratio != null && ratio < 0.8 && load.sessions28d() > 0) {
                    alerts.add(alert(a, name, discipline, "ORANGE", "ACWR_LOW",
                            "Charge en baisse", "ACWR " + ratio + " (désentraînement possible)."));
                }
                if (load.monotony() != null && load.monotony() >= 2.0) {
                    alerts.add(alert(a, name, discipline, "ORANGE", "MONOTONY",
                            "Monotonie élevée", "Monotonie " + load.monotony() + " (manque de variété)."));
                }
            } catch (RuntimeException ignored) {
                // un calcul de charge en échec ne doit pas masquer les autres alertes
            }

            // --- Séances manquées (14 derniers jours) ---
            List<Workout> recent = workoutRepository
                    .findByClubIdAndAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                            clubId, a.getId(), today.minusDays(14), today);
            long missed = recent.stream()
                    .filter(w -> w.getScheduledDate().isBefore(today)
                            && w.getStatus() == WorkoutStatus.PLANNED)
                    .count();
            if (missed >= 3) {
                alerts.add(alert(a, name, discipline, "RED", "MISSED",
                        missed + " séances manquées", "Sur les 14 derniers jours."));
            } else if (missed == 2) {
                alerts.add(alert(a, name, discipline, "ORANGE", "MISSED",
                        "2 séances manquées", "Sur les 14 derniers jours."));
            }

            // --- Silence (aucun retour depuis longtemps alors qu'un programme tourne) ---
            boolean hasRecentProgram = !recent.isEmpty();
            LocalDate lastFb = lastFeedback == null ? null : lastFeedback.getScheduledDate();
            long silence = lastFb == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(lastFb, today);
            if (hasRecentProgram && silence > 10) {
                String detail = lastFb == null ? "Aucun retour de séance enregistré."
                        : "Dernier retour il y a " + silence + " jours.";
                alerts.add(alert(a, name, discipline, "ORANGE", "SILENCE", "Athlète silencieux", detail));
            }
        }

        // Tri : rouge avant orange, puis par nom pour la stabilité.
        alerts.sort(java.util.Comparator
                .comparingInt((CoachAlertResponse al) -> "RED".equals(al.severity()) ? 0 : 1)
                .thenComparing(CoachAlertResponse::athleteName));
        return alerts;
    }

    private CoachAlertResponse alert(Athlete a, String name, String discipline,
                                     String severity, String type, String title, String detail) {
        return new CoachAlertResponse(a.getId(), name, discipline, severity, type, title, detail);
    }
}
