package com.coachrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'athlète corrige, note et supprime ses propres sorties.
 *
 * <p>Le ressenti (RPE, mot au coach) n'existait que porté par une séance <em>prescrite</em>. Une
 * course ou un footing improvisé arrivait donc chez le coach avec les seuls chiffres de la
 * montre — et c'est précisément là qu'il n'a rien d'autre à lire : aucune prescription à
 * comparer, aucun retour de séance.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AthleteActivityEditTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    @Test
    void noteEtCommenteUneSortieHorsProgramme() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String athlete = signUpAthlete(mvc);

        String id = logRun(mvc, athlete, "2026-06-14", 21000, 6300);

        mvc.perform(patch("/me/activities/{id}", id).header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Semi du club\",\"rpe\":8,"
                                + "\"comment\":\"Parti trop vite, fin difficile.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Semi du club"))
                .andExpect(jsonPath("$.rpe").value(8))
                .andExpect(jsonPath("$.athleteComment").value("Parti trop vite, fin difficile."));

        // Et le coach le lit sur la sortie, sans séance prescrite pour le porter.
        mvc.perform(get("/me/activities").header("Authorization", athlete))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rpe").value(8));
    }

    /** Un champ absent vaut « inchangé » : noter une sortie ne doit pas effacer son titre. */
    @Test
    void unChampAbsentNEffaceRien() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String athlete = signUpAthlete(mvc);
        String id = logRun(mvc, athlete, "2026-06-15", 10000, 3000);

        mvc.perform(patch("/me/activities/{id}", id).header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Footing du soir\",\"comment\":\"Jambes lourdes\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/me/activities/{id}", id).header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rpe\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Footing du soir"))
                .andExpect(jsonPath("$.athleteComment").value("Jambes lourdes"))
                .andExpect(jsonPath("$.rpe").value(4));
    }

    /** Effacer demande un geste explicite, que l'absence de valeur ne peut pas exprimer. */
    @Test
    void effaceRessentiEtCommentaireSurDemandeExplicite() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String athlete = signUpAthlete(mvc);
        String id = logRun(mvc, athlete, "2026-06-16", 8000, 2400);

        mvc.perform(patch("/me/activities/{id}", id).header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rpe\":6,\"comment\":\"Bien\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/me/activities/{id}", id).header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clearRpe\":true,\"clearComment\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpe").doesNotExist())
                .andExpect(jsonPath("$.athleteComment").doesNotExist());
    }

    /** Une correction de distance sur une saisie manuelle est acceptée : c'est une déclaration. */
    @Test
    void corrigeLesChiffresDUneSaisieManuelle() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String athlete = signUpAthlete(mvc);
        String id = logRun(mvc, athlete, "2026-06-17", 10000, 3000);

        mvc.perform(patch("/me/activities/{id}", id).header("Authorization", athlete)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"distanceM\":12400,\"durationS\":3720}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceM").value(12400))
                .andExpect(jsonPath("$.durationS").value(3720))
                // L'allure suit la correction, comme partout ailleurs.
                .andExpect(jsonPath("$.paceSPerKm").value(300));
    }

    @Test
    void supprimeUneSortie() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String athlete = signUpAthlete(mvc);
        String id = logRun(mvc, athlete, "2026-06-18", 9000, 2700);

        mvc.perform(delete("/me/activities/{id}", id).header("Authorization", athlete))
                .andExpect(status().isNoContent());

        mvc.perform(get("/me/activities").header("Authorization", athlete))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** Anti-IDOR : la sortie d'un autre athlète n'est ni modifiable ni supprimable. */
    @Test
    void neTouchePasAuxSortiesDesAutres() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String mine = signUpAthlete(mvc);
        String theirs = signUpAthlete(mvc);
        String id = logRun(mvc, theirs, "2026-06-19", 5000, 1500);

        mvc.perform(patch("/me/activities/{id}", id).header("Authorization", mine)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rpe\":9}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/me/activities/{id}", id).header("Authorization", mine))
                .andExpect(status().isNotFound());
    }

    private String logRun(MockMvc mvc, String athlete, String date, int distanceM, int durationS)
            throws Exception {
        return objectMapper.readTree(mvc.perform(post("/me/activities")
                        .header("Authorization", athlete).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityDate\":\"" + date + "\",\"title\":\"Sortie\","
                                + "\"distanceM\":" + distanceM + ",\"durationS\":" + durationS + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    /** Athlète inscrit par invitation, comme dans la vraie vie : coach → invitation → compte. */
    private String signUpAthlete(MockMvc mvc) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"edit-%s@test.fr","password":"password123","fullName":"C","termsAccepted": true, "clubName":"ED %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        String coach = "Bearer " + auth.get("accessToken").asText();
        String clubId = auth.get("user").get("clubId").asText();
        verifyCoachEmail(clubId);

        String email = "editathlete-" + UUID.randomUUID() + "@darilab.app";
        String athleteId = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes", clubId)
                        .header("Authorization", coach).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Léa\",\"lastName\":\"Run\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();

        String url = objectMapper.readTree(mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", clubId, athleteId)
                        .header("Authorization", coach))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("inviteUrl").asText();
        String token = url.substring(url.lastIndexOf('/') + 1);

        mvc.perform(post("/public/invitations/{t}/accept", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"healthDataConsent\":true,\"termsAccepted\":true,"
                                + "\"email\":\"" + email + "\",\"password\":\"athletepass1\"}"))
                .andExpect(status().isOk());

        return "Bearer " + objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"athletepass1\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private void verifyCoachEmail(String clubId) {
        com.coachrun.entity.User user = userRepository.findAll().stream()
                .filter(u -> u.getClub() != null && clubId.equals(u.getClub().getId().toString()))
                .filter(u -> u.getRole() == com.coachrun.entity.enums.UserRole.HEAD_COACH)
                .findFirst().orElseThrow();
        user.setEmailVerified(true);
        userRepository.saveAndFlush(user);
    }
}
