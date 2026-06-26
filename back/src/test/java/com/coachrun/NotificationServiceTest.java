package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.integration.ResendMailClient;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.service.NotificationService;
import com.coachrun.service.PushNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ResendMailClient mailClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PushNotificationService pushService;
    @Mock
    private CoachAthleteRelationRepository relationRepository;
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

    private Workout feedbackWorkout() {
        Workout w = sampleWorkout();
        w.getAthlete().setId(UUID.randomUUID());
        Club club = new Club();
        club.setId(UUID.randomUUID());
        w.setClub(club);
        w.setStatus(WorkoutStatus.COMPLETED);
        return w;
    }

    private User coach(String email) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        return u;
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

    @Test
    void feedbackNotifiesReferentCoach() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = feedbackWorkout();
        CoachAthleteRelation rel = new CoachAthleteRelation();
        rel.setCoach(coach("referent@test.fr"));
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.of(rel));

        notificationService.notifyAthleteFeedback(w);

        verify(mailClient).send(eq("referent@test.fr"), contains("renseigné une séance"), any());
        // Le head coach n'est pas sollicité quand un référent existe.
        verify(userRepository, never()).findFirstByClubIdAndRole(any(), any());
    }

    @Test
    void alertDigestDedupesPerAthleteAndOmitsHealthDetail() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        java.util.List<com.coachrun.dto.response.CoachAlertResponse> alerts = java.util.List.of(
                new com.coachrun.dto.response.CoachAlertResponse(a1, "Marie Durand", "ROUTE",
                        "RED", "PAIN", "Douleur élevée", "Douleur 8/10 au dernier retour."),
                new com.coachrun.dto.response.CoachAlertResponse(a1, "Marie Durand", "ROUTE",
                        "ORANGE", "ACWR_HIGH", "Charge en hausse", "ACWR 1.4."),
                new com.coachrun.dto.response.CoachAlertResponse(a2, "Paul Roy", "TRAIL",
                        "ORANGE", "MISSED", "2 séances manquées", "Sur les 14 derniers jours."));

        notificationService.notifyCoachAlertDigest(coach("coach@test.fr"), alerts);

        org.mockito.ArgumentCaptor<String> html = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailClient).send(eq("coach@test.fr"), contains("2 alerte"), html.capture());
        // Dédoublonnage par athlète (Marie une seule fois) et aucune donnée de santé (pas de "8/10").
        assertThatHtml(html.getValue());
    }

    private void assertThatHtml(String html) {
        org.assertj.core.api.Assertions.assertThat(html).contains("Marie Durand").contains("Paul Roy");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("8/10").doesNotContain("Douleur");
        int firstMarie = html.indexOf("Marie Durand");
        org.assertj.core.api.Assertions.assertThat(html.indexOf("Marie Durand", firstMarie + 1)).isEqualTo(-1);
    }

    @Test
    void mutedEmailSuppressesFeedbackEmailButKeepsInApp() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = feedbackWorkout();
        User coach = coach("muted@test.fr");
        coach.setNotifyEmailEnabled(false);
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.of(relWith(coach)));

        notificationService.notifyAthleteFeedback(w);

        verify(mailClient, never()).send(any(), any(), any());
    }

    private CoachAthleteRelation relWith(User coach) {
        CoachAthleteRelation rel = new CoachAthleteRelation();
        rel.setCoach(coach);
        return rel;
    }

    @Test
    void feedbackFallsBackToHeadCoachWhenNoReferent() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = feedbackWorkout();
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.empty());
        when(userRepository.findFirstByClubIdAndRole(w.getClub().getId(), UserRole.HEAD_COACH))
                .thenReturn(Optional.of(coach("head@test.fr")));

        notificationService.notifyAthleteFeedback(w);

        verify(mailClient).send(eq("head@test.fr"), contains("renseigné une séance"), any());
    }
}
