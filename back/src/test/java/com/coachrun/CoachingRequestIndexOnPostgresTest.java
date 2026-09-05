package com.coachrun;

import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.CoachingRequest;
import com.coachrun.entity.User;
import com.coachrun.entity.enums.CoachingRequestStatus;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.repository.AthleteAccountRepository;
import com.coachrun.repository.CoachingRequestRepository;
import com.coachrun.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L'index unique partiel des demandes de coaching, sur un VRAI PostgreSQL.
 *
 * <h2>Le trou que ce fichier comble</h2>
 *
 * <p>{@code ux_coaching_requests_pending} interdit deux demandes <b>en attente</b> pour le même
 * couple (athlète, coach). Il est déclaré {@code dbms: postgresql} dans la migration 101, parce
 * que H2 — sur lequel tourne toute la suite — refuse la clause {@code WHERE} d'un index. Il
 * n'était donc <b>exercé nulle part</b> : la migration l'annonçait, et rien ne prouvait qu'il
 * fonctionnait.</p>
 *
 * <p>Ce n'est pas une redite de la garde applicative. {@code CoachingRequestService} vérifie
 * l'absence de demande en attente avant d'écrire, ce qui couvre le cas normal et rend un message
 * lisible à l'athlète. L'index, lui, ferme la fenêtre que cette vérification laisse ouverte :
 * <b>deux requêtes simultanées</b> lisent toutes deux « aucune demande en attente », et écrivent
 * toutes deux. Une seule doit survivre, et c'est la base qui doit le dire.</p>
 *
 * <p>Ce que protège cet index n'est pas cosmétique : sans lui, un athlète pressé remplit la file
 * d'un coach de lignes identiques, et une file illisible est une file qu'on cesse d'ouvrir.</p>
 *
 * <p>Ne s'exécute qu'avec {@code -Dpgtest=true} et un PostgreSQL joignable (cf.
 * {@code application-pgtest.yml}). La CI le rejoue à chaque poussée.</p>
 */
@SpringBootTest
@ActiveProfiles("pgtest")
@EnabledIfSystemProperty(named = "pgtest", matches = "true")
@Transactional
class CoachingRequestIndexOnPostgresTest {

    @Autowired private CoachingRequestRepository requestRepository;
    @Autowired private AthleteAccountRepository accountRepository;
    @Autowired private UserRepository userRepository;

    private AthleteAccount account;
    private User coach;

    @BeforeEach
    void setUp() {
        coach = userRepository.save(newUser(UserRole.COACH));
        AthleteAccount a = new AthleteAccount();
        a.setUser(userRepository.save(newUser(UserRole.ATHLETE)));
        a.setFirstName("Nina");
        a.setLastName("Test");
        a.setBirthDate(LocalDate.now().minusYears(30));
        account = accountRepository.save(a);
    }

    /**
     * Deux demandes en attente pour le même couple : la seconde est refusée par la base.
     *
     * <p>Écrit par les dépôts, sans passer par le service : c'est justement la garde applicative
     * qu'on court-circuite ici, pour éprouver celle qui reste quand deux requêtes simultanées
     * l'ont franchie ensemble.</p>
     */
    @Test
    void theDatabaseRefusesASecondPendingRequestForTheSamePair() {
        requestRepository.saveAndFlush(pending());

        assertThatThrownBy(() -> requestRepository.saveAndFlush(pending()))
                .as("l'index partiel ferme la fenêtre de course que la garde applicative laisse")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Le pendant, et la raison d'être du mot « partiel ».
     *
     * <p>Une relation qui a mal commencé n'est pas une relation impossible : un athlète refusé
     * doit pouvoir redemander plus tard. Un index unique <em>total</em> le lui interdirait à vie —
     * ce qui serait une punition, là où le refus n'était qu'une réponse.</p>
     */
    @Test
    void aDeclinedRequestDoesNotBlockANewOne() {
        CoachingRequest declined = pending();
        declined.setStatus(CoachingRequestStatus.DECLINED);
        declined.setDecidedAt(Instant.now());
        requestRepository.saveAndFlush(declined);

        assertThatCode(() -> requestRepository.saveAndFlush(pending()))
                .as("après un refus, l'athlète peut redemander")
                .doesNotThrowAnyException();
    }

    /** Un retrait laisse la porte ouverte pour la même raison. */
    @Test
    void aWithdrawnRequestDoesNotBlockANewOne() {
        CoachingRequest withdrawn = pending();
        withdrawn.setStatus(CoachingRequestStatus.WITHDRAWN);
        requestRepository.saveAndFlush(withdrawn);

        assertThatCode(() -> requestRepository.saveAndFlush(pending()))
                .as("l'athlète qui s'est ravisé peut revenir")
                .doesNotThrowAnyException();
    }

    /** L'index porte sur le COUPLE : un même athlète peut solliciter plusieurs coachs à la fois. */
    @Test
    void theSameAthleteMayHavePendingRequestsWithDifferentCoaches() {
        requestRepository.saveAndFlush(pending());

        User otherCoach = userRepository.save(newUser(UserRole.COACH));
        CoachingRequest toAnother = pending();
        toAnother.setCoach(otherCoach);

        assertThatCode(() -> requestRepository.saveAndFlush(toAnother))
                .as("chercher un coach, c'est en solliciter plusieurs")
                .doesNotThrowAnyException();
        assertThat(requestRepository.countByAthleteAccountIdAndStatus(
                account.getId(), CoachingRequestStatus.PENDING)).isEqualTo(2);
    }

    private CoachingRequest pending() {
        CoachingRequest request = new CoachingRequest();
        request.setAthleteAccount(account);
        request.setCoach(coach);
        request.setStatus(CoachingRequestStatus.PENDING);
        request.setExpiresAt(Instant.now().plus(Duration.ofDays(14)));
        return request;
    }

    private User newUser(UserRole role) {
        User user = new User();
        user.setEmail(role.name().toLowerCase() + "-" + UUID.randomUUID() + "@exemple.fr");
        user.setPasswordHash("x");
        user.setFullName("Test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
