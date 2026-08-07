package com.coachrun;

import com.coachrun.config.VapidKeys;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ce qui échoue, exactement, quand une notification ne part pas.
 *
 * <p>Le bouton d'essai a rapporté « service de push injoignable » sur un iPhone abonné, serveur
 * pourvu de clés. Or ce message couvrait <b>trois étapes très différentes</b> : construire le
 * service signataire, chiffrer et signer la requête, puis l'émettre sur le réseau. Les deux
 * premières sont des erreurs de <i>configuration</i> — elles échouent pour tous les appareils, à
 * chaque essai, et aucun réseau n'y changera rien ; la troisième seule est une panne de liaison.
 * Les confondre envoie chercher le défaut du mauvais côté.</p>
 *
 * <p>Ces tests éprouvent la chaîne réelle (la bibliothèque de signature, pas une imitation) et
 * fixent ce qui distingue les cas.</p>
 */
class PushDeliveryFailureTest {

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    /**
     * Abonnement tel qu'un navigateur en produit : une <b>vraie</b> clé publique P-256, point non
     * compressé de 65 octets. Des octets au hasard ne feraient pas l'affaire — ils ne tombent pas
     * sur la courbe, et la bibliothèque les refuse (« Invalid point coordinates »), ce qui
     * éprouverait le rejet plutôt que le chemin nominal.
     */
    private static String browserPublicKey() throws Exception {
        java.security.KeyPairGenerator generator =
                java.security.KeyPairGenerator.getInstance("ECDH", "BC");
        generator.initialize(new java.security.spec.ECGenParameterSpec("prime256v1"));
        byte[] point = nl.martijndwars.webpush.Utils.encode(
                (org.bouncycastle.jce.interfaces.ECPublicKey) generator.generateKeyPair().getPublic());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(point);
    }

    private static String browserAuth() {
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(auth);
    }

    private VapidKeys generatedKeys(Path dir) {
        VapidKeys keys = new VapidKeys("", "", true, dir.resolve("v.json").toString());
        ReflectionTestUtils.invokeMethod(keys, "resolve");
        return keys;
    }

    /**
     * Le cœur du doute : avec des clés serveur valides et un abonnement de navigateur bien formé,
     * la <b>préparation</b> de la requête doit aboutir. Si elle échouait, aucune notification ne
     * pourrait jamais partir, quel que soit le réseau — et c'est ce que le message d'erreur
     * observé en production laissait envisager.
     */
    @Test
    void aWellFormedSubscriptionCanBeSignedAndEncrypted(@TempDir Path dir) throws Exception {
        VapidKeys keys = generatedKeys(dir);
        PushService service = new PushService(keys.publicKey(), keys.privateKey(),
                "mailto:no-reply@darilab.app");

        Notification notification = Notification.builder()
                .endpoint("https://web.push.apple.com/abcdef")
                .userPublicKey(browserPublicKey())
                .userAuth(browserAuth())
                .payload("{\"notification\":{\"title\":\"Test\"}}".getBytes(StandardCharsets.UTF_8))
                .build();

        assertThatCode(() -> service.preparePost(notification, Encoding.AES128GCM))
                .doesNotThrowAnyException();
    }

    /**
     * Des clés serveur illisibles (collage à la ligne, caractère perdu) font échouer la
     * construction même du signataire — pour <b>tous</b> les appareils, à chaque envoi. C'est une
     * erreur de configuration, et elle n'a rien d'un problème de réseau.
     */
    @Test
    void unusableServerKeysFailBeforeAnyNetworkCall() {
        assertThatThrownBy(() -> new PushService("pas-une-cle", "pas-une-cle-non-plus",
                "mailto:no-reply@darilab.app"))
                .isInstanceOf(Exception.class);
    }

    /**
     * Une paire fabriquée par l'application doit passer la vérification de la bibliothèque : c'est
     * ce contrôle qui permet de refuser au démarrage des clés que le serveur ne saura pas utiliser,
     * plutôt que de le découvrir appareil par appareil.
     */
    @Test
    void generatedKeysPassTheLibraryCheck(@TempDir Path dir) throws Exception {
        VapidKeys keys = generatedKeys(dir);

        assertThat(nl.martijndwars.webpush.Utils.verifyKeyPair(
                nl.martijndwars.webpush.Utils.loadPrivateKey(keys.privateKey()),
                nl.martijndwars.webpush.Utils.loadPublicKey(keys.publicKey()))).isTrue();
    }
}
