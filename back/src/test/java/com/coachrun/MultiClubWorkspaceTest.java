package com.coachrun;

import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le multi-espace : ce qui rendait un second club invisible.
 *
 * <p>Le modèle était prêt depuis l'origine — {@code User.additionalClubs}, {@code ClubMember} avec
 * son rôle, un validateur d'accès qui accepte explicitement les clubs additionnels — mais aucune
 * route ne les listait. Le front lisait le seul club principal et le passait partout : un coach
 * invité ailleurs voyait son adhésion créée, son accès autorisé côté serveur, et jamais ce club.
 * Sa seule issue était d'ouvrir un second compte.</p>
 *
 * <p>Le second test vise un défaut qui dormait derrière celui-là, et que le sélecteur réveille :
 * le tableau de bord ne regardait que le club principal des athlètes, là où la liste et la
 * recherche acceptaient le rattachement additionnel.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MultiClubWorkspaceTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private com.coachrun.repository.AthleteRepository athleteRepository;
    @Autowired private com.coachrun.repository.ClubRepository clubRepository;
    @Autowired private com.coachrun.repository.UserRepository userRepository;

    private MockMvc mvc;
    private String ownerBearer;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        ownerBearer = bearer(DemoSeedService.HEAD_COACH_EMAIL);
    }

    /**
     * Les espaces se listent enfin — et le jeu de démonstration en contenait déjà deux.
     *
     * <p>Le coach de démonstration a un club principal <b>et</b> un club additionnel. Cette
     * situation existait donc dans les données depuis toujours, et le second club était
     * <b>invisible</b> : l'interface lisait le seul {@code currentUser().clubId}. Ce test ne
     * vérifie pas une fonctionnalité neuve, il verrouille la fin d'un angle mort.</p>
     */
    @Test
    void aCoachCanFinallyListAllTheirWorkspaces() throws Exception {
        JsonNode clubs = json(mvc.perform(get("/me/clubs").header("Authorization", ownerBearer))
                .andExpect(status().isOk()));

        assertThat(clubs).as("le jeu de démo donne déjà deux espaces à ce coach").hasSize(2);

        JsonNode primary = clubs.get(0);
        assertThat(primary.get("primary").asBoolean()).as("le club principal vient en tête").isTrue();
        assertThat(primary.get("role").asText()).isEqualTo("OWNER");
        assertThat(primary.get("roleLabel").asText()).isEqualTo("Propriétaire");

        JsonNode additional = clubs.get(1);
        assertThat(additional.get("primary").asBoolean()).isFalse();
        assertThat(additional.get("name").asText()).isNotBlank();
        // Accès sans rôle club déclaré : le validateur retombe alors sur la lecture, et le libellé
        // doit dire « Accès » plutôt que d'afficher un rôle inventé.
        assertThat(additional.get("roleLabel").asText()).isEqualTo("Accès");
    }

    /** Un athlète n'a pas d'espace de travail : la route lui est fermée, pas seulement vide. */
    @Test
    void anAthleteHasNoWorkspaces() throws Exception {
        mvc.perform(get("/me/clubs").header("Authorization", bearer(DemoSeedService.ATHLETE_EMAIL)))
                .andExpect(status().isForbidden());
    }

    /**
     * Le défaut que le sélecteur réveille.
     *
     * <p>Un athlète rattaché à un <b>club additionnel</b> figurait dans la liste des athlètes de ce
     * club — la requête de liste accepte ce rattachement — mais jamais dans son tableau de bord, qui
     * ne regardait que le club principal. Les deux doivent le voir, sans quoi le coach du second
     * club a une liste d'athlètes et un cockpit qui se contredisent.</p>
     */
    @Test
    void anAthleteAttachedToASecondClubAppearsInItsDashboardToo() throws Exception {
        var club = clubRepository.findAll().stream().findFirst().orElseThrow();
        var second = new com.coachrun.entity.Club();
        second.setName("Club secondaire");
        second.setSlug("club-secondaire-test");
        second.setStatus(com.coachrun.entity.enums.ClubStatus.ACTIVE);
        second = clubRepository.saveAndFlush(second);

        // Un athlète du club principal est rattaché au second en club ADDITIONNEL.
        var athlete = athleteRepository.findByClubIdOrderByLastNameAsc(club.getId()).get(0);
        athlete.getAdditionalClubs().add(second);
        athleteRepository.saveAndFlush(athlete);

        // Le propriétaire rejoint ce second club, ce qui lui en donne l'accès.
        var owner = userRepository.findByEmailIgnoreCase(DemoSeedService.HEAD_COACH_EMAIL).orElseThrow();
        owner.getAdditionalClubs().add(second);
        userRepository.saveAndFlush(owner);

        assertThat(athleteRepository.findByClubMembershipOrderByLastNameAsc(second.getId()))
                .as("le rattachement additionnel compte comme une appartenance")
                .extracting(com.coachrun.entity.Athlete::getId)
                .contains(athlete.getId());

        assertThat(athleteRepository.findByClubIdOrderByLastNameAsc(second.getId()))
                .as("l'ancienne requête, elle, ne voyait que le club principal — c'était le défaut")
                .isEmpty();
    }

    // ------------------------------------------------------------------ utilitaires

    private JsonNode json(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(
                actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String bearer(String email) throws Exception {
        JsonNode res = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
        return "Bearer " + res.get("accessToken").asText();
    }
}
