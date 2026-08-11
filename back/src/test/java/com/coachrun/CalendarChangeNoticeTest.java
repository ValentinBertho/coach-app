package com.coachrun;

import com.coachrun.entity.Notification;
import com.coachrun.entity.User;
import com.coachrun.service.ClockService;
import com.coachrun.service.DemoSeedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le coach remanie le calendrier, l'athlète est prévenu.
 *
 * <p>Créer, déplacer, modifier et supprimer une séance <b>course</b> notifiaient déjà. Deux
 * chemins restaient muets, et ce sont les deux plus lourds de conséquences :</p>
 * <ul>
 *   <li>« Adapter » — l'écran par lequel le coach réécrit le <b>contenu</b> d'une séance course,
 *       passe un 6 × 400 en 8 × 400, change les allures : l'athlète partait courir la séance
 *       qu'il avait lue la veille, pas celle qui était désormais prescrite ;</li>
 *   <li>le <b>renforcement</b> déplacé ou déprogrammé : le poser prévenait, le retirer non —
 *       l'athlète se présentait en salle pour une séance qui n'existait plus.</li>
 * </ul>
 *
 * <p>Le canal ne vaut que s'il ne crie pas pour rien : ces tests fixent aussi ce qui ne notifie
 * <b>pas</b> — enregistrer sans avoir rien changé, ou remanier une séance déjà passée.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CalendarChangeNoticeTest {

    private static final String CHANGE = "WORKOUT_UPDATED";

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ClockService clock;
    @Autowired private com.coachrun.repository.UserRepository userRepository;
    @Autowired private com.coachrun.repository.NotificationRepository notificationRepository;

    private MockMvc mvc;
    private String coach;
    private String clubId;
    private String athleteId;
    private UUID athleteUserId;

    /** Deux structures réellement différentes : 6 × 400 puis 8 × 400. */
    private static final String SIX_TIMES_400 = structure(6);
    private static final String EIGHT_TIMES_400 = structure(8);

    private static String structure(int reps) {
        return """
                {"warmup":[],"main":[{"id":"b1","type":"intervals","reps":%d,"distanceM":400}],
                 "cooldown":[]}
                """.formatted(reps);
    }

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        JsonNode auth = objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + DemoSeedService.HEAD_COACH_EMAIL + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        coach = "Bearer " + auth.get("accessToken").asText();

        User athleteUser = userRepository.findByEmailIgnoreCase(DemoSeedService.ATHLETE_EMAIL).orElseThrow();
        athleteUserId = athleteUser.getId();
        athleteId = athleteUser.getAthlete().getId().toString();
        clubId = athleteUser.getClub().getId().toString();
    }

    /** Le coach crée une séance course pour l'athlète de démonstration, et rend son identifiant. */
    private String createWorkout(LocalDate date) throws Exception {
        String body = objectMapper.readTree(mvc.perform(
                        post("/clubs/{c}/athletes/{a}/workouts", clubId, athleteId)
                                .header("Authorization", coach)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"scheduledDate\":\"" + date + "\",\"type\":\"INTERVALS\","
                                        + "\"title\":\"Fractionné\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
        return body;
    }

    private void putStructure(String workoutId, String structure) throws Exception {
        mvc.perform(put("/clubs/{c}/athletes/{a}/workouts/{w}/structure", clubId, athleteId, workoutId)
                        .header("Authorization", coach)
                        .contentType(MediaType.APPLICATION_JSON).content(structure))
                .andExpect(status().isOk());
    }

    /** Notifications de changement de séance déposées pour l'athlète. */
    private List<Notification> changeNotices() {
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(athleteUserId, PageRequest.of(0, 50))
                .stream().filter(n -> CHANGE.equals(n.getType())).toList();
    }

    @Test
    void reecrireLeContenuDUneSeanceAVenirPrevientLAthlete() throws Exception {
        String workoutId = createWorkout(clock.today().plusDays(2));

        putStructure(workoutId, SIX_TIMES_400);

        assertThat(changeNotices()).hasSize(1);
        assertThat(changeNotices().get(0).getTitle()).isEqualTo("Séance modifiée");
    }

    /**
     * Ouvrir l'éditeur puis enregistrer sans rien toucher est un geste courant : le notifier
     * dévaluerait le canal qu'on cherche justement à garder crédible.
     */
    @Test
    void enregistrerSansRienChangerNeNotifiePas() throws Exception {
        String workoutId = createWorkout(clock.today().plusDays(2));
        putStructure(workoutId, SIX_TIMES_400);

        // On efface la trace du premier envoi : sans cela, c'est l'anti-rafale de 30 minutes qui
        // répondrait à notre place, et le test ne dirait rien de la garde qu'il prétend fixer.
        notificationRepository.deleteAll(changeNotices());
        notificationRepository.flush();

        putStructure(workoutId, SIX_TIMES_400);

        assertThat(changeNotices()).isEmpty();
    }

    /** Une vraie réécriture, elle, prévient — la garde porte sur l'égalité, pas sur la répétition. */
    @Test
    void changerLeContenuUneSecondeFoisPrevientANouveau() throws Exception {
        String workoutId = createWorkout(clock.today().plusDays(2));
        putStructure(workoutId, SIX_TIMES_400);
        notificationRepository.deleteAll(changeNotices());
        notificationRepository.flush();

        putStructure(workoutId, EIGHT_TIMES_400);

        assertThat(changeNotices()).hasSize(1);
    }

    /**
     * Remanier une séance passée est du ménage de calendrier : elle n'a plus à être courue, il
     * n'y a rien à annoncer.
     */
    @Test
    void remanierUneSeancePasseeNeNotifiePas() throws Exception {
        String workoutId = createWorkout(clock.today().minusDays(3));

        putStructure(workoutId, SIX_TIMES_400);

        assertThat(changeNotices()).isEmpty();
    }

    // --- Renforcement : le calendrier de force obéit aux mêmes règles ---------------------------

    /** La séance de force à venir posée par le jeu de démonstration. */
    private String scheduledStrengthId() throws Exception {
        JsonNode list = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/scheduled", clubId, athleteId)
                                .param("from", clock.today().toString())
                                .param("to", clock.today().plusDays(30).toString())
                                .header("Authorization", coach))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(list).isNotEmpty();
        return list.get(0).get("id").asText();
    }

    @Test
    void deplacerUneSeanceDeRenforcementPrevientLAthlete() throws Exception {
        String scheduledId = scheduledStrengthId();

        mvc.perform(patch("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/reschedule", clubId, athleteId, scheduledId)
                        .header("Authorization", coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + clock.today().plusDays(4) + "\"}"))
                .andExpect(status().isOk());

        assertThat(changeNotices()).hasSize(1);
        assertThat(changeNotices().get(0).getTitle()).isEqualTo("Séance de renforcement déplacée");
    }

    @Test
    void deprogrammerUneSeanceDeRenforcementPrevientLAthlete() throws Exception {
        String scheduledId = scheduledStrengthId();

        mvc.perform(delete("/clubs/{c}/athletes/{a}/pp/scheduled/{s}", clubId, athleteId, scheduledId)
                        .header("Authorization", coach))
                .andExpect(status().isNoContent());

        assertThat(changeNotices()).hasSize(1);
        assertThat(changeNotices().get(0).getTitle()).isEqualTo("Séance de renforcement annulée");
    }

    /** Reposer la même date n'est pas un déplacement : rien à annoncer. */
    @Test
    void reposerUneSeanceDeRenforcementSurSaDateNeNotifiePas() throws Exception {
        String scheduledId = scheduledStrengthId();
        JsonNode current = objectMapper.readTree(mvc.perform(
                        get("/clubs/{c}/athletes/{a}/pp/scheduled", clubId, athleteId)
                                .param("from", clock.today().toString())
                                .param("to", clock.today().plusDays(30).toString())
                                .header("Authorization", coach))
                .andReturn().getResponse().getContentAsString()).get(0);

        mvc.perform(patch("/clubs/{c}/athletes/{a}/pp/scheduled/{s}/reschedule", clubId, athleteId, scheduledId)
                        .header("Authorization", coach)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scheduledDate\":\"" + current.get("scheduledDate").asText() + "\"}"))
                .andExpect(status().isOk());

        assertThat(changeNotices()).isEmpty();
    }
}
