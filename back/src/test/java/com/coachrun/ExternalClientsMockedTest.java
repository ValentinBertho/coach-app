package com.coachrun;

import com.coachrun.integration.CorosMcpClient;
import com.coachrun.integration.GarminClient;
import com.coachrun.integration.StravaClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Socle des tests qui simulent Strava, Garmin ou COROS.
 *
 * <p>Un {@code @MockBean} change la clé du contexte Spring : chaque test qui en déclarait un
 * démarrait sa propre application et rejouait toutes les migrations sur sa propre base mémoire.
 * Huit classes, huit démarrages — le gros du temps de la CI serveur. Elles déclarent désormais
 * les mêmes trois simulations et la même base : un seul contexte, partagé.</p>
 *
 * <p>La base reste séparée de celle des autres tests (un contexte qui démarre rejoue Liquibase),
 * et chaque test est annulé à la fin ({@code @Transactional}). Les simulations sont remises à zéro
 * après chaque test (comportement par défaut de {@code @MockBean}).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:external-clients;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
abstract class ExternalClientsMockedTest {

    @MockBean protected StravaClient stravaClient;
    @MockBean protected GarminClient garminClient;
    @MockBean protected CorosMcpClient corosClient;
}
