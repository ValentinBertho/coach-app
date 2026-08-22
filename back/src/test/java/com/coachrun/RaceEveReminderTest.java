package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.RaceObjective;
import com.coachrun.entity.enums.NotificationCategory;
import com.coachrun.entity.enums.RacePriority;
import com.coachrun.repository.RaceObjectiveRepository;
import com.coachrun.scheduler.ReminderScheduler;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.NotificationService;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La veille d'une course.
 *
 * <p><b>Le défaut.</b> Le point de programme du soir ne regardait que les séances et le
 * renforcement. Une course n'étant ni l'un ni l'autre, l'athlète basculait dans le lot du repos
 * et recevait, à 21 h la veille de sa course, « Repos demain — rien de prévu ». Littéralement
 * faux le seul soir de l'année où ça compte.</p>
 *
 * <p>Le rappel de course J-1 existait pourtant déjà, mais le matin et sur un autre canal : les
 * deux services s'ignoraient. C'est le point que ces tests verrouillent — le soir aussi, une
 * course est une journée occupée.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RaceEveReminderTest {

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ReminderScheduler scheduler;
    @Autowired private RaceObjectiveRepository raceRepository;
    @Autowired private com.coachrun.repository.AthleteRepository athleteRepository;
    @Autowired private com.coachrun.repository.NotificationRepository notificationRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Une course demain range l'athlète parmi les occupés : plus aucune annonce de repos. */
    @Test
    void aRaceTomorrowIsNotARestDay() throws Exception {
        Athlete athlete = demoAthlete();
        raceTomorrow(athlete, "Corrida du repos", 15_000, RacePriority.C);

        scheduler.sweepTomorrow();

        assertThat(typesOf(notifications()))
                .as("le soir d'avant une course, on n'annonce pas le repos")
                .doesNotContain("WORKOUT_REMINDER");
    }

    /** Et il reçoit bien quelque chose : le silence serait une régression, pas un correctif. */
    @Test
    void theEveOfARaceIsAnnounced() throws Exception {
        Athlete athlete = demoAthlete();
        raceTomorrow(athlete, "Foulees du Cambout", 15_000, RacePriority.C);

        scheduler.sweepTomorrow();

        var eve = raceEveFor("Foulees du Cambout");
        assertThat(eve).as("une notification de veille de course est déposée").isNotNull();
        assertThat(eve.getBody())
                .as("elle nomme la course et sa distance")
                .contains("Foulees du Cambout")
                .contains("15 km");
        assertThat(eve.getLink())
                .as("le lien désigne LA course, sinon l'anti-rafale masquerait la suivante")
                .startsWith("/athlete/races?race=");
    }

    /**
     * Le ton suit la priorité : annoncer « c'est LE jour » pour une course C que l'athlète court
     * en préparation sonnerait faux — et c'est le meilleur moyen de faire ignorer les suivantes.
     */
    @Test
    void theToneFollowsTheRacePriority() throws Exception {
        Athlete athlete = demoAthlete();
        raceTomorrow(athlete, "Objectif majeur", 42_195, RacePriority.A);

        scheduler.sweepTomorrow();

        assertThat(raceEveFor("Objectif majeur").getTitle())
                .as("une course A mérite son emphase")
                .contains("le jour");
    }

    /** Le chrono visé, quand il existe, fait partie de ce qu'on relit la veille. */
    @Test
    void theTargetTimeIsRecalledWhenSet() throws Exception {
        Athlete athlete = demoAthlete();
        RaceObjective race = raceTomorrow(athlete, "Semi de controle", 21_100, RacePriority.B);
        race.setTargetTimeS(3900); // 1 h 05
        raceRepository.save(race);

        scheduler.sweepTomorrow();

        assertThat(raceEveFor("Semi de controle").getBody()).contains("1 h 05");
    }

    /**
     * Le réglage de notification : la veille de course se coupe avec les rappels, jamais avec le
     * suivi. Sans cette ligne, couper les alertes d'analyse ferait taire la course.
     */
    @Test
    void theRaceEveIsAReminderNotAnAnalysisAlert() {
        assertThat(NotificationCategory.of("RACE_EVE")).isEqualTo(NotificationCategory.RAPPELS);
    }

    /** Une course annulée ou déjà courue ne réveille personne. */
    @Test
    void onlyUpcomingRacesAreAnnounced() throws Exception {
        Athlete athlete = demoAthlete();
        RaceObjective race = raceTomorrow(athlete, "Course annulee", 10_000, RacePriority.B);
        race.setStatus(com.coachrun.entity.enums.RaceObjectiveStatus.CANCELLED);
        raceRepository.save(race);

        scheduler.sweepTomorrow();

        assertThat(raceEveFor("Course annulee"))
                .as("une course annulée ne réveille personne")
                .isNull();
    }

    // --- Utilitaires --------------------------------------------------------------------------
    //
    // Les libellés sont en ASCII : MockMvc lit le corps de réponse dans le jeu de caractères par
    // défaut de la plateforme, et une comparaison accentuée éprouverait l'encodage du harnais.

    private Athlete demoAthlete() throws Exception {
        String id = login(DemoSeedService.ATHLETE_EMAIL).get("user").get("athleteId").asText();
        return athleteRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
    }

    private RaceObjective raceTomorrow(Athlete athlete, String name, int distanceM,
                                       RacePriority priority) {
        RaceObjective race = new RaceObjective();
        race.setClub(athlete.getClub());
        race.setAthlete(athlete);
        race.setName(name);
        race.setRaceDate(LocalDate.now().plusDays(1));
        race.setDistanceM(distanceM);
        race.setPriority(priority);
        return raceRepository.save(race);
    }

    private JsonNode login(String email) throws Exception {
        return objectMapper.readTree(mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\""
                                + DemoSeedService.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /**
     * Notifications de l'athlète de démonstration, lues <b>en base</b>.
     *
     * <p>Et non par l'API : le centre de notifications est paginé — le jeu de démonstration en
     * crée assez pour repousser celle qu'on cherche hors de la première page — et MockMvc relit
     * le corps dans le jeu de caractères par défaut de la plateforme. Ni la pagination ni
     * l'encodage ne sont ce que ces tests éprouvent.</p>
     */
    private java.util.List<com.coachrun.entity.Notification> notifications() throws Exception {
        java.util.UUID userId = java.util.UUID.fromString(
                login(DemoSeedService.ATHLETE_EMAIL).get("user").get("id").asText());
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId,
                        org.springframework.data.domain.PageRequest.of(0, 200))
                .getContent();
    }

    private java.util.List<String> typesOf(java.util.List<com.coachrun.entity.Notification> list) {
        return list.stream().map(com.coachrun.entity.Notification::getType).toList();
    }

    /**
     * La veille de course portant ce nom, ou {@code null}.
     *
     * <p>Repérée par son contenu et non par son seul type : l'écriture des notifications échappe
     * au rollback du test, si bien que le centre garde celles des tests précédents. Chercher « la
     * première RACE_EVE » retournerait celle d'un autre scénario.</p>
     */
    private com.coachrun.entity.Notification raceEveFor(String raceName) throws Exception {
        return notifications().stream()
                .filter(n -> "RACE_EVE".equals(n.getType()))
                .filter(n -> n.getBody() != null && n.getBody().contains(raceName))
                .findFirst()
                .orElse(null);
    }

    /** Rappel de ce que la préparation raconte : trois nombres, ou rien du tout. */
    @Test
    void anEmptyPreparationSaysNothingRatherThanZero() {
        assertThat(new NotificationService.RacePrep(12, 0, 0).isEmpty()).isTrue();
        assertThat(new NotificationService.RacePrep(12, 61, 806).isEmpty()).isFalse();
    }
}
