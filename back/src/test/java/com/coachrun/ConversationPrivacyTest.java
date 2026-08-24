package com.coachrun;

import com.coachrun.entity.enums.ConversationKind;
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
 * Le cloisonnement de la messagerie.
 *
 * <p><b>Le défaut.</b> Il n'existait pas de fil : il existait un <i>athlète</i>. Tous les messages
 * le concernant tombaient dans le même tas, que n'importe quel coach ayant accès à lui pouvait
 * lire — et le responsable d'un club pilote a ainsi lu les échanges du propriétaire avec ses
 * athlètes. Le cloisonnement n'était pas troué : il n'existait pas.</p>
 *
 * <p>Ces tests fixent la règle : un fil par binôme, et l'appartenance déduite de l'état courant.
 * Les libellés sont en ASCII — MockMvc relit le corps dans le jeu de caractères de la plateforme,
 * et ces tests éprouvent la confidentialité, pas l'encodage.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ConversationPrivacyTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String ownerBearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode owner = login(DemoSeedService.HEAD_COACH_EMAIL);
        ownerBearer = "Bearer " + owner.get("accessToken").asText();
        clubId = owner.get("user").get("clubId").asText();
        athleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    // --- 1. Deux coachs, deux fils -------------------------------------------------------------

    /**
     * Le cœur du correctif : ce que le propriétaire écrit à un athlète n'est pas lisible par un
     * autre coach du club, fût-il coach principal avec accès en écriture.
     */
    @Test
    void aSecondCoachNeverReadsTheFirstCoachThread() throws Exception {
        sendAsCoach(ownerBearer, "Rendez-vous a 18h pour le test VMA.");

        String otherBearer = secondCoachBearer();
        String body = mvc.perform(get("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                        .header("Authorization", otherBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body)
                .as("le fil d'un binome n'appartient qu'a ses deux membres")
                .doesNotContain("test VMA");
    }

    /** Et son propre fil avec le même athlète reste bien le sien. */
    @Test
    void eachCoachHasHisOwnThreadWithTheSameAthlete() throws Exception {
        sendAsCoach(ownerBearer, "Message du proprietaire.");
        String otherBearer = secondCoachBearer();
        sendAsCoach(otherBearer, "Message du second coach.");

        assertThat(threadAsCoach(ownerBearer))
                .contains("Message du proprietaire")
                .doesNotContain("Message du second coach");
        assertThat(threadAsCoach(otherBearer))
                .contains("Message du second coach")
                .doesNotContain("Message du proprietaire");
    }

    /** L'athlète, lui, voit les deux fils : ce sont ses interlocuteurs. */
    @Test
    void theAthleteSeesOneThreadPerCoach() throws Exception {
        sendAsCoach(ownerBearer, "Message du proprietaire.");
        sendAsCoach(secondCoachBearer(), "Message du second coach.");

        JsonNode inbox = objectMapper.readTree(mvc.perform(get("/me/conversations")
                        .header("Authorization", athleteBearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        long binomes = 0;
        for (JsonNode c : inbox) {
            if (ConversationKind.ATHLETE_COACH.name().equals(c.get("kind").asText())) {
                binomes++;
            }
        }
        assertThat(binomes).as("un fil par coach qui lui a ecrit").isEqualTo(2);
    }

    // --- 2. À qui peut-on écrire ---------------------------------------------------------------

    /** Un coach écrit à ses collègues ; l'athlète écrit aux coachs qui le suivent. */
    @Test
    void recipientsFollowTheRoleOfWhoAsks() throws Exception {
        secondCoachBearer(); // le club compte désormais deux coachs

        JsonNode coachSees = recipients(ownerBearer);
        assertThat(kinds(coachSees)).as("un coach ecrit a des coachs et a des athletes")
                .contains("COACH", "ATHLETE");

        JsonNode athleteSees = recipients(athleteBearer());
        assertThat(kinds(athleteSees)).as("un athlete n'ouvre pas de fil avec un autre athlete")
                .containsOnly("COACH");
    }

    /** Un destinataire non proposé n'ouvre pas de fil, même en devinant son identifiant. */
    @Test
    void anUnofferedRecipientCannotBeReached() throws Exception {
        mvc.perform(post("/me/conversations/open")
                        .header("Authorization", athleteBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "ATHLETE", "targetId", athleteId))))
                .andExpect(status().isNotFound());
    }

    // --- 3. Le fil du club ---------------------------------------------------------------------

    /** Les coachs y annoncent, les athlètes y lisent : sans quoi c'est un forum à modérer. */
    @Test
    void theClubThreadIsReadOnlyForAthletes() throws Exception {
        String conversationId = openClubThread(ownerBearer);

        mvc.perform(post("/me/conversations/{id}/messages", conversationId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Sortie club dimanche 9h."))))
                .andExpect(status().isCreated());

        // L'athlète lit l'annonce…
        assertThat(mvc.perform(get("/me/conversations/{id}/messages", conversationId)
                        .header("Authorization", athleteBearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("dimanche");

        // … mais n'y écrit pas.
        mvc.perform(post("/me/conversations/{id}/messages", conversationId)
                        .header("Authorization", athleteBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Je viens !"))))
                .andExpect(status().isConflict());
    }

    // --- Utilitaires ---------------------------------------------------------------------------

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String athleteBearer() throws Exception {
        return "Bearer " + login(DemoSeedService.ATHLETE_EMAIL).get("accessToken").asText();
    }

    /**
     * Un second coach du club, coach principal — donc en écriture sur tous les athlètes club
     * depuis le durcissement des rôles. C'est exactement le cas signalé.
     */
    private String secondCoachBearer() throws Exception {
        JsonNode invite = objectMapper.readTree(mvc.perform(post("/clubs/{c}/members", clubId)
                        .header("Authorization", ownerBearer).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "second.coach@darilab.app",
                                "role", "COACH_PRINCIPAL",
                                "fullName", "Second Coach"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String url = invite.get("inviteUrl").asText();
        String token = url.substring(url.lastIndexOf('/') + 1);
        JsonNode session = objectMapper.readTree(mvc.perform(
                        post("/public/coach-invitations/{t}/accept", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"password123\",\"termsAccepted\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return "Bearer " + session.get("accessToken").asText();
    }

    private void sendAsCoach(String bearer, String body) throws Exception {
        mvc.perform(post("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", body))))
                .andExpect(status().isCreated());
    }

    private String threadAsCoach(String bearer) throws Exception {
        return mvc.perform(get("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private JsonNode recipients(String bearer) throws Exception {
        return objectMapper.readTree(mvc.perform(get("/me/conversations/recipients")
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private java.util.List<String> kinds(JsonNode list) {
        java.util.List<String> out = new java.util.ArrayList<>();
        list.forEach(r -> out.add(r.get("kind").asText()));
        return out;
    }

    private String openClubThread(String bearer) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/me/conversations/open")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "CLUB", "targetId", clubId))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }
}
