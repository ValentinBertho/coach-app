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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boîte de tri : « Marquer traité » sur une sélection de retours.
 *
 * <p>La sélection vient du navigateur ; seuls les retours que la file du coach lui montre
 * peuvent être marqués. Un identifiant de séance pris ailleurs est ignoré.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FeedbackSelectionReviewTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String clubId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String login(String email) throws Exception {
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email
                                + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        clubId = auth.get("user").get("clubId").asText();
        return "Bearer " + auth.get("accessToken").asText();
    }

    private List<String> queue(String bearer, String scope) throws Exception {
        JsonNode rows = objectMapper.readTree(mvc.perform(get("/clubs/{c}/dashboard/feedback", clubId)
                        .param("scope", scope).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<String> keys = new ArrayList<>();
        rows.forEach(r -> keys.add(r.get("kind").asText() + ":" + r.get("sessionId").asText()));
        return keys;
    }

    private int review(String bearer, String scope, List<String> keys) throws Exception {
        StringBuilder body = new StringBuilder("{\"items\":[");
        for (int i = 0; i < keys.size(); i++) {
            String[] k = keys.get(i).split(":");
            if (i > 0) body.append(',');
            body.append("{\"kind\":\"").append(k[0]).append("\",\"sessionId\":\"").append(k[1]).append("\"}");
        }
        body.append("]}");
        return objectMapper.readTree(mvc.perform(post("/clubs/{c}/dashboard/feedback/review", clubId)
                        .param("scope", scope).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("marked").asInt();
    }

    @Test
    void marksOnlyTheSelectedFeedbackAndLeavesTheRest() throws Exception {
        String bearer = login(DemoSeedService.HEAD_COACH_EMAIL);
        List<String> before = queue(bearer, "all");
        assertThat(before).hasSizeGreaterThan(3);

        List<String> selected = List.of(before.get(0), before.get(2));
        List<String> request = new ArrayList<>(selected);
        request.add("COURSE:" + UUID.randomUUID());

        assertThat(review(bearer, "all", request)).isEqualTo(2);

        List<String> after = queue(bearer, "all");
        assertThat(after).hasSize(before.size() - 2).doesNotContainAnyElementsOf(selected);
        assertThat(after).contains(before.get(1), before.get(3));
    }

    @Test
    void ignoresFeedbackOutsideTheCoachQueue() throws Exception {
        String head = login(DemoSeedService.HEAD_COACH_EMAIL);
        Set<String> headQueue = new HashSet<>(queue(head, "all"));
        String assistant = login(DemoSeedService.COACH_EMAIL);
        Set<String> assistantMine = new HashSet<>(queue(assistant, "mine"));

        List<String> outside = headQueue.stream().filter(k -> !assistantMine.contains(k)).limit(3).toList();
        assertThat(outside).isNotEmpty();

        assertThat(review(assistant, "mine", outside)).isZero();
        assertThat(queue(head, "all")).containsAll(outside);
    }

    @Test
    void rejectsAnEmptyOrMalformedSelection() throws Exception {
        String bearer = login(DemoSeedService.HEAD_COACH_EMAIL);
        mvc.perform(post("/clubs/{c}/dashboard/feedback/review", clubId).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/clubs/{c}/dashboard/feedback/review", clubId).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"kind\":\"AUTRE\",\"sessionId\":\"" + UUID.randomUUID() + "\"}]}"))
                .andExpect(status().isBadRequest());
    }
}
