package com.coachrun;

import com.coachrun.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Vérification d'e-mail : inscription non vérifiée → confirmation par jeton → vérifié. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmailVerificationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    @Test
    void registerIsUnverifiedThenConfirms() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        String email = "verify-" + UUID.randomUUID() + "@test.fr";

        JsonNode reg = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\","
                                + "\"fullName\":\"Coach V\",\"clubName\":\"V " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.emailVerified").value(false))
                .andReturn().getResponse().getContentAsString());
        String bearer = "Bearer " + reg.get("accessToken").asText();

        // Le jeton est lisible dans la même transaction de test.
        String token = userRepository.findByEmailIgnoreCase(email).orElseThrow().getVerifyToken();
        assertThat(token).isNotNull();

        mvc.perform(post("/public/verify-email/{t}", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        // /me reflète l'e-mail vérifié, et le jeton est consommé.
        mvc.perform(get("/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailVerified").value(true));
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getVerifyToken()).isNull();

        // Jeton à usage unique : rejoué → 404.
        mvc.perform(post("/public/verify-email/{t}", token)).andExpect(status().isNotFound());
    }
}
