package com.coachrun;

import com.coachrun.entity.AdminAuditLog;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.repository.AdminAuditLogRepository;
import com.coachrun.service.AdminAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Le journal d'audit, sur un VRAI PostgreSQL.
 *
 * <p><b>Pourquoi ce test ne peut pas tourner sur H2.</b> La requête de recherche neutralise chaque
 * filtre par un {@code (:param is null or …)}. Dans cette forme, le paramètre apparaît une fois
 * <b>seul</b>, sans colonne en face : PostgreSQL n'a alors aucun moyen d'en déduire le type et
 * refuse la requête — « could not determine data type of parameter » (SQLSTATE 42P18). H2, sur
 * lequel tourne toute la suite, l'accepte sans broncher.</p>
 *
 * <p>L'écran d'administration renvoyait donc 500 en production avec une suite de tests
 * entièrement verte. C'est exactement le défaut que {@code MessageRepository} documente déjà pour
 * la messagerie ; il restait à traiter ici.</p>
 *
 * <p>Ne s'exécute qu'avec {@code -Dpgtest=true} et un PostgreSQL joignable (cf.
 * {@code application-pgtest.yml}).</p>
 */
@SpringBootTest
@ActiveProfiles("pgtest")
@EnabledIfSystemProperty(named = "pgtest", matches = "true")
class AdminAuditOnPostgresTest {

    @Autowired
    private AdminAuditService auditService;
    @Autowired
    private AdminAuditLogRepository repository;

    /** L'appel exact de l'écran à son ouverture : « 30 derniers jours », aucun autre filtre. */
    @Test
    void theDefaultScreenLoads() {
        assertThatCode(() -> auditService.search(null, null, null, null, 30, null,
                PageRequest.of(0, 50)))
                .doesNotThrowAnyException();
    }

    /** Sans aucun filtre : tous les paramètres nuls à la fois, le cas le plus exposé. */
    @Test
    void noFilterAtAllLoads() {
        assertThatCode(() -> auditService.search(null, null, null, null, null, null,
                PageRequest.of(0, 50)))
                .doesNotThrowAnyException();
    }

    /**
     * Le filtre de date filtre <b>vraiment</b>.
     *
     * <p>Sans cette assertion, remplacer {@code (:since is null or …)} par un {@code coalesce}
     * aurait pu neutraliser la borne en silence : l'écran aurait cessé de renvoyer 500 tout en
     * affichant l'historique entier sous l'étiquette « 30 derniers jours ».</p>
     */
    @Test
    void theDateFilterStillExcludesWhatIsTooOld() {
        String marker = "borne-" + java.util.UUID.randomUUID();
        repository.save(entry(marker + "-recent", java.time.Instant.now().minus(java.time.Duration.ofHours(2))));
        repository.save(entry(marker + "-ancien", java.time.Instant.now().minus(java.time.Duration.ofDays(40))));

        var lastDay = auditService.search(null, null, null, null, 1, marker, PageRequest.of(0, 50));
        assertThat(lastDay.content()).as("un seul des deux tient dans les dernières 24 h")
                .hasSize(1);
        assertThat(lastDay.content().get(0).targetLabel()).contains("recent");

        var everything = auditService.search(null, null, null, null, null, marker, PageRequest.of(0, 50));
        assertThat(everything.content()).as("sans borne, les deux remontent").hasSize(2);
    }

    private AdminAuditLog entry(String label, java.time.Instant occurredAt) {
        AdminAuditLog e = new AdminAuditLog();
        e.setAction(AdminAuditAction.USER_UPDATED);
        e.setTargetType(AdminAuditTarget.USER);
        e.setTargetLabel(label);
        e.setOccurredAt(occurredAt);
        return e;
    }

    /** Chaque filtre posé isolément : aucun ne doit dépendre de la présence des autres. */
    @Test
    void eachFilterWorksOnItsOwn() {
        assertThatCode(() -> auditService.search(AdminAuditAction.USER_DELETED, null, null, null,
                null, null, PageRequest.of(0, 50))).doesNotThrowAnyException();
        assertThatCode(() -> auditService.search(null, AdminAuditTarget.CLUB, null, null,
                null, null, PageRequest.of(0, 50))).doesNotThrowAnyException();
        assertThatCode(() -> auditService.search(null, null, java.util.UUID.randomUUID(), null,
                null, null, PageRequest.of(0, 50))).doesNotThrowAnyException();
        assertThatCode(() -> auditService.search(null, null, null, java.util.UUID.randomUUID(),
                null, null, PageRequest.of(0, 50))).doesNotThrowAnyException();
        assertThatCode(() -> auditService.search(null, null, null, null, null, "dupont",
                PageRequest.of(0, 50))).doesNotThrowAnyException();
    }
}
