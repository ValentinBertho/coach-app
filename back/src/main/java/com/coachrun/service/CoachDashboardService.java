package com.coachrun.service;

import com.coachrun.dto.response.CoachDashboardResponse;
import com.coachrun.dto.response.RaceObjectiveResponse;
import com.coachrun.entity.enums.AthleteStatus;
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
import java.util.UUID;

/** Agrégation des indicateurs du tableau de bord coach. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoachDashboardService {

    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final RaceObjectiveRepository raceRepository;

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
}
