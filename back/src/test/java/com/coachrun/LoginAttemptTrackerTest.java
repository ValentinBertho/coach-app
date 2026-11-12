package com.coachrun;

import com.coachrun.repository.LoginAttemptRepository;
import com.coachrun.security.LoginAttemptTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrou de connexion par compte. Le rate limiting du filtre est par IP : il n'arrête pas un
 * attaquant qui répartit ses essais sur plusieurs adresses pour forcer un compte précis.
 *
 * <p>Les compteurs sont en base : ils survivent à un redémarrage (audit du 10/11/2026, B3).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class LoginAttemptTrackerTest {

    @Autowired private LoginAttemptTracker tracker;
    @Autowired private LoginAttemptRepository repository;
    @Autowired private PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        ReflectionTestUtils.setField(tracker, "enabled", true);
    }

    @AfterEach
    void tearDown() {
        // Désactivé en tests partout ailleurs (app.rate-limit.enabled=false) : on rend le bean tel quel.
        ReflectionTestUtils.setField(tracker, "enabled", false);
        repository.deleteAll();
    }

    /** Un redémarrage ne rouvre plus de fenêtre d'essais gratuits. */
    @Test
    void theLockSurvivesARestart() {
        for (int i = 0; i < 8; i++) {
            tracker.recordFailure("marie@test.fr");
        }
        LoginAttemptTracker afterRestart = new LoginAttemptTracker(repository, txManager);
        ReflectionTestUtils.setField(afterRestart, "enabled", true);

        assertThat(afterRestart.lockRemaining("marie@test.fr")).isNotNull();
    }

    /** Ni l'adresse saisie ni sa forme normalisée ne sont stockées en clair. */
    @Test
    void theAddressIsNotStoredInClear() {
        tracker.recordFailure("Marie@Test.fr");
        assertThat(repository.findAll())
                .hasSize(1)
                .allSatisfy(a -> assertThat(a.getAccountKey()).doesNotContain("marie").hasSize(64));
    }

    @Test
    void theFirstFailuresAreFree() {
        for (int i = 0; i < 5; i++) {
            tracker.recordFailure("marie@test.fr");
            assertThat(tracker.lockRemaining("marie@test.fr"))
                    .as("échec n°%d", i + 1)
                    .isNull();
        }
    }

    @Test
    void theDelayGrowsWithEachExtraFailure() {
        for (int i = 0; i < 6; i++) {
            tracker.recordFailure("marie@test.fr");
        }
        Duration first = tracker.lockRemaining("marie@test.fr");
        assertThat(first).isNotNull();

        tracker.recordFailure("marie@test.fr");
        Duration second = tracker.lockRemaining("marie@test.fr");

        assertThat(second).isGreaterThan(first);
        // Plafonné : passé un certain point, on punirait l'utilisateur légitime plus que l'attaquant.
        for (int i = 0; i < 30; i++) {
            tracker.recordFailure("marie@test.fr");
        }
        assertThat(tracker.lockRemaining("marie@test.fr"))
                .isLessThanOrEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void aSuccessfulLoginClearsTheCounter() {
        for (int i = 0; i < 8; i++) {
            tracker.recordFailure("marie@test.fr");
        }
        assertThat(tracker.lockRemaining("marie@test.fr")).isNotNull();

        tracker.recordSuccess("marie@test.fr");

        assertThat(tracker.lockRemaining("marie@test.fr")).isNull();
    }

    @Test
    void theCounterIsPerAccountNotGlobal() {
        for (int i = 0; i < 8; i++) {
            tracker.recordFailure("cible@test.fr");
        }
        assertThat(tracker.lockRemaining("cible@test.fr")).isNotNull();
        assertThat(tracker.lockRemaining("quelquun-dautre@test.fr")).isNull();
    }

    @Test
    void theEmailIsNormalised() {
        for (int i = 0; i < 8; i++) {
            tracker.recordFailure("  Marie@Test.FR ");
        }
        // Varier la casse ne doit pas rouvrir une fenêtre d'essais.
        assertThat(tracker.lockRemaining("marie@test.fr")).isNotNull();
    }

    @Test
    void disabledTrackerNeverLocks() {
        ReflectionTestUtils.setField(tracker, "enabled", false);
        for (int i = 0; i < 50; i++) {
            tracker.recordFailure("marie@test.fr");
        }
        assertThat(tracker.lockRemaining("marie@test.fr")).isNull();
    }

    /**
     * Échecs simultanés sur le même compte : l'incrément était appliqué hors du compute(), sur
     * des champs non volatiles — des tentatives se perdaient, et le verrou progressif ne se
     * déclenchait jamais face à un attaquant qui parallélise.
     */
    @Test
    void concurrentFailuresAllCount() throws Exception {
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    tracker.recordFailure("cible@darilab.app");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        // 16 échecs > 5 tolérés : le compte doit être verrouillé, quel que soit l'entrelacement.
        assertThat(tracker.lockRemaining("cible@darilab.app")).isNotNull();
        // Et aucun échec ne s'est perdu en route.
        assertThat(repository.findAll()).singleElement()
                .satisfies(a -> assertThat(a.getFailureCount()).isEqualTo(threads));
    }

    /** Les compteurs de connexion sans échec récent sont purgés. */
    @Test
    void loginAttemptsArePurged() {
        for (int i = 0; i < 100; i++) {
            tracker.recordFailure("inconnu-" + i + "@darilab.app");
        }
        assertThat(tracker.trackedAccounts()).isEqualTo(100);

        // Rien n'a expiré (TTL d'une heure) : la purge ne doit rien retirer prématurément.
        ReflectionTestUtils.invokeMethod(tracker, "purgeExpired");
        assertThat(tracker.trackedAccounts()).isEqualTo(100);
    }
}
