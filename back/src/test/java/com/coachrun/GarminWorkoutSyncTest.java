package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.GarminWorkoutSync;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.integration.GarminClient;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.GarminWorkoutSyncRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.ClockService;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.GarminService;
import com.coachrun.service.GarminWorkoutSyncService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Envoi des séances dans le calendrier Garmin : la réconciliation entre ce qui devrait être sur la
 * montre et ce qui y est. Chaque cas est un geste du coach — assigner, modifier, déplacer,
 * supprimer — et ce que la montre doit en voir.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
// @MockBean force un contexte dédié : base mémoire propre, comme StravaControllerTest.
@org.springframework.test.context.TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:garmin-sync;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class GarminWorkoutSyncTest {

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private WorkoutRepository workoutRepository;
    @Autowired private DeviceConnectionRepository connectionRepository;
    @Autowired private GarminWorkoutSyncRepository syncRepository;
    @Autowired private GarminWorkoutSyncService syncService;
    @Autowired private GarminService garminService;
    @Autowired private ClockService clock;
    @Autowired private EntityManager em;
    @Autowired private com.coachrun.repository.ScheduledStrengthSessionRepository strengthRepository;
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @MockBean private GarminClient garminClient;

    private Athlete athlete;
    private LocalDate today;
    private final AtomicLong ids = new AtomicLong(1000);

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        athlete = athleteRepository.findAll().stream()
                .filter(a -> a.getClub() != null).findFirst().orElseThrow();
        today = clock.today();
        when(garminClient.isConfigured()).thenReturn(true);
        when(garminClient.createWorkout(anyString(), any())).thenAnswer(inv -> ids.incrementAndGet());
        when(garminClient.scheduleWorkout(anyString(), anyLong(), any())).thenAnswer(inv -> ids.incrementAndGet());
        connect(List.of(GarminService.WORKOUT_IMPORT, GarminService.ACTIVITY_EXPORT));
    }

    @Test
    void uneSeanceCourseAVenirEstEnvoyeeEtPlanifieeASaDate() {
        Workout w = workout("Seuil 3 × 10 min", WorkoutType.THRESHOLD, today.plusDays(2));

        syncService.syncAthlete(athlete.getId());

        verify(garminClient).createWorkout(eq("acc"), argThat(named("Seuil 3 × 10 min")));
        GarminWorkoutSync row = rowFor(w);
        verify(garminClient).scheduleWorkout("acc", row.getGarminWorkoutId(), today.plusDays(2));
        assertThat(row.getGarminWorkoutId()).isNotNull();
        assertThat(row.getGarminScheduleId()).isNotNull();
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void uneSeanceInchangeeNeCoutePlusAucunAppel() {
        workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        syncService.syncAthlete(athlete.getId());
        clearInvocations(garminClient);

        syncService.syncAthlete(athlete.getId());

        verify(garminClient, never()).createWorkout(anyString(), any());
        verify(garminClient, never()).deleteWorkout(anyString(), anyLong());
    }

    @Test
    void uneSeanceModifieeEstRemplaceeSurLaMontre() {
        Workout w = workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        syncService.syncAthlete(athlete.getId());
        long first = rowFor(w).getGarminWorkoutId();
        clearInvocations(garminClient);

        w.setTitle("Footing vallonné");
        workoutRepository.saveAndFlush(w);
        syncService.syncAthlete(athlete.getId());

        verify(garminClient).deleteWorkout("acc", first);
        verify(garminClient).createWorkout(eq("acc"), argThat(named("Footing vallonné")));
        assertThat(rowFor(w).getGarminWorkoutId()).isNotEqualTo(first);
    }

    @Test
    void uneSeanceDeplaceeEstReplanifieeAuNouveauJour() {
        Workout w = workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        syncService.syncAthlete(athlete.getId());
        clearInvocations(garminClient);

        w.setScheduledDate(today.plusDays(4));
        workoutRepository.saveAndFlush(w);
        syncService.syncAthlete(athlete.getId());

        GarminWorkoutSync row = rowFor(w);
        verify(garminClient).scheduleWorkout("acc", row.getGarminWorkoutId(), today.plusDays(4));
        assertThat(row.getScheduledDate()).isEqualTo(today.plusDays(4));
    }

    @Test
    void uneSeanceSupprimeeParLeCoachDisparaitDeLaMontre() {
        Workout w = workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        syncService.syncAthlete(athlete.getId());
        long garminId = rowFor(w).getGarminWorkoutId();
        UUID workoutId = w.getId();
        clearInvocations(garminClient);

        workoutRepository.delete(w);
        workoutRepository.flush();
        em.clear(); // la base a mis workout_id à NULL ; on relit comme une nouvelle requête

        syncService.syncAthlete(athlete.getId());

        verify(garminClient).deleteWorkout("acc", garminId);
        assertThat(syncRepository.findByAthleteId(athlete.getId()))
                .noneMatch(r -> workoutId.equals(r.getWorkoutId()) || Long.valueOf(garminId).equals(r.getGarminWorkoutId()));
    }

    @Test
    void niLeRenfoNiLeReposNiUneSeanceFaiteNePartent() {
        workout("Renfo", WorkoutType.STRENGTH, today.plusDays(1));
        workout("Repos", WorkoutType.REST, today.plusDays(2));
        Workout done = workout("Déjà faite", WorkoutType.ENDURANCE, today);
        done.setStatus(WorkoutStatus.COMPLETED);
        workoutRepository.saveAndFlush(done);

        syncService.syncAthlete(athlete.getId());

        verify(garminClient, never()).createWorkout(anyString(), argThat(named("Renfo")));
        verify(garminClient, never()).createWorkout(anyString(), argThat(named("Repos")));
        verify(garminClient, never()).createWorkout(anyString(), argThat(named("Déjà faite")));
    }

    @Test
    void auDelaDeLHorizonRienNePart() {
        workout("Lointaine", WorkoutType.ENDURANCE, today.plusDays(GarminWorkoutSyncService.WINDOW_DAYS));
        syncService.syncAthlete(athlete.getId());
        verify(garminClient, never()).createWorkout(anyString(), argThat(named("Lointaine")));
    }

    @Test
    void sansLaPermissionWorkoutImportRienNePart() {
        connect(List.of(GarminService.ACTIVITY_EXPORT));
        workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));

        var result = syncService.syncAthlete(athlete.getId());

        assertThat(result.skipped()).isTrue();
        verify(garminClient, never()).createWorkout(anyString(), any());
    }

    @Test
    void unEnvoiEnEchecEstNoteEtRetenteAuPassageSuivant() {
        Workout w = workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(garminClient).createWorkout(anyString(), argThat(named("Footing")));

        syncService.syncAthlete(athlete.getId());
        assertThat(rowFor(w).getLastError()).contains("400");

        doReturn(4242L).when(garminClient).createWorkout(anyString(), argThat(named("Footing")));
        syncService.syncAthlete(athlete.getId());

        assertThat(rowFor(w).getGarminWorkoutId()).isEqualTo(4242L);
        assertThat(rowFor(w).getLastError()).isNull();
    }

    @Test
    void seDeconnecterRetireDeLaMontreLesSeancesAVenir() {
        Workout w = workout("Footing", WorkoutType.ENDURANCE, today.plusDays(1));
        syncService.syncAthlete(athlete.getId());
        long garminId = rowFor(w).getGarminWorkoutId();
        when(garminClient.deleteRegistration("acc")).thenReturn(true);

        garminService.disconnectForAthlete(athlete.getId());

        verify(garminClient).deleteWorkout("acc", garminId);
        verify(garminClient, atLeastOnce()).deleteRegistration("acc");
        assertThat(syncRepository.findByAthleteId(athlete.getId())).isEmpty();
    }

    // --- Renforcement ---

    @Test
    void uneSeanceDeRenfoPartEnStrengthTraining() throws Exception {
        var ss = strength("Renfo bas du corps", today.plusDays(3));

        syncService.syncAthlete(athlete.getId());

        verify(garminClient).createWorkout(eq("acc"), argThat(m -> m != null
                && "Renfo bas du corps".equals(m.get("workoutName"))
                && "STRENGTH_TRAINING".equals(m.get("sport"))));
        GarminWorkoutSync row = syncRepository.findByAthleteId(athlete.getId()).stream()
                .filter(r -> ss.getId().equals(r.getStrengthSessionId())).findFirst().orElseThrow();
        verify(garminClient).scheduleWorkout("acc", row.getGarminWorkoutId(), today.plusDays(3));
        assertThat(row.getWorkoutId()).isNull();
    }

    @Test
    void uneSeanceDeRenfoFaiteOuSupprimeeQuitteLaMontre() throws Exception {
        var faite = strength("Renfo A", today.plusDays(1));
        var supprimee = strength("Renfo B", today.plusDays(2));
        syncService.syncAthlete(athlete.getId());
        long idA = rowForStrength(faite.getId()).getGarminWorkoutId();
        long idB = rowForStrength(supprimee.getId()).getGarminWorkoutId();
        clearInvocations(garminClient);

        faite.setCompleted(true);
        strengthRepository.saveAndFlush(faite);
        strengthRepository.delete(supprimee);
        strengthRepository.flush();
        em.clear();
        syncService.syncAthlete(athlete.getId());

        verify(garminClient).deleteWorkout("acc", idA);
        verify(garminClient).deleteWorkout("acc", idB);
    }

    // --- Outils ---

    private com.coachrun.entity.ScheduledStrengthSession strength(String title, LocalDate date) throws Exception {
        var p = new com.coachrun.dto.strength.StrengthPrescription(null, 60.0, 60.0, null, null, null, null, null,
                null, null, 3, com.coachrun.entity.enums.VolumeType.REPS, 8, null, null, null, null, null, null,
                null, null, null, 90, 90, null);
        var structure = new com.coachrun.dto.strength.StrengthStructure(List.of(new com.coachrun.dto.strength.StrengthBlock(
                "b1", com.coachrun.entity.enums.BlockType.PRINCIPAL, com.coachrun.entity.enums.BlockFormat.CLASSIQUE,
                null, null, null, null,
                List.of(new com.coachrun.dto.strength.StrengthExerciseItem(null, "Squat arrière", null, p, null, null)))));
        var ss = new com.coachrun.entity.ScheduledStrengthSession();
        ss.setClub(athlete.getClub());
        ss.setAthlete(athlete);
        ss.setTitle(title);
        ss.setSessionSnapshot(objectMapper.writeValueAsString(structure));
        ss.setScheduledDate(date);
        return strengthRepository.saveAndFlush(ss);
    }

    private GarminWorkoutSync rowForStrength(UUID id) {
        return syncRepository.findByAthleteId(athlete.getId()).stream()
                .filter(r -> id.equals(r.getStrengthSessionId())).findFirst().orElseThrow();
    }

    private void connect(List<String> permissions) {
        DeviceConnection c = connectionRepository
                .findByAthleteIdAndProvider(athlete.getId(), DeviceProvider.GARMIN)
                .orElseGet(DeviceConnection::new);
        c.setAthlete(athlete);
        c.setProvider(DeviceProvider.GARMIN);
        c.setProviderAthleteId("g-user");
        c.setAccessToken("acc");
        c.setRefreshToken("ref");
        c.setExpiresAt(Instant.now().getEpochSecond() + 86400);
        c.setScope(String.join(",", permissions));
        connectionRepository.saveAndFlush(c);
    }

    private Workout workout(String title, WorkoutType type, LocalDate date) {
        Workout w = new Workout();
        w.setClub(athlete.getClub());
        w.setAthlete(athlete);
        w.setStatus(WorkoutStatus.PLANNED);
        w.setScheduledDate(date);
        w.setType(type);
        w.setTitle(title);
        w.setTargetDistanceM(8000);
        return workoutRepository.saveAndFlush(w);
    }

    private GarminWorkoutSync rowFor(Workout w) {
        return syncRepository.findByAthleteId(athlete.getId()).stream()
                .filter(r -> w.getId().equals(r.getWorkoutId()))
                .findFirst().orElseThrow();
    }

    private static org.mockito.ArgumentMatcher<Map<String, Object>> named(String name) {
        return m -> m != null && name.equals(m.get("workoutName"));
    }
}
