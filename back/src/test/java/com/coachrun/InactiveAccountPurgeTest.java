package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.Club;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.scheduler.InactiveAccountPurgeScheduler;
import com.coachrun.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * La durée de conservation annoncée, appliquée.
 *
 * <p><b>Ce que ce test garde vrai.</b> La politique de confidentialité annonce qu'un compte resté
 * inactif vingt-quatre mois est supprimé « après un e-mail de préavis ». Rien ne l'appliquait
 * (L-20) : le texte publié promettait une durée que le code ne tenait pas. Les cas ci-dessous
 * sont, un à un, les promesses de cette phrase — et surtout celles qui protègent l'utilisateur :
 * jamais de suppression sans préavis, jamais avant la fin du préavis, et un retour suffit à tout
 * annuler.</p>
 *
 * <p>Le planificateur est joué à la main, à une date choisie : attendre 4 h 20 — ou deux ans —
 * n'est pas une option, et l'instant est la seule chose qui distingue un passage de nuit d'un
 * passage de test.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
// @MockBean force un contexte dédié : on l'isole sur sa propre base mémoire pour éviter que
// Liquibase ne rejoue les migrations sur la base partagée des autres tests.
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:inactive-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class InactiveAccountPurgeTest {

    /** Les valeurs de production : 24 mois d'inactivité, 30 jours de préavis. */
    private static final int RETENTION_DAYS = 730;
    private static final int NOTICE_DAYS = 30;

    @Autowired private InactiveAccountPurgeScheduler scheduler;
    @Autowired private UserRepository userRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired private AthleteRepository athleteRepository;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    /** Le préavis est un e-mail : on vérifie qu'il part, et ce qu'il annonce. */
    @MockBean private NotificationService notifications;

    private final Instant now = Instant.parse("2026-09-07T04:20:00Z");

    // ------------------------------------------------------------------ phase 1 : le préavis

    @Test
    void anAccountNearingTheDeadlineIsWarnedAndNotDeleted() {
        User coach = coachInactiveFor(RETENTION_DAYS - 20);

        var outcome = scheduler.runAt(now);

        assertThat(outcome.warned()).isEqualTo(1);
        assertThat(outcome.purged()).isZero();
        assertThat(userRepository.findById(coach.getId())).isPresent();
        assertThat(reload(coach).getInactivityWarnedAt()).isEqualTo(now);
        verify(notifications).notifyInactiveAccountWarning(any(), any());
    }

    /**
     * L'e-mail annonce la date qui sera <b>réellement</b> appliquée. Un compte déjà au-delà des
     * vingt-quatre mois au moment du premier préavis ne sera pas supprimé le lendemain : c'est la
     * fin du préavis qui décide, et c'est donc elle qu'il faut annoncer. Le cas n'est pas
     * théorique — c'est celui de tout l'arriéré, au premier passage en production.
     */
    @Test
    void theWarningAnnouncesTheDateThatWillActuallyApply() {
        coachInactiveFor(RETENTION_DAYS + 400);

        scheduler.runAt(now);

        LocalDate endOfNotice = LocalDate.ofInstant(
                now.plus(Duration.ofDays(NOTICE_DAYS)), java.time.ZoneId.of("Europe/Paris"));
        verify(notifications).notifyInactiveAccountWarning(any(), argThat(d -> d.equals(endOfNotice)));
    }

    /** Deux passages ne font pas deux préavis : la date d'envoi est notée, et elle suffit. */
    @Test
    void anAccountIsNotWarnedTwiceForTheSameInactivity() {
        coachInactiveFor(RETENTION_DAYS - 20);

        scheduler.runAt(now);
        var second = scheduler.runAt(now.plus(Duration.ofDays(1)));

        assertThat(second.warned()).isZero();
    }

    @Test
    void anActiveAccountIsLeftAlone() {
        User coach = coachInactiveFor(30);

        var outcome = scheduler.runAt(now);

        assertThat(outcome.warned()).isZero();
        assertThat(reload(coach).getInactivityWarnedAt()).isNull();
        verify(notifications, never()).notifyInactiveAccountWarning(any(), any());
    }

    // ------------------------------------------------------------------ phase 2 : la suppression

    /**
     * La garantie centrale : dépasser les vingt-quatre mois ne suffit pas, il faut avoir été
     * prévenu. Un planificateur resté à l'arrêt des mois ne rattrape donc pas son retard en
     * supprimant sans préavis — il commence par prévenir.
     */
    @Test
    void anAccountIsNeverDeletedWithoutHavingBeenWarned() {
        User coach = coachInactiveFor(RETENTION_DAYS + 100);

        var outcome = scheduler.runAt(now);

        assertThat(outcome.purged()).isZero();
        assertThat(outcome.warned()).isEqualTo(1);
        assertThat(userRepository.findById(coach.getId())).isPresent();
    }

    /** Et le préavis doit avoir couru jusqu'au bout. */
    @Test
    void anAccountIsNotDeletedBeforeTheNoticePeriodHasElapsed() {
        User coach = coachInactiveFor(RETENTION_DAYS + 100);
        warnedAgo(coach, NOTICE_DAYS - 1);

        var outcome = scheduler.runAt(now);

        assertThat(outcome.purged()).isZero();
        assertThat(userRepository.findById(coach.getId())).isPresent();
    }

    @Test
    void anAccountPastTheDeadlineAndTheNoticeIsDeleted() {
        User coach = coachInactiveFor(RETENTION_DAYS + 100);
        warnedAgo(coach, NOTICE_DAYS + 1);

        var outcome = scheduler.runAt(now);

        assertThat(outcome.purged()).isEqualTo(1);
        assertThat(userRepository.findById(coach.getId())).isEmpty();
    }

    /**
     * Se connecter suffit à tout annuler — et le préavis reçu avant ce retour ne compte plus.
     * Sans cette règle, quelqu'un qui revient, puis s'absente de nouveau deux ans, serait
     * supprimé en s'appuyant sur un e-mail vieux de quatre ans.
     */
    @Test
    void comingBackCancelsAWarningAlreadySent() {
        User coach = coachInactiveFor(RETENTION_DAYS + 100);
        warnedAgo(coach, NOTICE_DAYS + 1);
        // Le compte se reconnecte : sa dernière activité passe APRÈS le préavis.
        coach.setLastSeenAt(now.minus(Duration.ofDays(1)));
        userRepository.saveAndFlush(coach);

        var outcome = scheduler.runAt(now);

        assertThat(outcome.purged()).isZero();
        assertThat(userRepository.findById(coach.getId())).isPresent();
    }

    /**
     * Un athlète supprimé emporte sa fiche et tout son historique — c'est le même chemin que le
     * bouton « supprimer mon compte » de son profil. Supprimer le seul compte utilisateur
     * laisserait sa fiche, ses séances et ses mesures en base : une purge qui ne purge rien.
     */
    @Test
    void deletingAnAthleteAccountRemovesTheAthleteRecordToo() {
        Club club = club("Purge AC");
        Athlete athlete = athlete(club, "Ana", "Inactive");
        User user = user(club, UserRole.ATHLETE, "athlete-inactif");
        user.setAthlete(athlete);
        user.setLastSeenAt(now.minus(Duration.ofDays(RETENTION_DAYS + 100)));
        user.setInactivityWarnedAt(now.minus(Duration.ofDays(NOTICE_DAYS + 1)));
        userRepository.saveAndFlush(user);
        // Un coach actif, pour que le club ne soit pas vide (cf. le cas suivant).
        user(club, UserRole.HEAD_COACH, "coach-actif");

        var outcome = scheduler.runAt(now);

        // La cascade qui emporte le compte est posée sur la CLÉ ÉTRANGÈRE (users.athlete_id
        // ON DELETE CASCADE) : elle s'applique en base, pas dans le contexte de persistance.
        // Sans ce vidage, la relecture rendrait l'instance encore en mémoire et le test
        // passerait — ou échouerait — pour une raison qui n'est pas celle qu'il examine.
        entityManager.flush();
        entityManager.clear();

        assertThat(outcome.purged()).isEqualTo(1);
        assertThat(athleteRepository.findById(athlete.getId())).isEmpty();
        assertThat(userRepository.findById(user.getId())).isEmpty();
    }

    // ------------------------------------------------------------------ les exclusions

    /**
     * Un administrateur de plateforme n'est jamais purgé. Son compte n'est pas un compte d'usage
     * — il peut légitimement dormir un an — et le supprimer fermerait le back-office sans aucun
     * moyen de le rouvrir depuis l'application.
     */
    @Test
    void aPlatformAdminIsNeverPurged() {
        User admin = user(null, UserRole.PLATFORM_ADMIN, "admin-dormant");
        admin.setLastSeenAt(now.minus(Duration.ofDays(RETENTION_DAYS + 500)));
        admin.setInactivityWarnedAt(now.minus(Duration.ofDays(NOTICE_DAYS + 10)));
        userRepository.saveAndFlush(admin);

        var outcome = scheduler.runAt(now);

        assertThat(outcome.warned()).isZero();
        assertThat(outcome.purged()).isZero();
        assertThat(userRepository.findById(admin.getId())).isPresent();
    }

    /**
     * Le dernier encadrant d'un club qui contient encore quelqu'un est conservé, et signalé.
     * L'effacer ne libérerait rien — le club et ses athlètes resteraient — et il ne resterait
     * personne pour y accéder, ni pour exercer un droit dessus. Fermer un club est une décision,
     * pas une conséquence de calendrier.
     */
    @Test
    void theLastCoachOfANonEmptyClubIsKeptAndReported() {
        Club club = club("Club orphelin");
        User coach = user(club, UserRole.HEAD_COACH, "coach-dormant");
        coach.setLastSeenAt(now.minus(Duration.ofDays(RETENTION_DAYS + 100)));
        coach.setInactivityWarnedAt(now.minus(Duration.ofDays(NOTICE_DAYS + 1)));
        userRepository.saveAndFlush(coach);
        // Un athlète actif, rattaché à ce club : ses données survivraient au coach.
        Athlete athlete = athlete(club, "Bea", "Active");
        User athleteUser = user(club, UserRole.ATHLETE, "athlete-actif");
        athleteUser.setAthlete(athlete);
        userRepository.saveAndFlush(athleteUser);

        var outcome = scheduler.runAt(now);

        assertThat(outcome.purged()).isZero();
        assertThat(outcome.kept()).isEqualTo(1);
        assertThat(userRepository.findById(coach.getId())).isPresent();
    }

    /**
     * Un compte antérieur à la migration qui a introduit {@code last_seen_at} n'a pas de dernière
     * activité connue. Le lire seul le ferait passer pour inactif depuis toujours — donc
     * supprimable dès le premier passage. C'est sa date de création qui fait alors foi.
     */
    @Test
    void anAccountWithoutActivityDatesFallsBackOnItsCreationDate() {
        User coach = user(club("Sans historique"), UserRole.COACH, "sans-dates");
        assertThat(coach.getLastSeenAt()).isNull();
        assertThat(coach.getLastLoginAt()).isNull();

        var outcome = scheduler.runAt(now);

        assertThat(outcome.warned()).as("créé à l'instant : rien à purger").isZero();
        assertThat(userRepository.findById(coach.getId())).isPresent();
    }

    // ------------------------------------------------------------------ fabrique

    private User coachInactiveFor(int days) {
        User coach = user(club("AC " + UUID.randomUUID()), UserRole.HEAD_COACH, "coach");
        coach.setLastSeenAt(now.minus(Duration.ofDays(days)));
        return userRepository.saveAndFlush(coach);
    }

    private void warnedAgo(User user, int days) {
        user.setInactivityWarnedAt(now.minus(Duration.ofDays(days)));
        userRepository.saveAndFlush(user);
    }

    private User reload(User user) {
        userRepository.flush();
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private Club club(String name) {
        Club club = new Club();
        club.setName(name);
        club.setSlug("c-" + UUID.randomUUID());
        return clubRepository.saveAndFlush(club);
    }

    private Athlete athlete(Club club, String first, String last) {
        Athlete athlete = new Athlete();
        athlete.setClub(club);
        athlete.setFirstName(first);
        athlete.setLastName(last);
        return athleteRepository.saveAndFlush(athlete);
    }

    private User user(Club club, UserRole role, String prefix) {
        User user = new User();
        user.setClub(club);
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setFullName(prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@test.fr");
        return userRepository.saveAndFlush(user);
    }
}
