package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.CoachAthleteRelation;
import com.coachrun.entity.Message;
import com.coachrun.entity.User;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.NotificationCategory;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.integration.MailTemplate;
import com.coachrun.integration.ResendMailClient;
import com.coachrun.repository.AlertDigestLogRepository;
import com.coachrun.repository.CoachAthleteRelationRepository;
import com.coachrun.repository.NotificationRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.service.ClockService;
import com.coachrun.service.NotificationService;
import com.coachrun.service.NotificationStreamService;
import com.coachrun.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ResendMailClient mailClient;
    /** Gabarit réel : les assertions portent sur l'e-mail effectivement rendu. */
    @Spy
    private MailTemplate mailTemplate = new MailTemplate();
    @Mock
    private UserRepository userRepository;
    @Mock
    private PushNotificationService pushService;
    @Mock
    private CoachAthleteRelationRepository relationRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationStreamService streamService;
    @Mock
    private AlertDigestLogRepository digestLogRepository;
    @Mock
    private ClockService clock;
    @InjectMocks
    private NotificationService notificationService;

    /**
     * Milieu d'après-midi : hors des heures de silence par défaut (22 h – 7 h), donc les tests
     * qui ne parlent pas d'horaire ne les rencontrent jamais.
     */
    @BeforeEach
    void aQuinzeHeures() {
        atLocalTime(15, 0);
    }

    /**
     * Fige l'heure locale du destinataire. C'est bien elle que le service consulte — les heures de
     * silence se calculent dans le fuseau de celui qui dort, pas dans celui du serveur.
     */
    private void atLocalTime(int hour, int minute) {
        Clock fixed = Clock.fixed(
                LocalDate.of(2026, 7, 1).atTime(hour, minute).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC);
        lenient().when(clock.clockIn(org.mockito.ArgumentMatchers.nullable(User.class))).thenReturn(fixed);
    }

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
        // La séance porte un identifiant : les actions rapides de la notification le transportent.
        w.setId(UUID.randomUUID());
        w.getAthlete().setId(UUID.randomUUID());
        Club club = new Club();
        club.setId(UUID.randomUUID());
        w.setClub(club);
        w.setStatus(WorkoutStatus.COMPLETED);
        return w;
    }

    /**
     * Les actions rapides d'une notification, quand le test ne porte pas dessus. Les retours
     * d'athlète et les messages en portent désormais (« Traité », « Répondre ») : la remise passe
     * donc par la forme à cinq arguments.
     */
    private List<PushNotificationService.QuickAction> anyActions() {
        return any();
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
        ReflectionTestUtils.setField(mailTemplate, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "publisher", "Darilab");

        notificationService.notifyWorkoutPlanned(sampleWorkout());

        verifyNoInteractions(mailClient);
    }

    @Test
    void skipsAthleteWithoutEmail() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "publisher", "Darilab");
        Workout w = sampleWorkout();
        w.getAthlete().setEmail(null);

        notificationService.notifyWorkoutPlanned(w);

        verify(mailClient, never()).send(any(), any(), any(), any(), any(), any());
    }

    @Test
    void feedbackNotifiesReferentCoach() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "publisher", "Darilab");
        Workout w = feedbackWorkout();
        // Douleur déclarée : c'est ce qui justifie désormais d'interrompre le coach. Ce test porte
        // sur le *destinataire* (référent plutôt que head coach), pas sur le seuil.
        w.setPain(4);
        CoachAthleteRelation rel = new CoachAthleteRelation();
        rel.setCoach(coach("referent@test.fr"));
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.of(rel));

        notificationService.notifyAthleteFeedback(w);

        // Le retour d'un athlète est une notification de routine : push + centre de
        // notifications, jamais d'e-mail (c'était le flux le plus volumineux côté coach).
        // Deux boutons l'accompagnent : le classer sans l'ouvrir, ou aller le lire.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushNotificationService.QuickAction>> actions =
                ArgumentCaptor.forClass(List.class);
        verify(pushService).sendToUser(eq(rel.getCoach().getId()), eq("Séance mise à jour"),
                any(), contains("/app/feedback"), actions.capture());
        assertThat(actions.getValue()).extracting(PushNotificationService.QuickAction::id)
                .containsExactly("traite", "ouvrir");
        assertThat(actions.getValue().get(0).url())
                .contains("/app/feedback?review=COURSE:" + w.getAthlete().getId() + ":" + w.getId());
        verify(mailClient, never()).send(any(), any(), any(), any(), any(), any());
        // Le head coach n'est pas sollicité quand un référent existe.
        verify(userRepository, never()).findFirstByClubIdAndRole(any(), any());
    }

    @Test
    void alertDigestDedupesPerAthleteAndOmitsHealthDetail() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "publisher", "Darilab");
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
        verify(mailClient).send(eq("coach@test.fr"), contains("2 alerte"), html.capture(),
                any(), any(), any());
        // Dédoublonnage par athlète (Marie une seule fois) et aucune donnée de santé (pas de "8/10").
        assertThatHtml(html.getValue());
    }

    private void assertThatHtml(String html) {
        org.assertj.core.api.Assertions.assertThat(html).contains("Marie Durand").contains("Paul Roy");
        org.assertj.core.api.Assertions.assertThat(html).doesNotContain("8/10").doesNotContain("Douleur");
        int firstMarie = html.indexOf("Marie Durand");
        org.assertj.core.api.Assertions.assertThat(html.indexOf("Marie Durand", firstMarie + 1)).isEqualTo(-1);
    }

    /**
     * Une blessure déclarée appelle un mot : le bouton de la notification mène au fil de
     * l'athlète, champ de saisie ouvert — et jamais à un écran qui dirait la blessure.
     */
    @Test
    void injuryAlertOffersToReplyOnThatAthleteThread() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Athlete athlete = new Athlete();
        athlete.setId(UUID.randomUUID());
        athlete.setFirstName("Marie");
        athlete.setLastName("Durand");
        User referent = coach("referent@test.fr");
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(athlete.getId()))
                .thenReturn(Optional.of(relWith(referent)));

        notificationService.notifyInjuryAlert(athlete, List.of(new com.coachrun.dto.InjuryReport(
                com.coachrun.entity.enums.InjuryKind.SPRAIN,
                com.coachrun.entity.enums.InjuryArea.ANKLE, null, "en descente")));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushNotificationService.QuickAction>> actions =
                ArgumentCaptor.forClass(List.class);
        verify(pushService).sendToUser(eq(referent.getId()), eq("Blessure déclarée"),
                body.capture(), contains("/app/athletes/" + athlete.getId()), actions.capture());
        // Ni la nature ni la localisation : elles s'afficheraient sur un écran verrouillé.
        assertThat(body.getValue()).doesNotContain("cheville").doesNotContain("entorse")
                .doesNotContain("descente");
        assertThat(actions.getValue()).singleElement()
                .extracting(PushNotificationService.QuickAction::url).asString()
                .endsWith("/app/athletes/" + athlete.getId() + "/messages?reply=1");
    }

    /**
     * Le digest de 7 h atterrit sur « Ma journée », pas sur le cockpit : il est lu sur un
     * téléphone, et il annonce exactement ce que cet écran rassemble.
     */
    @Test
    void alertDigestLandsOnTheCoachDayScreen() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "publisher", "Darilab");
        User target = coach("coach@test.fr");

        notificationService.notifyCoachAlertDigest(target, List.of(
                new com.coachrun.dto.response.CoachAlertResponse(UUID.randomUUID(), "Marie Durand",
                        "ROUTE", "RED", "PAIN", "Douleur élevée", "Douleur 8/10.")));

        verify(pushService).sendToUser(eq(target.getId()), eq("Alertes à traiter"), any(),
                eq("http://localhost:4200/app/journee"));
    }

    /**
     * Couper le push suffit à ne plus être notifié d'un retour : la trace reste au centre de
     * notifications, qui n'est jamais coupé (sinon le coach perdrait l'information, pas
     * seulement l'interruption).
     */
    @Test
    void mutedPushSuppressesFeedbackPushButKeepsInApp() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = feedbackWorkout();
        // Signal présent : sans lui le push serait déjà supprimé par le seuil, et le test ne
        // prouverait plus rien sur la préférence de canal.
        w.setPain(4);
        User coach = coach("muted@test.fr");
        coach.setNotifyPushEnabled(false);
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.of(relWith(coach)));

        notificationService.notifyAthleteFeedback(w);

        verify(pushService, never()).sendToUser(any(), any(), any(), any());
        verify(mailClient, never()).send(any(), any(), any(), any(), any(), any());
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
        ReflectionTestUtils.setField(mailTemplate, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "publisher", "Darilab");
        Workout w = feedbackWorkout();
        w.setPain(4);
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.empty());
        User head = coach("head@test.fr");
        when(userRepository.findFirstByClubIdAndRole(w.getClub().getId(), UserRole.HEAD_COACH))
                .thenReturn(Optional.of(head));

        notificationService.notifyAthleteFeedback(w);

        verify(pushService).sendToUser(eq(head.getId()), eq("Séance mise à jour"),
                any(), contains("/app/feedback"), anyActions());
        verify(mailClient, never()).send(any(), any(), any(), any(), any(), any());
    }

    /**
     * Le rappel de séance passe en push quand l'athlète a un appareil abonné, et ne retombe sur
     * l'e-mail que sinon : c'était le flux qui, à lui seul, consommait un envoi par séance et
     * par jour alors qu'il est le plus typiquement « notification » du produit.
     */
    @Test
    void workoutReminderPrefersPushAndFallsBackToEmail() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "frontendUrl", "http://localhost:4200");
        ReflectionTestUtils.setField(mailTemplate, "publisher", "Darilab");
        Workout w = feedbackWorkout();
        w.getAthlete().setEmail("athlete@test.fr");
        User athleteUser = coach("athlete@test.fr");
        when(userRepository.findByAthleteId(w.getAthlete().getId()))
                .thenReturn(Optional.of(athleteUser));
        when(pushService.canReach(athleteUser.getId())).thenReturn(true);

        notificationService.notifyWorkoutReminder(w);

        verify(pushService).sendToUser(eq(athleteUser.getId()), eq("Séance demain"),
                any(), contains("/athlete/today"));
        verify(mailClient, never()).send(any(), any(), any(), any(), any(), any());

        // Aucun appareil abonné : l'e-mail reprend son rôle de repli.
        when(pushService.canReach(athleteUser.getId())).thenReturn(false);
        notificationService.notifyWorkoutReminder(w);
        verify(mailClient).send(eq("athlete@test.fr"), contains("séance demain"),
                any(), any(), any(), any());
    }

    /**
     * Une séance planifiée n'envoie plus d'e-mail : déposer cinq séances depuis la bibliothèque
     * en envoyait cinq à la suite, pour une information déjà visible dans l'agenda de l'athlète.
     */
    @Test
    void plannedWorkoutNeverSendsEmail() {
        ReflectionTestUtils.setField(notificationService, "enabled", true);
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = feedbackWorkout();
        w.getAthlete().setEmail("athlete@test.fr");
        User athleteUser = coach("athlete@test.fr");
        when(userRepository.findByAthleteId(w.getAthlete().getId()))
                .thenReturn(Optional.of(athleteUser));

        notificationService.notifyWorkoutPlanned(w);

        verify(pushService).sendToUser(eq(athleteUser.getId()), eq("Nouvelle séance"),
                any(), contains("/athlete/today"));
        verify(mailClient, never()).send(any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------------------------------
    // Attribution d'un plan : une notification, pas une par séance
    // ---------------------------------------------------------------------------------------

    /**
     * Le plan entier tient en une notification. C'est le correctif central : l'attribution
     * générait une séance — donc une notification — par item, soit une cinquantaine en une salve
     * pour un plan de douze semaines.
     */
    @Test
    void planAssignmentSendsOneNotificationForTheWholeProgram() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Athlete athlete = athleteWithUser();
        User athleteUser = userFor(athlete);

        notificationService.notifyPlanAssigned(athlete, "Prépa 10 km", 48, LocalDate.of(2026, 11, 12));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUser(eq(athleteUser.getId()), eq("Nouveau plan d'entraînement"),
                body.capture(), contains("/athlete/calendar"));
        assertThat(body.getValue()).contains("Prépa 10 km").contains("48 séances").contains("novembre");
        verify(mailClient, never()).send(any(), any(), any(), any(), any(), any());
    }

    /** Un plan sans séance générée ne notifie rien. */
    @Test
    void emptyPlanAssignmentNotifiesNothing() {
        notificationService.notifyPlanAssigned(new Athlete(), "Plan vide", 0, null);

        verifyNoInteractions(pushService);
    }

    // ---------------------------------------------------------------------------------------
    // Messagerie
    // ---------------------------------------------------------------------------------------

    /**
     * Un message du coach atteint l'athlète hors de l'application — c'est tout l'objet du
     * correctif : la messagerie ne se reposait que sur le flux temps réel, inutile à qui n'a pas
     * déjà l'écran ouvert.
     *
     * <p>Le corps ne porte que le nom de l'expéditeur : une conversation coach ↔ athlète parle de
     * blessures et de fatigue, et n'a rien à faire sur un écran verrouillé.</p>
     */
    @Test
    void coachMessageNotifiesAthleteWithoutQuotingTheMessage() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Athlete athlete = athleteWithUser();
        User athleteUser = userFor(athlete);
        Message m = message(athlete, UserRole.COACH, "Coach Bernard", "Ton genou va mieux ?");

        notificationService.notifyNewMessage(m);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushNotificationService.QuickAction>> actions =
                ArgumentCaptor.forClass(List.class);
        verify(pushService).sendToUser(eq(athleteUser.getId()), eq("Nouveau message"),
                body.capture(), contains("/athlete/messages"), actions.capture());
        assertThat(body.getValue()).isEqualTo("Coach Bernard").doesNotContain("genou");
        // Répondre en un tap : le bouton vise le fil, champ de saisie déjà actif.
        assertThat(actions.getValue()).singleElement()
                .extracting(PushNotificationService.QuickAction::url).asString()
                .endsWith("/athlete/messages?reply=1");
    }

    /** Un message de l'athlète remonte à son coach référent, sur le fil de cet athlète. */
    @Test
    void athleteMessageNotifiesReferentCoachOnThatThread() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Athlete athlete = new Athlete();
        athlete.setId(UUID.randomUUID());
        athlete.setFirstName("Marie");
        athlete.setLastName("Durand");
        User referent = coach("referent@test.fr");
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(athlete.getId()))
                .thenReturn(Optional.of(relWith(referent)));

        notificationService.notifyNewMessage(message(athlete, UserRole.ATHLETE, "Marie Durand", "coucou"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushNotificationService.QuickAction>> actions =
                ArgumentCaptor.forClass(List.class);
        verify(pushService).sendToUser(eq(referent.getId()), eq("Nouveau message"),
                eq("Marie Durand"), contains("/app/athletes/" + athlete.getId() + "/messages"),
                actions.capture());
        // Le bouton mène au fil de CET athlète, pas à la boîte de réception.
        assertThat(actions.getValue()).singleElement()
                .extracting(PushNotificationService.QuickAction::url).asString()
                .endsWith("/app/athletes/" + athlete.getId() + "/messages?reply=1");
    }

    /**
     * Anti-rafale : cinq messages en trois minutes font une conversation, pas cinq notifications.
     * Le centre de notifications sert de mémoire — la trace précédente suffit à savoir qu'on
     * vient de sonner.
     */
    @Test
    void burstOfMessagesOnTheSameThreadNotifiesOnce() {
        Athlete athlete = athleteWithUser();
        userFor(athlete);
        when(notificationRepository.existsByUserIdAndTypeAndLinkAndCreatedAtAfter(
                any(), eq("NEW_MESSAGE"), anyString(), any(Instant.class))).thenReturn(true);

        notificationService.notifyNewMessage(message(athlete, UserRole.COACH, "Coach", "et aussi…"));

        verifyNoInteractions(pushService);
    }

    // ---------------------------------------------------------------------------------------
    // Changements de programme
    // ---------------------------------------------------------------------------------------

    /** Une séance déplacée par le coach ne passait plus aucun signal à l'athlète. */
    @Test
    void movedWorkoutNotifiesTheAthlete() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = sampleWorkout();
        w.getAthlete().setId(UUID.randomUUID());
        User athleteUser = userFor(w.getAthlete());

        notificationService.notifyWorkoutChanged(w, true);

        verify(pushService).sendToUser(eq(athleteUser.getId()), eq("Séance déplacée"),
                contains("Footing"), contains("/athlete/calendar"));
    }

    /** Remanier une semaine est un seul geste : la salve est regroupée. */
    @Test
    void burstOfWorkoutChangesNotifiesOnce() {
        Workout w = sampleWorkout();
        w.getAthlete().setId(UUID.randomUUID());
        userFor(w.getAthlete());
        when(notificationRepository.existsByUserIdAndTypeAndLinkAndCreatedAtAfter(
                any(), eq("WORKOUT_UPDATED"), anyString(), any(Instant.class))).thenReturn(true);

        notificationService.notifyWorkoutChanged(w, false);
        notificationService.notifyWorkoutCancelled(w);

        verifyNoInteractions(pushService);
    }

    // ---------------------------------------------------------------------------------------
    // Libellés
    // ---------------------------------------------------------------------------------------

    /** Le coach lisait « Marie Durand — COMPLETED » : seul endroit du produit parlant anglais. */
    @Test
    void feedbackBodyUsesFrenchStatusLabel() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = feedbackWorkout();
        w.setStatus(WorkoutStatus.PARTIAL);
        w.setPain(4);
        User referent = coach("referent@test.fr");
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.of(relWith(referent)));

        notificationService.notifyAthleteFeedback(w);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUser(eq(referent.getId()), eq("Séance mise à jour"),
                body.capture(), any(), anyActions());
        assertThat(body.getValue()).isEqualTo("Marie — partiellement réalisée");
    }

    // ---------------------------------------------------------------------------------------
    // Préférences : catégories coupées et heures de silence
    // ---------------------------------------------------------------------------------------

    /**
     * Couper une famille fait taire le push et <b>rien d'autre</b>. La trace reste au centre de
     * notifications : on retire l'interruption, jamais l'information — sans quoi l'utilisateur
     * perdrait la séance elle-même en croyant n'éteindre qu'une sonnerie.
     */
    @Test
    void mutedCategorySuppressesPushButKeepsInApp() {
        Workout w = sampleWorkout();
        w.getAthlete().setId(UUID.randomUUID());
        User athleteUser = userFor(w.getAthlete());
        athleteUser.setMutedCategories(Set.of(NotificationCategory.PROGRAMME));
        when(userRepository.findById(athleteUser.getId())).thenReturn(Optional.of(athleteUser));

        notificationService.notifyWorkoutPlanned(w);

        verifyNoInteractions(pushService);
        verify(notificationRepository).save(any());
    }

    /** Couper une famille n'en fait pas taire une autre. */
    @Test
    void mutingOneCategoryLeavesTheOthersAudible() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Athlete athlete = athleteWithUser();
        User athleteUser = userFor(athlete);
        athleteUser.setMutedCategories(Set.of(NotificationCategory.PROGRAMME));

        notificationService.notifyNewMessage(message(athlete, UserRole.COACH, "Coach", "salut"));

        verify(pushService).sendToUser(eq(athleteUser.getId()), eq("Nouveau message"), any(), any(),
                anyActions());
    }

    /** 23 h 30 tombe dans la plage par défaut : la trace se dépose, le téléphone ne sonne pas. */
    @Test
    void quietHoursSuppressPushButKeepInApp() {
        atLocalTime(23, 30);
        Workout w = sampleWorkout();
        w.getAthlete().setId(UUID.randomUUID());
        User athleteUser = userFor(w.getAthlete());
        when(userRepository.findById(athleteUser.getId())).thenReturn(Optional.of(athleteUser));

        notificationService.notifyCoachComment(w);

        verifyNoInteractions(pushService);
        verify(notificationRepository).save(any());
    }

    /** La plage traverse minuit : 6 h est encore dans la nuit, 8 h n'y est plus. */
    @Test
    void quietWindowWrapsAroundMidnight() {
        User u = new User();
        assertThat(NotificationService.inQuietHours(u, LocalTime.of(23, 0))).isTrue();
        assertThat(NotificationService.inQuietHours(u, LocalTime.of(6, 0))).isTrue();
        assertThat(NotificationService.inQuietHours(u, LocalTime.of(7, 0))).isFalse();
        assertThat(NotificationService.inQuietHours(u, LocalTime.of(8, 0))).isFalse();
        assertThat(NotificationService.inQuietHours(u, LocalTime.of(21, 59))).isFalse();
    }

    /** Deux bornes identiques valent « pas de silence », et non « silence permanent ». */
    @Test
    void identicalQuietBoundsDisableSilence() {
        User u = new User();
        u.setNotifyQuietStart(LocalTime.of(9, 0));
        u.setNotifyQuietEnd(LocalTime.of(9, 0));
        assertThat(NotificationService.inQuietHours(u, LocalTime.of(9, 0))).isFalse();
        assertThat(NotificationService.inQuietHours(u, LocalTime.of(3, 0))).isFalse();
    }

    // ---------------------------------------------------------------------------------------
    // Retours de séance : push seulement si le retour appelle une décision
    // ---------------------------------------------------------------------------------------

    /**
     * Une séance réalisée comme prévu ne fait plus sonner le coach : c'est l'information la moins
     * urgente du produit, et elle représentait l'essentiel de ses notifications quotidiennes. Elle
     * reste dans sa file et dans son centre.
     */
    @Test
    void routineFeedbackLandsInAppWithoutPush() {
        Workout w = feedbackWorkout();
        w.setRpe(5);
        User referent = coach("referent@test.fr");
        when(userRepository.findById(referent.getId())).thenReturn(Optional.of(referent));
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.of(relWith(referent)));

        notificationService.notifyAthleteFeedback(w);

        verifyNoInteractions(pushService);
        verify(notificationRepository).save(any());
    }

    /** Une douleur déclarée, elle, appelle une décision le jour même. */
    @Test
    void painfulFeedbackStillPushes() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = feedbackWorkout();
        w.setPain(4);
        User referent = coach("referent@test.fr");
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.of(relWith(referent)));

        notificationService.notifyAthleteFeedback(w);

        verify(pushService).sendToUser(eq(referent.getId()), eq("Séance mise à jour"),
                any(), contains("/app/feedback"), anyActions());
    }

    /** Une séance déclarée non faite aussi. */
    @Test
    void missedSessionStillPushes() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout w = feedbackWorkout();
        w.setStatus(WorkoutStatus.MISSED);
        User referent = coach("referent@test.fr");
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(w.getAthlete().getId()))
                .thenReturn(Optional.of(relWith(referent)));

        notificationService.notifyAthleteFeedback(w);

        verify(pushService).sendToUser(eq(referent.getId()), any(), any(), any(), anyActions());
    }

    // ---------------------------------------------------------------------------------------
    // Douleur élevée : le coach est prévenu tout de suite, sans l'intensité
    // ---------------------------------------------------------------------------------------

    /**
     * Une douleur 8/10 partait au digest du lendemain : vingt-trois heures d'attente sur le seul
     * signal du produit dont le délai se paie en blessure. Le corps ne porte pas l'intensité.
     */
    @Test
    void severePainAlertsTheCoachImmediatelyWithoutTheNumber() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Athlete athlete = athleteWithUser();
        athlete.setClub(clubWithId());
        User referent = coach("referent@test.fr");
        when(relationRepository.findByAthleteIdAndReferentTrueAndActiveTrue(athlete.getId()))
                .thenReturn(Optional.of(relWith(referent)));
        when(digestLogRepository.findByCoachId(referent.getId())).thenReturn(List.of());

        notificationService.notifyPainAlert(athlete, 8);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUser(eq(referent.getId()), eq("Douleur signalée"),
                body.capture(), contains("/app/athletes/" + athlete.getId()));
        assertThat(body.getValue()).contains("Marie Durand").doesNotContain("8");
        // L'envoi est inscrit dans la mémoire du digest, qui ne le redira donc pas demain matin.
        verify(digestLogRepository).save(any());
    }

    /** Une douleur modérée n'interrompt pas : elle passe par le digest, comme avant. */
    @Test
    void moderatePainDoesNotRaiseAnImmediateAlert() {
        notificationService.notifyPainAlert(athleteWithUser(), 3);

        verifyNoInteractions(pushService);
        verifyNoInteractions(digestLogRepository);
    }

    // ---------------------------------------------------------------------------------------
    // Rappel J-1 groupé
    // ---------------------------------------------------------------------------------------

    /** Deux séances demain font un rappel, pas deux notifications à la suite à 18 h. */
    @Test
    void tomorrowRemindersAreGroupedPerAthlete() {
        ReflectionTestUtils.setField(notificationService, "frontendUrl", "http://localhost:4200");
        Workout morning = sampleWorkout();
        morning.getAthlete().setId(UUID.randomUUID());
        Workout evening = new Workout();
        evening.setAthlete(morning.getAthlete());
        evening.setTitle("Renforcement");
        evening.setScheduledDate(morning.getScheduledDate());
        User athleteUser = userFor(morning.getAthlete());
        when(pushService.canReach(athleteUser.getId())).thenReturn(true);

        notificationService.notifyWorkoutReminder(List.of(morning, evening));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUser(eq(athleteUser.getId()), eq("2 séances demain"),
                body.capture(), contains("/athlete/today"));
        assertThat(body.getValue()).contains("Footing").contains("Renforcement");
        verify(mailClient, never()).send(any(), any(), any(), any(), any(), any());
    }

    // ---------------------------------------------------------------------------------------

    private Club clubWithId() {
        Club c = new Club();
        c.setId(UUID.randomUUID());
        return c;
    }

    private Athlete athleteWithUser() {
        Athlete a = new Athlete();
        a.setId(UUID.randomUUID());
        a.setFirstName("Marie");
        a.setLastName("Durand");
        return a;
    }

    /** Rattache un compte utilisateur à l'athlète et le renvoie. */
    private User userFor(Athlete athlete) {
        User u = coach("athlete@test.fr");
        when(userRepository.findByAthleteId(athlete.getId())).thenReturn(Optional.of(u));
        return u;
    }

    private Message message(Athlete athlete, UserRole senderRole, String senderName, String body) {
        Message m = new Message();
        m.setAthlete(athlete);
        m.setSenderRole(senderRole);
        m.setSenderName(senderName);
        m.setSenderUserId(UUID.randomUUID());
        m.setBody(body);
        return m;
    }
}
