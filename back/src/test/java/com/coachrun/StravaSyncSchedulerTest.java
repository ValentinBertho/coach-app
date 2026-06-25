package com.coachrun;

import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.scheduler.StravaSyncScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Synchro Strava planifiée : la requête de projection est valide et le job est un no-op sûr
 * quand l'intégration n'est pas configurée (cas du profil de test).
 */
@SpringBootTest
@ActiveProfiles("test")
class StravaSyncSchedulerTest {

    @Autowired private DeviceConnectionRepository connectionRepository;
    @Autowired private StravaSyncScheduler scheduler;

    @Test
    void projectionQueryIsValid() {
        assertThat(connectionRepository.findAthleteClubIdsByProvider(DeviceProvider.STRAVA)).isNotNull();
    }

    @Test
    void syncIsNoopWhenStravaNotConfigured() {
        assertThatCode(scheduler::syncConnectedAthletes).doesNotThrowAnyException();
    }
}
