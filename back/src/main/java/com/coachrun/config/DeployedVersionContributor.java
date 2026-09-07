package com.coachrun.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publie sur {@code /actuator/info} la réponse à « quelle version tourne, là, maintenant ? ».
 *
 * <h2>Pourquoi</h2>
 *
 * <p>Une erreur remontait avec un identifiant de corrélation, une heure, un écran — et rien pour
 * savoir <b>quel code</b> l'avait produite. La version applicative était bien quelque part (dans
 * le {@code pom}, dans le nom d'un artefact), mais rien ne reliait l'instance en ligne à un commit :
 * entre deux déploiements du même numéro de version, « ça marchait hier » restait indécidable.
 * C'est le point OPS-09 du plan de conformité.</p>
 *
 * <h2>D'où vient le commit</h2>
 *
 * <p>Deux sources, dans cet ordre :</p>
 *
 * <ol>
 *   <li><b>Une variable d'environnement</b>, posée par la plateforme de déploiement
 *       ({@code RAILWAY_GIT_COMMIT_SHA}) ou par le build d'image ({@code APP_COMMIT}). C'est la
 *       seule source disponible en production : l'image est construite depuis un contexte Docker
 *       qui ne contient pas le dépôt Git.</li>
 *   <li><b>Le dépôt lui-même</b> au moment de la compilation, via {@code git.properties} que
 *       produit {@code git-commit-id-maven-plugin} — présent en CI et en local, absent de l'image.
 *       Spring l'expose déjà sous la clé {@code git} ; on le relit ici pour que {@code app.commit}
 *       soit renseigné dans tous les cas.</li>
 * </ol>
 *
 * <p>Faute des deux, la valeur est {@code "inconnu"} — explicitement, plutôt qu'une clé absente
 * qu'on prendrait pour un oubli d'affichage.</p>
 *
 * <p>Le nom de cette classe n'est pas anodin : Spring Boot déclare déjà un bean
 * {@code buildInfoContributor} — celui qui publie le bloc {@code build}. Une classe portant ce
 * nom-là fait échouer <b>tout</b> le contexte au démarrage (refus d'écraser une définition de
 * bean), et le message d'erreur ne parle que de définitions, jamais de version.</p>
 */
@Component
public class DeployedVersionContributor implements InfoContributor {

    /** Valeur affichée quand ni la plateforme ni le dépôt n'ont fourni de commit. */
    static final String UNKNOWN = "inconnu";

    private final String version;
    private final String environment;
    private final String commit;
    private final String builtAt;

    public DeployedVersionContributor(
            @Value("${app.version:inconnu}") String version,
            @Value("${sentry.environment:inconnu}") String environment,
            @Value("${app.build.commit:}") String configuredCommit,
            ObjectProvider<GitProperties> gitProperties,
            ObjectProvider<BuildProperties> buildProperties) {
        this.version = version;
        this.environment = environment;
        GitProperties git = gitProperties.getIfAvailable();
        this.commit = shortened(configuredCommit, git != null ? git.getShortCommitId() : null);
        BuildProperties build = buildProperties.getIfAvailable();
        this.builtAt = build != null && build.getTime() != null
                ? build.getTime().toString() : UNKNOWN;
    }

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, Object> app = new LinkedHashMap<>();
        app.put("name", "DARI Lab");
        app.put("version", version);
        app.put("commit", commit);
        app.put("builtAt", builtAt);
        app.put("environment", environment);
        builder.withDetail("app", app);
    }

    /** Ce que le contributeur publiera comme commit. Lisible par les tests. */
    String commit() {
        return commit;
    }

    /**
     * Le commit exposé, abrégé à sept caractères comme le fait Git : c'est ce qu'on recopie dans
     * une recherche, et une empreinte complète n'apporte rien à l'œil.
     */
    private static String shortened(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                String trimmed = candidate.trim();
                return trimmed.length() > 7 ? trimmed.substring(0, 7) : trimmed;
            }
        }
        return UNKNOWN;
    }
}
