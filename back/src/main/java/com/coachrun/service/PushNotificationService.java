package com.coachrun.service;

import com.coachrun.entity.PushSubscription;
import com.coachrun.repository.PushSubscriptionRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Notifications push (WebPush/VAPID). Désactivé si les clés VAPID sont absentes (no-op).
 *
 * <h2>Pourquoi l'envoi ne se fait ni dans la transaction, ni sur le thread appelant</h2>
 *
 * <p>Depuis que la routine (séance planifiée, commentaire du coach, retour d'athlète, rappel J-1)
 * est passée de l'e-mail au push, ce service est sur le <b>chemin chaud</b> : il est appelé depuis
 * {@code WorkoutService.create}, qui est {@code @Transactional}, et depuis les planificateurs de
 * 18 h et de 7 h, qui le sont aussi. L'envoi était synchrone et sans délai à l'intérieur de ces
 * transactions — exactement le défaut corrigé pour l'e-mail
 * ({@code NotificationService#send}) : un appel HTTP au milieu d'une transaction retient une
 * connexion Hikari (pool de 10) pendant toute sa durée, et un endpoint FCM lent suffisait à figer
 * l'API. Sur le digest quotidien, une seule transaction couvrait <em>tous</em> les clubs : un push
 * bloqué empêchait le commit, donc empêchait le départ de <em>tous</em> les e-mails de digest,
 * enregistrés en {@code afterCommit}.</p>
 *
 * <p>Trois garde-fous, donc :</p>
 * <ol>
 *   <li><b>Après commit</b> : dans une transaction, l'envoi est différé au commit — on ne notifie
 *       jamais d'une action qui a échoué, et la connexion est rendue avant l'appel réseau.</li>
 *   <li><b>Hors du thread appelant</b> : la remise part sur un exécuteur dédié et borné. Une
 *       notification est best-effort par nature ; faire attendre le coach qui planifie une séance
 *       pendant que trois appareils sont contactés n'a aucun sens. La file est bornée : une salve
 *       exceptionnelle perd des notifications plutôt que la mémoire du serveur.</li>
 *   <li><b>Appel borné</b> : la requête préparée par la bibliothèque est exécutée par un client
 *       HTTP à délais explicites. {@code PushService.send()} passe par un client asynchrone sans
 *       aucun délai de lecture : un endpoint qui accepte la connexion puis ne répond jamais
 *       immobilisait un thread définitivement.</li>
 * </ol>
 *
 * <h2>Abonnements morts</h2>
 * <p>Un abonnement révoqué (PWA désinstallée, notifications coupées, navigateur purgé) répond
 * {@code 404} ou {@code 410}. Ces réponses étaient avalées en {@code log.debug} : les lignes
 * mortes s'accumulaient, chacune coûtant un aller-retour à chaque notification — et surtout
 * {@link #canReach(UUID)} les comptait comme joignables, donc le rappel J-1 partait « en push »
 * dans le vide <b>sans repli e-mail</b>. L'athlète cessait silencieusement d'être prévenu. Elles
 * sont désormais supprimées à la première réponse qui les déclare caduques.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    /** Établissement de la connexion : au-delà, l'endpoint est injoignable. */
    private static final int CONNECT_TIMEOUT_MS = 3_000;
    /** Lecture : FCM et Mozilla répondent en centaines de millisecondes. */
    private static final int READ_TIMEOUT_MS = 10_000;
    /** Attente d'une connexion du pool : au-delà, la remise est abandonnée. */
    private static final int CONNECTION_REQUEST_TIMEOUT_MS = 2_000;

    /**
     * Profondeur de la file de remise. Dimensionnée pour absorber le plus gros lot du produit
     * (le rappel J-1 de 18 h, une notification par séance prévue le lendemain) avec une marge
     * confortable, sans jamais devenir un réservoir non borné.
     */
    private static final int QUEUE_CAPACITY = 2_000;

    private final PushSubscriptionRepository repository;

    @Value("${app.vapid.public-key:}")
    private String publicKey;
    @Value("${app.vapid.private-key:}")
    private String privateKey;
    @Value("${app.vapid.subject:mailto:no-reply@coachrun.fr}")
    private String subject;

    private volatile PushService pushService;
    private volatile CloseableHttpClient httpClient;
    private volatile ThreadPoolExecutor deliveryExecutor;

    public boolean isEnabled() {
        return StringUtils.hasText(publicKey) && StringUtils.hasText(privateKey);
    }

    public String publicKey() {
        return publicKey;
    }

    /**
     * Cet utilisateur peut-il réellement recevoir un push ? Vrai seulement si VAPID est
     * configuré <strong>et</strong> qu'au moins un appareil est abonné.
     *
     * <p>Sert de condition de repli : les notifications de routine (rappel de séance) passent en
     * push et ne retombent sur l'e-mail que pour les comptes qui n'ont pas d'appareil abonné.
     * Sans ce test, « passer en push » reviendrait à ne plus rien envoyer à ceux qui ont refusé
     * les notifications système.</p>
     *
     * <p>La réponse n'a de valeur que si les abonnements caducs sont retirés — sinon un compte
     * dont le seul appareil a désinstallé la PWA reste « joignable » et perd son repli e-mail.
     * C'est {@link #dropSubscription(String)} qui rend ce test honnête.</p>
     */
    public boolean canReach(UUID userId) {
        return isEnabled() && userId != null && !repository.findByUserId(userId).isEmpty();
    }

    @Transactional
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth) {
        PushSubscription sub = repository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        sub.setUserId(userId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        repository.save(sub);
    }

    /**
     * Désabonnement d'un appareil, <b>borné au propriétaire</b>. Un abonnement qui n'appartient
     * pas à l'appelant est ignoré silencieusement : répondre différemment révélerait quels
     * endpoints existent.
     */
    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repository.findByEndpoint(endpoint)
                .filter(sub -> sub.getUserId().equals(userId))
                .ifPresent(repository::delete);
    }

    /**
     * Retire les abonnements d'un utilisateur (déconnexion d'un appareil partagé, suppression
     * de compte). Sans ça, un appareil continue de recevoir les notifications d'un compte qui
     * ne s'en sert plus — et sur un téléphone partagé, celles d'un autre utilisateur.
     */
    @Transactional
    public void unsubscribeUser(UUID userId) {
        List<PushSubscription> subs = repository.findByUserId(userId);
        if (!subs.isEmpty()) {
            repository.deleteAll(subs);
            log.info("Abonnements push retirés (user={}, {} appareil(s))", userId, subs.size());
        }
    }

    /**
     * Action rapide d'une notification : un bouton affiché par le système, qui ouvre l'app sur
     * {@code url} sans passer par l'écran d'accueil.
     *
     * @param id    identifiant technique de l'action (unique dans la notification)
     * @param title libellé du bouton, tel qu'affiché par le système
     * @param url   destination relative (ex. {@code /athlete/today?feedback=…&rpe=7})
     */
    public record QuickAction(String id, String title, String url) {
    }

    /** Envoie une notification à tous les appareils d'un utilisateur (best-effort). */
    public void sendToUser(UUID userId, String title, String body, String url) {
        sendToUser(userId, title, body, url, List.of());
    }

    /**
     * Notification avec actions rapides. Le corps du message reste utile même si le système
     * n'affiche aucune action : selon la plateforme, seules les deux premières apparaissent
     * ({@code Notification.maxActions}), et un clic sur le corps ouvre {@code url}.
     *
     * <p>Ne porte plus {@code @Transactional} : la remise ne touche la base que pour lire les
     * abonnements et supprimer ceux qui sont morts, et elle ne doit surtout pas s'exécuter dans
     * la transaction métier de l'appelant (cf. javadoc de classe).</p>
     */
    public void sendToUser(UUID userId, String title, String body, String url, List<QuickAction> actions) {
        if (!isEnabled() || userId == null) {
            return;
        }
        String payload = payload(title, body, url, actions);
        // Dans une transaction : on attend le commit. Une notification annonçant une séance dont
        // l'enregistrement a échoué est pire que pas de notification du tout.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enqueue(userId, payload);
                }
            });
            return;
        }
        enqueue(userId, payload);
    }

    /** Confie la remise à l'exécuteur dédié ; une file pleine perd la notification, pas le serveur. */
    private void enqueue(UUID userId, String payload) {
        try {
            executor().execute(() -> deliver(userId, payload));
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            log.warn("File de remise push saturée : notification abandonnée (user={})", userId);
        }
    }

    /**
     * Remise effective, hors transaction métier et hors thread appelant. Chaque appel au dépôt
     * ouvre sa propre transaction courte : aucune connexion n'est retenue pendant le réseau.
     */
    void deliver(UUID userId, String payload) {
        for (PushSubscription sub : repository.findByUserId(userId)) {
            try {
                Notification notification = Notification.builder()
                        .endpoint(sub.getEndpoint())
                        .userPublicKey(sub.getP256dh())
                        .userAuth(sub.getAuth())
                        .payload(payload.getBytes(StandardCharsets.UTF_8))
                        .build();
                int status = post(notification);
                // 404/410 : l'endpoint a été révoqué par le navigateur. C'est définitif, et c'est
                // la seule information qui nous dise qu'un abonnement ne vaut plus rien.
                if (status == 404 || status == 410) {
                    dropSubscription(sub.getEndpoint());
                } else if (status >= 400) {
                    log.warn("Push refusé ({}) pour l'abonnement {} — user={}",
                            status, truncate(sub.getEndpoint()), userId);
                }
            } catch (Exception ex) {
                // Best-effort, mais plus silencieux : un push qui ne part jamais est indiscernable
                // d'une absence de notification, côté utilisateur comme côté support.
                log.warn("Échec d'envoi push vers {} (user={}) : {}",
                        truncate(sub.getEndpoint()), userId, ex.getMessage());
                io.sentry.Sentry.captureException(ex);
            }
        }
    }

    /**
     * Exécute la requête préparée par la bibliothèque avec <b>nos</b> délais.
     *
     * <p>{@code PushService.send()} délègue à un client asynchrone construit en interne, sans
     * aucun délai de lecture : il n'y a pas de point d'injection. On réutilise donc uniquement la
     * préparation — chiffrement de la charge utile et signature VAPID, c'est-à-dire toute la
     * partie cryptographique — et on maîtrise le transport.</p>
     */
    private int post(Notification notification) throws Exception {
        HttpPost request = service().preparePost(notification, Encoding.AES128GCM);
        try (CloseableHttpResponse response = client().execute(request)) {
            return response.getStatusLine().getStatusCode();
        }
    }

    /**
     * Suppression d'un abonnement caduc. La transaction est portée par le dépôt
     * ({@code deleteByEndpoint}) et non par cette méthode : l'appel vient de {@link #deliver},
     * dans la même classe, donc sans passer par le proxy Spring — une annotation ici serait
     * silencieusement sans effet.
     */
    void dropSubscription(String endpoint) {
        if (repository.deleteByEndpoint(endpoint) > 0) {
            log.info("Abonnement push caduc retiré ({})", truncate(endpoint));
        }
    }

    /** Endpoint tronqué pour les journaux : l'URL complète est un identifiant d'appareil. */
    private static String truncate(String endpoint) {
        if (endpoint == null) {
            return "—";
        }
        return endpoint.length() <= 40 ? endpoint : endpoint.substring(0, 40) + "…";
    }

    /**
     * Charge utile attendue par le service worker Angular (ngsw) : le bloc {@code notification}
     * est passé tel quel à {@code showNotification}, et {@code data.onActionClick} dit à ngsw
     * où naviguer selon le bouton pressé — c'est ce qui rend l'action rapide « en deux taps »
     * possible sans que l'athlète ait à retrouver sa séance dans l'app.
     */
    private String payload(String title, String body, String url, List<QuickAction> actions) {
        StringBuilder sb = new StringBuilder("{\"notification\":{\"title\":").append(json(title))
                .append(",\"body\":").append(json(body));

        if (!actions.isEmpty()) {
            sb.append(",\"actions\":[");
            for (int i = 0; i < actions.size(); i++) {
                QuickAction a = actions.get(i);
                sb.append(i > 0 ? "," : "")
                        .append("{\"action\":").append(json(a.id()))
                        .append(",\"title\":").append(json(a.title())).append("}");
            }
            sb.append("]");
        }

        sb.append(",\"data\":{\"url\":").append(json(url));
        if (!actions.isEmpty()) {
            sb.append(",\"onActionClick\":{\"default\":")
                    .append(navigate(url));
            for (QuickAction a : actions) {
                sb.append(",").append(json(a.id())).append(":").append(navigate(a.url()));
            }
            sb.append("}");
        }
        return sb.append("}}}").toString();
    }

    /** Opération ngsw : réutiliser l'onglet déjà ouvert plutôt qu'en empiler un nouveau. */
    private String navigate(String url) {
        return "{\"operation\":\"navigateLastFocusedOrOpen\",\"url\":" + json(url) + "}";
    }

    private PushService service() throws Exception {
        if (pushService == null) {
            synchronized (this) {
                if (pushService == null) {
                    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                        Security.addProvider(new BouncyCastleProvider());
                    }
                    pushService = new PushService(publicKey, privateKey, subject);
                }
            }
        }
        return pushService;
    }

    /** Client HTTP borné, partagé : connexion 3 s, lecture 10 s, pool plafonné. */
    private CloseableHttpClient client() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    PoolingHttpClientConnectionManager pool = new PoolingHttpClientConnectionManager();
                    pool.setMaxTotal(20);
                    pool.setDefaultMaxPerRoute(10);
                    httpClient = HttpClients.custom()
                            .setConnectionManager(pool)
                            .setDefaultRequestConfig(RequestConfig.custom()
                                    .setConnectTimeout(CONNECT_TIMEOUT_MS)
                                    .setSocketTimeout(READ_TIMEOUT_MS)
                                    .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT_MS)
                                    .build())
                            .build();
                }
            }
        }
        return httpClient;
    }

    /**
     * Exécuteur de remise : deux threads suffisent (le travail est de l'attente réseau), file
     * bornée, threads nommés pour que la supervision distingue ce poste des autres.
     */
    private ThreadPoolExecutor executor() {
        if (deliveryExecutor == null) {
            synchronized (this) {
                if (deliveryExecutor == null) {
                    deliveryExecutor = new ThreadPoolExecutor(
                            2, 2, 0L, TimeUnit.MILLISECONDS,
                            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                            r -> {
                                Thread t = new Thread(r, "darilab-push");
                                t.setDaemon(true);
                                return t;
                            });
                }
            }
        }
        return deliveryExecutor;
    }

    /** Arrêt propre : on laisse un court délai aux remises en cours, sans retenir l'arrêt. */
    @PreDestroy
    void shutdown() {
        if (deliveryExecutor != null) {
            deliveryExecutor.shutdown();
            try {
                if (!deliveryExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    deliveryExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deliveryExecutor.shutdownNow();
            }
        }
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (java.io.IOException e) {
                log.debug("Fermeture du client push : {}", e.getMessage());
            }
        }
    }

    private String json(String s) {
        return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }
}
