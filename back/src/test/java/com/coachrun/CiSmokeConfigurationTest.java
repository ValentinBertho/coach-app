package com.coachrun;

import com.coachrun.config.StartupSecretsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La configuration du smoke test de CI satisfait-elle le garde-fou de démarrage ?
 *
 * <p><b>Pourquoi ce test existe.</b> {@code StartupSecretsValidator} a été élargi à plusieurs
 * reprises — URL de front non locale, CORS non local, clés VAPID, code d'invitation, relais de
 * confiance — sans que {@code .github/workflows/ci.yml} suive. Le smoke test démarre pourtant
 * l'application en profil {@code prod} : il ne bootait donc plus, le job échouait après quarante
 * tentatives, et l'échec ne disait rien du code — il disait que la CI était mal configurée.</p>
 *
 * <p>Le défaut est particulièrement sournois parce qu'il touche le seul test qui vérifie que
 * l'application <em>démarre</em> en configuration de production. Tant qu'il est rouge pour une
 * mauvaise raison, une vraie régression de démarrage passe inaperçue dans le bruit.</p>
 *
 * <p>Ce test relit les variables d'environnement déclarées dans le workflow et les soumet au
 * validateur. Toute règle ajoutée au garde-fou sans mise à jour de la CI échoue ici — dans le
 * module qui a introduit la règle, en une seconde, plutôt qu'en fin de pipeline.</p>
 */
class CiSmokeConfigurationTest {

    /** Le workflow est à la racine du dépôt, le module Maven est dans {@code back/}. */
    private static final Path CI_WORKFLOW = Path.of("..", ".github", "workflows", "ci.yml");

    /** Bloc `env:` de l'étape de smoke test, jusqu'à la ligne `run:`. */
    private static final Pattern SMOKE_ENV_BLOCK = Pattern.compile(
            "Smoke test démarrage.*?\\n\\s*env:\\n(.*?)\\n\\s*run:", Pattern.DOTALL);
    private static final Pattern ENV_LINE =
            Pattern.compile("^\\s{10}([A-Z_]+):\\s*\"?([^\"\\n]*)\"?\\s*$", Pattern.MULTILINE);

    @Test
    void ciSmokeTestEnvironmentBootsInProductionProfile() throws IOException {
        Map<String, String> env = smokeTestEnv();

        // Garde-fou du garde-fou : si l'extraction casse, le test doit le dire plutôt que
        // valider une configuration vide.
        assertThat(env)
                .as("variables extraites du bloc env: du smoke test")
                .containsKeys("SPRING_PROFILES_ACTIVE", "JWT_SECRET", "FIELD_ENCRYPTION_KEY",
                        "FRONTEND_URL", "CORS_ORIGINS");
        assertThat(env.get("SPRING_PROFILES_ACTIVE"))
                .as("le smoke test doit bien s'exécuter en profil prod, sinon il ne teste rien")
                .isEqualTo("prod");

        StartupSecretsValidator validator = new StartupSecretsValidator(
                env.getOrDefault("JWT_SECRET", ""),
                env.getOrDefault("FIELD_ENCRYPTION_KEY", ""),
                env.getOrDefault("FRONTEND_URL", ""),
                Boolean.parseBoolean(env.getOrDefault("MAIL_ENABLED", "false")),
                env.getOrDefault("RESEND_API_KEY", ""),
                env.getOrDefault("CORS_ORIGINS", ""),
                env.getOrDefault("VAPID_PUBLIC_KEY", ""),
                env.getOrDefault("VAPID_PRIVATE_KEY", ""),
                // Le profil prod force « invite » (application-prod.yml) : c'est ce que verra
                // le smoke test, quelle que soit l'absence de la variable dans le workflow.
                env.getOrDefault("REGISTRATION_MODE", "invite"),
                env.getOrDefault("REGISTRATION_INVITE_CODE", ""),
                Integer.parseInt(env.getOrDefault("RATE_LIMIT_TRUSTED_PROXY_HOPS", "2")));

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(validator, "validate"))
                .as("le smoke test de CI doit pouvoir démarrer : une règle ajoutée au garde-fou "
                        + "sans mise à jour de ci.yml rend le job rouge pour une mauvaise raison")
                .doesNotThrowAnyException();
    }

    private Map<String, String> smokeTestEnv() throws IOException {
        String workflow = Files.readString(CI_WORKFLOW);
        Matcher block = SMOKE_ENV_BLOCK.matcher(workflow);
        assertThat(block.find())
                .as("bloc env: de l'étape « Smoke test démarrage » introuvable dans ci.yml")
                .isTrue();

        Map<String, String> env = new HashMap<>();
        Matcher line = ENV_LINE.matcher(block.group(1));
        while (line.find()) {
            env.put(line.group(1), line.group(2).trim());
        }
        return env;
    }
}
