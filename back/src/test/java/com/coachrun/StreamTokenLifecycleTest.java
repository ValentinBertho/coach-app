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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le jeton de flux, de bout en bout : on le demande avec l'en-tête, on ouvre le flux avec lui,
 * et il ne sert plus à rien ensuite.
 *
 * <p><b>Ce que ce test garde fermé.</b> Le flux SSE du centre de notifications et l'ouverture
 * d'une pièce jointe portaient le jeton de session en paramètre d'URL. Une URL fuit — journaux
 * d'accès du relais, historique du navigateur, {@code Referer} — et ce jeton-là vaut une heure
 * sur <i>toute</i> l'API. Ici, ce qui circule dans l'URL ne vaut qu'une minute, qu'un usage, et
 * que pour la route qui l'a demandé.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class StreamTokenLifecycleTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String register(MockMvc mvc) throws Exception {
        JsonNode session = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"stream-%s@test.fr","password":"password123","fullName":"C",\
                                "termsAccepted": true, "clubName":"AC %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return session.get("accessToken").asText();
    }

    private String streamToken(MockMvc mvc, String bearer, String scope) throws Exception {
        String body = mvc.perform(post("/auth/stream-token")
                        .header("Authorization", "Bearer " + bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"" + scope + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void aStreamTokenOpensTheStreamOnce() throws Exception {
        MockMvc mvc = mockMvc();
        String bearer = register(mvc);
        String token = streamToken(mvc, bearer, "STREAM");

        mvc.perform(get("/notifications/stream").param("stream_token", token))
                .andExpect(status().is2xxSuccessful());

        // Rejouer l'URL — ce que permettrait un journal d'accès — ne donne plus rien.
        mvc.perform(get("/notifications/stream").param("stream_token", token))
                .andExpect(status().isUnauthorized());
    }

    /** Émettre un jeton demande d'être authentifié : c'est une requête ordinaire, avec en-tête. */
    @Test
    void issuingAStreamTokenRequiresTheSessionHeader() throws Exception {
        mockMvc().perform(post("/auth/stream-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"STREAM\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Le jeton de flux ne vaut <b>que</b> pour les routes qui ne peuvent pas porter d'en-tête. Il
     * n'ouvre pas l'API : sans cela, on aurait juste remplacé un jeton de session d'une heure par
     * un jeton de session d'une minute.
     */
    @Test
    void aStreamTokenOpensNothingElse() throws Exception {
        MockMvc mvc = mockMvc();
        String bearer = register(mvc);

        mvc.perform(get("/auth/me").param("stream_token", streamToken(mvc, bearer, "STREAM")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/notifications/unread-count")
                        .param("stream_token", streamToken(mvc, bearer, "STREAM")))
                .andExpect(status().isUnauthorized());
    }

    /** Un jeton de pièce jointe n'ouvre pas un flux. */
    @Test
    void scopesDoNotCrossOver() throws Exception {
        MockMvc mvc = mockMvc();
        String bearer = register(mvc);

        mvc.perform(get("/notifications/stream")
                        .param("stream_token", streamToken(mvc, bearer, "ATTACHMENT")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * L'envoi d'une pièce jointe poste sur la même adresse que son téléchargement. Un jeton
     * d'URL, qui traîne par nature, ne doit pas pouvoir écrire.
     */
    @Test
    void aStreamTokenNeverWrites() throws Exception {
        MockMvc mvc = mockMvc();
        String bearer = register(mvc);

        mvc.perform(post("/me/messages/attachment")
                        .param("stream_token", streamToken(mvc, bearer, "ATTACHMENT")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aForgedTokenOpensNothing() throws Exception {
        mockMvc().perform(get("/notifications/stream").param("stream_token", "aaaaaaaaaaaa.inventé"))
                .andExpect(status().isUnauthorized());
    }

    /** La réponse annonce la durée de vie, pour que le client sache qu'il doit demander tard. */
    @Test
    void theResponseAdvertisesAShortLifetime() throws Exception {
        MockMvc mvc = mockMvc();
        String body = mvc.perform(post("/auth/stream-token")
                        .header("Authorization", "Bearer " + register(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"STREAM\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(json.get("expiresIn").asLong())
                .as("un jeton de flux se compte en secondes, pas en heures")
                .isBetween(1L, 300L);
        assertThat(json.get("token").asText()).isNotBlank();
    }
}
