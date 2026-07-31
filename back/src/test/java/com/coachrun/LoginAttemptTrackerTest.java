package com.coachrun;

import com.coachrun.security.LoginAttemptTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrou de connexion par compte. Le rate limiting du filtre est par IP : il n'arrête pas un
 * attaquant qui répartit ses essais sur plusieurs adresses pour forcer un compte précis.
 */
class LoginAttemptTrackerTest {

    private LoginAttemptTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new LoginAttemptTracker();
        ReflectionTestUtils.setField(tracker, "enabled", true);
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
}
