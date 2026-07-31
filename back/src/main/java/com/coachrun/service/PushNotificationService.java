package com.coachrun.service;

import com.coachrun.entity.PushSubscription;
import com.coachrun.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.UUID;

/**
 * Notifications push (WebPush/VAPID). Désactivé si les clés VAPID sont absentes
 * (no-op). Les envois en échec ne bloquent jamais le métier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final PushSubscriptionRepository repository;

    @Value("${app.vapid.public-key:}")
    private String publicKey;
    @Value("${app.vapid.private-key:}")
    private String privateKey;
    @Value("${app.vapid.subject:mailto:no-reply@coachrun.fr}")
    private String subject;

    private volatile PushService pushService;

    public boolean isEnabled() {
        return StringUtils.hasText(publicKey) && StringUtils.hasText(privateKey);
    }

    public String publicKey() {
        return publicKey;
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

    @Transactional
    public void unsubscribe(String endpoint) {
        repository.findByEndpoint(endpoint).ifPresent(repository::delete);
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
    @Transactional
    public void sendToUser(UUID userId, String title, String body, String url) {
        sendToUser(userId, title, body, url, List.of());
    }

    /**
     * Notification avec actions rapides. Le corps du message reste utile même si le système
     * n'affiche aucune action : selon la plateforme, seules les deux premières apparaissent
     * ({@code Notification.maxActions}), et un clic sur le corps ouvre {@code url}.
     */
    @Transactional
    public void sendToUser(UUID userId, String title, String body, String url, List<QuickAction> actions) {
        if (!isEnabled() || userId == null) {
            return;
        }
        String payload = payload(title, body, url, actions);
        for (PushSubscription sub : repository.findByUserId(userId)) {
            try {
                Notification notification = Notification.builder()
                        .endpoint(sub.getEndpoint())
                        .userPublicKey(sub.getP256dh())
                        .userAuth(sub.getAuth())
                        .payload(payload.getBytes(StandardCharsets.UTF_8))
                        .build();
                service().send(notification);
            } catch (Exception ex) {
                log.debug("Échec push vers {} : {}", sub.getEndpoint(), ex.getMessage());
            }
        }
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

    private String json(String s) {
        return "\"" + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }
}
