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

    /** Envoie une notification à tous les appareils d'un utilisateur (best-effort). */
    @Transactional
    public void sendToUser(UUID userId, String title, String body, String url) {
        if (!isEnabled() || userId == null) {
            return;
        }
        String payload = "{\"notification\":{\"title\":" + json(title)
                + ",\"body\":" + json(body)
                + ",\"data\":{\"url\":" + json(url) + "}}}";
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
