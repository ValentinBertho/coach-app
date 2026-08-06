package com.coachrun.service;

import com.coachrun.entity.PushSubscription;
import com.coachrun.repository.PushSubscriptionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * Nombre total de tentatives par appareil, première comprise. Trois couvre la panne passagère
     * — celle qui dure quelques secondes — sans transformer une indisponibilité de fond en file
     * d'attente qui se vide des heures plus tard, quand la notification n'a plus d'objet.
     */
    private static final int MAX_ATTEMPTS = 3;

    /** Premier délai de réessai, doublé à chaque tentative (1 s puis 2 s). */
    private static final long RETRY_BASE_DELAY_MS = 1_000L;

    /**
     * Plafond de réessais en attente. Même raison d'être que la file de remise : lors d'une panne
     * générale du service de push, c'est la remise qui doit se dégrader, pas la mémoire du serveur.
     */
    private static final int MAX_RETRIES_IN_FLIGHT = 500;

    /** Fréquence maximale d'écriture de l'horodatage « appareil joignable ». */
    private static final java.time.Duration TOUCH_INTERVAL = java.time.Duration.ofHours(1);

    /** Essais autorisés par minute et par compte : de quoi vérifier, pas de quoi s'amuser. */
    private static final int TEST_MAX_PER_MINUTE = 10;

    /** Appareils contactés par un essai. La remise y est synchrone : la requête doit rester courte. */
    private static final int TEST_MAX_DEVICES = 10;

    private final PushSubscriptionRepository repository;
    private final com.coachrun.repository.UserRepository userRepository;
    private final com.coachrun.config.VapidKeys vapidKeys;
    private final ObjectMapper objectMapper;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    /**
     * Plafond de l'envoi d'essai, compté par compte. Le plafond global du {@code RateLimitFilter}
     * est par porteur de jeton et large : il laisse passer une salve d'essais, dont chacun ouvre
     * autant d'appels réseau sortants qu'il y a d'appareils abonnés.
     */
    private final com.coachrun.util.FixedWindowRateLimiter testLimiter =
            new com.coachrun.util.FixedWindowRateLimiter(TEST_MAX_PER_MINUTE, java.time.Duration.ofMinutes(1));

    @Value("${app.vapid.subject:mailto:no-reply@coachrun.fr}")
    private String subject;

    private volatile PushService pushService;
    private volatile CloseableHttpClient httpClient;
    private volatile ThreadPoolExecutor deliveryExecutor;
    private volatile java.util.concurrent.ScheduledExecutorService retryScheduler;
    private final java.util.concurrent.atomic.AtomicInteger retriesInFlight =
            new java.util.concurrent.atomic.AtomicInteger();

    public boolean isEnabled() {
        return vapidKeys.isConfigured();
    }

    public String publicKey() {
        return vapidKeys.publicKey();
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
    public void subscribe(UUID userId, String endpoint, String p256dh, String auth, String userAgent) {
        PushSubscription sub = repository.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        sub.setUserId(userId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        if (StringUtils.hasText(userAgent)) {
            // Tronqué à la largeur de la colonne : certaines chaînes de navigateur sont énormes,
            // et ce qui identifie l'appareil se trouve toujours au début.
            sub.setUserAgent(userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent);
        }
        repository.save(sub);
    }

    /** Appareils abonnés de l'utilisateur, pour qu'il puisse voir — et retirer — ce qui le suit. */
    public List<com.coachrun.dto.response.PushDeviceResponse> devices(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(com.coachrun.dto.response.PushDeviceResponse::from)
                .toList();
    }

    /**
     * Retrait d'un appareil <b>par identifiant</b>, borné au propriétaire.
     *
     * <p>Le retrait n'existait que par endpoint, c'est-à-dire depuis l'appareil lui-même : un
     * téléphone revendu ou perdu restait abonné jusqu'à ce que son navigateur réponde 410, ce qui
     * peut ne jamais arriver.</p>
     */
    @Transactional
    public void removeDevice(UUID userId, UUID subscriptionId) {
        repository.findByIdAndUserId(subscriptionId, userId).ifPresent(repository::delete);
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

    /**
     * Envoi d'essai vers les appareils du compte, déclenché par son propriétaire.
     *
     * <p>Le push est le seul canal du produit dont personne ne peut vérifier l'état : l'athlète
     * autorise les notifications, ne reçoit rien pendant trois jours, et n'a aucun moyen de savoir
     * si c'est parce qu'il n'y avait rien à annoncer ou parce que la chaîne est cassée quelque part
     * — abonnement révoqué par le navigateur, clés VAPID absentes du serveur, canal coupé dans ses
     * propres réglages. Le support n'avait pas davantage de réponse. Cet essai rend la chaîne
     * observable de bout en bout, depuis l'appareil qui la subit.</p>
     *
     * <p>L'envoi part <b>même si le canal est coupé</b> dans les préférences : il a été demandé
     * explicitement, et l'utilisateur qui teste veut savoir si son téléphone reçoit, pas si son
     * réglage est à « oui ». La réponse porte le réglage pour qu'il ne conclue pas de travers.</p>
     *
     * @param url destination à l'ouverture de la notification, selon le rôle de l'appelant
     */
    public com.coachrun.dto.response.PushTestResponse sendTest(UUID userId, String url) {
        if (!testLimiter.tryAcquire(userId.toString())) {
            throw new com.coachrun.exception.ApiException(
                    org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Trop d'essais — réessaie dans une minute.");
        }
        List<PushSubscription> subscriptions = repository.findByUserId(userId);
        boolean muted = userRepository.findById(userId)
                .map(u -> !u.isNotifyPushEnabled())
                .orElse(false);
        if (!isEnabled() || subscriptions.isEmpty()) {
            return new com.coachrun.dto.response.PushTestResponse(
                    isEnabled(), subscriptions.size(), muted, 0, List.of());
        }

        // Remise SYNCHRONE, contrairement à tout le reste du service. C'est la raison d'être de
        // cet essai : dire ce qui s'est réellement passé sur le réseau. Confié à l'exécuteur, il
        // ne pourrait annoncer qu'« envoyé » — c'est-à-dire « mis en file », ce qui reste vrai
        // quand le service de push refuse la signature et que rien n'arrive jamais.
        String payload = payload("Darilab — test de notification",
                "Si tu vois ce message, tes notifications fonctionnent.", url, List.of());
        int delivered = 0;
        List<String> failures = new ArrayList<>();
        for (PushSubscription sub : subscriptions.stream().limit(TEST_MAX_DEVICES).toList()) {
            String label = com.coachrun.dto.response.PushDeviceResponse.label(sub.getUserAgent());
            try {
                int status = post(Notification.builder()
                        .endpoint(sub.getEndpoint())
                        .userPublicKey(sub.getP256dh())
                        .userAuth(sub.getAuth())
                        .payload(payload.getBytes(StandardCharsets.UTF_8))
                        .build());
                if (status >= 200 && status < 300) {
                    delivered++;
                    count("sent");
                    touch(sub.getEndpoint());
                    continue;
                }
                if (status == 404 || status == 410) {
                    // Abonnement révoqué par le navigateur : on le retire ici comme la remise
                    // ordinaire le fait, sinon l'essai suivant réinterrogerait la même ligne morte.
                    count("expired");
                    dropSubscription(sub.getEndpoint());
                } else {
                    count("failed");
                }
                failures.add(label + " : " + explain(status));
            } catch (Exception ex) {
                count("failed");
                log.warn("Essai push en échec vers {} (user={}) : {}",
                        truncate(sub.getEndpoint()), userId, ex.getMessage());
                failures.add(label + " : service de push injoignable");
            }
        }
        return new com.coachrun.dto.response.PushTestResponse(
                true, subscriptions.size(), muted, delivered, failures);
    }

    /**
     * Ce que veut dire un refus du service de push, en français et sans code HTTP à décoder.
     * Un « ça ne marche pas » se règle en lisant la cause, pas en la devinant.
     */
    private static String explain(int status) {
        return switch (status) {
            case 400 -> "requête refusée (400) — charge utile ou en-têtes invalides";
            case 401, 403 -> "signature refusée (" + status + ") — les clés VAPID du serveur ne "
                    + "sont pas celles avec lesquelles cet appareil s'est abonné : "
                    + "il doit se réabonner";
            case 404, 410 -> "abonnement expiré — appareil retiré, il faut réactiver les "
                    + "notifications dessus";
            case 413 -> "message trop long (413)";
            case 429 -> "service de push saturé (429) — réessayer plus tard";
            default -> status >= 500
                    ? "panne du service de push (" + status + ")"
                    : "refusé (" + status + ")";
        };
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
            attempt(userId, sub.getEndpoint(), sub.getP256dh(), sub.getAuth(), payload, 0);
        }
    }

    /**
     * Une tentative de remise vers un appareil, avec réessai différé sur panne passagère.
     *
     * <p>Il n'y avait aucun réessai : un 500 du service de push, une coupure réseau d'une seconde,
     * et la notification était perdue sans trace. C'est précisément la classe de panne qui se
     * répare toute seule — FCM et Mozilla renvoient des 5xx transitoires, et un {@code 429} dit
     * explicitement « reviens plus tard ».</p>
     *
     * <p>Le réessai est <b>différé, pas bloquant</b> : dormir sur le fil de remise occuperait l'un
     * des deux threads du pool pendant toute l'attente, et une salve de pannes suffirait à figer
     * la remise de tout le monde. Le nombre de réessais en vol est plafonné pour la même raison
     * que la file d'attente l'est : une panne générale doit dégrader la remise, pas la mémoire du
     * serveur.</p>
     */
    private void attempt(UUID userId, String endpoint, String p256dh, String auth,
                         String payload, int attempt) {
        try {
            Notification notification = Notification.builder()
                    .endpoint(endpoint)
                    .userPublicKey(p256dh)
                    .userAuth(auth)
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .build();
            int status = post(notification);

            if (status >= 200 && status < 300) {
                count("sent");
                touch(endpoint);
                return;
            }
            // 404/410 : l'endpoint a été révoqué par le navigateur. C'est définitif, et c'est
            // la seule information qui nous dise qu'un abonnement ne vaut plus rien.
            if (status == 404 || status == 410) {
                count("expired");
                dropSubscription(endpoint);
                return;
            }
            if (retryable(status) && retry(userId, endpoint, p256dh, auth, payload, attempt)) {
                return;
            }
            count("failed");
            log.warn("Push refusé ({}) pour l'abonnement {} — user={}, tentative {}",
                    status, truncate(endpoint), userId, attempt + 1);
        } catch (Exception ex) {
            // Une panne réseau est par nature passagère : elle mérite le même réessai qu'un 5xx.
            if (retry(userId, endpoint, p256dh, auth, payload, attempt)) {
                return;
            }
            count("failed");
            // Best-effort, mais plus silencieux : un push qui ne part jamais est indiscernable
            // d'une absence de notification, côté utilisateur comme côté support.
            log.warn("Échec d'envoi push vers {} (user={}) après {} tentative(s) : {}",
                    truncate(endpoint), userId, attempt + 1, ex.getMessage());
            io.sentry.Sentry.captureException(ex);
        }
    }

    /**
     * Ce code appelle-t-il un réessai ? Les 5xx sont des pannes du service de push, et
     * {@code 429} est une demande explicite de ralentir. Un {@code 400} ou un {@code 403}, en
     * revanche, dit que la requête est fautive : la rejouer à l'identique ne peut que rater.
     */
    private static boolean retryable(int status) {
        return status == 429 || status >= 500;
    }

    /**
     * Reprogramme une tentative si le quota le permet.
     *
     * @return vrai si un réessai a été planifié — l'appelant n'a alors rien à journaliser.
     */
    private boolean retry(UUID userId, String endpoint, String p256dh, String auth,
                          String payload, int attempt) {
        if (attempt >= MAX_ATTEMPTS - 1) {
            return false;
        }
        if (retriesInFlight.incrementAndGet() > MAX_RETRIES_IN_FLIGHT) {
            retriesInFlight.decrementAndGet();
            count("dropped");
            log.warn("Réessais push saturés : remise abandonnée (user={})", userId);
            return false;
        }
        long delayMs = RETRY_BASE_DELAY_MS * (1L << attempt);
        count("retried");
        retryScheduler().schedule(() -> {
            try {
                attempt(userId, endpoint, p256dh, auth, payload, attempt + 1);
            } finally {
                retriesInFlight.decrementAndGet();
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Marque l'appareil comme joignable, au plus une fois par heure.
     *
     * <p>Sans ce garde-fou, chaque notification écrirait une ligne par appareil : l'information
     * cherchée — « cet appareil répond-il encore ? » — se lit à l'heure près, pas à la seconde.</p>
     */
    private void touch(String endpoint) {
        try {
            repository.markUsed(endpoint, Instant.now(), Instant.now().minus(TOUCH_INTERVAL));
        } catch (RuntimeException ex) {
            log.debug("Horodatage d'appareil non mis à jour : {}", ex.getMessage());
        }
    }

    /** Compteur de remise, exposé par l'actuator : un canal muet doit se voir en supervision. */
    private void count(String outcome) {
        try {
            meterRegistry.counter("darilab.push.delivery", "outcome", outcome).increment();
        } catch (RuntimeException ignored) {
            // La métrique ne doit jamais faire échouer une remise.
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
     *
     * <p><b>Sérialisé par Jackson, plus à la main.</b> Le JSON était assemblé par concaténation,
     * avec un échappement qui ne traitait que l'antislash et le guillemet. Or le titre d'une
     * séance est une saisie libre du coach, sans contrainte de ligne unique : un titre collé
     * depuis un document et portant un retour à la ligne produisait un JSON invalide, rejeté par
     * le service de push. L'athlète n'était pas prévenu, et rien ne le signalait — l'échec se
     * confondait avec une absence de notification.</p>
     */
    private String payload(String title, String body, String url, List<QuickAction> actions) {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("title", title == null ? "" : title);
        notification.put("body", body == null ? "" : body);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);

        if (!actions.isEmpty()) {
            List<Map<String, String>> buttons = new ArrayList<>(actions.size());
            Map<String, Object> onActionClick = new LinkedHashMap<>();
            onActionClick.put("default", navigate(url));
            for (QuickAction a : actions) {
                buttons.add(Map.of("action", a.id(), "title", a.title()));
                onActionClick.put(a.id(), navigate(a.url()));
            }
            notification.put("actions", buttons);
            data.put("onActionClick", onActionClick);
        }
        notification.put("data", data);

        try {
            return objectMapper.writeValueAsString(Map.of("notification", notification));
        } catch (JsonProcessingException ex) {
            // Inatteignable avec des chaînes et des maps ; on ne laisse pas pour autant une
            // notification malformée partir sur le réseau.
            throw new IllegalStateException("Charge utile push non sérialisable", ex);
        }
    }

    /** Opération ngsw : réutiliser l'onglet déjà ouvert plutôt qu'en empiler un nouveau. */
    private Map<String, String> navigate(String url) {
        return Map.of("operation", "navigateLastFocusedOrOpen", "url", url);
    }

    private PushService service() throws Exception {
        if (pushService == null) {
            synchronized (this) {
                if (pushService == null) {
                    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                        Security.addProvider(new BouncyCastleProvider());
                    }
                    pushService = new PushService(vapidKeys.publicKey(), vapidKeys.privateKey(), subject);
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

    /**
     * Fil unique porteur des réessais différés. Séparé de l'exécuteur de remise à dessein : une
     * attente n'a pas à consommer l'un des deux threads qui font le travail utile.
     */
    private java.util.concurrent.ScheduledExecutorService retryScheduler() {
        if (retryScheduler == null) {
            synchronized (this) {
                if (retryScheduler == null) {
                    retryScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "darilab-push-retry");
                        t.setDaemon(true);
                        return t;
                    });
                }
            }
        }
        return retryScheduler;
    }

    /** Arrêt propre : on laisse un court délai aux remises en cours, sans retenir l'arrêt. */
    @PreDestroy
    void shutdown() {
        if (retryScheduler != null) {
            retryScheduler.shutdownNow();
        }
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
}
