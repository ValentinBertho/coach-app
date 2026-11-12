package com.coachrun.config;

import com.coachrun.service.DemoSeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Au démarrage (profil dev + app.seed.enabled), charge le jeu de données de démo
 * via {@link DemoSeedService} (idempotent). Les comptes sont listés dans le README à la section
 * « Comptes de démonstration » (ce renvoi pointait vers un fichier de documentation qui n'a
 * jamais existé).
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevSeedConfig implements CommandLineRunner {

    private final DemoSeedService demoSeedService;

    @Override
    public void run(String... args) {
        if (demoSeedService.seed()) {
            log.info("[seed dev] Jeu de démo prêt — connexion : {} / {}",
                    DemoSeedService.HEAD_COACH_EMAIL, DemoSeedService.DEMO_PASSWORD);
        }
        // Le hub à part : `seed()` est appelé par toute la suite de tests, où des fiches coachs
        // publiées changeraient ce que voit l'annuaire dans chaque classe. Ici, en dev, on veut au
        // contraire pouvoir ouvrir l'annuaire et y trouver quelqu'un.
        if (demoSeedService.seedHub()) {
            log.info("[seed dev] Hub prêt — annuaire : {}, athlète sans coach : {} (mot de passe {})",
                    DemoSeedService.SOLO_COACH_EMAIL, DemoSeedService.HUB_ATHLETE_EMAIL,
                    DemoSeedService.DEMO_PASSWORD);
        }
    }
}
