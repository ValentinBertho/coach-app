package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.DemoSeedService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Une séance prescrite doit tenir en base, quelle que soit la taille de sa prescription.
 *
 * <p><b>Ce que ce test empêche de revenir.</b> Les colonnes de snapshot étaient des
 * {@code VARCHAR(20000)}. Un modèle de séance un peu fourni — beaucoup de blocs, de répétitions,
 * d'allures calculées — produisait un JSON plus long, et PostgreSQL refusait l'insertion :
 * « value too long for type character varying(20000) » (SQLSTATE 22001). La séance n'était pas
 * créée et le coach ne voyait qu'une erreur générique, l'échec ne se produisant qu'au commit,
 * après la ligne de journal annonçant la prescription.</p>
 *
 * <p>H2 ne reproduit pas ce refus de la même façon : la vérification appartient au profil
 * {@code pgtest} (cf. {@code application-pgtest.yml}), rejoué en CI sur un PostgreSQL réel.</p>
 */
@SpringBootTest
@ActiveProfiles("pgtest")
@EnabledIfSystemProperty(named = "pgtest", matches = "true")
class WorkoutSnapshotOnPostgresTest {

    /** Au-delà de l'ancienne borne : c'est exactement ce qui échouait. */
    private static final int OVER_THE_OLD_LIMIT = 25_000;

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private WorkoutRepository workoutRepository;

    @Test
    void aLargePrescriptionSnapshotIsStoredWhole() {
        demoSeedService.seed();
        Athlete athlete = athleteRepository.findAll().stream().findFirst().orElseThrow();

        String snapshot = json(OVER_THE_OLD_LIMIT);
        String paces = json(OVER_THE_OLD_LIMIT);

        Workout workout = new Workout();
        workout.setClub(athlete.getClub());
        workout.setAthlete(athlete);
        workout.setStatus(WorkoutStatus.PLANNED);
        workout.setScheduledDate(LocalDate.now());
        workout.setType(WorkoutType.INTERVALS);
        workout.setTitle("Séance très structurée");
        workout.setSessionSnapshot(snapshot);
        workout.setCalculatedPaces(paces);

        assertThatCode(() -> workoutRepository.saveAndFlush(workout))
                .as("une prescription volumineuse ne doit plus être refusée par la base")
                .doesNotThrowAnyException();

        // Relu depuis la base : la borne ne doit pas non plus tronquer en silence, ce qui
        // rendrait la séance incohérente sans que rien ne le signale.
        Workout reloaded = workoutRepository.findById(workout.getId()).orElseThrow();
        assertThat(reloaded.getSessionSnapshot()).hasSize(snapshot.length());
        assertThat(reloaded.getCalculatedPaces()).hasSize(paces.length());
    }

    /** Un JSON plausible de la longueur voulue — le contenu importe peu, la taille seule compte. */
    private static String json(int length) {
        StringBuilder sb = new StringBuilder("{\"blocks\":[");
        while (sb.length() < length) {
            sb.append("{\"repeat\":8,\"work\":\"400m\",\"pace\":\"3:30\",\"recovery\":\"1:30\"},");
        }
        return sb.append("{}]}").toString();
    }
}
