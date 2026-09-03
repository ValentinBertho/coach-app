package com.coachrun.service;

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
 * Diffusion temps réel du compteur de notifications non lues via Server-Sent Events.
 * Les émetteurs sont regroupés par utilisateur ; à chaque nouvelle notification, le compteur
 * à jour est poussé aux flux ouverts de cet utilisateur (mise à jour instantanée du badge).
 */
@Slf4j
@Service
public class NotificationStreamService {

    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    /**
     * Flux simultanés tolérés par utilisateur. Un usage normal en ouvre un par onglet ; six
     * couvre largement « le portable, la tablette et trois onglets sur le poste ».
     *
     * <p>Sans plafond, cette table croissait au rythme des reconnexions : le proxy Vercel coupe
     * mal les connexions longues (constaté et documenté dans le runbook), et {@code EventSource}
     * rouvre automatiquement. Un onglet laissé ouvert la nuit derrière un proxy capricieux
     * empilait donc des émetteurs sur une instance unique, sans qu'aucun compteur ne le voie —
     * ces routes échappaient aussi au rate limiting, faute de porter le jeton en en-tête.</p>
     */
    private static final int MAX_STREAMS_PER_USER = 6;

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Ouvre un flux SSE pour un utilisateur, en fermant le plus ancien au-delà du plafond. */
    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> userEmitters =
                emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        // Le plus ancien cède la place : un flux abandonné par un onglet fermé sans que le
        // serveur en soit informé ne doit pas condamner les suivants.
        while (userEmitters.size() >= MAX_STREAMS_PER_USER) {
            SseEmitter oldest = userEmitters.isEmpty() ? null : userEmitters.get(0);
            if (oldest == null) {
                break;
            }
            userEmitters.remove(oldest);
            try {
                oldest.complete();
            } catch (RuntimeException ignored) {
                // Déjà terminé côté client : rien à fermer.
            }
        }
        userEmitters.add(emitter);

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

    /**
     * Maintient les flux ouverts en écrivant un commentaire SSE à intervalle régulier.
     *
     * <h2>Ce que cela corrige</h2>
     *
     * <p>Un flux de notifications n'écrit rien entre deux notifications — soit, en usage réel,
     * pendant des heures. Un relais qui coupe les connexions inactives ne peut pas faire la
     * différence entre « rien à dire » et « mort » : il coupait à <b>150 secondes</b>, avec une
     * régularité d'horloge (relevé en production : une coupure toutes les 152 à 154 s, sans
     * exception). {@code EventSource} rouvrait deux secondes plus tard, et la boucle recommençait
     * — de l'ordre de six cents reconnexions par jour et par onglet, chacune laissant sa trace
     * dans les journaux.</p>
     *
     * <p>Le délai serveur (trente minutes) n'y était pour rien, ni le client : la coupure venait
     * du silence. Un commentaire toutes les vingt secondes suffit à l'occuper.</p>
     *
     * <p>Le commentaire — {@code :hb} — est invisible côté navigateur : {@code EventSource} ne le
     * remonte à aucun écouteur. Rien à changer dans le front.</p>
     */
    @Scheduled(fixedRateString = "${app.sse.heartbeat-ms:20000}")
    public void heartbeat() {
        emitters.forEach((userId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("hb"));
                } catch (IOException | RuntimeException e) {
                    // Client parti sans que le serveur en soit informé : c'est justement ce que
                    // le battement révèle.
                    drop(userId, emitter, e);
                }
            }
        });
    }

    /** Nombre de flux actuellement ouverts, tous utilisateurs confondus (diagnostic). */
    public int openStreams() {
        return emitters.values().stream().mapToInt(List::size).sum();
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
                drop(userId, emitter, e);
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
    private void drop(UUID userId, SseEmitter emitter, Throwable cause) {
        remove(userId, emitter);
        try {
            emitter.completeWithError(cause);
        } catch (RuntimeException ignored) {
            // Déjà clos par le conteneur : rien à faire.
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
