package com.coachrun.service;

import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.repository.DeviceConnectionRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Réception des événements Strava (Webhook Events API) : une activité terminée remonte en
 * quelques secondes, au lieu d'attendre la synchronisation planifiée.
 *
 * <h2>Ce que change le webhook</h2>
 *
 * <p>La synchronisation tournait à l'heure. Un athlète qui arrête sa montre à 18 h 05 voyait sa
 * sortie apparaître à 18 h 30 — et son coach aussi. Ce délai n'a rien d'inévitable : Strava
 * <b>prévient</b> dès qu'une activité est enregistrée. La synchro planifiée reste en place, non
 * pas comme chemin principal mais comme filet : un événement peut se perdre (redéploiement en
 * cours, panne réseau de quelques minutes), et il ne doit pas emporter la sortie avec lui.</p>
 *
 * <h2>Deux contraintes de la plateforme, qui dictent la forme de ce service</h2>
 * <ol>
 *   <li><b>Répondre en moins de deux secondes</b>, sinon Strava considère la remise en échec et
 *       finit par désactiver l'abonnement. L'import — plusieurs appels HTTP vers Strava — ne peut
 *       donc pas se faire dans la requête : l'événement est mis en file et acquitté aussitôt.</li>
 *   <li><b>Plusieurs événements pour une même sortie</b> (création, puis mises à jour du titre ou
 *       du type). On ne réimporte pas trois fois : un athlète déjà en cours de synchronisation
 *       n'est pas remis en file, et l'import est de toute façon idempotent — la déduplication par
 *       identifiant externe est déjà en place.</li>
 * </ol>
 *
 * <h2>Sécurité</h2>
 * <p>L'URL de rappel est publique par construction : Strava doit pouvoir l'appeler sans jeton. Ce
 * qu'un événement forgé permettrait est donc borné : on ne lit <b>rien</b> de son contenu sinon
 * l'identifiant d'athlète Strava, et la seule action possible est de synchroniser un athlète
 * <b>déjà connecté</b>, avec son propre jeton, depuis Strava. Aucune donnée n'entre par ce canal ;
 * un athlète inconnu est ignoré sans un mot.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StravaWebhookService {

    /** Profondeur de la file : une salve de fin de journée tient largement dedans. */
    private static final int QUEUE_CAPACITY = 500;

    private final DeviceConnectionRepository connectionRepository;
    private final StravaService stravaService;

    /**
     * Jeton convenu avec Strava à la création de l'abonnement, et qu'il redonne lors de la
     * validation de l'URL. Sans lui, n'importe qui pourrait faire valider une URL de rappel à
     * notre place.
     */
    @Value("${app.strava.webhook-verify-token:}")
    private String verifyToken;

    private volatile ThreadPoolExecutor executor;
    /** Athlètes dont une synchronisation est déjà en file : inutile d'en empiler une seconde. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Le jeton attendu lors de la validation de l'URL de rappel par Strava.
     *
     * <p>Un jeton non configuré ne vaut jamais accord : sinon une instance déployée sans réglage
     * validerait l'abonnement de n'importe qui.</p>
     */
    public boolean verifies(String token) {
        return StringUtils.hasText(verifyToken) && verifyToken.equals(token);
    }

    /**
     * Prend un événement en charge et rend la main <b>immédiatement</b> : tout le travail utile se
     * fait après l'acquittement (cf. la contrainte des deux secondes).
     *
     * @param objectType {@code activity} ou {@code athlete}
     * @param aspectType {@code create}, {@code update} ou {@code delete}
     * @param ownerId    identifiant Strava de l'athlète propriétaire
     * @param updates    champs modifiés ; porte {@code authorized=false} à la révocation d'accès
     */
    public void accept(String objectType, String aspectType, Long ownerId, Map<String, Object> updates) {
        if (ownerId == null) {
            return;
        }
        if ("athlete".equals(objectType)
                && updates != null && "false".equals(String.valueOf(updates.get("authorized")))) {
            dispatch(ownerId, this::revoke);
            return;
        }
        // Une suppression côté Strava ne retire rien chez nous : la sortie a pu être notée, un
        // ressenti déposé, une séance rapprochée. Effacer sur un signal externe ferait disparaître
        // le travail de l'athlète sans qu'il l'ait demandé.
        if ("activity".equals(objectType) && ("create".equals(aspectType) || "update".equals(aspectType))) {
            dispatch(ownerId, this::sync);
        }
    }

    /** Retrouve le ou les athlètes locaux liés à ce compte Strava et programme le traitement. */
    private void dispatch(Long ownerId, Consumer<UUID> action) {
        List<UUID> athleteIds = connectionRepository.findAthleteIdsByProviderAndProviderAthleteId(
                DeviceProvider.STRAVA, String.valueOf(ownerId));
        if (athleteIds.isEmpty()) {
            // Une application Strava est unique : les événements de TOUS ses athlètes arrivent ici,
            // y compris ceux d'une autre instance (dev, préproduction) partageant l'application.
            log.debug("Événement Strava ignoré : athlète {} inconnu de cette instance.", ownerId);
            return;
        }
        athleteIds.forEach(athleteId -> enqueue(athleteId, action));
    }

    private void enqueue(UUID athleteId, Consumer<UUID> action) {
        if (!inFlight.add(athleteId)) {
            return; // déjà en file : les événements d'une même sortie se coalescent
        }
        try {
            executor().execute(() -> {
                try {
                    action.accept(athleteId);
                } catch (RuntimeException ex) {
                    // Un échec ne se rejoue pas ici : la synchro planifiée repassera de toute façon.
                    log.warn("Traitement d'un événement Strava en échec (athlète {}) : {}",
                            athleteId, ex.getMessage());
                } finally {
                    inFlight.remove(athleteId);
                }
            });
        } catch (RejectedExecutionException ex) {
            inFlight.remove(athleteId);
            // La synchro planifiée rattrapera : c'est précisément à cela qu'elle sert désormais.
            log.warn("File d'événements Strava saturée : synchro différée pour l'athlète {}", athleteId);
        }
    }

    private void sync(UUID athleteId) {
        int imported = stravaService.importForAthlete(athleteId);
        log.info("Webhook Strava : {} activité(s) importée(s) pour l'athlète {}", imported, athleteId);
    }

    /** Accès révoqué depuis Strava : la connexion locale n'a plus de raison d'être. */
    private void revoke(UUID athleteId) {
        stravaService.disconnectForAthlete(athleteId);
        log.info("Webhook Strava : accès révoqué côté Strava, connexion retirée (athlète {})", athleteId);
    }

    /**
     * Exécuteur dédié et borné, créé au premier événement. Deux threads suffisent — le travail est
     * de l'attente réseau, et le quota Strava (100 requêtes / 15 min) ne récompense pas le
     * parallélisme — et une file pleine perd un événement plutôt que la mémoire du serveur.
     */
    private ThreadPoolExecutor executor() {
        ThreadPoolExecutor local = executor;
        if (local == null) {
            synchronized (this) {
                local = executor;
                if (local == null) {
                    local = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS,
                            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                            r -> {
                                Thread t = new Thread(r, "strava-webhook");
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
