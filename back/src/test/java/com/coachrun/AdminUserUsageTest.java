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
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ce que la fiche d'un compte dit de son usage.
 *
 * <p><b>Ce que ces tests protègent.</b> La fiche disait qui était le compte et ce qu'on lui avait
 * fait ; rien de la façon dont il se sert de l'application. Or c'est ce qui décide de la réponse
 * à un ticket : conseiller d'installer l'application à quelqu'un qui n'a pas de push, ou chercher
 * un bug d'import chez quelqu'un dont Strava n'a jamais été connecté, fait perdre le même temps
 * aux deux côtés.</p>
 *
 * <p>La chaîne va de bout en bout : une requête ordinaire annonce son navigateur et sa version,
 * le filtre d'authentification les note au passage, et la fiche les rend.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminUserUsageTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private MockMvc mvc;
    private org.springframework.transaction.support.TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static final String IPHONE_AGENT =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 "
                    + "(KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    // --- Avec quoi ---------------------------------------------------------------------------

    /**
     * Le trajet complet : une requête authentifiée annonce son navigateur et sa version, et la
     * fiche de l'administrateur les restitue classés.
     *
     * <p>Sur un compte <b>créé pour ce test</b>, et c'est nécessaire : le traqueur n'écrit qu'une
     * fois par quart d'heure et par compte, et sa mémoire est un singleton partagé par toutes les
     * classes de test de la même JVM. Sur un compte du jeu de démonstration, une autre classe
     * ayant touché le même utilisateur ferait échouer celle-ci — un échec qui dépendrait de
     * l'ordre d'exécution, donc qui ferait douter du code.</p>
     */
    @Test
    void aVisitTellsTheFicheWhichDeviceAndVersionWereUsed() throws Exception {
        String email = "usage-" + java.util.UUID.randomUUID() + "@test.fr";
        String token = registerCoach(email);

        mvc.perform(get("/clubs/{c}/dashboard", clubIdOf(email))
                        .header("Authorization", "Bearer " + token)
                        .header("User-Agent", IPHONE_AGENT)
                        .header("X-App-Version", "0.4.0"))
                .andExpect(status().isOk());

        JsonNode client = usageOf(email).get("client");
        assertThat(client.get("platform").asText()).isEqualTo("Mobile");
        assertThat(client.get("os").asText()).isEqualTo("iOS");
        assertThat(client.get("browser").asText()).isEqualTo("Safari");
        assertThat(client.get("appVersion").asText()).isEqualTo("0.4.0");
    }

    /**
     * Le service worker peut laisser un téléphone des jours en arrière : c'est la première chose
     * à vérifier sur un « ça ne marche pas », et personne ne sait y répondre de mémoire.
     */
    @Test
    void aStaleClientIsFlaggedAgainstTheServedVersion() throws Exception {
        String email = "vieux-" + java.util.UUID.randomUUID() + "@test.fr";
        registerCoach(email);
        touchAs(email, IPHONE_AGENT, "0.0.1-antique");

        JsonNode client = usageOf(email).get("client");
        assertThat(client.get("outdated").asBoolean()).isTrue();
        assertThat(client.get("latestAppVersion").asText())
                .as("on dit sur quoi porte le retard").isNotBlank();
    }

    /** Sans version annoncée, on ne dit rien — accuser un client muet serait faux. */
    @Test
    void aClientThatNeverAnnouncedItselfIsNotCalledOutdated() throws Exception {
        // Compte neuf : les tests partagent la base, et un compte du jeu de démonstration
        // pourrait déjà porter une version posée par une autre méthode.
        String email = "muet-" + java.util.UUID.randomUUID() + "@test.fr";
        registerCoach(email);
        touchAs(email, IPHONE_AGENT, null);

        JsonNode client = usageOf(email).get("client");
        assertThat(client.hasNonNull("appVersion")).isFalse();
        assertThat(client.get("outdated").asBoolean()).isFalse();
        assertThat(client.get("platform").asText())
                .as("on sait tout de même sur quoi il est").isEqualTo("Mobile");
    }

    /** Un client qui n'annonce rien ne doit pas effacer ce qu'on savait de la visite précédente. */
    @Test
    void aSilentClientDoesNotErasePreviousKnowledge() throws Exception {
        String email = "silencieux-" + java.util.UUID.randomUUID() + "@test.fr";
        registerCoach(email);
        touchAs(email, IPHONE_AGENT, "0.4.0");
        touchAs(email, null, null);

        JsonNode client = usageOf(email).get("client");
        assertThat(client.get("appVersion").asText()).isEqualTo("0.4.0");
        assertThat(client.get("platform").asText()).isEqualTo("Mobile");
    }

    // --- Ce qui peut l'atteindre --------------------------------------------------------------

    /**
     * « Joignable » ne se déduit d'aucun champ pris seul : il faut un appareil abonné ET la
     * préférence active. C'est exactement le cas qui a coûté trois jours en bêta.
     */
    @Test
    void reachabilityIsStatedPlainlyAndNotGuessed() throws Exception {
        JsonNode notifications = usageOf(DemoSeedService.ATHLETE_EMAIL).get("notifications");
        assertThat(notifications.has("reachable")).isTrue();
        assertThat(notifications.get("reachable").asBoolean())
                .as("sans appareil abonné, aucun push ne peut arriver").isFalse();
        assertThat(notifications.get("devices").asLong()).isZero();
    }

    // --- Sa montre ----------------------------------------------------------------------------

    @Test
    void aCoachHasNoWatchAndNoTrainingCounters() throws Exception {
        JsonNode usage = usageOf(DemoSeedService.HEAD_COACH_EMAIL);

        assertThat(usage.get("watch").get("connected").asBoolean()).isFalse();
        // Null et non zéro : « aucune séance » se lirait comme un reproche, alors que la
        // question ne se pose pas pour un compte qui ne s'entraîne pas ici.
        assertThat(usage.get("engagement").hasNonNull("sessionsCompleted30d")).isFalse();
    }

    @Test
    void anAthleteCarriesTrainingCounters() throws Exception {
        JsonNode engagement = usageOf(DemoSeedService.ATHLETE_EMAIL).get("engagement");

        assertThat(engagement.hasNonNull("sessionsCompleted30d")).isTrue();
        assertThat(engagement.hasNonNull("activitiesImported30d")).isTrue();
    }

    // --- Rien de sensible ---------------------------------------------------------------------

    /** Le bloc compte des séances ; il ne rend ni RPE, ni douleur, ni fatigue, ni commentaire. */
    @Test
    void theUsageBlockCarriesNoHealthData() throws Exception {
        String usage = usageOf(DemoSeedService.ATHLETE_EMAIL).toString();

        assertThat(usage).doesNotContain("rpe").doesNotContain("pain").doesNotContain("fatigue");
    }

    // --- Utilitaires --------------------------------------------------------------------------

    /** Crée un compte coach neuf et rend son jeton. Neuf = jamais vu par l'anti-rafale. */
    private String registerCoach(String email) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "password", "password123",
                                "fullName", "Coach Usage", "termsAccepted", true,
                                "clubName", "Usage " + java.util.UUID.randomUUID()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return auth.get("accessToken").asText();
    }

    private String clubIdOf(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow()
                .getClub().getId().toString();
    }

    private String bearer(String email) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + auth.get("accessToken").asText();
    }

    /**
     * Écrit l'état d'une visite directement par le dépôt.
     *
     * <p>Pas par le traqueur : il n'écrit qu'au plus une fois par quart d'heure et par compte —
     * c'est ce qui rend ce renseignement gratuit — donc deux visites d'affilée n'en produiraient
     * qu'une. Le contournement aurait été d'ouvrir une méthode de confort dans le code de
     * production pour les seuls tests ; l'anti-rafale n'est de toute façon pas ce qu'on vérifie
     * ici, et le trajet HTTP complet est couvert par le premier test.</p>
     */
    private void touchAs(String email, String userAgent, String appVersion) {
        java.util.UUID id = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
        transactions.executeWithoutResult(status ->
                userRepository.touchLastSeenWithClient(
                        id, java.time.Instant.now(), userAgent, appVersion));
    }

    private JsonNode usageOf(String email) throws Exception {
        String adminBearer = bearer(DemoSeedService.ADMIN_EMAIL);
        String userId = userRepository.findByEmailIgnoreCase(email).orElseThrow().getId().toString();
        JsonNode detail = objectMapper.readTree(mvc.perform(
                        get("/admin/users/{id}/detail", userId).header("Authorization", adminBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detail.hasNonNull("usage")).as("la fiche porte le bloc d'usage").isTrue();
        return detail.get("usage");
    }
}
