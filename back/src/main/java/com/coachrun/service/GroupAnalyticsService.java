package com.coachrun.service;

import com.coachrun.dto.response.AnalyticsResponse;
import com.coachrun.dto.response.GroupAnalyticsResponse;
import com.coachrun.dto.response.LoadResponse;
import com.coachrun.engine.FormStatusEngine;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.TrainingGroup;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.FormStatus;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.TrainingGroupRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Analytics agrégées d'un groupe d'entraînement : état de forme, charge (ACWR), volume et
 * adhérence — réutilise les calculs par athlète existants (charge, volume) puis les agrège.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupAnalyticsService {

    private final TrainingGroupRepository groupRepository;
    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final FormStatusEngine formStatusEngine;
    private final AthleteLoadService loadService;
    private final AnalyticsService analyticsService;

    public GroupAnalyticsResponse compute(UUID clubId, UUID groupId, int weeks) {
        TrainingGroup group = groupRepository.findByIdAndClubId(groupId, clubId)
                .orElseThrow(() -> new NotFoundException("Groupe introuvable."));
        int window = Math.max(1, Math.min(weeks, 26));

        List<Athlete> athletes = athleteRepository.findActiveByGroup(groupId, clubId, AthleteStatus.ACTIVE);
        List<GroupAnalyticsResponse.Row> rows = new ArrayList<>();
        int green = 0;
        int orange = 0;
        int red = 0;
        double acwrSum = 0;
        int acwrCount = 0;
        double totalPlanned = 0;
        double totalRealized = 0;

        for (Athlete a : athletes) {
            Workout last = workoutRepository
                    .findFirstByAthleteIdAndFatigueIsNotNullOrderByScheduledDateDescCreatedAtDesc(a.getId())
                    .orElse(null);
            Integer fatigue = last == null ? null : last.getFatigue();
            Integer pain = last == null ? null : last.getPain();
            FormStatus status = formStatusEngine.classify(fatigue, pain);
            switch (status) {
                case GREEN -> green++;
                case ORANGE -> orange++;
                case RED -> red++;
                default -> { }
            }

            LoadResponse load = loadService.loadForAthlete(a.getId());
            Double acwr = load.ratio();
            if (acwr != null) {
                acwrSum += acwr;
                acwrCount++;
            }

            AnalyticsResponse analytics = analyticsService.computeForAthlete(a.getId(), window);
            double planned = analytics.weeklyVolume().stream().mapToDouble(AnalyticsResponse.WeekPoint::plannedKm).sum();
            double realized = analytics.weeklyVolume().stream().mapToDouble(AnalyticsResponse.WeekPoint::realizedKm).sum();
            totalPlanned += planned;
            totalRealized += realized;

            Discipline discipline = a.getDiscipline() == null ? Discipline.ROUTE : a.getDiscipline();
            rows.add(new GroupAnalyticsResponse.Row(
                    a.getId(), a.getFirstName(), a.getLastName(), discipline, status, fatigue, pain,
                    acwr, round1(planned), round1(realized), compliance(planned, realized),
                    last == null ? null : last.getScheduledDate()));
        }

        Double avgAcwr = acwrCount == 0 ? null : round2(acwrSum / acwrCount);
        GroupAnalyticsResponse.Aggregate totals = new GroupAnalyticsResponse.Aggregate(
                avgAcwr, round1(totalPlanned), round1(totalRealized), compliance(totalPlanned, totalRealized));

        return new GroupAnalyticsResponse(group.getId(), group.getName(), rows.size(),
                new GroupAnalyticsResponse.FormDistribution(green, orange, red), totals, rows);
    }

    private static Integer compliance(double planned, double realized) {
        return planned <= 0 ? null : (int) Math.round(100.0 * realized / planned);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
