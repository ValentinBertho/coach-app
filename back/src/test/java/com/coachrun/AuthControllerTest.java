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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void register_login_me_flow() throws Exception {
        MockMvc mvc = mockMvc();
        String registerBody = """
                {"email":"coach@test.fr","password":"password123","fullName":"Test Coach","clubName":"Team Test"}
                """;

        String response = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.role").value("HEAD_COACH"))
                .andExpect(jsonPath("$.user.clubId").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String token = json.get("accessToken").asText();

        // /auth/me sans token → 401
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());

        // /auth/me avec token → 200
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("coach@test.fr"));

        // login
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"coach@test.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void logoutRevokesToken() throws Exception {
        MockMvc mvc = mockMvc();
        String registerBody = """
                {"email":"logout-%s@test.fr","password":"password123","fullName":"X","clubName":"L %s"}
                """.formatted(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        JsonNode json = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andReturn().getResponse().getContentAsString());
        String token = json.get("accessToken").asText();

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        // token révoqué → 401
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        MockMvc mvc = mockMvc();
        String body = """
                {"email":"dup@test.fr","password":"password123","fullName":"Dup","clubName":"Dup Club"}
                """;
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        MockMvc mvc = mockMvc();
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nope@test.fr\",\"password\":\"whatever123\"}"))
                .andExpect(status().isUnauthorized());
    }
}
