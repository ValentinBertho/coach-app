package com.coachrun.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Notifications « push » envoyées par Garmin.
 *
 * <p>Garmin exige un <b>200 immédiat</b>, avant tout traitement (délai de 30 s, puis nouvelle
 * tentative toutes les heures pendant 7 jours). Le contrôleur acquitte donc toujours, et le
 * travail se fait ici, sur un exécuteur dédié.</p>
 *
 * <p>Garmin ne signe pas ses notifications. L'adresse déclarée dans son outil de configuration
 * porte donc un jeton secret ({@code GARMIN_WEBHOOK_TOKEN}, en dernier segment du chemin) : sans lui, une notification est
 * ignorée — sinon n'importe qui connaissant un identifiant Garmin pourrait déconnecter un
 * athlète.</p>
 *
 * <p>Étape 1 : désinscriptions et changements de permissions. Les activités s'ajouteront ici.</p>
 */
@Slf4j
@Service
public class GarminWebhookService {

    private static final int QUEUE_CAPACITY = 500;

    private final GarminService garminService;
    private final byte[] expectedToken;
    private volatile ThreadPoolExecutor executor;

    public GarminWebhookService(GarminService garminService,
                                @Value("${app.garmin.webhook-token:}") String webhookToken) {
        this.garminService = garminService;
        this.expectedToken = StringUtils.hasText(webhookToken)
                ? webhookToken.getBytes(StandardCharsets.UTF_8) : null;
    }

    /** Le jeton présenté est-il le bon ? Comparaison à temps constant ; refus si non configuré. */
    public boolean verifies(String token) {
        if (expectedToken == null || token == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
    }

    /** Prend la notification en charge et rend la main immédiatement. */
    public void accept(Map<String, Object> body) {
        if (body == null) {
            return;
        }
        for (Map<String, Object> item : items(body.get("deregistrations"))) {
            String userId = text(item.get("userId"));
            if (userId != null) {
                enqueue(() -> garminService.handleDeregistration(userId));
            }
        }
        for (Map<String, Object> item : items(body.get("userPermissionsChange"))) {
            String userId = text(item.get("userId"));
            List<String> permissions = strings(item.get("permissions"));
            if (userId != null) {
                enqueue(() -> garminService.handlePermissionsChange(userId, permissions));
            }
        }
    }

    private void enqueue(Runnable task) {
        try {
            executor().execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException ex) {
                    log.warn("Traitement d'une notification Garmin en échec : {}", ex.getMessage());
                }
            });
        } catch (RejectedExecutionException ex) {
            log.warn("File de notifications Garmin saturée : notification perdue.");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Object value) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o != null) {
                    out.add(String.valueOf(o));
                }
            }
        }
        return out;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private ThreadPoolExecutor executor() {
        ThreadPoolExecutor local = executor;
        if (local == null) {
            synchronized (this) {
                local = executor;
                if (local == null) {
                    local = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                            r -> {
                                Thread t = new Thread(r, "garmin-webhook");
                                t.setDaemon(true);
                                return t;
                            });
                    executor = local;
                }
            }
        }
        return local;
    }

    @PreDestroy
    void shutdown() {
        ThreadPoolExecutor local = executor;
        if (local != null) {
            local.shutdown();
        }
    }
}
