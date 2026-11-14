package com.coachrun;

import com.coachrun.repository.UserRepository;
import com.coachrun.service.UnverifiedAccountPurgeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les comptes d'athlète jamais confirmés partent au bout de sept jours — et seulement eux.
 *
 * <p>La purge supprime des comptes sans intervention humaine : ce qu'elle doit épargner compte
 * autant que ce qu'elle supprime. Un compte confirmé, un compte récent, et un compte encore
 * utilisé (l'athlète qui a changé d'adresse sans la confirmer) restent en place.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UnverifiedAccountPurgeTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UnverifiedAccountPurgeService purgeService;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void abandonedUnverifiedAccountIsDeleted() throws Exception {
        String email = register();
        ageAccount(email, 10, null);

        purgeService.purge(Instant.now());

        assertThat(userRepository.findByEmailIgnoreCase(email))
                .as("jamais confirmé, lien expiré, aucune connexion : le compte doit partir")
                .isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM athlete_accounts a JOIN users u ON u.id = a.user_id "
                        + "WHERE u.email = ?", Integer.class, email))
                .isZero();
    }

    @Test
    void recentUnverifiedAccountIsKept() throws Exception {
        String email = register();

        purgeService.purge(Instant.now());

        assertThat(userRepository.findByEmailIgnoreCase(email))
                .as("le lien de vérification est encore valable : la personne peut encore confirmer")
                .isPresent();
    }

    @Test
    void verifiedAccountIsKept() throws Exception {
        String email = register();
        ageAccount(email, 30, null);
        jdbc.update("UPDATE users SET email_verified = true WHERE email = ?", email);

        purgeService.purge(Instant.now());

        assertThat(userRepository.findByEmailIgnoreCase(email)).isPresent();
    }

    @Test
    void unverifiedAccountStillInUseIsKept() throws Exception {
        String email = register();
        ageAccount(email, 30, Instant.now().minus(1, ChronoUnit.DAYS));

        purgeService.purge(Instant.now());

        assertThat(userRepository.findByEmailIgnoreCase(email))
                .as("connecté hier : c'est un athlète qui a changé d'adresse, pas un compte abandonné")
                .isPresent();
    }

    private String register() throws Exception {
        String email = "purge-" + UUID.randomUUID() + "@exemple.fr";
        mvc.perform(post("/public/athlete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password1234\","
                                + "\"firstName\":\"Lena\",\"lastName\":\"Purge\","
                                + "\"birthDate\":\"" + LocalDate.now().minusYears(28) + "\","
                                + "\"termsAccepted\":true,\"healthDataConsent\":true}"))
                .andExpect(status().isCreated());
        // L'inscription partage la transaction du test : sans flush, la ligne n'est pas encore en
        // base et les mises à jour SQL ci-dessous ne toucheraient rien.
        entityManager.flush();
        return email;
    }

    /** Vieillit le compte : création et expiration du lien dans le passé. */
    private void ageAccount(String email, int days, Instant lastLogin) {
        Instant created = Instant.now().minus(days, ChronoUnit.DAYS);
        jdbc.update("UPDATE users SET created_at = ?, verify_expires_at = ?, last_login_at = ? "
                        + "WHERE email = ?",
                Timestamp.from(created), Timestamp.from(created.plus(7, ChronoUnit.DAYS)),
                lastLogin == null ? null : Timestamp.from(lastLogin), email);
    }
}
