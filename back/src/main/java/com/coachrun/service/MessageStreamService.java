package com.coachrun.service;

import com.coachrun.dto.response.MessageResponse;
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
 * Diffusion temps réel des messages via Server-Sent Events (cf. DARI Lab — messagerie).
 * Les abonnés (coach et athlète) sont regroupés par fil de conversation (athleteId) ; un
 * message persisté est poussé à tous les émetteurs ouverts de ce fil.
 */
@Slf4j
@Service
public class MessageStreamService {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Ouvre un flux SSE pour le fil d'un athlète. */
    public SseEmitter subscribe(UUID athleteId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(athleteId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(athleteId, emitter));
        emitter.onTimeout(() -> remove(athleteId, emitter));
        emitter.onError(e -> remove(athleteId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(athleteId, emitter);
        }
        return emitter;
    }

    /** Pousse un message à tous les abonnés du fil. */
    public void broadcast(UUID athleteId, MessageResponse message) {
        List<SseEmitter> list = emitters.get(athleteId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("message").data(message));
            } catch (IOException | IllegalStateException e) {
                remove(athleteId, emitter);
            }
        }
    }

    private void remove(UUID athleteId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(athleteId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(athleteId);
            }
        }
    }
}
