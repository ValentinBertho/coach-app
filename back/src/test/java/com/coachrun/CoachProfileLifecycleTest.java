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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La vitrine d'un coach, de son brouillon à sa publication.
 *
 * <p>Ce que ces tests protègent, dans l'ordre d'importance : qu'une fiche ne se publie pas sans
 * passer par un humain (c'est le sens même d'une validation manuelle), qu'un coach sache
 * <b>quoi</b> compléter plutôt que d'essuyer un refus muet, et qu'une fiche soumise soit gelée —
 * sans quoi l'administrateur validerait un texte que le coach a changé entre-temps, et la
 * validation ne voudrait plus rien dire.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachProfileLifecycleTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String adminBearer;
    private String athleteBearer;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coachBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
        adminBearer = bearer(DemoSeedService.ADMIN_EMAIL);
        athleteBearer = bearer(DemoSeedService.ATHLETE_EMAIL);
    }

    /**
     * La fiche naît en brouillon à la première lecture, et dit d'emblée ce qui lui manque.
     *
     * <p>Provisionnement paresseux : demander au coach de « créer sa fiche » avant de pouvoir la
     * remplir est une étape qui n'apprend rien à personne.</p>
     */
    @Test
    void theProfileIsBornAsADraftAndSaysWhatItNeeds() throws Exception {
        JsonNode profile = json(mvc.perform(get("/me/coach-profile").header("Authorization", coachBearer))
                .andExpect(status().isOk()));

        assertThat(profile.get("status").asText()).isEqualTo("DRAFT");
        assertThat(profile.get("slug").asText()).isNotBlank();
        assertThat(profile.get("missing")).isNotEmpty();
    }

    /** Une fiche incomplète ne se soumet pas, et le refus nomme chaque manque. */
    @Test
    void anIncompleteProfileCannotBeSubmittedAndTheRefusalNamesWhatIsMissing() throws Exception {
        mvc.perform(get("/me/coach-profile").header("Authorization", coachBearer));

        String body = mvc.perform(post("/me/coach-profile/submit").header("Authorization", coachBearer))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(body)
                .as("« fiche incomplète » obligerait le coach à deviner ce qu'on attend")
                .contains("une accroche")
                .contains("au moins une formule tarifaire");
    }

    /** Le parcours nominal : brouillon complété, soumis, puis publié par un administrateur. */
    @Test
    void aCompleteProfileGoesThroughValidationBeforeReachingTheDirectory() throws Exception {
        completeProfile();

        JsonNode submitted = json(mvc.perform(post("/me/coach-profile/submit")
                .header("Authorization", coachBearer)).andExpect(status().isOk()));
        assertThat(submitted.get("status").asText()).isEqualTo("PENDING");
        assertThat(submitted.get("submittedAt").isNull()).isFalse();

        // La file du back-office la voit arriver, avec de quoi décider sans rien ouvrir d'autre.
        JsonNode queue = json(mvc.perform(get("/admin/coach-profiles").param("status", "PENDING")
                .header("Authorization", adminBearer)).andExpect(status().isOk()));
        assertThat(queue.get("content")).hasSize(1);
        assertThat(queue.get("content").get(0).get("offers")).isNotEmpty();
        assertThat(queue.get("content").get(0).get("certifications")).isNotEmpty();

        String profileId = queue.get("content").get(0).get("id").asText();
        JsonNode approved = json(mvc.perform(post("/admin/coach-profiles/{id}/approve", profileId)
                .header("Authorization", adminBearer)).andExpect(status().isOk()));
        assertThat(approved.get("status").asText()).isEqualTo("PUBLISHED");
    }

    /**
     * Une fiche en attente est gelée. Sans cela, l'administrateur validerait un texte que le coach
     * a changé entre-temps.
     */
    @Test
    void aPendingProfileIsFrozen() throws Exception {
        completeProfile();
        mvc.perform(post("/me/coach-profile/submit").header("Authorization", coachBearer))
                .andExpect(status().isOk());

        mvc.perform(put("/me/coach-profile").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content(profileBody("Autre accroche")))
                .andExpect(status().isConflict());
    }

    /** Un refus renvoie la fiche en brouillon, avec son motif — pas dans un état terminal. */
    @Test
    void aRejectionSendsTheProfileBackToDraftWithItsReason() throws Exception {
        completeProfile();
        String profileId = json(mvc.perform(post("/me/coach-profile/submit")
                .header("Authorization", coachBearer))).get("id").asText();

        mvc.perform(post("/admin/coach-profiles/{id}/reject", profileId)
                        .header("Authorization", adminBearer)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                        .content("{\"note\":\"Merci de préciser vos tarifs.\"}"))
                .andExpect(status().isOk());

        JsonNode mine = json(mvc.perform(get("/me/coach-profile").header("Authorization", coachBearer)));
        assertThat(mine.get("status").asText()).isEqualTo("DRAFT");
        assertThat(mine.get("reviewNote").asText()).contains("préciser vos tarifs");
    }

    /**
     * Retirer une formule la désactive sans la supprimer : c'est peut-être celle sur laquelle un
     * athlète a été accepté, et son libellé doit rester lisible.
     */
    @Test
    void removingAnOfferDeactivatesItRatherThanDeletingIt() throws Exception {
        String offerId = json(mvc.perform(post("/me/coach-profile/offers")
                .header("Authorization", coachBearer)
                .contentType(MediaType.APPLICATION_JSON).characterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                .content(offerBody("Suivi mensuel", 9000)))).get("id").asText();

        mvc.perform(delete("/me/coach-profile/offers/{id}", offerId).header("Authorization", coachBearer))
                .andExpect(status().isNoContent());

        JsonNode offers = json(mvc.perform(get("/me/coach-profile")
                .header("Authorization", coachBearer))).get("offers");
        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).get("active").asBoolean()).isFalse();
    }

    /** Un athlète n'a pas de vitrine : la route lui est fermée, pas seulement vide. */
    @Test
    void anAthleteHasNoCoachProfile() throws Exception {
        mvc.perform(get("/me/coach-profile").header("Authorization", athleteBearer))
                .andExpect(status().isForbidden());
    }

    /** Seule l'administration arbitre : un coach ne publie pas sa propre fiche. */
    @Test
    void aCoachCannotApproveTheirOwnProfile() throws Exception {
        completeProfile();
        String profileId = json(mvc.perform(post("/me/coach-profile/submit")
                .header("Authorization", coachBearer))).get("id").asText();

        mvc.perform(post("/admin/coach-profiles/{id}/approve", profileId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ utilitaires

    /** Remplit la fiche jusqu'à ce qu'elle soit soumettable, formule et diplôme compris. */
    private void completeProfile() throws Exception {
        mvc.perform(put("/me/coach-profile").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content(profileBody("Coach route et trail")))
                .andExpect(status().isOk());
        mvc.perform(post("/me/coach-profile/offers").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(java.nio.charset.StandardCharsets.UTF_8).content(offerBody("Suivi mensuel", 9000)))
                .andExpect(status().isCreated());
        mvc.perform(post("/me/coach-profile/certifications").header("Authorization", coachBearer)
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                        .content("{\"label\":\"BPJEPS Athlétisme\",\"organisation\":\"FFA\",\"obtainedYear\":2018}"))
                .andExpect(status().isCreated());

        assertThat(json(mvc.perform(get("/me/coach-profile").header("Authorization", coachBearer)))
                .get("missing"))
                .as("la fiche doit être complète avant les tests de soumission")
                .isEmpty();
    }

    private String profileBody(String headline) {
        return "{"
                + "\"headline\":\"" + headline + "\","
                + "\"bio\":\"" + "J'accompagne des coureurs de tous niveaux depuis douze ans, "
                + "de la première course de dix kilomètres au trail long. Mon approche part de la "
                + "physiologie et des contraintes réelles de chacun.\","
                + "\"disciplines\":[\"ROUTE\",\"TRAIL\"],"
                + "\"specialties\":[\"MARATHON\",\"TRAIL\"],"
                + "\"levels\":[],"
                + "\"languages\":[\"fr\"],"
                + "\"city\":\"Lyon\",\"country\":\"FR\","
                + "\"remote\":true,\"inPerson\":false,"
                + "\"experienceYears\":12,\"capacityMax\":20}";
    }

    private String offerBody(String name, int amountCents) {
        return "{\"name\":\"" + name + "\",\"description\":\"Plan hebdomadaire et retours\","
                + "\"amountCents\":" + amountCents + ",\"periodicity\":\"MONTHLY\","
                + "\"active\":true,\"position\":0}";
    }

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse()
                        .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String bearer(String email) throws Exception {
        JsonNode res = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).characterEncoding(java.nio.charset.StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        return "Bearer " + res.get("accessToken").asText();
    }
}
