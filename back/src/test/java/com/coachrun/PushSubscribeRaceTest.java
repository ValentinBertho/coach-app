package com.coachrun;

import com.coachrun.repository.PushSubscriptionRepository;
import com.coachrun.service.PushNotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le même abonnement envoyé plusieurs fois <b>en même temps</b> ne doit jamais échouer.
 *
 * <p>Défaut vu sur iPhone après réinstallation de l'app : à l'activation, le navigateur envoyait
 * l'abonnement deux fois presque simultanément. Les deux requêtes inséraient, la seconde tombait
 * sur l'unicité de l'endpoint (409), et l'application défaisait l'abonnement du téléphone : plus
 * aucune notification. Pas de {@code @Transactional} ici : la concurrence doit traverser de vraies
 * transactions.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class PushSubscribeRaceTest {

    @Autowired private PushNotificationService pushService;
    @Autowired private PushSubscriptionRepository repository;

    private final String endpoint = "https://web.push.apple.com/race-" + UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        repository.deleteByEndpoint(endpoint);
    }

    @Test
    void concurrentSubscriptionsOfTheSameDeviceAllSucceed() throws Exception {
        UUID userId = UUID.randomUUID();
        int parallel = 6;
        ExecutorService pool = Executors.newFixedThreadPool(parallel);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> calls = new ArrayList<>();
        try {
            for (int i = 0; i < parallel; i++) {
                calls.add(pool.submit(() -> {
                    start.await();
                    pushService.subscribe(userId, endpoint, "BNc-cle-publique", "secret", "iPhone");
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> call : calls) {
                call.get(30, TimeUnit.SECONDS); // relance l'exception éventuelle
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(repository.findByEndpoint(endpoint)).isPresent()
                .get().extracting(s -> s.getUserId()).isEqualTo(userId);
        assertThat(repository.findByUserId(userId)).hasSize(1);
    }
}
