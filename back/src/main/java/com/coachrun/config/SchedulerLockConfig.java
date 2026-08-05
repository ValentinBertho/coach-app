package com.coachrun.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Verrou distribué des tâches planifiées.
 *
 * <p><b>Pourquoi.</b> Chaque planificateur du produit portait la même note : « mono-instance pour
 * le MVP ». Ce n'est pas une contrainte technique mais un pari — celui qu'on ne déploiera jamais
 * deux instances. Le jour où on le fait, rien ne casse bruyamment : le rappel J-1 part deux fois,
 * le digest de 7 h aussi, et la synchro Strava importe deux fois les mêmes activités. Des doublons
 * silencieux, du côté utilisateur, sont exactement ce qui fait couper les notifications.</p>
 *
 * <p>Le verrou s'appuie sur la base déjà présente : pas de Redis, pas de coordinateur, une table
 * de quatre colonnes. Il est sans effet en mono-instance — c'est un filet, pas un changement de
 * comportement.</p>
 *
 * <p><b>Ce que ça ne couvre pas.</b> Les émetteurs SSE du centre de notifications et de la
 * messagerie vivent en mémoire ({@code ConcurrentHashMap} par instance) : à plusieurs instances,
 * le badge ne se met à jour que si le client retombe sur celle qui a écrit. Régler ce point
 * demande un bus de diffusion (Redis pub/sub ou équivalent), donc une brique d'infrastructure qui
 * n'existe pas dans la stack — c'est une décision à prendre, pas un détail d'implémentation.</p>
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        // Horloge de la base plutôt que celle de la JVM : deux instances peuvent
                        // dériver de quelques secondes, et c'est précisément l'écart qui ferait
                        // passer deux verrous pour un.
                        .usingDbTime()
                        .build());
    }
}
