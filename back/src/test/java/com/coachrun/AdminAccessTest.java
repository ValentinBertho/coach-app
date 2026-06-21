package com.coachrun;

import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.UserRepository;
import com.coachrun.security.JwtService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AdminAccessTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String registerCoachToken(MockMvc mvc) throws Exception {
        String body = """
                {"email":"adm-%s@test.fr","password":"password123","fullName":"C","clubName":"AdmC %s"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString());
        return auth.get("accessToken").asText();
    }

    private String adminToken() {
        User admin = new User();
        admin.setEmail("admin-" + UUID.randomUUID() + "@test.fr");
        admin.setFullName("Admin Test");
        admin.setRole(UserRole.PLATFORM_ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        admin = userRepository.save(admin);
        return jwtService.generateAccessToken(admin);
    }

    @Test
    void nonAdminCannotAccessAdmin() throws Exception {
        MockMvc mvc = mockMvc();
        String coachToken = registerCoachToken(mvc);
        mvc.perform(get("/admin/stats").header("Authorization", "Bearer " + coachToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mockMvc().perform(get("/admin/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminCanAccessStatsAndManageClubs() throws Exception {
        MockMvc mvc = mockMvc();
        String token = adminToken();

        mvc.perform(get("/admin/stats").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clubs").exists());

        mvc.perform(post("/admin/clubs").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Club Admin Test\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").exists());
    }

    @Test
    void resetIsForbiddenWhenDisabled() throws Exception {
        // En profil test, app.demo.reset.enabled est false par défaut → 403 même pour l'admin.
        MockMvc mvc = mockMvc();
        String token = adminToken();
        mvc.perform(post("/admin/demo/reset").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}
