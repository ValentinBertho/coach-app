package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.integration.ResendMailClient;
import com.coachrun.repository.UserRepository;
import com.coachrun.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ResendMailClient mailClient;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private NotificationService notificationService;

    private Workout sampleWorkout() {
        Athlete athlete = new Athlete();
        athlete.setFirstName("Marie");
        athlete.setLastName("Durand");
        athlete.setEmail("marie@test.fr");
        Workout w = new Workout();
        w.setAthlete(athlete);
        w.setTitle("Footing");
        w.setScheduledDate(LocalDate.of(2026, 7, 1));
        return w;
    }

    @Test
    void doesNotSendWhenDisabled() {
        ReflectionTestUtils.setField(notificationService, "enabled", false);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");

        notificationService.notifyWorkoutPlanned(sampleWorkout());

        verifyNoInteractions(mailClient);
    }

    @Test
    void skipsAthleteWithoutEmail() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = sampleWorkout();
        w.getAthlete().setEmail(null);

        notificationService.notifyWorkoutPlanned(w);

        verify(mailClient, never()).send(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
