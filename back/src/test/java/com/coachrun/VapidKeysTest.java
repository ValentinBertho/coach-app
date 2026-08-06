package com.coachrun;

import com.coachrun.config.VapidKeys;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Clés VAPID : sans elles, tout le dispositif de notification est inerte et muet — le navigateur
 * ne peut même pas s'abonner, faute de clé publique de serveur.
 *
 * <p>Rien ne les fabriquait : il fallait connaître la commande, poser deux variables et
 * redémarrer. En développement, la conséquence pratique était qu'on ne pouvait pas <i>essayer</i>
 * les notifications, donc pas les corriger. Ces règles fixent le comportement des deux régimes —
 * et surtout la persistance, sans laquelle chaque redémarrage invaliderait silencieusement les
 * abonnements du poste.</p>
 */
class VapidKeysTest {

    private VapidKeys keys(String pub, String priv, boolean autoGenerate, Path store) {
        VapidKeys k = new VapidKeys(pub, priv, autoGenerate, store.toString());
        ReflectionTestUtils.invokeMethod(k, "resolve");
        return k;
    }

    @Test
    void configuredKeysWin(@TempDir Path dir) {
        VapidKeys k = keys("pub-configuree", "priv-configuree", true, dir.resolve("v.json"));

        assertThat(k.isConfigured()).isTrue();
        assertThat(k.publicKey()).isEqualTo("pub-configuree");
        assertThat(k.privateKey()).isEqualTo("priv-configuree");
    }

    /** En production, l'absence reste une absence : on ne fabrique pas d'identité de serveur. */
    @Test
    void nothingIsGeneratedWhenGenerationIsNotAllowed(@TempDir Path dir) {
        Path store = dir.resolve("v.json");

        VapidKeys k = keys("", "", false, store);

        assertThat(k.isConfigured()).isFalse();
        assertThat(store).doesNotExist();
    }

    /** La paire fabriquée doit être utilisable telle quelle par la bibliothèque qui signe. */
    @Test
    void aGeneratedPairIsAValidVapidKeyPair(@TempDir Path dir) throws Exception {
        VapidKeys k = keys("", "", true, dir.resolve("v.json"));

        assertThat(k.isConfigured()).isTrue();
        assertThat(Utils.verifyKeyPair(
                Utils.loadPrivateKey(k.privateKey()), Utils.loadPublicKey(k.publicKey()))).isTrue();
        // Le test qui compte vraiment : la bibliothèque d'envoi les accepte sans broncher.
        assertThat(new PushService(k.publicKey(), k.privateKey(), "mailto:dev@darilab.app")).isNotNull();
    }

    /**
     * Persistance : un abonnement de navigateur est lié à la clé publique avec laquelle il a été
     * créé. Une paire régénérée à chaque démarrage couperait tous les abonnements du poste, sans
     * que rien ne le signale — exactement le genre de panne qu'on met des jours à imputer.
     */
    @Test
    void aGeneratedPairSurvivesARestart(@TempDir Path dir) {
        Path store = dir.resolve("v.json");

        VapidKeys first = keys("", "", true, store);
        VapidKeys second = keys("", "", true, store);

        assertThat(store).exists();
        assertThat(second.publicKey()).isEqualTo(first.publicKey());
        assertThat(second.privateKey()).isEqualTo(first.privateKey());
    }

    /** Fichier abîmé : on régénère plutôt que de démarrer avec des clés inutilisables. */
    @Test
    void anUnreadableStoreIsRegenerated(@TempDir Path dir) throws Exception {
        Path store = dir.resolve("v.json");
        Files.writeString(store, "ceci n'est pas du json");

        VapidKeys k = keys("", "", true, store);

        assertThat(k.isConfigured()).isTrue();
        assertThat(Files.readString(store)).contains("publicKey");
    }
}
