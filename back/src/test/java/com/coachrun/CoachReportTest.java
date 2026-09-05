package com.coachrun;

import com.coachrun.entity.enums.CoachReportStatus;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le signalement d'une fiche coach — la contrepartie de la décision 4.
 *
 * <p>Les diplômes sont publiés comme déclarés, sans vérification. Ce qui est testé ici, c'est le
 * recours qui rend cette publication tenable : que n'importe qui puisse contester, que personne ne
 * puisse noyer la file, et qu'aucun seuil ne dépublie une fiche tout seul.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachReportTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String adminBearer;
    private String slug;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coachBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        adminBearer = bearer(DemoSeedService.ADMIN_EMAIL);
        slug = publishCoachProfile();
    }

    /**
     * Un visiteur sans compte peut signaler.
     *
     * <p>C'est le point qui décide si le dispositif sert : exiger un compte écarterait le confrère
     * qui reconnaît un diplôme faux et l'ancien athlète parti sans se retourner — c'est-à-dire
     * ceux qui savent quelque chose.</p>
     */
    @Test
    void anAnonymousVisitorCanReportAProfile() throws Exception {
        report(null, "FALSE_CREDENTIALS",
                "Ce coach affiche un BEES 2 ; je suis formateur dans ce diplôme et il n'y figure pas.")
                .andExpect(status().isNoContent());

        JsonNode mine = findByDetails("je suis formateur dans ce diplôme");
        assertThat(mine.get("fromKnownUser").asBoolean())
                .as("déposé sans jeton : le signalement est anonyme")
                .isFalse();
        assertThat(mine.get("status").asText()).isEqualTo("OPEN");
    }

    /** Une case cochée sans explication ne se traite pas : le texte est obligatoire. */
    @Test
    void aReportWithoutDetailsIsRejected() throws Exception {
        report(null, "OTHER", "trop court").andExpect(status().isBadRequest());
    }

    /**
     * Signaler ne dépublie rien.
     *
     * <p>Le seuil automatique est le piège de ce genre de dispositif : trois personnes décidées à
     * nuire à un concurrent suffiraient à le retirer de l'annuaire sans qu'un humain ait rien lu.
     * La fiche reste donc visible et continue d'accepter des demandes.</p>
     */
    @Test
    void reportingNeverUnpublishesTheProfileOnItsOwn() throws Exception {
        for (int i = 0; i < 3; i++) {
            report(null, "INAPPROPRIATE_CONTENT",
                    "Les propos tenus dans la présentation me paraissent déplacés, signalement " + i)
                    .andExpect(status().isNoContent());
        }

        JsonNode detail = json(mvc.perform(get("/public/coaches/{slug}", slug)).andExpect(status().isOk()));
        assertThat(detail.get("acceptingAthletes").asBoolean())
                .as("aucun seuil ne suspend une fiche : la suspension reste un geste d'administrateur")
                .isTrue();
    }

    /** Au-delà de trois signalements sur la même fiche, cette adresse a dit ce qu'elle avait à dire. */
    @Test
    void theSameAddressCannotFloodOneProfile() throws Exception {
        for (int i = 0; i < 3; i++) {
            report(null, "SPAM", "Cette fiche renvoie vers un site marchand externe, signalement " + i)
                    .andExpect(status().isNoContent());
        }
        report(null, "SPAM", "Encore une fois la même chose, pour insister lourdement")
                .andExpect(status().isConflict());
    }

    /** Un signalement nominatif se pèse autrement : l'administrateur doit voir la différence. */
    @Test
    void aSignedReportIsMarkedAsSuch() throws Exception {
        report(coachBearer, "IMPERSONATION",
                "Cette fiche reprend mon nom et mes résultats de compétition, je suis le coach concerné.")
                .andExpect(status().isNoContent());

        assertThat(findByDetails("je suis le coach concerné").get("fromKnownUser").asBoolean())
                .as("déposé avec un jeton : l'administrateur doit voir la différence")
                .isTrue();
    }

    /**
     * Clore avec suite et clore sans suite sont deux histoires différentes, et la file les garde.
     *
     * <p>Les fondre en un « traité » unique ferait perdre la seule statistique qui dise si le
     * dispositif sert à quelque chose : la part des signalements qui aboutissent.</p>
     */
    @Test
    void anAdminClosesAReportWithOrWithoutAction() throws Exception {
        report(null, "DANGEROUS_ADVICE",
                "Conseils de perte de poids qui me semblent relever du médecin, pas du coach.")
                .andExpect(status().isNoContent());
        JsonNode mine = findByDetails("relever du médecin");
        String reportId = mine.get("id").asText();
        long openOnProfileBefore = mine.get("openReportsOnProfile").asLong();

        JsonNode handled = json(mvc.perform(post("/admin/coach-reports/{id}/dismiss", reportId)
                .header("Authorization", adminBearer)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"note\":\"Vérifié avec le coach, rien à corriger.\"}"))
                .andExpect(status().isOk()));
        assertThat(handled.get("status").asText()).isEqualTo(CoachReportStatus.DISMISSED.name());
        // Un décrément, et non « zéro » : le compteur porte sur la FICHE, et d'autres cas de cette
        // classe laissent des signalements ouverts sur la même fiche de démonstration. Exiger zéro
        // revenait à exiger d'être le seul à avoir signalé — ce que la production ne garantit
        // jamais, et ce qui faisait dépendre le test de l'ordre de passage des autres.
        assertThat(handled.get("openReportsOnProfile").asLong())
                .as("clos, il cesse de peser sur le compteur de sa fiche")
                .isEqualTo(openOnProfileBefore - 1);

        JsonNode stillOpen = json(mvc.perform(get("/admin/coach-reports")
                .header("Authorization", adminBearer)));
        for (JsonNode row : stillOpen) {
            assertThat(row.get("details").asText())
                    .as("la file par défaut ne montre que ce qui reste à faire")
                    .doesNotContain("relever du médecin");
        }
    }

    /** La file d'arbitrage n'est pas publique, et pas davantage ouverte au premier coach venu. */
    @Test
    void theQueueIsReservedToPlatformAdmins() throws Exception {
        mvc.perform(get("/admin/coach-reports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/admin/coach-reports").header("Authorization", coachBearer))
                .andExpect(status().isForbidden());
    }

    /** Une fiche non publiée reste introuvable — y compris par la porte du signalement. */
    @Test
    void anUnknownProfileCannotBeReported() throws Exception {
        mvc.perform(post("/public/coaches/{slug}/report", "personne-de-ce-nom")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"reason\":\"OTHER\",\"details\":\"Un texte assez long pour passer la validation.\"}"))
                .andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- outillage

    /**
     * Retrouve dans la file le signalement portant ce texte.
     *
     * <p>Plutôt que de lire `queue.get(0)` en supposant la file vide. Cette classe passait seule et
     * échouait dès qu'une autre laissait des signalements derrière elle : elle testait alors
     * l'ordre de passage des classes de tests, pas la règle qu'elle décrit. Chercher son propre
     * dossier vaut aussi pour la production, où la file n'est jamais vide.</p>
     */
    private JsonNode findByDetails(String fragment) throws Exception {
        JsonNode queue = json(mvc.perform(get("/admin/coach-reports")
                .header("Authorization", adminBearer)).andExpect(status().isOk()));
        for (JsonNode row : queue) {
            if (row.get("details").asText().contains(fragment)) {
                return row;
            }
        }
        throw new AssertionError("Signalement introuvable dans la file : « " + fragment + " »");
    }

    private ResultActions report(String bearer, String reason, String details) throws Exception {
        var request = post("/public/coaches/{slug}/report", slug)
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content(objectMapper.writeValueAsString(
                        java.util.Map.of("reason", reason, "details", details)));
        if (bearer != null) {
            request = request.header("Authorization", bearer);
        }
        return mvc.perform(request);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String email) throws Exception {
        JsonNode body = json(mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()));
        return "Bearer " + body.get("accessToken").asText();
    }

    /** Une fiche publiée : sans elle, il n'y a rien à signaler. */
    private String publishCoachProfile() throws Exception {
        mvc.perform(put("/me/coach-profile").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"headline\":\"Coach route\",\"bio\":\"" + "x".repeat(130) + "\","
                                + "\"disciplines\":[\"ROUTE\"],\"specialties\":[\"MARATHON\"],"
                                + "\"levels\":[],\"languages\":[\"fr\"],\"remote\":true,"
                                + "\"inPerson\":false}"))
                .andExpect(status().isOk());
        mvc.perform(post("/me/coach-profile/offers").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"Suivi mensuel\",\"amountCents\":9000,"
                                + "\"periodicity\":\"MONTHLY\",\"active\":true,\"position\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/me/coach-profile/submit").header("Authorization", coachBearer))
                .andExpect(status().isOk());
        JsonNode queue = json(mvc.perform(get("/admin/coach-profiles").param("status", "PENDING")
                .header("Authorization", adminBearer)));
        mvc.perform(post("/admin/coach-profiles/{id}/approve",
                        queue.get("content").get(0).get("id").asText())
                .header("Authorization", adminBearer)).andExpect(status().isOk());
        return json(mvc.perform(get("/me/coach-profile").header("Authorization", coachBearer)))
                .get("slug").asText();
    }
}
