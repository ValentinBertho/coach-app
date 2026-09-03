package com.coachrun.service;

import com.coachrun.dto.response.MessageResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
 * Les abonnés sont regroupés par <b>conversation</b> ; un message persisté est poussé à tous les
 * émetteurs ouverts de ce fil-là.
 *
 * <p>La clé était l'athlète. Deux coachs suivant le même athlète recevaient donc en direct les
 * messages de l'autre, quand bien même le fil ne leur était pas destiné.</p>
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
     * <p>La clé est le fil, pas l'utilisateur : ses participants le partagent. Douze couvre donc un
     * athlète sur deux appareils et plusieurs personnes regardant le même fil de groupe.</p>
     */
    private static final int MAX_STREAMS_PER_THREAD = 12;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Ouvre un flux SSE pour un fil, en fermant le plus ancien au-delà du plafond. */
    public SseEmitter subscribe(UUID conversationId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> threadEmitters =
                emitters.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>());
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

        emitter.onCompletion(() -> remove(conversationId, emitter));
        emitter.onTimeout(() -> remove(conversationId, emitter));
        emitter.onError(e -> remove(conversationId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove(conversationId, emitter);
        }
        return emitter;
    }

    /**
     * Maintient les flux ouverts en écrivant un commentaire SSE à intervalle régulier.
     *
     * <p>Même mécanisme que pour le compteur de notifications, et même raison : un fil silencieux
     * n'écrit rien, et le relais qui coupe les connexions inactives ne distingue pas « rien à
     * dire » de « mort ». Il coupait à 150 secondes, avec une régularité d'horloge ; le client
     * rouvrait, et la boucle recommençait. Le commentaire {@code :hb} occupe la connexion sans
     * rien remonter au navigateur — {@code EventSource} ne le passe à aucun écouteur.</p>
     */
    @Scheduled(fixedRateString = "${app.sse.heartbeat-ms:20000}")
    public void heartbeat() {
        emitters.forEach((conversationId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (IOException | RuntimeException e) {
                    // Client parti sans que le serveur en soit informé : le battement le révèle.
                    drop(conversationId, emitter, e);
                }
            }
        });
    }

    /** Nombre de flux actuellement ouverts, tous fils confondus (diagnostic). */
    public int openStreams() {
        return emitters.values().stream().mapToInt(List::size).sum();
    }

    /** Pousse un message à tous les abonnés du fil. */
    public void broadcast(UUID conversationId, MessageResponse message) {
        List<SseEmitter> list = emitters.get(conversationId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("message").data(message));
            } catch (IOException | IllegalStateException e) {
                drop(conversationId, emitter, e);
            }
        }
    }

    /**
     * Retire un émetteur devenu inutilisable, <b>et clôt la requête asynchrone qui le porte</b>.
     *
     * <p>Le retirer de la table ne suffisait pas : côté conteneur, la requête restait ouverte
     * jusqu'au délai de l'émetteur — une demi-heure à occuper une connexion pour un client parti
     * depuis longtemps. Le battement détecte le départ en vingt secondes ; autant rendre la place
     * tout de suite.</p>
     */
    private void drop(UUID conversationId, SseEmitter emitter, Throwable cause) {
        remove(conversationId, emitter);
        try {
            emitter.completeWithError(cause);
        } catch (RuntimeException ignored) {
            // Déjà clos par le conteneur : rien à faire.
        }
    }

    private void remove(UUID conversationId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(conversationId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(conversationId);
            }
        }
    }
}
