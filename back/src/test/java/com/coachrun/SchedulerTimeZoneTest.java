package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.scheduler.ReminderScheduler;
import com.coachrun.scheduler.SessionDebriefScheduler;
import com.coachrun.service.ClockService;
import com.coachrun.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les schedulers raisonnent dans le fuseau métier, pas en UTC.
 *
 * <p>Le conteneur tourne en UTC : à 23 h 30 UTC un soir d'été, il est déjà 1 h 30 le lendemain à
 * Paris. Sans {@link ClockService}, le rappel J-1 visait la mauvaise journée et le rappel de
 * débriefing partait avec deux heures de décalage.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SchedulerTimeZoneTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    /** 30 juin 2026, 23 h 30 UTC = 1ᵉʳ juillet 2026, 1 h 30 à Paris (UTC+2 en été). */
    private static final Instant AFTER_MIDNIGHT_IN_PARIS = Instant.parse("2026-06-30T23:30:00Z");

    @Mock private WorkoutRepository workoutRepository;
    @Mock private com.coachrun.repository.AthleteRepository athleteRepository;
    @Mock private ScheduledStrengthSessionRepository strengthRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private ClockService clockAt(Instant instant) {
        return new ClockService(Clock.fixed(instant, PARIS));
    }

    @Test
    void clockServiceReturnsTheCivilDateOfTheBusinessZone() {
        ClockService clock = clockAt(AFTER_MIDNIGHT_IN_PARIS);

        // En UTC on serait encore le 30 juin : c'est exactement le décalage que corrige le service.
        assertThat(clock.today()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(clock.now().getHour()).isEqualTo(1);
        assertThat(LocalDate.ofInstant(AFTER_MIDNIGHT_IN_PARIS, ZoneId.of("UTC")))
                .isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void reminderSchedulerTargetsTomorrowInTheBusinessZone() {
        // Le balayage délègue chaque rappel à `remindOne` via le proxy Spring : en test unitaire,
        // le fournisseur renvoie simplement l'instance elle-même.
        ReminderScheduler scheduler = reminderScheduler(clockAt(AFTER_MIDNIGHT_IN_PARIS));
        when(workoutRepository.findByScheduledDateAndStatus(any(), any())).thenReturn(List.of());

        scheduler.sendTomorrowReminders();

        // Demain = 2 juillet à Paris, et non le 1er comme le donnerait LocalDate.now() en UTC.
        verify(workoutRepository).findByScheduledDateAndStatus(
                eq(LocalDate.of(2026, 7, 2)), eq(WorkoutStatus.PLANNED));
    }

    /** Le balayage délègue via le proxy Spring : en test unitaire, le fournisseur rend l'instance. */
    private ReminderScheduler reminderScheduler(ClockService clock) {
        ReminderScheduler[] holder = new ReminderScheduler[1];
        ReminderScheduler scheduler = new ReminderScheduler(
                workoutRepository, athleteRepository, notificationService, clock,
                selfProvider(() -> holder[0]));
        holder[0] = scheduler;
        return scheduler;
    }

    /**
     * Point de programme du soir : un athlète suivi qui n'a rien demain doit l'apprendre.
     *
     * <p>Le rappel ne parlait qu'à ceux qui avaient une séance ; les autres devaient ouvrir
     * l'application pour découvrir qu'il n'y avait justement rien. « Demain, repos » est une
     * information d'entraînement, pas une absence d'information.</p>
     */
    @Test
    void theEveningRoundAnnouncesRestToAthletesWithNothingPlanned() {
        ClockService clock = clockAt(AFTER_MIDNIGHT_IN_PARIS);
        ReminderScheduler scheduler = reminderScheduler(clock);
        UUID resting = UUID.randomUUID();
        Athlete athlete = new Athlete();
        ReflectionTestUtils.setField(athlete, "id", resting);
        athlete.setStatus(com.coachrun.entity.enums.AthleteStatus.ACTIVE);

        when(workoutRepository.findByScheduledDateAndStatus(any(), any())).thenReturn(List.of());
        when(workoutRepository.findAthleteIdsWithWorkoutsBetween(any(), any()))
                .thenReturn(List.of(resting));
        when(athleteRepository.findById(resting)).thenReturn(Optional.of(athlete));

        scheduler.sendTomorrowReminders();

        verify(notificationService).notifyRestDay(athlete);
    }

    /** Celui qui a une séance demain reçoit son programme, pas une annonce de repos. */
    @Test
    void anAthleteWithASessionTomorrowIsNotToldToRest() {
        ClockService clock = clockAt(AFTER_MIDNIGHT_IN_PARIS);
        ReminderScheduler scheduler = reminderScheduler(clock);
        Workout workout = plannedWorkout();
        UUID busy = workout.getAthlete().getId();

        when(workoutRepository.findByScheduledDateAndStatus(any(), any())).thenReturn(List.of(workout));
        when(workoutRepository.findAllById(any())).thenReturn(List.of(workout));
        when(workoutRepository.findAthleteIdsWithWorkoutsBetween(any(), any())).thenReturn(List.of(busy));

        scheduler.sendTomorrowReminders();

        verify(notificationService).notifyWorkoutReminder(List.of(workout));
        verify(notificationService, never()).notifyRestDay(any());
    }

    /**
     * Un dossier sans programme vivant ne reçoit rien : sans ce filtre, tout compte dormant
     * recevrait « Repos demain » toutes les nuits, indéfiniment.
     */
    @Test
    void aDormantAthleteIsNeverToldToRest() {
        ClockService clock = clockAt(AFTER_MIDNIGHT_IN_PARIS);
        ReminderScheduler scheduler = reminderScheduler(clock);

        when(workoutRepository.findByScheduledDateAndStatus(any(), any())).thenReturn(List.of());
        when(workoutRepository.findAthleteIdsWithWorkoutsBetween(any(), any())).thenReturn(List.of());

        scheduler.sendTomorrowReminders();

        verify(notificationService, never()).notifyRestDay(any());
    }

    /** Athlète en pause ou archivé : son programme s'est arrêté, pas seulement sa journée. */
    @Test
    void aPausedAthleteIsNeverToldToRest() {
        ClockService clock = clockAt(AFTER_MIDNIGHT_IN_PARIS);
        ReminderScheduler scheduler = reminderScheduler(clock);
        UUID paused = UUID.randomUUID();
        Athlete athlete = new Athlete();
        ReflectionTestUtils.setField(athlete, "id", paused);
        athlete.setStatus(com.coachrun.entity.enums.AthleteStatus.PAUSED);

        when(workoutRepository.findByScheduledDateAndStatus(any(), any())).thenReturn(List.of());
        when(workoutRepository.findAthleteIdsWithWorkoutsBetween(any(), any())).thenReturn(List.of(paused));
        when(athleteRepository.findById(paused)).thenReturn(Optional.of(athlete));

        scheduler.sendTomorrowReminders();

        verify(notificationService, never()).notifyRestDay(any());
    }

    @Test
    void debriefSchedulerUsesTheBusinessDayAndHour() {
        // 18 h 30 UTC = 20 h 30 à Paris → relance des athlètes dont la séance est à 18 h 30.
        ClockService clock = clockAt(Instant.parse("2026-07-01T18:30:00Z"));
        SessionDebriefScheduler scheduler = new SessionDebriefScheduler(
                workoutRepository, strengthRepository, userRepository, notificationService, clock);
        ReflectionTestUtils.setField(scheduler, "enabled", true);

        Workout workout = plannedWorkout();
        when(workoutRepository.findByScheduledDateAndStatus(any(), any())).thenReturn(List.of(workout));
        when(strengthRepository.findByScheduledDateAndCompletedFalse(any())).thenReturn(List.of());
        when(userRepository.findByAthleteId(workout.getAthlete().getId()))
                .thenReturn(Optional.of(athleteUserTrainingAt(LocalTime.of(18, 30))));

        scheduler.sendDebriefReminders();

        verify(workoutRepository).findByScheduledDateAndStatus(
                eq(LocalDate.of(2026, 7, 1)), eq(WorkoutStatus.PLANNED));
        verify(notificationService).notifySessionDebrief(any(), eq("Séance seuil"), any());
    }

    @Test
    void debriefSchedulerSkipsAthletesOutsideTheCurrentBusinessHour() {
        // Même instant : en UTC (18 h) on relancerait les séances de 16 h, pas celles de 18 h 30.
        ClockService clock = clockAt(Instant.parse("2026-07-01T18:30:00Z"));
        SessionDebriefScheduler scheduler = new SessionDebriefScheduler(
                workoutRepository, strengthRepository, userRepository, notificationService, clock);
        ReflectionTestUtils.setField(scheduler, "enabled", true);

        Workout workout = plannedWorkout();
        when(workoutRepository.findByScheduledDateAndStatus(any(), any())).thenReturn(List.of(workout));
        when(strengthRepository.findByScheduledDateAndCompletedFalse(any())).thenReturn(List.of());
        when(userRepository.findByAthleteId(workout.getAthlete().getId()))
                .thenReturn(Optional.of(athleteUserTrainingAt(LocalTime.of(16, 0))));

        scheduler.sendDebriefReminders();

        verify(notificationService, never()).notifySessionDebrief(any(), any(), any());
    }

    private Workout plannedWorkout() {
        Athlete athlete = new Athlete();
        athlete.setId(UUID.randomUUID());
        athlete.setFirstName("Marie");
        Workout w = new Workout();
        w.setId(UUID.randomUUID());
        w.setAthlete(athlete);
        w.setTitle("Séance seuil");
        w.setStatus(WorkoutStatus.PLANNED);
        return w;
    }

    private User athleteUserTrainingAt(LocalTime usualSessionTime) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setNotifyPushEnabled(true);
        u.setUsualSessionTime(usualSessionTime);
        return u;
    }

    /**
     * Fournisseur minimal tenant lieu de proxy Spring. En production, le planificateur passe par
     * lui pour que le {@code @Transactional} de {@code remindOne} s'applique réellement ; ici,
     * il rend l'instance sous test.
     */
    private static <T> org.springframework.beans.factory.ObjectProvider<T> selfProvider(
            java.util.function.Supplier<T> supplier) {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public T getObject() {
                return supplier.get();
            }

            @Override
            public T getObject(Object... args) {
                return supplier.get();
            }

            @Override
            public T getIfAvailable() {
                return supplier.get();
            }

            @Override
            public T getIfUnique() {
                return supplier.get();
            }
        };
    }
}
