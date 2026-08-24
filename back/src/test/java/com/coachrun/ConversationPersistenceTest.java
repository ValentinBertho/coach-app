package com.coachrun;

import com.coachrun.entity.Conversation;
import com.coachrun.repository.ConversationRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un fil ouvert pour la première fois doit exister <b>en base</b> après la requête.
 *
 * <p><b>Pourquoi ce test n'est pas transactionnel.</b> Les fils se créent à la volée : ouvrir la
 * messagerie matérialise le binôme, le groupe, le club. Or ces lectures traversent des services
 * déclarés {@code @Transactional(readOnly = true)}, et une transaction en lecture seule met
 * Hibernate en flush manuel : l'entité est bien créée en mémoire, reçoit un identifiant… et
 * n'atteint jamais la base. L'écran afficherait un fil dont l'identifiant ne désigne rien.</p>
 *
 * <p>Le défaut est invisible sous {@code @Transactional} : la transaction du test, elle, est en
 * écriture, et le service s'y joint. Il fallait donc laisser chaque requête valider la sienne —
 * ce qui est aussi la seule façon de relire la base telle que la verra la requête suivante.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ConversationPersistenceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConversationRepository conversationRepository;

    private MockMvc mvc;
    private String coachBearer;
    private String clubId;
    private UUID coachUserId;
    private UUID athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        coachUserId = UUID.fromString(coach.get("user").get("id").asText());
        athleteId = UUID.fromString(
                login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText());
    }

    /** Ouvrir le fil d'un binôme le crée — et il est encore là à la requête suivante. */
    @Test
    void openingABinomeThreadPersistsIt() throws Exception {
        mvc.perform(get("/clubs/{c}/athletes/{a}/messages", clubId, athleteId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk());

        assertThat(conversationRepository.findByDedupKey(
                Conversation.athleteCoachKey(athleteId, coachUserId)))
                .as("un fil creee en lecture seule n'atteint jamais la base")
                .isPresent();
    }

    /** Idem pour les fils collectifs, materialises a l'ouverture de la boite de reception. */
    @Test
    void theInboxMaterialisesTheClubThread() throws Exception {
        mvc.perform(get("/me/conversations").header("Authorization", coachBearer))
                .andExpect(status().isOk());

        assertThat(conversationRepository.findByDedupKey(Conversation.clubKey(UUID.fromString(clubId))))
                .as("le fil du club doit exister apres l'avoir liste")
                .isPresent();
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
