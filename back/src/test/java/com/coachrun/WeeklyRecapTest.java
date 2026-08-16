package com.coachrun;

import com.coachrun.entity.Activity;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.WorkoutRepository;
import com.coachrun.service.DemoSeedService;
import com.coachrun.service.WeeklyRecapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;


import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le bilan de la semaine — le premier message du produit qui ne réclame rien.
 *
 * <p>Vingt-sept types de notification, et pas un bilan : tout était événementiel. Ces tests
 * portent sur les trois décisions de calcul qui font qu'un bilan est juste ou vexant — ce qu'on
 * compte comme réalisé, d'où viennent les kilomètres, et dans quel sens se lit une sensation.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WeeklyRecapTest {

    @Autowired private WeeklyRecapService recapService;
    @Autowired private AthleteRepository athleteRepository;
    @Autowired private WorkoutRepository workoutRepository;
    @Autowired private ActivityRepository activityRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private DemoSeedService demoSeedService;

    /** Une semaine passée, close et stable : aucun test ne doit dépendre du jour où il tourne. */
    private static final LocalDate FROM = LocalDate.of(2026, 3, 2);   // lundi
    private static final LocalDate TO = LocalDate.of(2026, 3, 8);     // dimanche

    private Athlete athlete;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        var club = clubRepository.findAll().get(0);
        Athlete a = new Athlete();
        a.setClub(club);
        a.setFirstName("Bilan");
        a.setLastName("Semaine");
        athlete = athleteRepository.save(a);
    }

    private void workout(LocalDate date, WorkoutStatus status, Integer feel) {
        Workout w = new Workout();
        w.setClub(athlete.getClub());
        w.setAthlete(athlete);
        w.setScheduledDate(date);
        w.setType(WorkoutType.ENDURANCE);
        w.setTitle("Séance");
        w.setStatus(status);
        w.setFeel(feel);
        // Une cible volontairement énorme : elle ne doit jamais apparaître dans le bilan, qui ne
        // compte que ce qui a réellement été couru.
        w.setTargetDistanceM(99000);
        workoutRepository.save(w);
    }

    private void activity(LocalDate date, int distanceM) {
        Activity a = new Activity();
        a.setClub(athlete.getClub());
        a.setAthlete(athlete);
        a.setActivityDate(date);
        a.setSource(com.coachrun.entity.enums.ActivitySource.MANUAL);
        a.setTitle("Sortie");
        a.setDistanceM(distanceM);
        activityRepository.save(a);
    }

    /**
     * Une séance écourtée compte comme réalisée.
     *
     * <p>Le taux répond à « l'athlète s'est-il entraîné ? », pas à « a-t-il fait exactement ce qui
     * était écrit ? ». Traiter une sortie longue arrêtée à mi-parcours comme une séance manquée
     * découragerait précisément la déclaration honnête que le produit cherche à obtenir.</p>
     */
    @Test
    void aShortenedSessionStillCountsAsDone() {
        workout(FROM, WorkoutStatus.COMPLETED, null);
        workout(FROM.plusDays(2), WorkoutStatus.PARTIAL, null);
        workout(FROM.plusDays(4), WorkoutStatus.MISSED, null);
        workout(FROM.plusDays(5), WorkoutStatus.PLANNED, null);

        var recap = recapService.forAthlete(athlete.getId(), FROM, TO);

        assertThat(recap.done()).isEqualTo(2);
        // Une séance annulée reste une séance qui était prévue : sans quoi « 2 sur 2 »
        // récompenserait celui qui n'a rien fait.
        assertThat(recap.planned()).isEqualTo(4);
    }

    /**
     * Les kilomètres viennent des <b>activités</b>, jamais des cibles de séance : annoncer la
     * cible reviendrait à féliciter l'athlète pour ce qu'on lui avait demandé.
     */
    @Test
    void kilometresComeFromWhatWasRunNotFromWhatWasAsked() {
        workout(FROM, WorkoutStatus.COMPLETED, null);      // cible : 99 km
        activity(FROM, 12300);
        activity(FROM.plusDays(3), 8000);
        // Hors fenêtre : la semaine d'avant n'entre pas dans le bilan de celle-ci.
        activity(FROM.minusDays(1), 30000);

        var recap = recapService.forAthlete(athlete.getId(), FROM, TO);

        assertThat(recap.km()).isEqualTo(20.3);
    }

    /**
     * Le sens de l'échelle de sensation est contre-intuitif — <b>1 est la meilleure</b> — et une
     * tendance calculée à l'envers dirait à un athlète en progrès que ses sensations se dégradent.
     */
    @Test
    void aFallingFeelScoreIsAnImprovement() {
        // Semaine précédente : sensations moyennes (3).
        workout(FROM.minusWeeks(1), WorkoutStatus.COMPLETED, 3);
        workout(FROM.minusWeeks(1).plusDays(2), WorkoutStatus.COMPLETED, 3);
        // Semaine du bilan : sensations bonnes (2) — donc en hausse.
        workout(FROM, WorkoutStatus.COMPLETED, 2);
        workout(FROM.plusDays(2), WorkoutStatus.COMPLETED, 2);

        var recap = recapService.forAthlete(athlete.getId(), FROM, TO);

        assertThat(recap.feel()).isEqualTo(2.0);
        assertThat(recap.feelPrevious()).isEqualTo(3.0);
        assertThat(recap.feelTrend()).isEqualTo(1);
    }

    /** Sans semaine de référence, aucune tendance n'est annoncée — on ne devine pas. */
    @Test
    void noTrendIsClaimedWithoutAPreviousWeek() {
        workout(FROM, WorkoutStatus.COMPLETED, 2);

        var recap = recapService.forAthlete(athlete.getId(), FROM, TO);

        assertThat(recap.feelPrevious()).isNull();
        assertThat(recap.feelTrend()).isZero();
    }

    /**
     * Une semaine sans rien ne produit pas de bilan. « 0 km, 0 séance sur 0 » envoyé à quelqu'un
     * qui n'a pas couru n'est pas un bilan, c'est un reproche.
     */
    @Test
    void anEmptyWeekProducesNothingToSay() {
        var recap = recapService.forAthlete(athlete.getId(), FROM, TO);

        assertThat(recap.isEmpty()).isTrue();
    }

    /** Le bilan du coach : un taux de réalisation, et le compte de ce qui appelle une décision. */
    @Test
    void theCoachRecapReportsCompletionOverTheWholeGroup() {
        workout(FROM, WorkoutStatus.COMPLETED, null);
        workout(FROM.plusDays(1), WorkoutStatus.COMPLETED, null);
        workout(FROM.plusDays(2), WorkoutStatus.MISSED, null);
        workout(FROM.plusDays(3), WorkoutStatus.PLANNED, null);

        var recap = recapService.forCoach(List.of(athlete), FROM, TO, 1);

        assertThat(recap.athletes()).isEqualTo(1);
        assertThat(recap.done()).isEqualTo(2);
        assertThat(recap.planned()).isEqualTo(4);
        assertThat(recap.completionPct()).isEqualTo(50);
        assertThat(recap.toWatch()).isEqualTo(1);
    }

    /** Aucun athlète : pas de division par zéro, et rien à envoyer. */
    @Test
    void aCoachWithoutAthletesGetsNothing() {
        var recap = recapService.forCoach(List.<Athlete>of(), FROM, TO, 0);

        assertThat(recap.isEmpty()).isTrue();
        assertThat(recap.completionPct()).isZero();
    }
}
