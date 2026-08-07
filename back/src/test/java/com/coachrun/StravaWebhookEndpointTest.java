package com.coachrun;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * L'URL de rappel Strava, vue du réseau : elle doit être atteignable sans authentification et
 * acquitter tout ce qui arrive.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StravaWebhookEndpointTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Le défi de validation, que Strava lance une fois, au moment de créer l'abonnement. */
    @Test
    void leDefiDeValidationEstReleveSansAuthentification() throws Exception {
        mvc().perform(get("/public/strava/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.challenge", "defi-strava")
                        .param("hub.verify_token", "jeton-de-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['hub.challenge']").value("defi-strava"));
    }

    /** Sans le bon jeton, la validation est refusée : l'adresse ne se laisse pas revendiquer. */
    @Test
    void unDefiSansLeBonJetonEstRefuse() throws Exception {
        mvc().perform(get("/public/strava/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.challenge", "defi-strava")
                        .param("hub.verify_token", "jeton-invente"))
                .andExpect(status().isForbidden());
    }

    /**
     * Un événement s'acquitte, toujours et vite. Un 4xx ou un 5xx compte comme une remise en échec
     * côté Strava, et une série d'échecs coupe l'abonnement pour <b>tous</b> les athlètes — un
     * contenu inattendu ne vaut pas cette peine-là.
     */
    @Test
    void unEvenementEstToujoursAcquitte() throws Exception {
        mvc().perform(post("/public/strava/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"object_type\":\"activity\",\"aspect_type\":\"create\","
                                + "\"object_id\":123,\"owner_id\":999999,\"subscription_id\":1}"))
                .andExpect(status().isOk());

        // Contenu que l'on ne sait pas lire : c'est un non-événement, pas une panne à signaler.
        mvc().perform(post("/public/strava/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner_id\":\"pas-un-nombre\",\"updates\":\"pas-un-objet\"}"))
                .andExpect(status().isOk());

        mvc().perform(post("/public/strava/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
