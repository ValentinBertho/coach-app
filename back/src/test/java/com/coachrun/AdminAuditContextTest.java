package com.coachrun;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le contexte que le journal d'audit ne portait pas : de quel droit, qui vraiment, par où.
 *
 * <p><b>Ce que ces tests protègent.</b> Le journal répondait à « qui, quoi, quand, sur quoi ».
 * Manquaient les trois renseignements qu'on cherche le jour où l'on ouvre un journal — et ce jour
 * est toujours après coup, donc trop tard pour les instrumenter.</p>
 *
 * <p>Le plus grave était l'emprunt d'identité. {@code ImpersonationService} le documentait :
 * « écrire depuis un compte emprunté laisse une trace indiscernable de celle de son titulaire ».
 * L'ouverture de la session était consignée, la suite ne l'était pas.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminAuditContextTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private com.coachrun.repository.UserRepository userRepository;
    @Autowired private com.coachrun.config.ImpersonationAuditFilter impersonationAuditFilter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        // Le filtre est monté explicitement : MockMvc n'embarque que la chaîne de sécurité, pas
        // les filtres @Component que Spring Boot enregistre tout seul en production (même
        // convention que LogContextFilter). Il est ajouté APRÈS springSecurity() pour se
        // retrouver à l'intérieur de la chaîne, là où le principal existe encore au retour.
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .addFilters(impersonationAuditFilter)
                .build();
    }

    // --- 1. De quel droit : le rôle de l'acteur est figé -------------------------------------

    @Test
    void theActorRoleIsFrozenOnTheEntry() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);
        revokeSessions(admin, userIdOf(DemoSeedService.ATHLETE_EMAIL));

        JsonNode entry = latestOfAction(admin, "USER_SESSIONS_REVOKED");
        assertThat(entry).as("le geste est bien consigné").isNotNull();
        assertThat(entry.get("actorRole").asText())
                .as("le rôle qu'il avait à cet instant, pas celui qu'il aura demain")
                .isEqualTo("PLATFORM_ADMIN");
    }

    // --- 2. Par où : l'appel HTTP et le navigateur -------------------------------------------

    /** Le navigateur était enregistré depuis l'origine sans jamais ressortir de l'API. */
    @Test
    void theCallItselfIsRecordedAndExposed() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);
        revokeSessions(admin, userIdOf(DemoSeedService.ATHLETE_EMAIL));

        JsonNode entry = latestOfAction(admin, "USER_SESSIONS_REVOKED");
        assertThat(entry.get("requestMethod").asText()).isEqualTo("POST");
        assertThat(entry.get("requestPath").asText()).contains("/admin/users/");
        assertThat(entry.get("userAgent").asText()).isEqualTo(TEST_AGENT);
    }

    /** Le chemin est celui de la route, jamais la chaîne de requête, qui porte des saisies. */
    @Test
    void theRecordedPathCarriesNoQueryString() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);
        revokeSessions(admin, userIdOf(DemoSeedService.ATHLETE_EMAIL));

        assertThat(latestOfAction(admin, "USER_SESSIONS_REVOKED").get("requestPath").asText())
                .doesNotContain("?");
    }

    // --- 3. Qui vraiment : l'écriture en session empruntée -----------------------------------

    /**
     * Le cœur du sujet. Un administrateur emprunte le compte d'un athlète et écrit : la trace doit
     * exister, et nommer l'administrateur.
     */
    @Test
    void aWriteFromABorrowedSessionIsRecordedWithTheAdminBehindIt() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);
        String borrowed = impersonate(admin, userIdOf(DemoSeedService.ATHLETE_EMAIL));

        // Une écriture ordinaire de l'athlète, faite depuis la session empruntée.
        mvc.perform(post("/me/messages")
                        .header("Authorization", borrowed)
                        .header("User-Agent", TEST_AGENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Ecrit en emprunt"))))
                .andExpect(status().isCreated());

        JsonNode entry = latestOfAction(admin, "IMPERSONATED_WRITE");
        assertThat(entry).as("l'écriture en session empruntée laisse une trace").isNotNull();
        assertThat(entry.get("impersonatorEmail").asText())
                .as("et elle nomme l'administrateur qui était derrière")
                .isEqualTo(DemoSeedService.ADMIN_EMAIL);
        assertThat(entry.get("actorEmail").asText())
                .as("sans cesser de dire sous quelle identité le geste a été fait")
                .isEqualTo(DemoSeedService.ATHLETE_EMAIL);
        assertThat(entry.get("sensitive").asBoolean())
                .as("un geste en session empruntée est toujours signalé").isTrue();
        assertThat(entry.get("summary").asText()).contains("/me/messages");
    }

    /** Le corps de la requête n'entre jamais dans le journal : il porte du ressenti. */
    @Test
    void theRequestBodyNeverReachesTheJournal() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);
        String borrowed = impersonate(admin, userIdOf(DemoSeedService.ATHLETE_EMAIL));

        mvc.perform(post("/me/messages")
                        .header("Authorization", borrowed)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("body", "mon genou me fait souffrir"))))
                .andExpect(status().isCreated());

        assertThat(latestOfAction(admin, "IMPERSONATED_WRITE").toString())
                .doesNotContain("genou");
    }

    /**
     * Lire n'est pas écrire. L'impersonation sert d'abord à voir l'application comme
     * l'utilisateur : journaliser chaque écran noierait les écritures sous les lectures.
     */
    @Test
    void readingFromABorrowedSessionIsNotRecorded() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);
        String borrowed = impersonate(admin, userIdOf(DemoSeedService.ATHLETE_EMAIL));
        long before = countOfAction(admin, "IMPERSONATED_WRITE");

        mvc.perform(get("/me/messages").header("Authorization", borrowed))
                .andExpect(status().isOk());

        assertThat(countOfAction(admin, "IMPERSONATED_WRITE"))
                .as("aucune trace pour une simple consultation").isEqualTo(before);
    }

    /** Une session ordinaire n'est pas un emprunt : rien ne doit être consigné. */
    @Test
    void anOrdinarySessionWritesNothingToTheJournal() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);
        String athlete = bearer(DemoSeedService.ATHLETE_EMAIL);
        long before = countOfAction(admin, "IMPERSONATED_WRITE");

        mvc.perform(post("/me/messages")
                        .header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Bonjour coach"))))
                .andExpect(status().isCreated());

        assertThat(countOfAction(admin, "IMPERSONATED_WRITE"))
                .as("l'athlète chez lui n'est surveillé par personne").isEqualTo(before);
    }

    /**
     * Chercher l'adresse d'un administrateur doit ramener aussi ce qu'il a fait sous une autre
     * identité — c'est précisément ce qu'on cherche quand on tape son nom.
     */
    @Test
    void searchingTheAdminFindsWhatHeDidUnderABorrowedIdentity() throws Exception {
        String admin = bearer(DemoSeedService.ADMIN_EMAIL);
        String borrowed = impersonate(admin, userIdOf(DemoSeedService.ATHLETE_EMAIL));
        mvc.perform(post("/me/messages")
                        .header("Authorization", borrowed)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Coucou"))))
                .andExpect(status().isCreated());

        JsonNode page = objectMapper.readTree(mvc.perform(get("/admin/audit")
                        .param("q", DemoSeedService.ADMIN_EMAIL)
                        .param("size", "100")
                        .header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        boolean found = false;
        for (JsonNode row : page.get("content")) {
            if ("IMPERSONATED_WRITE".equals(row.get("action").asText())) {
                found = true;
            }
        }
        assertThat(found).as("la recherche par administrateur couvre l'emprunteur").isTrue();
    }

    /**
     * Le filtre doit rester <b>à l'intérieur</b> de la chaîne de sécurité.
     *
     * <p>Il lit le principal <b>après</b> {@code chain.doFilter()}. Enregistré plus tôt que la
     * chaîne de sécurité (ordre plus petit), il se retrouverait à l'extérieur : Spring Security
     * aurait déjà vidé le {@code SecurityContext} au retour, le principal serait nul, et plus
     * aucune écriture en session empruntée ne serait consignée — <b>sans que rien n'échoue</b>.
     * C'est le genre de panne qu'on ne découvre qu'en cherchant une trace qui n'existe pas.</p>
     */
    @Test
    void theFilterRunsInsideTheSecurityChain() {
        int security = org.springframework.boot.autoconfigure.security.SecurityProperties.DEFAULT_FILTER_ORDER;
        assertThat(com.coachrun.config.ImpersonationAuditFilter.ORDER)
                .as("un ordre plus grand que celui de la chaîne de sécurité la place à l'intérieur")
                .isGreaterThan(security);
    }

    // --- Utilitaires --------------------------------------------------------------------------

    /** Agent volontairement reconnaissable : on vérifie qu'il ressort tel quel. */
    private static final String TEST_AGENT = "Mozilla/5.0 (AuditTest) Chrome/120.0";

    private String bearer(String email) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + auth.get("accessToken").asText();
    }

    private String userIdOf(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId().toString();
    }

    private String impersonate(String adminBearer, String targetUserId) throws Exception {
        JsonNode session = objectMapper.readTree(mvc.perform(
                        post("/admin/users/{id}/impersonate", targetUserId)
                                .header("Authorization", adminBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + session.get("accessToken").asText();
    }

    private void revokeSessions(String adminBearer, String targetUserId) throws Exception {
        mvc.perform(post("/admin/users/{id}/revoke-sessions", targetUserId)
                        .header("Authorization", adminBearer)
                        .header("User-Agent", TEST_AGENT))
                .andExpect(status().isOk());
    }

    /**
     * Combien de lignes portent cette action.
     *
     * <p>Les tests négatifs comparent un avant et un après plutôt que de lire un état global :
     * une assertion « il n'y a rien » dépend de tout ce que les autres méthodes ont écrit, et
     * échoue selon l'ordre d'exécution — ce qui fait douter du code, jamais du test.</p>
     */
    private long countOfAction(String adminBearer, String action) throws Exception {
        JsonNode page = objectMapper.readTree(mvc.perform(get("/admin/audit")
                        .param("action", action)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return page.get("totalElements").asLong();
    }

    /** La ligne la plus récente portant cette action, ou null. */
    private JsonNode latestOfAction(String adminBearer, String action) throws Exception {
        JsonNode page = objectMapper.readTree(mvc.perform(get("/admin/audit")
                        .param("action", action)
                        .header("Authorization", adminBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode content = page.get("content");
        return (content != null && content.size() > 0) ? content.get(0) : null;
    }
}
