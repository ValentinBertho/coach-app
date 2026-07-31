package com.coachrun;

import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Messagerie — pièces jointes : upload (image), métadonnée sur le fil, téléchargement, type refusé. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageAttachmentTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;
    private String clubId;
    private String athleteId;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode coach = login(DemoSeedService.HEAD_COACH_EMAIL);
        coachBearer = "Bearer " + coach.get("accessToken").asText();
        clubId = coach.get("user").get("clubId").asText();
        athleteId = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void uploadsAttachmentAndDownloadsIt() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", png);

        JsonNode msg = objectMapper.readTree(mvc.perform(
                        multipart("/clubs/{c}/athletes/{a}/messages/attachment", clubId, athleteId)
                                .file(file).param("body", "Regarde ta posture")
                                .header("Authorization", coachBearer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(msg.get("attachmentId").isNull()).isFalse();
        assertThat(msg.get("attachmentFilename").asText()).isEqualTo("photo.png");
        String messageId = msg.get("id").asText();

        // Le fil contient le message avec la métadonnée de pièce jointe.
        JsonNode thread = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/messages", clubId, athleteId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(thread).anyMatch(m -> "photo.png".equals(m.path("attachmentFilename").asText(null)));

        // Téléchargement : les octets et le type sont restitués.
        byte[] dl = mvc.perform(get("/clubs/{c}/athletes/{a}/messages/{m}/attachment", clubId, athleteId, messageId)
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Type", "image/png"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(dl).isEqualTo(png);
    }

    @Test
    void rejectsDisallowedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/clubs/{c}/athletes/{a}/messages/attachment", clubId, athleteId)
                        .file(file).header("Authorization", coachBearer))
                .andExpect(status().isBadRequest());
    }

    /**
     * Quota de stockage par club : les pièces jointes vivent en {@code bytea}, sans plafond c'est
     * la sauvegarde quotidienne qui casse en premier — bien avant que quiconque voie le volume.
     */
    @Test
    void refusesUploadsBeyondTheClubQuota() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

        JsonNode before = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/storage", clubId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long quota = before.get("quotaBytes").asLong();
        assertThat(quota).isPositive();

        mvc.perform(multipart("/clubs/{c}/athletes/{a}/messages/attachment", clubId, athleteId)
                        .file(new MockMultipartFile("file", "photo.png", "image/png", png))
                        .header("Authorization", coachBearer))
                .andExpect(status().isCreated());

        // Le compteur suit les octets réellement stockés.
        JsonNode after = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/storage", clubId).header("Authorization", coachBearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(after.get("usedBytes").asLong())
                .isEqualTo(before.get("usedBytes").asLong() + png.length);

        // Un fichier plus gros que le quota entier est refusé, avec un message explicite.
        byte[] huge = new byte[(int) Math.min(quota + 1, 12L * 1024 * 1024)];
        huge[0] = (byte) 0x89;
        String body = mvc.perform(multipart("/clubs/{c}/athletes/{a}/messages/attachment", clubId, athleteId)
                        .file(new MockMultipartFile("file", "enorme.png", "image/png", huge))
                        .header("Authorization", coachBearer))
                .andExpect(status().isPayloadTooLarge())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("stockage");
    }
}
