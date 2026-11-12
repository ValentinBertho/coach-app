package com.coachrun;

import com.coachrun.service.DemoSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La couche « présentation » du jeu de démonstration : ce qu'on montre en démo, en capture et en
 * vidéo.
 *
 * <p>Comme {@code seedHub()}, {@code seedShowcase()} ne tourne qu'au démarrage en profil dev :
 * cette classe est le seul endroit de la suite qui l'exécute. Chaque test vérifie un défaut
 * qu'un visiteur voyait à l'écran avant elle.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShowcaseDemoSeedTest {

    @Autowired private DemoSeedService demoSeedService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager em;

    private boolean created;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        demoSeedService.seedHub();
        created = demoSeedService.seedShowcase();
        // Les requêtes ci-dessous passent par JDBC, qui ne déclenche pas l'écriture de ce que
        // JPA garde encore en mémoire.
        em.flush();
    }

    @Test
    void runsOnceThenChangesNothing() {
        assertThat(created).isTrue();
        assertThat(demoSeedService.seedShowcase()).isFalse();
    }

    /** « Bonjour Coach Démo » : la coach a un vrai nom. */
    @Test
    void theDemoCoachHasARealName() {
        assertThat(jdbc.queryForObject("select full_name from users where email = ?", String.class,
                DemoSeedService.HEAD_COACH_EMAIL)).isEqualTo(DemoSeedService.SHOWCASE_COACH_NAME);
    }

    /** Plus d'une centaine de retours à traiter : il n'en reste qu'une poignée, tous récents. */
    @Test
    void onlyAFewRecentFeedbacksAreLeftToReview() {
        Integer open = jdbc.queryForObject("""
                select count(*) from workouts w join users u on u.club_id = w.club_id
                where u.email = ? and w.status = 'COMPLETED' and w.coach_reviewed_at is null""",
                Integer.class, DemoSeedService.HEAD_COACH_EMAIL);
        assertThat(open).isBetween(1, 10);
        Integer old = jdbc.queryForObject("""
                select count(*) from workouts w join users u on u.club_id = w.club_id
                where u.email = ? and w.status = 'COMPLETED' and w.coach_reviewed_at is null
                and w.scheduled_date < ?""",
                Integer.class, DemoSeedService.HEAD_COACH_EMAIL,
                Date.valueOf(LocalDate.now().minusDays(2)));
        assertThat(old).isZero();
    }

    /**
     * La messagerie s'ouvrait vide : les messages du jeu de base n'avaient pas de fil. Chaque
     * message créé ici en a un.
     */
    @Test
    void theCoachHasRealConversations() {
        Integer threads = jdbc.queryForObject("""
                select count(*) from conversations c join users u on u.id = c.coach_user_id
                where u.email = ? and c.kind = 'ATHLETE_COACH' and c.last_message_at is not null""",
                Integer.class, DemoSeedService.HEAD_COACH_EMAIL);
        assertThat(threads).isGreaterThanOrEqualTo(3);
    }

    /** Le calendrier s'arrêtait à la semaine en cours : la suivante est planifiée. */
    @Test
    void nextWeekIsPlanned() {
        Integer planned = jdbc.queryForObject("""
                select count(*) from workouts w join users u on u.club_id = w.club_id
                where u.email = ? and w.status = 'PLANNED' and w.scheduled_date > ?""",
                Integer.class, DemoSeedService.HEAD_COACH_EMAIL,
                Date.valueOf(LocalDate.now().plusDays(7)));
        assertThat(planned).isPositive();
    }

    /** La coach est dans l'annuaire, une demande l'attend, et l'annuaire a de quoi trier. */
    @Test
    void theCoachIsListedAndHasARequestWaiting() {
        Integer published = jdbc.queryForObject(
                "select count(*) from coach_profiles where status = 'PUBLISHED'", Integer.class);
        assertThat(published).isGreaterThanOrEqualTo(7);
        Integer pending = jdbc.queryForObject("""
                select count(*) from coaching_requests r join users u on u.id = r.coach_user_id
                where u.email = ? and r.status = 'PENDING'""",
                Integer.class, DemoSeedService.HEAD_COACH_EMAIL);
        assertThat(pending).isEqualTo(1);
    }
}
