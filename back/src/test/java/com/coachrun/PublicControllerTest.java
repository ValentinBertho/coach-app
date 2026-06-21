package com.coachrun;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie que l'endpoint public /api/public/ping répond sans authentification
 * et qu'une route protégée renvoie bien 401 (sécurité stateless câblée).
 */
@SpringBootTest
@ActiveProfiles("test")
class PublicControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void pingIsPublicAndReturnsOk() throws Exception {
        mockMvc().perform(get("/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.version").exists());
    }

    @Test
    void protectedRouteReturns401WithoutToken() throws Exception {
        mockMvc().perform(get("/clubs"))
                .andExpect(status().isUnauthorized());
    }
}
