package com.coachrun;

import com.coachrun.repository.UserRepository;
import com.coachrun.service.DemoSeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Deux connexions simultanées sur le même compte aboutissent toutes les deux.
 *
 * <p>La seconde recevait une erreur 500 : la date de dernière connexion passait par l'entité,
 * dont la version changeait sous ses pieds. Cas réel : le téléphone et l'ordinateur, ou une
 * application qui se relance. Pas de {@code @Transactional} ici — chaque connexion doit valider
 * sa propre transaction pour que la concurrence existe.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentLoginTest {

    private static final int PARALLEL = 8;

    @Autowired private WebApplicationContext context;
    @Autowired private DemoSeedService demoSeedService;
    @Autowired private UserRepository userRepository;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        demoSeedService.seed();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void simultaneousLoginsOnTheSameAccountAllSucceed() throws Exception {
        String body = "{\"email\":\"" + DemoSeedService.ATHLETE_EMAIL + "\",\"password\":\""
                + DemoSeedService.DEMO_PASSWORD + "\"}";
        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < PARALLEL; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return mvc.perform(post("/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON).content(body))
                            .andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsOnly(200);
        } finally {
            pool.shutdownNow();
        }
        assertThat(userRepository.findByEmailIgnoreCase(DemoSeedService.ATHLETE_EMAIL)
                .orElseThrow().getLastLoginAt()).isNotNull();
    }
}
