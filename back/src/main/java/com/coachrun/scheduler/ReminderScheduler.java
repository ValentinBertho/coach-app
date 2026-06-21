package com.coachrun.scheduler;

import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Rappel quotidien J-1 : notifie les athlètes des séances prévues le lendemain.
 * (Mono-instance pour le MVP ; passer à ShedLock en cas de scale-out.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final WorkoutRepository workoutRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "${app.reminders.cron:0 0 18 * * *}")
    @Transactional(readOnly = true)
    public void sendTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        var workouts = workoutRepository.findByScheduledDateAndStatus(tomorrow, WorkoutStatus.PLANNED);
        log.info("Rappels J-1 : {} séance(s) prévue(s) le {}", workouts.size(), tomorrow);
        workouts.forEach(notificationService::notifyWorkoutReminder);
    }
}
