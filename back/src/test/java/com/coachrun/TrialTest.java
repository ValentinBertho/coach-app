package com.coachrun;

import com.coachrun.entity.Club;
import com.coachrun.repository.ClubRepository;
import com.coachrun.trial.TrialService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * « Essayer sans inscription » : un club jetable par visiteur, isolé, borné, puis supprimé.
 */
@SpringBootTest
@ActiveProfiles("test")
class TrialTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ClubRepository clubRepository;
    @Autowired private TrialService trialService;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Une adresse IP par test : le plafond par adresse ne déborde pas d'un test sur l'autre. */
    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(1, 255);
    }

    private JsonNode start(MockMvc mvc, String ip) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/public/trial").with(from(ip)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private static String bearer(JsonNode auth) {
        return "Bearer " + auth.get("accessToken").asText();
    }

    @Test
    void aVisitorGetsTheirOwnFilledClubAsCoach() throws Exception {
        MockMvc mvc = mvc();
        mvc.perform(get("/public/trial")).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));

        JsonNode a = start(mvc, randomIp());
        JsonNode b = start(mvc, randomIp());
        String clubA = a.at("/user/clubId").asText();
        String clubB = b.at("/user/clubId").asText();

        assertThat(a.at("/user/role").asText()).isEqualTo("HEAD_COACH");
        assertThat(a.at("/user/email").asText()).endsWith(TrialService.EMAIL_SUFFIX);
        // Deux visiteurs, deux clubs : personne ne voit l'essai d'un autre.
        assertThat(clubA).isNotEqualTo(clubB);
        assertThat(a.at("/user/email").asText()).isNotEqualTo(b.at("/user/email").asText());

        // Rempli comme la démonstration : des athlètes, des séances.
        Integer athletes = jdbc.queryForObject("select count(*) from athletes where club_id = ?",
                Integer.class, UUID.fromString(clubA));
        Integer workouts = jdbc.queryForObject("select count(*) from workouts where club_id = ?",
                Integer.class, UUID.fromString(clubA));
        assertThat(athletes).isGreaterThan(3);
        assertThat(workouts).isGreaterThan(20);

        // Toutes les adresses du club sont des adresses d'essai : aucun e-mail ne peut partir.
        Integer realAddresses = jdbc.queryForObject(
                "select count(*) from users where club_id = ? and lower(email) not like ?",
                Integer.class, UUID.fromString(clubA), "%" + TrialService.EMAIL_SUFFIX);
        assertThat(realAddresses).isZero();

        // La coach travaille dans son club…
        mvc.perform(get("/clubs/{c}/athletes", clubA).header("Authorization", bearer(a)))
                .andExpect(status().isOk());
        // …et pas dans celui du voisin.
        mvc.perform(get("/clubs/{c}/athletes", clubB).header("Authorization", bearer(a)))
                .andExpect(status().isForbidden());

        // Aucune fiche dans l'annuaire public.
        Integer profiles = jdbc.queryForObject("""
                select count(*) from coach_profiles p join users u on u.id = p.user_id
                where u.club_id = ?""", Integer.class, UUID.fromString(clubA));
        assertThat(profiles).isZero();
    }

    @Test
    void theCoachCanSwitchToTheAthleteSideAndBack() throws Exception {
        MockMvc mvc = mvc();
        JsonNode coach = start(mvc, randomIp());

        JsonNode athlete = objectMapper.readTree(mvc.perform(post("/trial/switch")
                        .header("Authorization", bearer(coach)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(athlete.at("/user/role").asText()).isEqualTo("ATHLETE");
        assertThat(athlete.at("/user/clubId").asText()).isEqualTo(coach.at("/user/clubId").asText());
        mvc.perform(get("/me/today").header("Authorization", bearer(athlete)))
                .andExpect(status().isOk());

        JsonNode back = objectMapper.readTree(mvc.perform(post("/trial/switch")
                        .header("Authorization", bearer(athlete)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(back.at("/user/id").asText()).isEqualTo(coach.at("/user/id").asText());
    }

    @Test
    void aRealAccountCannotUseTheSwitch() throws Exception {
        MockMvc mvc = mvc();
        JsonNode real = objectMapper.readTree(mvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"vrai-%s@test.fr","password":"password1234","fullName":"C","termsAccepted": true, "clubName":"Vrai %s"}
                                """.formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/trial/switch").header("Authorization", bearer(real)))
                .andExpect(status().isForbidden());
    }

    /** Ce qui ferait sortir quelque chose de l'essai est refusé ; le reste fonctionne. */
    @Test
    void aTrialCannotReachOutsideItself() throws Exception {
        MockMvc mvc = mvc();
        JsonNode coach = start(mvc, randomIp());
        String club = coach.at("/user/clubId").asText();
        String athleteId = jdbc.queryForObject("select id from athletes where club_id = ? limit 1",
                String.class, UUID.fromString(club));

        // Inviter une vraie personne.
        mvc.perform(post("/clubs/{c}/members", club).header("Authorization", bearer(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"quelquun@exemple.fr\",\"fullName\":\"Q\",\"role\":\"COACH\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("essai")));
        mvc.perform(post("/clubs/{c}/athletes/{a}/invitation", club, athleteId)
                        .header("Authorization", bearer(coach)))
                .andExpect(status().isForbidden());
        // Toucher au compte.
        mvc.perform(post("/auth/change-password").header("Authorization", bearer(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"password12345\"}"))
                .andExpect(status().isForbidden());
        // Publier une fiche d'annuaire.
        mvc.perform(post("/me/coach-profile").header("Authorization", bearer(coach))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        // Côté athlète : écrire à un vrai coach, supprimer « son » compte.
        JsonNode athlete = objectMapper.readTree(mvc.perform(post("/trial/switch")
                        .header("Authorization", bearer(coach)))
                .andReturn().getResponse().getContentAsString());
        mvc.perform(post("/me/coaching-requests").header("Authorization", bearer(athlete))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/me").header("Authorization", bearer(athlete)))
                .andExpect(status().isForbidden());

        // Ce qu'on vient essayer marche : lire l'agenda, la bibliothèque.
        mvc.perform(get("/clubs/{c}/athletes/{a}/workouts", club, athleteId)
                        .header("Authorization", bearer(coach))
                        .param("from", "2020-01-01").param("to", "2030-01-01"))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTrialsAreDeletedWithEverythingInThem() throws Exception {
        MockMvc mvc = mvc();
        JsonNode coach = start(mvc, randomIp());
        UUID club = UUID.fromString(coach.at("/user/clubId").asText());
        JsonNode other = start(mvc, randomIp());
        UUID kept = UUID.fromString(other.at("/user/clubId").asText());

        Club c = clubRepository.findById(club).orElseThrow();
        c.setTrialExpiresAt(Instant.now().minusSeconds(60));
        clubRepository.saveAndFlush(c);

        trialService.purgeExpired();

        assertThat(clubRepository.findById(club)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from users where club_id = ?", Integer.class, club))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from athletes where club_id = ?", Integer.class, club))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from workouts where club_id = ?", Integer.class, club))
                .isZero();
        // L'essai encore en cours reste là.
        assertThat(clubRepository.findById(kept)).isPresent();
        // Et la session de l'essai supprimé ne mène plus à rien : le jeton d'accès vit encore
        // quelques minutes, sur un club vide ; il ne se renouvelle pas, le compte n'existe plus.
        mvc.perform(get("/clubs/{c}/athletes", club).header("Authorization", bearer(coach)))
                .andExpect(r -> assertThat(r.getResponse().getStatus() == 200
                        ? objectMapper.readTree(r.getResponse().getContentAsString()).get("totalElements").asInt()
                        : 0).isZero());
    }

    @Test
    void oneAddressCannotOpenTrialsWithoutLimit() throws Exception {
        MockMvc mvc = mvc();
        String ip = randomIp();
        for (int i = 0; i < 3; i++) {
            start(mvc, ip);
        }
        mvc.perform(post("/public/trial").with(from(ip))).andExpect(status().isTooManyRequests());
    }
}
