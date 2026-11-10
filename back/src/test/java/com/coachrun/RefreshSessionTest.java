package com.coachrun;

import com.coachrun.repository.RefreshTokenRepository;
import com.coachrun.security.RefreshCookie;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sessions à jetons de rafraîchissement en base, dans un cookie protégé (audit du 10/11/2026, B3).
 *
 * <p>Avant : jeton JWT de trente jours rangé dans le {@code localStorage} (lisible par tout script
 * de la page), et liste des jetons consommés en mémoire — vidée à chaque redémarrage, ce qui
 * rendait de nouveau rejouable tout jeton volé.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class RefreshSessionTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private MockHttpServletResponse register() throws Exception {
        return mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"sess-%s@test.fr","password":"password123","fullName":"C",\
                                "termsAccepted": true, "clubName":"AC %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated()).andReturn().getResponse();
    }

    private MockHttpServletResponse refresh(String token) throws Exception {
        return mvc.perform(post("/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, token)))
                .andReturn().getResponse();
    }

    @Test
    void theRefreshTokenTravelsOnlyInAProtectedCookie() throws Exception {
        MockHttpServletResponse res = register();

        JsonNode body = objectMapper.readTree(res.getContentAsString());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.path("refreshToken").isNull() || body.path("refreshToken").isMissingNode())
                .as("le jeton de rafraîchissement ne doit plus apparaître dans le corps JSON").isTrue();

        String setCookie = res.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .startsWith(RefreshCookie.NAME + "=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/auth");
    }

    @Test
    void onlyAHashIsStored() throws Exception {
        String raw = register().getCookie(RefreshCookie.NAME).getValue();

        assertThat(refreshTokenRepository.findAll())
                .noneMatch(t -> t.getTokenHash().equals(raw))
                .anyMatch(t -> t.getTokenHash().length() == 64);
    }

    @Test
    void eachRefreshRotatesTheToken() throws Exception {
        String first = register().getCookie(RefreshCookie.NAME).getValue();

        MockHttpServletResponse rotated = refresh(first);
        assertThat(rotated.getStatus()).isEqualTo(200);
        String second = rotated.getCookie(RefreshCookie.NAME).getValue();
        assertThat(second).isNotEqualTo(first);
        assertThat(objectMapper.readTree(rotated.getContentAsString()).get("accessToken").asText()).isNotBlank();

        assertThat(refresh(second).getStatus()).isEqualTo(200);
    }

    /**
     * Rejeu : l'ancien jeton revient après rotation. Quelqu'un d'autre l'a eu entre les mains ;
     * on ne sait pas qui est légitime, donc toute la session tombe — y compris le jeton le plus
     * récent, celui que détient peut-être le voleur.
     */
    @Test
    void replayingAConsumedTokenRevokesTheWholeSession() throws Exception {
        String first = register().getCookie(RefreshCookie.NAME).getValue();
        String second = refresh(first).getCookie(RefreshCookie.NAME).getValue();

        MockHttpServletResponse replay = refresh(first);
        assertThat(replay.getStatus()).isEqualTo(401);
        // Le cookie mort est effacé côté navigateur.
        assertThat(replay.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        assertThat(refresh(second).getStatus())
                .as("le jeton issu de la rotation tombe avec sa session").isEqualTo(401);
    }

    @Test
    void otherSessionsSurviveAReplayOnOneDevice() throws Exception {
        MockHttpServletResponse res = register();
        String email = objectMapper.readTree(res.getContentAsString()).get("user").get("email").asText();
        String phone = res.getCookie(RefreshCookie.NAME).getValue();
        String laptop = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(RefreshCookie.NAME).getValue();

        refresh(phone);
        refresh(phone); // rejeu sur le téléphone

        assertThat(refresh(laptop).getStatus()).isEqualTo(200);
    }

    /** La déconnexion ferme la session du cookie, même si le jeton d'accès a déjà expiré. */
    @Test
    void logoutWithoutAccessTokenStillClosesTheSession() throws Exception {
        String token = register().getCookie(RefreshCookie.NAME).getValue();

        MockHttpServletResponse out = mvc.perform(post("/auth/logout").cookie(new Cookie(RefreshCookie.NAME, token)))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(out.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");

        assertThat(refresh(token).getStatus()).isEqualTo(401);
    }

    @Test
    void anUnknownOrMissingTokenIsRejected() throws Exception {
        assertThat(refresh("inconnu").getStatus()).isEqualTo(401);
        assertThat(mvc.perform(post("/auth/refresh")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
