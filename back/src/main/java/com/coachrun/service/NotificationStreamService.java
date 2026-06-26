package com.coachrun.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Diffusion temps réel du compteur de notifications non lues via Server-Sent Events.
 * Les émetteurs sont regroupés par utilisateur ; à chaque nouvelle notification, le compteur
 * à jour est poussé aux flux ouverts de cet utilisateur (mise à jour instantanée du badge).
 */
@Slf4j
@Service
public class NotificationStreamService {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Ouvre un flux SSE pour un utilisateur. */
    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /** Pousse le nombre de non-lues à tous les flux ouverts de l'utilisateur. */
    public void publishUnread(UUID userId, long unread) {
        List<SseEmitter> list = emitters.get(userId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("unread").data(unread));
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(userId);
            }
        }
    }
}
