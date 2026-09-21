package com.coachrun;

import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditScope;
import com.coachrun.repository.AdminAuditLogRepository;
import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le journal consigne aussi ce que font les utilisateurs — pas seulement les administrateurs.
 *
 * <p><b>Ce que ces tests protègent.</b> Avant, un journal d'audit ne savait rien répondre à
 * « qui s'est connecté depuis cette adresse ? », « quand ce compte a-t-il changé de mot de
 * passe ? », « qui a effacé les tests de cet athlète ? ». Les gestes existaient, leurs traces
 * n'existaient pas ailleurs que dans des lignes {@code INFO} perdues à la rotation des journaux
 * applicatifs.</p>
 *
 * <p><b>La transaction du test est ce qui rend l'échec de connexion vérifiable.</b> Les appels
 * passés par MockMvc rejoignent la transaction du test, qui est annulée à la fin : un refus de
 * connexion la marque d'ailleurs pour annulation dès qu'il lève. Les traces d'accès, elles,
 * s'écrivent en transaction séparée — elles sont donc là, lisibles, alors même que la transaction
 * qui les entoure ne sera jamais validée. C'est exactement la propriété que le journal doit avoir
 * en production.</p>
 *
 * <p>Chaque test compare un avant et un après plutôt que de lire un état global : la base de la
 * suite est partagée, et une assertion « il y a exactement une ligne » dépendrait de ce que les
 * autres classes ont écrit avant.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.transaction.annotation.Transactional
class UserActivityAuditTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private AdminAuditLogRepository auditRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // --- Sécurité des accès -------------------------------------------------------------------

    @Test
    void aSuccessfulLoginIsRecordedWithItsAuthor() throws Exception {
        long before = countOfAction(AdminAuditAction.LOGIN_SUCCEEDED);

        login(DemoSeedService.COACH_EMAIL, DemoSeedService.DEMO_PASSWORD).andExpect(status().isOk());

        assertThat(countOfAction(AdminAuditAction.LOGIN_SUCCEEDED)).isEqualTo(before + 1);
        var entry = latestOfAction(AdminAuditAction.LOGIN_SUCCEEDED);
        assertThat(entry.getActorEmail()).isEqualTo(DemoSeedService.COACH_EMAIL);
        assertThat(entry.getActorRole()).as("de quel droit, figé à l'instant du geste").isNotNull();
    }

    /**
     * Le test central de l'écriture en transaction séparée.
     *
     * <p>Une connexion refusée lève, donc annule la transaction courante. Consignée dans cette
     * transaction, la trace de l'échec serait partie avec — et l'échec de connexion est
     * exactement la ligne qu'on vient chercher dans un journal de sécurité.</p>
     */
    @Test
    void aFailedLoginSurvivesTheRollbackThatRefusesIt() throws Exception {
        long before = countOfAction(AdminAuditAction.LOGIN_FAILED);

        login(DemoSeedService.COACH_EMAIL, "ce-n-est-pas-le-bon")
                .andExpect(status().isUnauthorized());

        assertThat(countOfAction(AdminAuditAction.LOGIN_FAILED))
                .as("le refus est consigné malgré l'annulation de la transaction")
                .isEqualTo(before + 1);
        assertThat(latestOfAction(AdminAuditAction.LOGIN_FAILED).getSummary())
                .isEqualTo("Mot de passe incorrect");
    }

    /** Une adresse inconnue laisse une trace : c'est ce qui rend un balayage visible. */
    @Test
    void aLoginOnAnUnknownAddressIsRecordedToo() throws Exception {
        long before = countOfAction(AdminAuditAction.LOGIN_FAILED);

        login("personne@nulle-part.test", "peu importe").andExpect(status().isUnauthorized());

        assertThat(countOfAction(AdminAuditAction.LOGIN_FAILED)).isEqualTo(before + 1);
        var entry = latestOfAction(AdminAuditAction.LOGIN_FAILED);
        assertThat(entry.getActorUserId()).as("aucun acteur : le compte n'existe pas").isNull();
        assertThat(entry.getTargetLabel())
                .as("l'adresse visée, sans quoi la ligne ne se relie à rien")
                .isEqualTo("personne@nulle-part.test");
        assertThat(entry.getSummary()).isEqualTo("Aucun compte pour cette adresse");
    }

    /** Le mot de passe présenté n'entre jamais au journal, sous aucune forme. */
    @Test
    void thePresentedPasswordNeverReachesTheJournal() throws Exception {
        String secret = "Sup3rS3cret-" + java.util.UUID.randomUUID();

        login(DemoSeedService.COACH_EMAIL, secret).andExpect(status().isUnauthorized());

        assertThat(latestOfAction(AdminAuditAction.LOGIN_FAILED).toString()).doesNotContain(secret);
        assertThat(auditRepository.findTop10ByActionInOrderByOccurredAtDesc(
                        AdminAuditAction.inScope(AdminAuditScope.SECURITY)).toString())
                .doesNotContain(secret);
    }

    // --- Données personnelles -----------------------------------------------------------------

    /**
     * L'export RGPD vient d'un service en <b>lecture seule</b>. Une trace jointe à cette
     * transaction ne serait jamais vidée en base par Hibernate — sans la moindre erreur pour le
     * signaler. Le journal se croirait complet ; ce test dit qu'il l'est.
     */
    @Test
    void aDataExportIsRecordedAlthoughItOnlyReads() throws Exception {
        long before = countOfAction(AdminAuditAction.PERSONAL_DATA_EXPORTED);

        mvc.perform(get("/me/export").header("Authorization", bearer(DemoSeedService.ATHLETE_EMAIL)))
                .andExpect(status().isOk());

        assertThat(countOfAction(AdminAuditAction.PERSONAL_DATA_EXPORTED))
                .as("l'exercice d'un droit laisse une trace, même sans écriture métier")
                .isEqualTo(before + 1);
    }

    /** Retirer son consentement santé est consigné — sans recopier ce qui vient d'être effacé. */
    @Test
    void withdrawingHealthConsentIsRecordedWithoutAnyHealthValue() throws Exception {
        long before = countOfAction(AdminAuditAction.HEALTH_CONSENT_WITHDRAWN);

        mvc.perform(post("/me/consent/withdraw")
                        .header("Authorization", bearer(DemoSeedService.ATHLETE_EMAIL)))
                .andExpect(status().isNoContent());

        assertThat(countOfAction(AdminAuditAction.HEALTH_CONSENT_WITHDRAWN)).isEqualTo(before + 1);
        var entry = latestOfAction(AdminAuditAction.HEALTH_CONSENT_WITHDRAWN);
        assertThat(entry.getSummary())
                .as("le résumé dit ce qui a été effacé, jamais ce qu'il y avait dedans")
                .contains("art. 7-3");
        assertThat(entry.getAction().sensitive())
                .as("un consentement retiré efface des données qui ne reviennent pas").isTrue();
    }

    // --- Ce que l'ouverture du journal ne devait PAS abîmer ------------------------------------

    /**
     * Le bandeau du tableau de bord montre les dix dernières actions. Sans restriction de famille,
     * il ne montrerait plus que des connexions — et cesserait de servir à ce pour quoi il existe.
     */
    @Test
    void theDashboardBannerStillShowsAdministrationOnly() throws Exception {
        login(DemoSeedService.ATHLETE_EMAIL, DemoSeedService.DEMO_PASSWORD).andExpect(status().isOk());
        login(DemoSeedService.COACH_EMAIL, DemoSeedService.DEMO_PASSWORD).andExpect(status().isOk());

        JsonNode overview = objectMapper.readTree(mvc.perform(get("/admin/overview")
                        .header("Authorization", bearer(DemoSeedService.ADMIN_EMAIL)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        for (JsonNode row : overview.get("recentActions")) {
            assertThat(row.get("scope").asText())
                    .as("le bandeau reste celui de l'administration")
                    .isEqualTo(AdminAuditScope.ADMINISTRATION.name());
        }
    }

    /** Même raison pour le compteur : son libellé dit « actions d'administration ». */
    @Test
    void theWeeklyAdminCounterIgnoresLogins() throws Exception {
        long before = adminActions7d();

        login(DemoSeedService.COACH_EMAIL, DemoSeedService.DEMO_PASSWORD).andExpect(status().isOk());
        login(DemoSeedService.ATHLETE_EMAIL, DemoSeedService.DEMO_PASSWORD).andExpect(status().isOk());

        assertThat(adminActions7d())
                .as("des connexions ne sont pas des gestes d'administration")
                .isEqualTo(before);
    }

    /** Le filtre de famille isole bien les accès du reste du journal. */
    @Test
    void theScopeFilterSeparatesAccessFromAdministration() throws Exception {
        login(DemoSeedService.COACH_EMAIL, DemoSeedService.DEMO_PASSWORD).andExpect(status().isOk());

        JsonNode page = objectMapper.readTree(mvc.perform(get("/admin/audit")
                        .param("scope", AdminAuditScope.SECURITY.name())
                        .param("size", "100")
                        .header("Authorization", bearer(DemoSeedService.ADMIN_EMAIL)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(page.get("content")).isNotEmpty();
        for (JsonNode row : page.get("content")) {
            assertThat(row.get("scope").asText()).isEqualTo(AdminAuditScope.SECURITY.name());
        }
    }

    /**
     * La purge ne touche qu'aux traces d'accès. Un geste d'administration de la même ancienneté
     * reste : c'est lui qu'on vient chercher des années plus tard.
     */
    @Test
    void thePurgeSparesEverythingButAccessTraces() {
        java.time.Instant old = java.time.Instant.now().minus(java.time.Duration.ofDays(400));
        var login = save(AdminAuditAction.LOGIN_SUCCEEDED, old);
        var deletion = save(AdminAuditAction.USER_DELETED, old);

        auditRepository.deleteByActionInAndOccurredAtBefore(
                AdminAuditAction.inScope(AdminAuditScope.SECURITY),
                java.time.Instant.now().minus(java.time.Duration.ofDays(365)));

        assertThat(auditRepository.findById(login.getId())).as("la trace d'accès part").isEmpty();
        assertThat(auditRepository.findById(deletion.getId()))
                .as("la suppression de compte reste").isPresent();
    }

    // --- Utilitaires --------------------------------------------------------------------------

    private com.coachrun.entity.AdminAuditLog save(AdminAuditAction action, java.time.Instant when) {
        com.coachrun.entity.AdminAuditLog e = new com.coachrun.entity.AdminAuditLog();
        e.setAction(action);
        e.setTargetType(com.coachrun.entity.enums.AdminAuditTarget.USER);
        e.setOccurredAt(when);
        return auditRepository.save(e);
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password)
            throws Exception {
        return mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        java.util.Map.of("email", email, "password", password))));
    }

    private String bearer(String email) throws Exception {
        JsonNode auth = objectMapper.readTree(
                login(email, DemoSeedService.DEMO_PASSWORD).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString());
        return "Bearer " + auth.get("accessToken").asText();
    }

    private long countOfAction(AdminAuditAction action) {
        return auditRepository.countByActionInAndOccurredAtAfter(
                java.util.List.of(action), java.time.Instant.now().minus(java.time.Duration.ofDays(1)));
    }

    private com.coachrun.entity.AdminAuditLog latestOfAction(AdminAuditAction action) {
        var rows = auditRepository.findTop10ByActionInOrderByOccurredAtDesc(java.util.List.of(action));
        assertThat(rows).as("aucune ligne pour " + action).isNotEmpty();
        return rows.get(0);
    }

    private long adminActions7d() throws Exception {
        JsonNode overview = objectMapper.readTree(mvc.perform(get("/admin/overview")
                        .header("Authorization", bearer(DemoSeedService.ADMIN_EMAIL)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return overview.get("engagement").get("adminActions7d").asLong();
    }
}
