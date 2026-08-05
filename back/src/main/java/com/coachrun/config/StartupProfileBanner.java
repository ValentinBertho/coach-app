package com.coachrun.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Une ligne au démarrage : quel profil tourne, et avec quoi.
 *
 * <p><b>Pourquoi.</b> {@code spring.profiles.default} vaut {@code dev}. Le profil de production
 * n'est donc actif que si {@code SPRING_PROFILES_ACTIVE=prod} est posé dans l'environnement
 * d'exécution — une variable qui vit chez l'hébergeur, pas dans le dépôt. Si elle manque, tout
 * démarre normalement, mais en profil dev : journalisation bavarde, jeu de démo au démarrage,
 * remise à zéro de démo autorisée, inscription ouverte, et aucun des garde-fous de
 * {@link StartupSecretsValidator}, qui ne s'applique qu'en prod. Rien dans les logs ne le disait.</p>
 *
 * <p>D'où aussi l'avertissement ci-dessous : un profil dev servant une adresse publique est
 * presque toujours une variable oubliée, pas une intention.</p>
 */
@Slf4j
@Component
public class StartupProfileBanner {

    private final Environment environment;

    @Value("${app.frontend-url:}")
    private String frontendUrl;

    @Value("${app.version:?}")
    private String version;

    public StartupProfileBanner(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logActiveProfile() {
        String[] active = environment.getActiveProfiles();
        String profiles = active.length == 0
                ? String.join(",", environment.getDefaultProfiles()) + " (par défaut)"
                : String.join(",", active);

        log.info("Darilab {} démarré — profil actif : {} · front : {}", version, profiles, frontendUrl);

        boolean devActive = active.length == 0
                ? Arrays.asList(environment.getDefaultProfiles()).contains("dev")
                : Arrays.asList(active).contains("dev");
        if (devActive && !isLocal(frontendUrl)) {
            log.warn("""
                    Profil DEV actif alors que le front pointe vers {} — ce n'est pas une machine \
                    de développement. Poser SPRING_PROFILES_ACTIVE=prod : sans lui, le jeu de \
                    démonstration se recharge, l'inscription est ouverte et les garde-fous de \
                    secrets ne s'appliquent pas.""", frontendUrl);
        }
    }

    /** Une adresse locale : le seul cas où le profil dev est à sa place. */
    private static boolean isLocal(String url) {
        return url == null || url.isBlank()
                || url.contains("localhost") || url.contains("127.0.0.1");
    }
}
