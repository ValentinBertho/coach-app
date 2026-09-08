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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le jeu de démonstration du hub : de quoi ouvrir le produit et le regarder tourner.
 *
 * <h2>Ce qu'il répare</h2>
 *
 * <p>Le jeu de démonstration ne contenait <b>rien</b> du hub : ni fiche coach, ni compte athlète
 * autoporté, ni demande. Conséquence à l'usage : l'annuaire s'ouvrait vide, aucune fiche n'était
 * consultable, les deux files d'arbitrage n'avaient aucun dossier, et il n'existait aucun compte
 * athlète du hub pour se connecter. La moitié de ce qui a été livré ce mois-ci ne pouvait tout
 * simplement pas se regarder fonctionner.</p>
 *
 * <p>{@code seedHub()} est appelé au démarrage en profil dev, et <b>pas</b> par
 * {@code seed()} — que cent-cinquante-huit classes de tests appellent, et où des fiches publiées
 * changeraient ce que voit l'annuaire partout. Cette classe est donc le seul endroit qui
 * l'exécute : sans elle, ce serait du code livré sans avoir jamais tourné.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HubDemoSeedTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        demoSeedService.seedHub();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Idempotent, comme le reste du jeu de démonstration : un second appel ne double rien. */
    @Test
    void seedingTheHubTwiceChangesNothing() {
        assertThat(demoSeedService.seedHub())
                .as("le hub est déjà semé : le second appel ne recrée rien")
                .isFalse();
    }

    /**
     * L'annuaire n'est plus vide — c'est le point de départ de tout le parcours athlète.
     *
     * <p>Deux coachs publiés, et non un : avec un seul, toutes les facettes rendent le même
     * résultat et l'on ne voit pas si le filtrage fonctionne.</p>
     */
    @Test
    void theDirectoryShowsTheIndependentCoaches() throws Exception {
        JsonNode page = json(mvc.perform(get("/public/coaches").param("size", "48"))
                .andExpect(status().isOk()));
        assertThat(page.get("content").toString())
                .contains("Sarah Lemoine")
                .contains("Malik Fournier");
        assertThat(page.get("totalElements").asLong()).isGreaterThanOrEqualTo(2);
    }

    /** Une fiche s'ouvre, avec son tarif et ses diplômes déclarés : c'est la page de décision. */
    @Test
    void aProfilePageIsComplete() throws Exception {
        JsonNode detail = json(mvc.perform(get("/public/coaches/{slug}", "sarah-lemoine"))
                .andExpect(status().isOk()));
        assertThat(detail.get("name").asText()).isEqualTo("Sarah Lemoine");
        assertThat(detail.get("offers")).as("un tarif, sinon la fiche ne sert à rien").isNotEmpty();
        assertThat(detail.get("certifications")).isNotEmpty();
        assertThat(detail.get("acceptingAthletes").asBoolean()).isTrue();
    }

    /** La fiche en attente n'est pas publique — et elle donne son dossier à la file du matin. */
    @Test
    void thePendingProfileIsAwaitingReviewAndInvisible() throws Exception {
        mvc.perform(get("/public/coaches/{slug}", "julie-ferrand"))
                .andExpect(status().isNotFound());

        JsonNode queue = json(mvc.perform(get("/admin/coach-profiles").param("status", "PENDING")
                .header("Authorization", bearer(DemoSeedService.ADMIN_EMAIL)))
                .andExpect(status().isOk()));
        assertThat(queue.get("content").toString())
                .as("sans elle, la file de validation s'ouvre vide")
                .contains("Julie Ferrand");
    }

    /**
     * Le compte avec lequel on parcourt l'annuaire : inscrit, vérifié, et <b>sans coach</b>.
     *
     * <p>C'est l'état qu'aucun compte de démonstration ne permettait d'essayer, et c'est celui de
     * tout athlète qui arrive par le hub.</p>
     */
    @Test
    void theHubAthleteHasAnAccountAndNoCoachYet() throws Exception {
        JsonNode me = json(mvc.perform(get("/me")
                .header("Authorization", bearer(DemoSeedService.HUB_ATHLETE_EMAIL)))
                .andExpect(status().isOk()));
        assertThat(me.get("athleteId").isNull()).as("aucune fiche tant qu'aucun coach n'a accepté").isTrue();
        assertThat(me.get("clubId").isNull()).as("ni espace").isTrue();
    }

    /** Et une demande attend déjà dans la file du coach, sinon elle s'ouvrirait vide aussi. */
    @Test
    void aCoachingRequestIsWaitingInTheCoachInbox() throws Exception {
        JsonNode inbox = json(mvc.perform(get("/me/received-requests")
                .header("Authorization", bearer(DemoSeedService.SOLO_COACH_EMAIL)))
                .andExpect(status().isOk()));
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).get("athleteGoal").asText()).contains("40 minutes");
    }

    /** L'espace d'un indépendant ne s'appelle pas « club » : c'est le drapeau du lot 5. */
    @Test
    void theIndependentCoachWorkspaceIsMarkedAsSolo() throws Exception {
        JsonNode me = json(mvc.perform(get("/auth/me")
                .header("Authorization", bearer(DemoSeedService.SOLO_COACH_EMAIL)))
                .andExpect(status().isOk()));
        assertThat(me.get("soloPractice").asBoolean())
                .as("l'interface doit cesser de lui parler d'un club qu'il n'a pas")
                .isTrue();
    }

    // ---------------------------------------------------------------- outillage

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String email) throws Exception {
        return "Bearer " + json(mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"email\":\"" + email + "\",\"password\":\""
                        + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }
}
