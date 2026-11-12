package com.coachrun;

import com.coachrun.repository.AthleteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La recherche d'athlètes filtrée par visibilité, sur un VRAI PostgreSQL.
 *
 * <h2>Pourquoi ce test existe</h2>
 *
 * <p>{@code searchVisibleTo} ajoute deux sous-requêtes corrélées et un paramètre à une requête qui
 * en portait déjà trois neutralisés par {@code (:param is null or …)}. C'est exactement la forme
 * qui a déjà piégé ce produit : un paramètre sans colonne en face laisse PostgreSQL sans moyen
 * d'en déduire le type et fait échouer la requête — « could not determine data type of parameter »
 * (SQLSTATE 42P18) — là où H2, sur lequel tourne toute la suite, l'accepte sans broncher. L'écran
 * d'audit avait ainsi rendu 500 en production avec une suite entièrement verte
 * (cf. {@code AdminAuditOnPostgresTest}).</p>
 *
 * <p>On appelle donc la requête telle que l'écran l'appelle, avec les filtres facultatifs à
 * {@code null} : c'est le cas où l'inférence de type est la plus fragile.</p>
 *
 * <p>Ne s'exécute qu'avec {@code -Dpgtest=true} et un PostgreSQL joignable. La CI le rejoue.</p>
 */
@SpringBootTest
@ActiveProfiles("pgtest")
@EnabledIfSystemProperty(named = "pgtest", matches = "true")
class AthleteVisibilityQueryOnPostgresTest {

    @Autowired private AthleteRepository athleteRepository;

    /** L'appel exact de l'écran à son ouverture : un club, un observateur, aucun autre filtre. */
    @Test
    void theVisibilityFilteredSearchRunsOnPostgres() {
        assertThatCode(() -> athleteRepository.searchVisibleTo(
                UUID.randomUUID(), null, null, "", UUID.randomUUID(), PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }

    /** Et avec les filtres renseignés, l'autre branche de chaque `is null`. */
    @Test
    void theSameSearchRunsWithEveryFilterSet() {
        assertThatCode(() -> athleteRepository.searchVisibleTo(
                UUID.randomUUID(), com.coachrun.entity.enums.AthleteStatus.ACTIVE,
                UUID.randomUUID(), "dupont", UUID.randomUUID(), PageRequest.of(0, 20)))
                .doesNotThrowAnyException();
    }
}
