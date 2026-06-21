package com.coachrun.service;

import com.coachrun.dto.response.AdminStatsResponse;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compteurs du tableau de bord d'administration. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final ActivityRepository activityRepository;

    public AdminStatsResponse stats() {
        return new AdminStatsResponse(
                clubRepository.count(),
                userRepository.countByRole(UserRole.HEAD_COACH),
                userRepository.countByRole(UserRole.COACH),
                athleteRepository.count(),
                athleteRepository.countByInviteTokenIsNotNull(),
                workoutRepository.count(),
                activityRepository.count());
    }
}
