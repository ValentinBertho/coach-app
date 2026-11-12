package com.coachrun;

import com.coachrun.security.RefreshCookie;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La déconnexion, sur un VRAI PostgreSQL.
 *
 * <p><b>Pourquoi ce test ne peut pas tourner sur H2.</b> {@code AuthService} est en lecture seule
 * par défaut ({@code @Transactional(readOnly = true)} sur la classe). {@code closeSession} n'avait
 * pas d'annotation propre : la révocation du jeton de rafraîchissement s'exécutait donc dans une
 * transaction en lecture seule. PostgreSQL refuse alors l'UPDATE (« cannot execute UPDATE in a
 * read-only transaction ») ; H2 l'accepte.</p>
 *
 * <p>En production, {@code POST /auth/logout} renvoyait 500 et la session restait ouverte : le
 * cookie de rafraîchissement continuait d'obtenir de nouveaux jetons. {@code RefreshSessionTest}
 * couvre le même parcours, mais sur H2, et restait vert.</p>
 *
 * <p>Ne s'exécute qu'avec {@code -Dpgtest=true} et un PostgreSQL joignable (cf.
 * {@code application-pgtest.yml}).</p>
 */
@SpringBootTest
@ActiveProfiles("pgtest")
@EnabledIfSystemProperty(named = "pgtest", matches = "true")
class LogoutOnPostgresTest {

    @Autowired private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void logoutClosesTheSessionOfThisBrowser() throws Exception {
        String token = mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"pg-logout-%s@test.fr","password":"password1234","fullName":"C",\
                                "termsAccepted": true, "clubName":"AC %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie(RefreshCookie.NAME).getValue();

        mvc.perform(post("/auth/logout").cookie(new Cookie(RefreshCookie.NAME, token)))
                .andExpect(status().isNoContent());

        int refreshStatus = mvc.perform(post("/auth/refresh").cookie(new Cookie(RefreshCookie.NAME, token)))
                .andReturn().getResponse().getStatus();
        assertThat(refreshStatus).as("le cookie d'une session fermée n'ouvre plus rien").isEqualTo(401);
    }
}
