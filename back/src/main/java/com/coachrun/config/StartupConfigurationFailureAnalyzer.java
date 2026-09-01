package com.coachrun.config;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Traduit un {@link StartupConfigurationException} en message de fin de journal.
 *
 * <p><b>Ce que cela change.</b> Le garde-fou de démarrage existait déjà et disait juste ; il
 * n'était simplement pas lisible. Une exception levée depuis un {@code @PostConstruct} ressort
 * enveloppée dans {@code UnsatisfiedDependencyException} → {@code BeanCreationException} →
 * {@code ApplicationContextException}, et l'hébergeur affiche la trace complète : la bascule en
 * profil {@code prod} se soldait par un mur de texte où « il manque VAPID_PUBLIC_KEY » est une
 * ligne parmi deux cents. Un {@code FailureAnalyzer} court-circuite tout cela — Spring imprime
 * la description et l'action ci-dessous, sans trace, en dernier bloc du journal.</p>
 *
 * <p>Déclaré dans {@code META-INF/spring.factories} : les analyseurs sont chargés avant le
 * contexte applicatif, ils ne peuvent donc pas être des beans.</p>
 */
public class StartupConfigurationFailureAnalyzer
        extends AbstractFailureAnalyzer<StartupConfigurationException> {

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, StartupConfigurationException cause) {
        StringBuilder description = new StringBuilder(
                "Le profil « prod » est actif, mais la configuration est incomplète.\n"
                        + "L'application a refusé de démarrer plutôt que de servir avec des réglages "
                        + "qui échoueraient en silence.\n\n"
                        + "Il manque " + cause.problems().size() + " réglage(s) :\n");
        int n = 1;
        for (String problem : cause.problems()) {
            description.append("  ").append(n++).append(". ").append(problem).append('\n');
        }

        String action = """
                Poser les variables d'environnement ci-dessus, puis redéployer.

                Pour répondre AVANT de pousser (le script rejoue ces mêmes contrôles hors de
                l'application, plus ceux qu'elle ne peut pas faire) :

                    ./ops/preflight-prod.sh

                Génération des secrets :
                    JWT_SECRET           : openssl rand -base64 48
                    FIELD_ENCRYPTION_KEY : openssl rand -hex 32
                    VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY : npx web-push generate-vapid-keys

                Le détail de chaque variable est dans .env.example et docs/DEPLOIEMENT.md.
                """;

        return new FailureAnalysis(description.toString(), action, cause);
    }
}
