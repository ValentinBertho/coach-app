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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un changement de mot de passe met fin aux sessions ouvertes.
 *
 * <p>Sans ça, réinitialiser son mot de passe ne chassait personne : les refresh tokens déjà émis
 * (TTL 30 jours) restaient valables, y compris celui qu'on cherchait à révoquer.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class SessionRevocationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private JsonNode register(MockMvc mvc, String prefix) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s-%s@test.fr","password":"password123","fullName":"C",\
                                "termsAccepted": true, "clubName":"AC %s"}
                                """.formatted(prefix, UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void changingThePasswordInvalidatesTokensIssuedBefore() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode session = register(mvc, "rev");
        String oldAccess = session.get("accessToken").asText();
        String oldRefresh = session.get("refreshToken").asText();

        // Le jeton fonctionne avant le changement.
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isOk());

        // Une seconde d'écart : le « iat » d'un JWT n'a pas de précision plus fine.
        Thread.sleep(1100);
        mvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + oldAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"nouveaumdp456\"}"))
                .andExpect(status().isNoContent());

        // L'ancien access token ne passe plus…
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + oldAccess))
                .andExpect(status().isUnauthorized());
        // …et l'ancien refresh token non plus : c'est lui qui vivait 30 jours.
        mvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aFreshLoginWorksRightAfterThePasswordChange() throws Exception {
        MockMvc mvc = mockMvc();
        JsonNode session = register(mvc, "rev2");
        String email = session.get("user").get("email").asText();
        String access = session.get("accessToken").asText();

        Thread.sleep(1100);
        mvc.perform(post("/auth/change-password")
                        .header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"password123\",\"newPassword\":\"nouveaumdp456\"}"))
                .andExpect(status().isNoContent());

        JsonNode fresh = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"nouveaumdp456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        mvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + fresh.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    /**
     * Le jeton en paramètre de requête n'est accepté que là où l'en-tête est impossible (SSE,
     * pièce jointe ouverte dans un onglet). Partout ailleurs il fuit dans les journaux d'accès,
     * l'historique du navigateur et le Referer.
     */
    @Test
    void queryParameterTokenIsRejectedOnOrdinaryRoutes() throws Exception {
        MockMvc mvc = mockMvc();
        String access = register(mvc, "qp").get("accessToken").asText();

        mvc.perform(get("/auth/me").param("access_token", access))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/me/today").param("access_token", access))
                .andExpect(status().isUnauthorized());

        // Sur un flux, il reste accepté : EventSource ne peut pas porter d'en-tête.
        mvc.perform(get("/notifications/stream").param("access_token", access))
                .andExpect(status().is2xxSuccessful());
    }
}
