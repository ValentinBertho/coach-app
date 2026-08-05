package com.coachrun;

import com.coachrun.dto.request.WorkoutRequest;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.NotificationService;
import com.coachrun.service.WorkoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le commutateur qui empêche une génération en lot de notifier séance par séance.
 *
 * <p>Attribuer un plan de douze semaines appelle {@code create} une cinquantaine de fois. Tant que
 * chacun de ces appels notifiait, le geste le plus banal du coach produisait une salve de push —
 * rejouée à chaque réattribution, la génération étant idempotente. C'est le premier motif de
 * désabonnement d'un canal de notification, et un opt-out push ne se récupère pas.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkWorkoutNotificationTest {

    @Mock private WorkoutRepository workoutRepository;
    @Mock private AthleteRepository athleteRepository;
    @Mock private NotificationService notificationService;
    @InjectMocks private WorkoutService workoutService;

    private final UUID clubId = UUID.randomUUID();
    private final UUID athleteId = UUID.randomUUID();

    private void givenAthlete() {
        Club club = new Club();
        club.setId(clubId);
        Athlete athlete = new Athlete();
        athlete.setId(athleteId);
        athlete.setFirstName("Léa");
        athlete.setLastName("Run");
        athlete.setClub(club);
        when(athleteRepository.findByIdAndClubMembership(athleteId, clubId)).thenReturn(Optional.of(athlete));
        when(workoutRepository.save(any(Workout.class))).thenAnswer(i -> i.getArgument(0));
    }

    private WorkoutRequest request(LocalDate date) {
        return new WorkoutRequest(date, WorkoutType.ENDURANCE, "Footing", null, 8000, null, List.of());
    }

    /** Le chemin unitaire — le coach pose une séance — reste annoncé. C'est bien un fait nouveau. */
    @Test
    void singleWorkoutStillNotifiesTheAthlete() {
        givenAthlete();

        workoutService.create(clubId, athleteId, request(LocalDate.of(2026, 9, 8)), null);

        verify(notificationService).notifyWorkoutPlanned(any(Workout.class));
    }

    /**
     * Le chemin de génération n'annonce rien à l'unité : c'est l'appelant qui émettra une
     * notification unique pour tout le bloc.
     */
    @Test
    void generatedWorkoutsAreSilentWhateverTheirNumber() {
        givenAthlete();
        UUID planId = UUID.randomUUID();

        for (int week = 0; week < 12; week++) {
            workoutService.create(clubId, athleteId,
                    request(LocalDate.of(2026, 9, 8).plusWeeks(week)), planId, false);
        }

        verify(workoutRepository, times(12)).save(any(Workout.class));
        verify(notificationService, never()).notifyWorkoutPlanned(any(Workout.class));
    }
}
