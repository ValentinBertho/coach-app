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

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'annuaire public.
 *
 * <p>Ce que ces tests protègent, dans l'ordre : qu'une fiche non publiée n'apparaisse jamais — une
 * vitrine qui fuite est pire que pas de vitrine ; qu'aucune coordonnée ne sorte, un annuaire étant
 * exactement ce qu'on aspire pour se faire un fichier de démarchage ; et qu'un visiteur ne tombe
 * jamais sur un cul-de-sac, puisque l'ouverture est prévue à dix coachs et qu'une recherche
 * filtrée rendra souvent zéro.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachDirectoryTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String adminBearer;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coachBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        adminBearer = bearer(DemoSeedService.ADMIN_EMAIL);
    }

    /**
     * Le point le plus important du lot : une fiche qui n'a pas été validée n'existe pas pour le
     * public. Tant qu'elle est en brouillon ou en attente, ni la recherche ni son adresse directe
     * ne la rendent.
     */
    @Test
    void anUnpublishedProfileIsInvisibleToTheWorld() throws Exception {
        completeProfile();

        assertThat(searchTotal()).as("un brouillon n'est pas dans l'annuaire").isZero();
        mvc.perform(get("/public/coaches/{slug}", slug())).andExpect(status().isNotFound());

        submit();
        assertThat(searchTotal()).as("une fiche en attente non plus").isZero();
        mvc.perform(get("/public/coaches/{slug}", slug())).andExpect(status().isNotFound());

        publish();
        assertThat(searchTotal()).as("publiée, elle paraît").isEqualTo(1);
        mvc.perform(get("/public/coaches/{slug}", slug())).andExpect(status().isOk());
    }

    /** Un annuaire est ce qu'on aspire : aucune coordonnée n'en sort, ni en liste ni en fiche. */
    @Test
    void theDirectoryNeverExposesContactDetails() throws Exception {
        publishProfile();

        String list = body(mvc.perform(get("/public/coaches")).andExpect(status().isOk()));
        String detail = body(mvc.perform(get("/public/coaches/{slug}", slug())).andExpect(status().isOk()));

        for (String leak : new String[] { DemoSeedService.HEAD_COACH_EMAIL, "\"email\"",
                "\"coachId\"", "\"clubId\"", "\"userId\"" }) {
            assertThat(list).as("liste — %s ne doit pas sortir", leak).doesNotContain(leak);
            assertThat(detail).as("fiche — %s ne doit pas sortir", leak).doesNotContain(leak);
        }
    }

    /** Tout se consulte sans compte : demander une inscription pour voir l'annuaire le viderait. */
    @Test
    void everythingIsReadableWithoutAnAccount() throws Exception {
        publishProfile();

        mvc.perform(get("/public/coaches")).andExpect(status().isOk());
        mvc.perform(get("/public/coach-facets")).andExpect(status().isOk());
        mvc.perform(get("/public/coach-suggestions")).andExpect(status().isOk());
        mvc.perform(get("/public/coaches/{slug}", slug())).andExpect(status().isOk());
    }

    /**
     * Les facettes portent leur compte, y compris à zéro : c'est ce qui permet à l'écran de griser
     * un filtre au lieu de le proposer et de rendre une liste vide.
     */
    @Test
    void facetsCarryTheirCountsSoTheScreenCanDisableTheEmptyOnes() throws Exception {
        publishProfile();

        JsonNode facets = json(mvc.perform(get("/public/coach-facets")).andExpect(status().isOk()));

        assertThat(facets.get("total").asLong()).isEqualTo(1);
        assertThat(facets.get("accepting").asLong()).isEqualTo(1);

        JsonNode specialties = facets.get("specialties");
        assertThat(specialties).as("toutes les spécialités sont rendues, même vides").isNotEmpty();
        boolean hasZero = false;
        boolean hasChosen = false;
        for (JsonNode s : specialties) {
            if (s.get("count").asLong() == 0) {
                hasZero = true;
            }
            if ("MARATHON".equals(s.get("value").asText()) && s.get("count").asLong() == 1) {
                hasChosen = true;
            }
            assertThat(s.get("label").asText()).as("chaque valeur porte un libellé lisible").isNotBlank();
        }
        assertThat(hasChosen).as("la spécialité cochée est comptée").isTrue();
        assertThat(hasZero).as("celles que personne ne propose sortent à zéro, pas absentes").isTrue();
    }

    /** Une recherche trop étroite rend zéro — et le repli, lui, rend quelque chose. */
    @Test
    void anOverNarrowSearchReturnsNothingButTheFallbackStillDoes() throws Exception {
        publishProfile();

        JsonNode empty = json(mvc.perform(get("/public/coaches").param("city", "Brest"))
                .andExpect(status().isOk()));
        assertThat(empty.get("totalElements").asLong()).isZero();

        JsonNode fallback = json(mvc.perform(get("/public/coach-suggestions"))
                .andExpect(status().isOk()));
        assertThat(fallback.get("content")).as("le visiteur ne reste jamais sur un cul-de-sac").isNotEmpty();
    }

    /** Les critères se cumulent, et aucun ne peut faire ressortir une fiche non publiée. */
    @Test
    void filtersNarrowTheResults() throws Exception {
        publishProfile();

        assertThat(searchTotal("specialty", "MARATHON")).isEqualTo(1);
        assertThat(searchTotal("specialty", "TRIATHLON")).isZero();
        assertThat(searchTotal("language", "fr")).isEqualTo(1);
        assertThat(searchTotal("language", "de")).isZero();
        assertThat(searchTotal("maxMonthlyCents", "10000")).as("90 € tient dans 100 €").isEqualTo(1);
        assertThat(searchTotal("maxMonthlyCents", "5000")).as("90 € ne tient pas dans 50 €").isZero();
    }

    /** Le tarif affiché est le moins cher ramené au mois — le même que celui qui filtre. */
    @Test
    void theListedPriceIsTheCheapestMonthlyEquivalent() throws Exception {
        publishProfile();

        JsonNode first = json(mvc.perform(get("/public/coaches")).andExpect(status().isOk()))
                .get("content").get(0);
        assertThat(first.get("fromMonthlyCents").asInt()).isEqualTo(9000);
        assertThat(first.get("acceptingAthletes").asBoolean()).isTrue();
        assertThat(first.get("slug").asText()).isNotBlank();
    }

    // ------------------------------------------------------------------ utilitaires

    private long searchTotal(String... params) throws Exception {
        var request = get("/public/coaches");
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return json(mvc.perform(request).andExpect(status().isOk())).get("totalElements").asLong();
    }

    private void publishProfile() throws Exception {
        completeProfile();
        submit();
        publish();
    }

    private void completeProfile() throws Exception {
        mvc.perform(put("/me/coach-profile").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"headline\":\"Coach route et trail\",\"bio\":\"" + "x".repeat(130) + "\","
                                + "\"disciplines\":[\"ROUTE\"],\"specialties\":[\"MARATHON\"],"
                                + "\"levels\":[],\"languages\":[\"fr\"],\"city\":\"Lyon\","
                                + "\"country\":\"FR\",\"remote\":true,\"inPerson\":false,"
                                + "\"experienceYears\":12}"))
                .andExpect(status().isOk());
        mvc.perform(post("/me/coach-profile/offers").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"name\":\"Suivi mensuel\",\"amountCents\":9000,"
                                + "\"periodicity\":\"MONTHLY\",\"active\":true,\"position\":0}"))
                .andExpect(status().isCreated());
    }

    private void submit() throws Exception {
        mvc.perform(post("/me/coach-profile/submit").header("Authorization", coachBearer))
                .andExpect(status().isOk());
    }

    private void publish() throws Exception {
        JsonNode queue = json(mvc.perform(get("/admin/coach-profiles").param("status", "PENDING")
                .header("Authorization", adminBearer)).andExpect(status().isOk()));
        String id = queue.get("content").get(0).get("id").asText();
        mvc.perform(post("/admin/coach-profiles/{id}/approve", id)
                .header("Authorization", adminBearer)).andExpect(status().isOk());
    }

    private String slug() throws Exception {
        return json(mvc.perform(get("/me/coach-profile").header("Authorization", coachBearer)))
                .get("slug").asText();
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(body(actions));
    }

    private String body(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String email) throws Exception {
        JsonNode res = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        return "Bearer " + res.get("accessToken").asText();
    }
}
