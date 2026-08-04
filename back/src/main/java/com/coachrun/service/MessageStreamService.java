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

    /**
     * Flux simultanés tolérés par fil de conversation.
     *
     * <p>Le plafond posé sur le compteur de notifications n'avait pas été appliqué ici, alors que
     * la cause est la même : le proxy coupe mal les connexions longues et {@code EventSource}
     * rouvre tout seul, indéfiniment. Le rate limiting attrape bien ces routes, mais au plafond
     * authentifié de 300 requêtes par minute — de quoi empiler des centaines d'émetteurs retenus
     * une demi-heure chacun.</p>
     *
     * <p>La clé est le fil, pas l'utilisateur : l'athlète et ses coachs le partagent. Douze couvre
     * donc un athlète sur deux appareils et plusieurs coachs regardant la même conversation.</p>
     */
    private static final int MAX_STREAMS_PER_THREAD = 12;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Ouvre un flux SSE pour le fil d'un athlète, en fermant le plus ancien au-delà du plafond. */
    public SseEmitter subscribe(UUID athleteId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> threadEmitters =
                emitters.computeIfAbsent(athleteId, k -> new CopyOnWriteArrayList<>());
        // Le plus ancien cède la place : un onglet fermé sans que le serveur en soit informé ne
        // doit pas condamner les suivants.
        while (threadEmitters.size() >= MAX_STREAMS_PER_THREAD) {
            SseEmitter oldest = threadEmitters.isEmpty() ? null : threadEmitters.get(0);
            if (oldest == null) {
                break;
            }
            threadEmitters.remove(oldest);
            try {
                oldest.complete();
            } catch (RuntimeException ignored) {
                // Déjà terminé côté client : rien à fermer.
            }
        }
        threadEmitters.add(emitter);

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
