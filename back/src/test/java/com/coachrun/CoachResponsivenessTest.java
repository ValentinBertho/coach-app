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
import com.coachrun.service.CoachResponsivenessService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La réactivité d'un coach — le signal qui tient lieu d'avis au lancement.
 *
 * <p>C'est un chiffre affiché sur une page publique, à côté du nom d'une personne réelle, et c'est
 * ce qui rend ces règles sérieuses : un délai calculé sur une seule demande, une moyenne écrasée
 * par un départ en stage, ou un taux que fait chuter le retrait d'un athlète seraient des mesures
 * fausses portant l'autorité d'une mesure.</p>
 *
 * <p>Les tests écrivent de vraies demandes en base et remontent leur date de création par requête
 * native : {@code BaseEntity.onCreate()} pose {@code created_at} sans condition, et le délai qu'on
 * veut mesurer est précisément l'écart entre cette date et la décision.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoachResponsivenessTest {

    @Autowired private CoachResponsivenessService service;
    @Autowired private CoachingRequestRepository requestRepository;
    @Autowired private AthleteAccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManager entityManager;

    private User coach;
    private AthleteAccount account;

    @BeforeEach
    void setUp() {
        coach = userRepository.save(newUser("coach-" + UUID.randomUUID() + "@exemple.fr",
                UserRole.COACH));
        User athleteUser = userRepository.save(newUser("athlete-" + UUID.randomUUID() + "@exemple.fr",
                UserRole.ATHLETE));
        AthleteAccount a = new AthleteAccount();
        a.setUser(athleteUser);
        a.setFirstName("Nina");
        a.setLastName("Test");
        a.setBirthDate(LocalDate.now().minusYears(30));
        account = accountRepository.save(a);
    }

    /** Un coach qui a répondu une fois en dix minutes n'est pas « très réactif » : il a eu une demande. */
    @Test
    void nothingIsPublishedBelowTheMinimumSample() {
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofHours(3), CoachingRequestStatus.DECLINED);

        assertThat(service.medianResponseHours(coach.getId()))
                .as("deux demandes, sous le seuil de %d : aucun chiffre", CoachResponsivenessService.MIN_SAMPLE)
                .isNull();
        assertThat(service.responseRatePercent(coach.getId()))
                .as("le taux se tait pour la même raison")
                .isNull();
    }

    /**
     * La médiane, et non la moyenne : c'est toute la différence entre décrire un coach et le
     * condamner pour être parti trois semaines en stage.
     *
     * <p>Deux, trois, quatre, cinq heures, puis trois cents. La moyenne dirait « 63 h » — un délai
     * que cet athlète-là n'a jamais vécu. La médiane dit 4 h, et c'est ce à quoi il faut
     * s'attendre.</p>
     */
    @Test
    void theMedianIgnoresTheOneLongAbsence() {
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofHours(3), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofHours(4), CoachingRequestStatus.DECLINED);
        answeredIn(Duration.ofHours(5), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofHours(300), CoachingRequestStatus.ACCEPTED);

        assertThat(service.medianResponseHours(coach.getId()))
                .as("médiane de 2, 3, 4, 5 et 300 h — la moyenne dirait 63 h")
                .isEqualTo(4);
    }

    /** « Répond en 0 h » ne veut rien dire : une réponse immédiate se dit « moins d'une heure ». */
    @Test
    void anImmediateAnswerIsNeverZeroHours() {
        answeredIn(Duration.ofMinutes(4), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofMinutes(9), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofMinutes(20), CoachingRequestStatus.DECLINED);

        assertThat(service.medianResponseHours(coach.getId()))
                .as("arrondi à l'heure supérieure, jamais à zéro")
                .isEqualTo(1);
    }

    /**
     * Le silence pèse sur le taux, jamais sur le délai.
     *
     * <p>Trois réponses en 2 h et une demande laissée filer : le coach est rapide <b>quand</b> il
     * répond, et il ne répond pas toujours. Confondre les deux — en comptant l'expiration comme un
     * délai de quatorze jours — aurait effacé la première moitié de la phrase.</p>
     */
    @Test
    void anExpiredRequestLowersTheRateWithoutTouchingTheDelay() {
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.DECLINED);
        undecided(CoachingRequestStatus.EXPIRED);

        assertThat(service.medianResponseHours(coach.getId()))
                .as("une expiration n'est pas un délai très long, c'est une absence de délai")
                .isEqualTo(2);
        assertThat(service.responseRatePercent(coach.getId()))
                .as("trois réponses sur quatre demandes qu'il pouvait trancher")
                .isEqualTo(75);
    }

    /**
     * Un retrait ne compte nulle part — c'est le geste de l'athlète, pas celui du coach.
     *
     * <p>Le défaut qu'on ferme ici : le dénominateur comptait d'abord toutes les demandes closes,
     * retraits compris. Un athlète qui change d'avis au bout d'une heure faisait donc baisser le
     * taux affiché d'un coach qui n'avait rien fait de mal, sur sa page publique.</p>
     */
    @Test
    void aWithdrawalDoesNotCountAgainstTheCoach() {
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.DECLINED);
        undecided(CoachingRequestStatus.WITHDRAWN);

        assertThat(service.responseRatePercent(coach.getId()))
                .as("l'athlète a repris sa demande : rien ne dit que le coach n'allait pas répondre")
                .isEqualTo(100);
    }

    /** Une demande encore ouverte n'est ni une réponse ni un silence : elle n'a pas encore d'histoire. */
    @Test
    void aPendingRequestIsCountedNowhere() {
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.ACCEPTED);
        answeredIn(Duration.ofHours(2), CoachingRequestStatus.ACCEPTED);
        undecided(CoachingRequestStatus.PENDING);

        assertThat(service.medianResponseHours(coach.getId()))
                .as("une demande encore ouverte ne dit rien : le coach a peut-être répondu depuis")
                .isNull();
        assertThat(service.responseRatePercent(coach.getId()))
                .as("elle ne gonfle pas non plus le dénominateur")
                .isNull();
    }

    // ---------------------------------------------------------------- fabrique

    /** Une demande tranchée après le délai donné. */
    private void answeredIn(Duration delay, CoachingRequestStatus status) {
        CoachingRequest request = persist(status);
        Instant createdAt = Instant.now().minus(delay).minus(Duration.ofMinutes(1));
        // `created_at` est `updatable = false` et posé par @PrePersist : seule une requête native
        // permet de dater la demande dans le passé, ce qui est tout l'objet de la mesure.
        entityManager.createNativeQuery(
                        "update coaching_requests set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", request.getId())
                .executeUpdate();
        request.setDecidedAt(createdAt.plus(delay));
        requestRepository.saveAndFlush(request);
        entityManager.clear();
    }

    /** Une demande sans décision : en attente, périmée ou retirée. */
    private void undecided(CoachingRequestStatus status) {
        persist(status);
        entityManager.flush();
        entityManager.clear();
    }

    private CoachingRequest persist(CoachingRequestStatus status) {
        CoachingRequest request = new CoachingRequest();
        request.setAthleteAccount(account);
        request.setCoach(coach);
        request.setStatus(status);
        // Une demande périmée doit l'être vraiment : dater son échéance dans le futur aurait laissé
        // en base un état que la production ne produit jamais.
        request.setExpiresAt(status == CoachingRequestStatus.EXPIRED
                ? Instant.now().minus(Duration.ofDays(1))
                : Instant.now().plus(Duration.ofDays(14)));
        return requestRepository.saveAndFlush(request);
    }

    private User newUser(String email, UserRole role) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("x");
        user.setFullName("Test");
        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        return user;
    }
}
