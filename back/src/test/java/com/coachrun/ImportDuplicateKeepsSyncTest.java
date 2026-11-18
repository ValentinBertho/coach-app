package com.coachrun;

import com.coachrun.dto.request.ActivityImportRequest;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivitySport;
import com.coachrun.exception.ConflictException;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.service.ActivityService;
import com.coachrun.service.ClockService;
import com.coachrun.service.DemoSeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une synchro de montre qui croise un doublon doit garder les sorties nouvelles.
 *
 * <p>Défaut vu en production sur la synchro Strava : la boucle d'import passe le doublon
 * ({@link ConflictException}), mais l'exception sortie de {@code importActivity} marquait la
 * transaction de la synchro « à annuler ». Au commit, tout était défait (sortie nouvelle et
 * curseur) et l'athlète lisait « Une erreur interne est survenue ». Pas de {@code @Transactional}
 * sur la classe : c'est le vrai commit qu'il faut traverser.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:import-duplicate;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class ImportDuplicateKeepsSyncTest {

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ActivityService activityService;
    @Autowired private ClockService clock;
    @Autowired private TransactionTemplate tx;

    @Test
    void unDoublonPasseNAnnulePasLesSortiesNouvelles() {
        demoSeedService.seed();
        Athlete athlete = athleteRepository.findAll().stream()
                .filter(a -> a.getClub() != null).findFirst().orElseThrow();
        UUID clubId = athlete.getClub().getId();
        LocalDate day = clock.today().minusDays(40);
        String fresh = "fresh-" + UUID.randomUUID();
        String twin = "twin-" + UUID.randomUUID();

        tx.executeWithoutResult(s -> activityService.importActivity(clubId, athlete.getId(),
                request(twin, day, 10_000, 3_000)));

        // Comme la boucle de StravaService : une sortie nouvelle, puis un doublon passé.
        tx.executeWithoutResult(s -> {
            activityService.importActivity(clubId, athlete.getId(), request(fresh, day.plusDays(1), 8_000, 2_400));
            assertThatThrownBy(() -> activityService.importActivity(clubId, athlete.getId(),
                    request(twin, day, 10_000, 3_000))).isInstanceOf(ConflictException.class);
        });

        assertThat(activityRepository.findByAthleteIdAndSourceAndExternalId(
                athlete.getId(), ActivitySource.STRAVA, fresh)).isPresent();
    }

    private static ActivityImportRequest request(String externalId, LocalDate date, int distanceM, int durationS) {
        return new ActivityImportRequest(ActivitySource.STRAVA, externalId, date, "Footing",
                distanceM, durationS, null, null, ActivitySport.RUN, "Run", false);
    }
}
