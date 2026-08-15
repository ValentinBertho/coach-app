package com.coachrun;

import com.coachrun.entity.enums.MailKind;
import com.coachrun.integration.MailTemplate.Audience;
import com.coachrun.repository.MailLogRepository;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.MailLogService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Suivi de la consommation d'e-mails : le journal, ses agrégats, et son accès réservé.
 *
 * <p>{@link MailLogService} écrit dans une transaction séparée ({@code REQUIRES_NEW}) — parce que
 * l'envoi part après le commit de l'action métier. Ses écritures survivent donc au rollback du
 * test : la table est vidée <b>elle aussi hors transaction</b>, avant et après chaque cas, sans
 * quoi les lignes d'un cas se compteraient dans le suivant.</p>
 *
 * <p>Le test reste pour le reste transactionnel comme tous les autres : le seed doit être annulé.
 * Laissé commité, il rendrait la base durablement seedée et ferait échouer, selon l'ordre
 * d'exécution, le test d'idempotence du seed lui-même.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MailLogTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MailLogService mailLogService;
    @Autowired private MailLogRepository mailLogRepository;
    @Autowired private PlatformTransactionManager txManager;

    private MockMvc mvc;
    private String adminBearer;

    @BeforeEach
    void setUp() throws Exception {
        purgeLogOutsideTransaction();
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminBearer = "Bearer " + login(DemoSeedService.ADMIN_EMAIL);
    }

    @AfterEach
    void tearDown() {
        purgeLogOutsideTransaction();
    }

    /** Vide le journal dans sa propre transaction : commitées, ses lignes ne s'annulent pas. */
    private void purgeLogOutsideTransaction() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> mailLogRepository.deleteAll());
    }

    private String login(String email) throws Exception {
        String body = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    @Test
    void statsCountTodayAndSplitSuccessesFromFailures() throws Exception {
        mailLogService.record("a@test.fr", "Invitation", MailKind.ATHLETE_INVITATION, Audience.ATHLETE, null);
        mailLogService.record("b@test.fr", "Mot de passe", MailKind.PASSWORD_RESET, Audience.COACH, null);
        mailLogService.record("c@test.fr", "Digest", MailKind.ALERT_DIGEST, Audience.COACH,
                "Daily quota exceeded");

        JsonNode stats = objectMapper.readTree(mvc.perform(
                        get("/admin/mail/stats").header("Authorization", adminBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // Deux envois acceptés, un refusé : l'échec ne compte pas dans la consommation.
        assertThat(stats.get("today").asLong()).isEqualTo(2);
        assertThat(stats.get("month").asLong()).isEqualTo(2);
        assertThat(stats.get("failed7d").asLong()).isEqualTo(1);
        assertThat(stats.get("dailyQuota").asLong()).isEqualTo(100);

        // L'histogramme couvre toute la fenêtre, jours vides compris : sans quoi trois barres
        // côte à côte laisseraient croire à trois jours consécutifs.
        assertThat(stats.get("byDay")).hasSize(30);
        JsonNode last = stats.get("byDay").get(29);
        assertThat(last.get("sent").asLong()).isEqualTo(2);
        assertThat(last.get("failed").asLong()).isEqualTo(1);
    }

    @Test
    void statsBreakDownByKindAndFlagWhatCannotBeCut() throws Exception {
        mailLogService.record("a@test.fr", "Reset", MailKind.PASSWORD_RESET, Audience.COACH, null);
        mailLogService.record("b@test.fr", "Digest", MailKind.ALERT_DIGEST, Audience.COACH, null);
        mailLogService.record("c@test.fr", "Digest", MailKind.ALERT_DIGEST, Audience.COACH, null);

        JsonNode stats = objectMapper.readTree(mvc.perform(
                        get("/admin/mail/stats").header("Authorization", adminBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode first = stats.get("byKind").get(0);
        // Trié par volume : le digest (2) devant la réinitialisation (1).
        assertThat(first.get("kind").asText()).isEqualTo("ALERT_DIGEST");
        assertThat(first.get("count").asLong()).isEqualTo(2);
        assertThat(first.get("transactional").asBoolean()).isFalse();
        assertThat(first.get("label").asText()).isEqualTo("Digest d'alertes");

        boolean resetIsTransactional = false;
        for (JsonNode row : stats.get("byKind")) {
            if ("PASSWORD_RESET".equals(row.get("kind").asText())) {
                resetIsTransactional = row.get("transactional").asBoolean();
            }
        }
        // Ce qu'on ne peut pas couper quand un plafond menace doit se voir comme tel.
        assertThat(resetIsTransactional).isTrue();
    }

    @Test
    void logListsRecentSendsWithTheirOutcome() throws Exception {
        mailLogService.record("coach@test.fr", "Invitation coach", MailKind.COACH_INVITATION,
                Audience.COACH, null);
        mailLogService.record("perdu@test.fr", "Digest", MailKind.ALERT_DIGEST, Audience.COACH,
                "Invalid recipient");

        JsonNode page = objectMapper.readTree(mvc.perform(
                        get("/admin/mail/log").header("Authorization", adminBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("content")).hasSize(2);
        JsonNode latest = page.get("content").get(0);
        assertThat(latest.get("recipient").asText()).isEqualTo("perdu@test.fr");
        assertThat(latest.get("sent").asBoolean()).isFalse();
        // Le motif distingue un plafond atteint d'une adresse invalide : deux pannes qui se
        // ressemblent depuis l'application et n'appellent pas la même réaction.
        assertThat(latest.get("errorMessage").asText()).isEqualTo("Invalid recipient");
    }

    /** Le journal porte des adresses : il n'est pas lisible par un coach. */
    @Test
    void mailStatsAreReservedToPlatformAdmins() throws Exception {
        String coach = "Bearer " + login(DemoSeedService.HEAD_COACH_EMAIL);

        mvc.perform(get("/admin/mail/stats").header("Authorization", coach))
                .andExpect(status().isForbidden());
    }
}
