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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le fil d'un groupe, et les groupes privés.
 *
 * <p>Deux demandes d'un responsable de club : pouvoir donner rendez-vous à tout un groupe sans
 * écrire dix fois le même message, et tenir un groupe que ses collègues ne voient pas — le sien
 * s'appelait « privé », et ce nom était son seul rempart.</p>
 *
 * <p>Un groupe privé cache le groupe, jamais les athlètes : leur confidentialité reste portée par
 * la relation référente et les permissions. Superposer deux mécanismes les rendrait tous deux
 * inexplicables, d'autant qu'un athlète peut appartenir à plusieurs groupes.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupConversationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String ownerBearer;
    private String clubId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode owner = login(DemoSeedService.HEAD_COACH_EMAIL);
        ownerBearer = "Bearer " + owner.get("accessToken").asText();
        clubId = owner.get("user").get("clubId").asText();
    }

    // --- Le fil du groupe ----------------------------------------------------------------------

    /** Un rendez-vous donné une fois arrive à tout le groupe, athlètes compris. */
    @Test
    void aGroupMessageReachesTheAthletesOfThatGroup() throws Exception {
        String groupId = groupOfDemoAthlete();
        String conversationId = openGroupThread(ownerBearer, groupId);

        say(conversationId, ownerBearer, "Seance de groupe samedi 9h au stade.");

        assertThat(messages(conversationId, athleteBearer()))
                .as("l'athlete du groupe lit l'annonce")
                .contains("samedi 9h");
    }

    /** Et un athlète du groupe peut y répondre : c'est une discussion, pas un mégaphone. */
    @Test
    void anAthleteOfTheGroupCanAnswerThere() throws Exception {
        String conversationId = openGroupThread(ownerBearer, groupOfDemoAthlete());

        mvc.perform(post("/me/conversations/{id}/messages", conversationId)
                        .header("Authorization", athleteBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", "Present, je covoiture."))))
                .andExpect(status().isCreated());

        assertThat(messages(conversationId, ownerBearer)).contains("covoiture");
    }

    // --- Les groupes privés --------------------------------------------------------------------

    /** Un groupe privé n'apparaît pas dans la liste des autres coachs. */
    @Test
    void aPrivateGroupIsInvisibleToOtherCoaches() throws Exception {
        createGroup("Groupe confidentiel", "PRIVATE");
        String otherBearer = secondCoachBearer();

        assertThat(groupNames(otherBearer))
                .as("le nom du groupe etait son seul rempart")
                .doesNotContain("Groupe confidentiel");
        assertThat(groupNames(ownerBearer)).contains("Groupe confidentiel");
    }

    /** Et son fil non plus : deviner l'identifiant n'ouvre rien. */
    @Test
    void thePrivateGroupThreadIsOutOfReachToo() throws Exception {
        String groupId = createGroup("Groupe confidentiel", "PRIVATE");
        String otherBearer = secondCoachBearer();

        mvc.perform(post("/me/conversations/open")
                        .header("Authorization", otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "GROUP", "targetId", groupId))))
                .andExpect(status().isNotFound());
    }

    /** Un coach convié, lui, le voit — c'est tout l'intérêt de pouvoir en inviter. */
    @Test
    void anInvitedCoachSeesThePrivateGroup() throws Exception {
        String groupId = createGroup("Groupe confidentiel", "PRIVATE");
        String otherBearer = secondCoachBearer();
        String otherCoachId = coachIdOf("Second Coach");

        mvc.perform(put("/clubs/{c}/groups/{g}", clubId, groupId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", "Groupe confidentiel",
                                "visibility", "PRIVATE",
                                "invitedCoachIds", List.of(otherCoachId)))))
                .andExpect(status().isOk());

        assertThat(groupNames(otherBearer)).contains("Groupe confidentiel");
    }

    /** Un groupe de club reste visible de tous : c'est le comportement historique. */
    @Test
    void aClubGroupStaysVisibleToEveryCoach() throws Exception {
        createGroup("Groupe ouvert", "CLUB");
        assertThat(groupNames(secondCoachBearer())).contains("Groupe ouvert");
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
        return "Bearer " + objectMapper.readTree(mvc.perform(
                        post("/public/coach-invitations/{t}/accept", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"password\":\"password123\",\"termsAccepted\":true}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String coachIdOf(String fullName) throws Exception {
        JsonNode members = objectMapper.readTree(mvc.perform(get("/clubs/{c}/members", clubId)
                        .header("Authorization", ownerBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode m : members) {
            if (fullName.equals(m.get("name").asText())) {
                return m.get("coachId").asText();
            }
        }
        throw new AssertionError("Coach absent : " + fullName);
    }

    /** Le groupe de l'athlète de démonstration : le jeu de démo en rattache un. */
    private String groupOfDemoAthlete() throws Exception {
        String athleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
        JsonNode athlete = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}", clubId, athleteId)
                                .header("Authorization", ownerBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(athlete.hasNonNull("groupId"))
                .as("le jeu de demonstration rattache l'athlete a un groupe").isTrue();
        return athlete.get("groupId").asText();
    }

    private String createGroup(String name, String visibility) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/groups", clubId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name, "visibility", visibility))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private List<String> groupNames(String bearer) throws Exception {
        JsonNode list = objectMapper.readTree(mvc.perform(get("/clubs/{c}/groups", clubId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> names = new java.util.ArrayList<>();
        list.forEach(g -> names.add(g.get("name").asText()));
        return names;
    }

    private String openGroupThread(String bearer, String groupId) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/me/conversations/open")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "kind", "GROUP", "targetId", groupId))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    /** Nommé `say` et non `post` : le second masquerait l'import statique de MockMvc. */
    private void say(String conversationId, String bearer, String body) throws Exception {
        mvc.perform(post("/me/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("body", body))))
                .andExpect(status().isCreated());
    }

    private String messages(String conversationId, String bearer) throws Exception {
        return mvc.perform(get("/me/conversations/{id}/messages", conversationId)
                        .header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
}
