package com.coachrun;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provisionnement du répertoire de préparation physique d'un club neuf.
 *
 * <p>Seules les zones étaient seedées : la bibliothèque d'exercices démarrait vide, et un
 * préparateur physique devait saisir à la main les quarante à soixante exercices de son répertoire
 * avant de pouvoir prescrire sa première séance de force. Plusieurs heures avant la première
 * valeur perçue, sur un produit qu'on essaie.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PpCatalogSeedTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mvc;
    private String bearer;
    private String clubId;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cat-%s@test.fr","password":"password123","fullName":"C",\
                                "termsAccepted": true, "clubName":"Catalogue %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        bearer = "Bearer " + auth.get("accessToken").asText();
        clubId = auth.get("user").get("clubId").asText();
    }

    private JsonNode exercises() throws Exception {
        return objectMapper.readTree(mvc.perform(get("/clubs/{c}/pp/exercises", clubId)
                        .header("Authorization", bearer).param("size", "200"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void aBrandNewClubOpensOnAUsableExerciseLibrary() throws Exception {
        JsonNode page = exercises();
        assertThat(page.get("content").size())
                .as("un club neuf ne doit pas ouvrir sa bibliothèque sur une page blanche")
                .isGreaterThanOrEqualTo(30);

        // Le répertoire vise le coureur : la chaîne postérieure et le pied-cheville doivent y être.
        boolean hasNordic = false;
        boolean hasCalf = false;
        for (JsonNode e : page.get("content")) {
            String name = e.get("name").asText();
            if (name.contains("Nordic")) hasNordic = true;
            if (name.toLowerCase().contains("mollet")) hasCalf = true;
        }
        assertThat(hasNordic).as("prévention ischios").isTrue();
        assertThat(hasCalf).as("triceps sural").isTrue();
    }

    /** Les exercices seedés portent leurs consignes : sans elles, il faut tout rouvrir pour prescrire. */
    @Test
    void seededExercisesCarryTheirCoachingCues() throws Exception {
        JsonNode first = exercises().get("content").get(0);
        assertThat(first.get("objective").asText()).isNotBlank();
        assertThat(first.get("instructions").asText()).isNotBlank();
        assertThat(first.get("muscleGroups").size()).isGreaterThan(0);
    }

    /** Idempotence : rouvrir la bibliothèque ne duplique rien. */
    @Test
    void reopeningTheLibraryDoesNotDuplicateTheCatalog() throws Exception {
        int first = exercises().get("content").size();
        assertThat(exercises().get("content").size()).isEqualTo(first);
    }
}
