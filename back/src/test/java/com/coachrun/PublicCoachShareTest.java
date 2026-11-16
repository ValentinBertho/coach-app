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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le lien court d'un coach s'affiche comme le coach, pas comme la page d'accueil.
 *
 * <p>Les robots de prévisualisation (WhatsApp, iMessage, LinkedIn) n'exécutent pas Angular : ce
 * sont les balises de cette page, rendue par le serveur, qu'ils lisent.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PublicCoachShareTest {

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

    @Test
    void thePreviewCarriesTheCoachTagsWithoutLoggingIn() throws Exception {
        String html = mvc.perform(get("/public/share/coach/{s}", slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String name = json(mvc.perform(get("/public/coaches/{s}", slug))).get("name").asText();

        assertThat(html).contains("<meta property=\"og:title\" content=\"" + name);
        assertThat(html).contains("og:image").contains("/api/public/share/coach/" + slug + "/card.png");
        assertThat(html).contains("summary_large_image");
        assertThat(html).as("le visiteur humain est renvoyé vers la fiche")
                .contains("http-equiv=\"refresh\"").contains("/coachs/" + slug);
    }

    @Test
    void theCardIsAPngInTheOpenGraphFormat() throws Exception {
        byte[] png = mvc.perform(get("/public/share/coach/{s}/card.png", slug))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).isEqualTo(1200);
        assertThat(img.getHeight()).isEqualTo(630);
    }

    @Test
    void theStoryIsAVerticalPng() throws Exception {
        byte[] png = mvc.perform(get("/public/share/coach/{s}/story.png", slug))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(img.getWidth()).isEqualTo(1080);
        assertThat(img.getHeight()).isEqualTo(1920);
    }

    @Test
    void anUnknownCoachLeadsToTheDirectory() throws Exception {
        mvc.perform(get("/public/share/coach/{s}", "personne-" + System.nanoTime()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/coachs")));
    }

    @Test
    void anUnknownCoachHasNoCard() throws Exception {
        mvc.perform(get("/public/share/coach/{s}/card.png", "personne-" + System.nanoTime()))
                .andExpect(status().isNotFound());
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String email) throws Exception {
        return bearer(email, DemoSeedService.DEMO_PASSWORD);
    }

    private String bearer(String email, String password) throws Exception {
        return "Bearer " + MfaTestSupport.complete(mvc, context, email, json(mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding(StandardCharsets.UTF_8)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()))).get("accessToken").asText();
    }

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
