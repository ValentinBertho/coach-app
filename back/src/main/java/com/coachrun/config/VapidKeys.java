package com.coachrun.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Base64Encoder;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;

/**
 * Paire de clés VAPID de l'application : celle qui signe chaque notification push.
 *
 * <h2>Pourquoi ce composant existe</h2>
 *
 * <p>Sans clés, tout le dispositif est <b>inerte et silencieux</b> : le navigateur ne peut même pas
 * s'abonner (il lui faut la clé publique du serveur), et le serveur n'a rien pour signer. Or rien
 * ne les fabriquait : il fallait connaître {@code npx web-push generate-vapid-keys}, poser deux
 * variables d'environnement, et redémarrer — un détour qu'on ne fait pas tant qu'on ignore qu'il
 * manque quelque chose. En développement, la conséquence était qu'on ne pouvait tout simplement
 * pas essayer les notifications, donc pas les corriger.</p>
 *
 * <p>Deux régimes, et un seul comportement automatique :</p>
 * <ul>
 *   <li><b>Clés fournies</b> ({@code VAPID_PUBLIC_KEY} / {@code VAPID_PRIVATE_KEY}) : elles font
 *       foi, quel que soit le profil. C'est le régime de production.</li>
 *   <li><b>Clés absentes et génération autorisée</b> ({@code app.vapid.auto-generate}, vrai
 *       uniquement en profil {@code dev}) : une paire est fabriquée puis <b>conservée sur disque</b>
 *       à côté du projet. La persistance n'est pas un détail de confort : un abonnement de
 *       navigateur est lié à la clé publique avec laquelle il a été créé, donc une paire qui
 *       changerait à chaque redémarrage invaliderait tous les abonnements du poste à chaque fois —
 *       et la notification cesserait d'arriver sans que rien ne le dise.</li>
 * </ul>
 *
 * <p>En production, l'absence reste une erreur de configuration, signalée par
 * {@link StartupSecretsValidator} : on ne fabrique pas en silence une identité de serveur qui
 * changerait au prochain déploiement et couperait les abonnements de tous les athlètes.</p>
 */
@Slf4j
@Component
public class VapidKeys {

    /** Courbe imposée par la spécification WebPush (P-256). */
    private static final String CURVE = "prime256v1";

    private final String configuredPublicKey;
    private final String configuredPrivateKey;
    private final boolean autoGenerate;
    private final Path store;

    private volatile String publicKey;
    private volatile String privateKey;

    public VapidKeys(@Value("${app.vapid.public-key:}") String configuredPublicKey,
                     @Value("${app.vapid.private-key:}") String configuredPrivateKey,
                     @Value("${app.vapid.auto-generate:false}") boolean autoGenerate,
                     @Value("${app.vapid.key-store:.vapid-dev.json}") String store) {
        this.configuredPublicKey = configuredPublicKey;
        this.configuredPrivateKey = configuredPrivateKey;
        this.autoGenerate = autoGenerate;
        this.store = Path.of(store);
    }

    /**
     * Résolution au démarrage, et <b>une ligne de journal dans tous les cas</b> : « les
     * notifications ne marchent pas » était jusqu'ici indiscernable de « le serveur n'a jamais eu
     * de quoi les envoyer ».
     */
    @PostConstruct
    void resolve() {
        if (StringUtils.hasText(configuredPublicKey) && StringUtils.hasText(configuredPrivateKey)) {
            publicKey = configuredPublicKey.trim();
            privateKey = configuredPrivateKey.trim();
            log.info("Push VAPID : clés fournies par la configuration — les notifications peuvent partir.");
            return;
        }
        if (!autoGenerate) {
            log.warn("Push VAPID : aucune clé configurée — AUCUNE notification push ne partira, "
                    + "et aucun appareil ne pourra s'abonner. Poser VAPID_PUBLIC_KEY et "
                    + "VAPID_PRIVATE_KEY (npx web-push generate-vapid-keys).");
            return;
        }
        if (loadFromStore()) {
            log.info("Push VAPID : paire de développement relue depuis {}.", store.toAbsolutePath());
            return;
        }
        generateAndStore();
    }

    /** Le serveur sait-il signer un push ? */
    public boolean isConfigured() {
        return StringUtils.hasText(publicKey) && StringUtils.hasText(privateKey);
    }

    /** Clé publique, celle que le navigateur utilise pour s'abonner. Vide si non configurée. */
    public String publicKey() {
        return publicKey == null ? "" : publicKey;
    }

    public String privateKey() {
        return privateKey == null ? "" : privateKey;
    }

    private boolean loadFromStore() {
        if (!Files.isReadable(store)) {
            return false;
        }
        try {
            String json = Files.readString(store, StandardCharsets.UTF_8);
            String pub = extract(json, "publicKey");
            String priv = extract(json, "privateKey");
            if (!StringUtils.hasText(pub) || !StringUtils.hasText(priv)) {
                return false;
            }
            publicKey = pub;
            privateKey = priv;
            return true;
        } catch (Exception e) {
            log.warn("Push VAPID : fichier de clés de développement illisible ({}), régénération.",
                    e.getMessage());
            return false;
        }
    }

    /** Lecture d'un champ du petit JSON écrit ici même — pas de dépendance pour deux chaînes. */
    private String extract(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private void generateAndStore() {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(new ECGenParameterSpec(CURVE));
            KeyPair pair = generator.generateKeyPair();

            // Encodage confié à la bibliothèque qui les relira : l'alphabet base64 (URL, sans
            // remplissage) est celui qu'attendent le navigateur et la signature VAPID.
            publicKey = Base64Encoder.encodeUrlWithoutPadding(Utils.encode((ECPublicKey) pair.getPublic()));
            privateKey = Base64Encoder.encodeUrlWithoutPadding(Utils.encode((ECPrivateKey) pair.getPrivate()));

            Files.writeString(store, "{\n  \"publicKey\": \"" + publicKey + "\",\n"
                    + "  \"privateKey\": \"" + privateKey + "\"\n}\n", StandardCharsets.UTF_8);
            log.info("Push VAPID : paire de développement générée et conservée dans {}. "
                    + "Les notifications push fonctionnent en local.", store.toAbsolutePath());
        } catch (Exception e) {
            publicKey = null;
            privateKey = null;
            log.warn("Push VAPID : génération impossible ({}) — le push restera inerte.", e.getMessage());
        }
    }
}
