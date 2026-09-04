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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La photo d'une fiche coach.
 *
 * <p>Ce que ces tests protègent : que le serveur ne rende <b>jamais</b> le fichier qu'on lui a
 * donné. Une photo prise au téléphone porte les coordonnées GPS de l'endroit où elle a été prise —
 * souvent le domicile — et l'annuaire est public : republier le fichier tel quel reviendrait à
 * publier l'adresse du coach sans le lui dire. Le ré-encodage règle cela, et au passage le cas du
 * fichier qui prétend être une image sans en être une.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachPhotoTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String coachBearer;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        coachBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
    }

    /**
     * Une image plus grande que la borne d'affichage ressort réduite, en JPEG, et se sert sans
     * authentification — l'annuaire est public, une vitrine derrière un mot de passe ne sert à rien.
     */
    @Test
    void anUploadedImageIsReEncodedResizedAndServedPublicly() throws Exception {
        JsonNode profile = upload(png(1200, 900));
        String photoUrl = profile.get("photoUrl").asText();
        assertThat(photoUrl).startsWith("/public/coach-photos/");

        byte[] served = mvc.perform(get(photoUrl))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(served));
        assertThat(decoded).as("le serveur rend bien une image").isNotNull();
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight()))
                .as("réduite à la borne d'affichage")
                .isLessThanOrEqualTo(512);
        assertThat(decoded.getWidth()).as("proportions conservées (4:3)").isEqualTo(512);
        assertThat(decoded.getHeight()).isEqualTo(384);

        // Le fichier servi n'est PAS celui qui a été envoyé : c'est là que les métadonnées de
        // l'appareil disparaissent.
        assertThat(served).isNotEqualTo(png(1200, 900));
    }

    /** Un {@code Content-Type} se choisit côté client : seul le décodage fait foi. */
    @Test
    void aFileThatIsNotAnImageIsRefusedEvenWhenItClaimsToBeOne() throws Exception {
        MockMultipartFile liar = new MockMultipartFile("file", "photo.jpg", "image/jpeg",
                "ce n'est pas une image".getBytes(StandardCharsets.UTF_8));

        mvc.perform(multipart("/me/coach-profile/photo").file(liar)
                        .header("Authorization", coachBearer))
                .andExpect(status().isBadRequest());
    }

    /** Un format que le serveur ne sait pas lire est refusé avec ce qu'il faut faire. */
    @Test
    void anUnsupportedFormatIsRefusedWithSomethingActionable() throws Exception {
        MockMultipartFile heic = new MockMultipartFile("file", "photo.heic", "image/heic",
                new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c' });

        String body = mvc.perform(multipart("/me/coach-profile/photo").file(heic)
                        .header("Authorization", coachBearer))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("JPEG");
    }

    /**
     * Remplacer une photo lui donne une nouvelle adresse : l'identifiant fait partie de l'URL,
     * et c'est ce qui autorise un cache long sans jamais avoir à l'invalider.
     */
    @Test
    void replacingThePhotoChangesItsAddress() throws Exception {
        String first = upload(png(300, 300)).get("photoUrl").asText();
        String second = upload(png(400, 400)).get("photoUrl").asText();

        assertThat(second).isNotEqualTo(first);
        mvc.perform(get(second)).andExpect(status().isOk());
    }

    @Test
    void removingThePhotoLeavesTheProfileWithoutOne() throws Exception {
        upload(png(300, 300));

        JsonNode after = objectMapper.readTree(mvc.perform(delete("/me/coach-profile/photo")
                        .header("Authorization", coachBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(after.get("photoUrl").isNull()).isTrue();
    }

    /**
     * Une fiche en validation est gelée, photo comprise — sinon l'administrateur validerait un
     * visage et la fiche en publierait un autre.
     */
    @Test
    void aPendingProfileRefusesAPhotoChange() throws Exception {
        completeAndSubmit();

        mvc.perform(multipart("/me/coach-profile/photo").file(imagePart(png(300, 300)))
                        .header("Authorization", coachBearer))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ utilitaires

    private JsonNode upload(byte[] image) throws Exception {
        return objectMapper.readTree(mvc.perform(multipart("/me/coach-profile/photo")
                        .file(imagePart(image)).header("Authorization", coachBearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private MockMultipartFile imagePart(byte[] image) {
        return new MockMultipartFile("file", "photo.png", "image/png", image);
    }

    /** Un PNG réel, produit ici : un tableau d'octets arbitraire ne testerait pas le décodage. */
    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(20, 120, 130));
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private void completeAndSubmit() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/me/coach-profile")
                        .header("Authorization", coachBearer)
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
                        .content("{\"name\":\"Suivi\",\"amountCents\":9000,\"periodicity\":\"MONTHLY\","
                                + "\"active\":true,\"position\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/me/coach-profile/submit").header("Authorization", coachBearer))
                .andExpect(status().isOk());
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
