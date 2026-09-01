package com.coachrun;

import com.coachrun.repository.ClubRepository;
import com.coachrun.repository.UserRepository;
import com.coachrun.service.DemoSeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seed de démo : idempotence + purge/recharge. @Transactional → rollback
 * (réutilise le contexte de test partagé, sans polluer les autres tests).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoSeedServiceTest {

    @Autowired
    private DemoSeedService demoSeedService;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private UserRepository userRepository;

    /**
     * Semer deux fois ne duplique rien.
     *
     * <p>La purge d'entrée n'est pas un détail : elle rend le test indépendant de l'ordre
     * d'exécution. {@code seed()} court-circuite dès que {@code admin@coachrun.fr} existe, et
     * deux classes de la suite — {@code ConversationPersistenceTest} et
     * {@code ConversationOnPostgresTest} — ne sont volontairement pas transactionnelles et
     * <b>commitent</b> le jeu de démo dans la base H2 partagée. Passer après elles faisait donc
     * rendre {@code false} au premier appel, et l'assertion tombait sur un défaut qui n'existait
     * pas. La classe étant transactionnelle, cette purge est annulée à la fin du test.</p>
     */
    @Test
    void seedIsIdempotent() {
        demoSeedService.purge();
        assertThat(demoSeedService.seed()).isTrue();
        long clubsAfterFirst = clubRepository.count();
        assertThat(clubsAfterFirst).isGreaterThanOrEqualTo(3);
        assertThat(userRepository.existsByEmailIgnoreCase(DemoSeedService.ADMIN_EMAIL)).isTrue();

        // Deuxième appel : aucun doublon
        assertThat(demoSeedService.seed()).isFalse();
        assertThat(clubRepository.count()).isEqualTo(clubsAfterFirst);
    }

    @Test
    void resetPurgesAndReseeds() {
        demoSeedService.seed();
        demoSeedService.reset();

        assertThat(userRepository.existsByEmailIgnoreCase(DemoSeedService.ADMIN_EMAIL)).isTrue();
        assertThat(clubRepository.count()).isGreaterThanOrEqualTo(3);
    }
}
