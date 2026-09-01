package com.coachrun.config;

import java.util.List;

/**
 * Configuration de production incomplète, détectée au démarrage par
 * {@link StartupSecretsValidator}.
 *
 * <p>Type dédié, et non une {@code IllegalStateException} : c'est lui qui permet à
 * {@link StartupConfigurationFailureAnalyzer} d'intercepter l'échec et d'écrire un bloc lisible
 * en fin de journal. Avec une exception générique, Spring rendait une trace de deux cents lignes
 * dont la seule phrase utile — la liste des variables manquantes — se trouvait au milieu, entre
 * un {@code BeanCreationException} et un {@code UnsatisfiedDependencyException}. Sur un hébergeur
 * qui redéploie à chaud, c'est cette trace que l'exploitant lit, et elle ne dit rien de ce qu'il
 * doit poser.</p>
 */
public class StartupConfigurationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Les manques, un par entrée, chacun nommant la variable d'environnement en cause. */
    private final transient List<String> problems;

    public StartupConfigurationException(List<String> problems) {
        super("Configuration de production incomplète :\n - " + String.join("\n - ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
