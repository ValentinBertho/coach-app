package com.coachrun.service;

import com.coachrun.dto.request.ActivityImportRequest;
import com.coachrun.dto.response.ActivityResponse;
import com.coachrun.dto.response.StravaStatusResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.integration.StravaClient;
import com.coachrun.integration.StravaClient.StravaActivity;
import com.coachrun.integration.StravaClient.TokenResponse;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Connexion Strava (OAuth) et import des activités (cf. DARI Lab — sync). Réutilise l'import
 * d'activités existant (déduplication par source + externalId). Dégrade proprement si non configuré.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StravaService {

    private static final DeviceProvider PROVIDER = DeviceProvider.STRAVA;
    /**
     * Le scope d'écriture, demandé à la connexion mais <b>jamais exigé</b>.
     *
     * <p>Strava présente ses permissions à la case à cocher : un athlète peut accorder la lecture
     * et refuser l'écriture, et sa synchronisation fonctionne alors exactement comme avant. Nous le
     * demandons quand même à tous, parce que le scope ne s'ajoute pas après coup — sans lui dans la
     * demande initiale, activer le renommage plus tard obligerait à tout redemander.</p>
     *
     * <p>Le scope réellement accordé est celui que Strava renvoie avec le jeton, et c'est lui seul
     * qui fait foi côté écriture : voir {@link #canWriteToStrava(DeviceConnection)}.</p>
     */
    private static final String WRITE_SCOPE = "activity:write";

    /**
     * {@code activity:read_all} et non {@code activity:read} : ce dernier ne remonte que les
     * activités <em>publiques</em>. Or les athlètes suivis par un coach gardent très souvent
     * leurs sorties en « privé » ou « abonnés uniquement » — avec le scope restreint, elles ne
     * se synchronisent jamais et l'intégration paraît cassée.
     */
    private static final String SCOPE = "activity:read_all," + WRITE_SCOPE;
    /** Échantillonnage des flux, aligné sur {@code GpxParser} : au-delà, la courbe n'y gagne rien. */
    private static final int STREAM_MAX_POINTS = 400;
    /** Fenêtre d'import par défaut au premier import : 30 jours. */
    private static final long DEFAULT_LOOKBACK_SEC = 30L * 24 * 3600;

    private final StravaClient client;
    private final DeviceConnectionRepository connectionRepository;
    private final AthleteRepository athleteRepository;
    private final ActivityService activityService;
    private final com.coachrun.security.OAuthStateCodec stateCodec;
    private final NotificationService notificationService;

    public StravaStatusResponse status(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER).orElse(null);
        return new StravaStatusResponse(
                client.isConfigured(),
                conn != null,
                conn != null ? conn.getProviderAthleteId() : null,
                conn != null ? conn.getLastImportEpoch() : null,
                conn != null && conn.isRenameOnProvider(),
                conn != null && canWriteToStrava(conn));
    }

    /** URL d'autorisation Strava (l'athleteId transite par le paramètre state, signé anti-CSRF). */
    public String authorizeUrl(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        requireConfigured();
        return UriComponentsBuilder.fromHttpUrl("https://www.strava.com/oauth/authorize")
                .queryParam("client_id", client.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", client.redirectUri())
                .queryParam("approval_prompt", "auto")
                .queryParam("scope", SCOPE)
                .queryParam("state", stateCodec.issue(athleteId))
                .build().toUriString();
    }

    @Transactional
    public StravaStatusResponse connect(UUID clubId, UUID athleteId, String code, String state) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        requireConfigured();
        stateCodec.verify(state, athleteId);
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code d'autorisation manquant.");
        }
        TokenResponse token = client.exchangeCode(code);
        if (token == null || token.accessToken() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Échec de l'échange de jetons Strava.");
        }
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER)
                .orElseGet(() -> {
                    DeviceConnection c = new DeviceConnection();
                    c.setAthlete(athlete);
                    c.setProvider(PROVIDER);
                    return c;
                });
        conn.setAccessToken(token.accessToken());
        conn.setRefreshToken(token.refreshToken());
        conn.setExpiresAt(token.expiresAt());
        conn.setScope(token.scope() != null ? token.scope() : SCOPE);
        if (token.athlete() != null && token.athlete().id() != null) {
            conn.setProviderAthleteId(String.valueOf(token.athlete().id()));
        }
        connectionRepository.save(conn);
        log.info("Strava connecté pour l'athlète {}", athleteId);
        return status(clubId, athleteId);
    }

    @Transactional
    public int importActivities(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        requireConfigured();
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Compte Strava non connecté."));

        String accessToken = freshAccessToken(conn);
        // Plancher glissant : au-delà de trente jours en arrière, on ne remonte pas — sinon un
        // compte resté longtemps sans sortie relirait un historique entier à chaque synchro.
        long floor = Instant.now().getEpochSecond() - DEFAULT_LOOKBACK_SEC;
        long after = conn.getLastImportEpoch() != null
                ? Math.max(conn.getLastImportEpoch(), floor)
                : floor;

        int imported = 0;
        long latestStart = 0L;
        List<ImportedActivity> summaries = new ArrayList<>();
        for (StravaActivity a : client.listActivities(accessToken, after)) {
            if (a.id() == null || a.startDateLocal() == null) {
                continue;
            }
            latestStart = Math.max(latestStart, startEpoch(a));
            // Les flux détaillés coûtent un appel par activité (quota Strava : 100 req / 15 min).
            // On écarte donc les activités déjà connues AVANT d'aller les chercher : sur une
            // synchro horaire, la quasi-totalité de la page renvoyée est déjà en base.
            if (activityService.alreadyKnown(athleteId, ActivitySource.STRAVA, String.valueOf(a.id()))) {
                continue;
            }
            try {
                ActivityResponse saved = activityService.importActivity(clubId, athleteId,
                        toImportRequest(a), extras(a, accessToken));
                imported++;
                mirrorRenameToStrava(conn, accessToken, a, saved.title());
                // Le titre retenu, pas celui de Strava : quand « Morning Run » a été remplacé par
                // le nom de la séance, c'est ce nom-là que l'athlète doit lire dans son e-mail.
                summaries.add(new ImportedActivity(saved.title(),
                        a.movingTime(),
                        a.distance() == null ? null : (int) Math.round(a.distance())));
            } catch (ConflictException dup) {
                // Deux cas, même traitement : course entre deux synchros, ou sortie déjà présente
                // sous une autre provenance. Dans les deux cas on passe — c'est le doublon qu'on
                // ne veut pas, pas l'import.
            }
        }
        conn.setLastImportEpoch(nextWatermark(after, latestStart));
        connectionRepository.save(conn);
        log.info("Import Strava athlète {} : {} activité(s)", athleteId, imported);
        // Une seule notification pour tout l'import, jamais une par activité : la synchro horaire
        // en remonte parfois plusieurs d'un coup après un week-end sans réseau.
        notificationService.notifyActivitiesImported(conn.getAthlete(), summaries);
        return imported;
    }

    /**
     * Répercute sur Strava le titre que Darilab vient de retenir — si, et seulement si, tout
     * concorde.
     *
     * <p>Le renommage local (voir {@code ActivityService}) ne touche que les noms que Strava a
     * composés lui-même, et il ne touche que notre base : le fil Strava de l'athlète garde son
     * « Morning Run ». Cette méthode ferme la boucle, sous trois conditions cumulatives :</p>
     *
     * <ol>
     *   <li><b>Le titre a effectivement changé.</b> On compare au nom reçu de Strava : s'il est
     *       resté tel quel, c'est que l'athlète l'avait nommée lui-même, et il n'y a rien à
     *       corriger chez lui.</li>
     *   <li><b>L'athlète l'a demandé</b>, case cochée, jamais par défaut.</li>
     *   <li><b>Strava nous a accordé l'écriture.</b> Le consentement dans Darilab ne vaut pas
     *       autorisation chez Strava : seul le scope renvoyé avec le jeton en décide.</li>
     * </ol>
     *
     * <p>L'échec est sans conséquence et le reste : le titre juste est déjà en base, Strava n'en
     * est que le reflet. Une écriture refusée n'interrompt ni cet import ni les suivants.</p>
     */
    private void mirrorRenameToStrava(DeviceConnection conn, String accessToken,
                                      StravaActivity a, String savedTitle) {
        if (savedTitle == null || savedTitle.equals(a.name())
                || !conn.isRenameOnProvider() || !canWriteToStrava(conn)) {
            return;
        }
        if (client.renameActivity(accessToken, a.id(), savedTitle)) {
            log.info("Sortie {} renommée sur Strava : « {} » → « {} »", a.id(), a.name(), savedTitle);
        }
    }

    /**
     * Le jeton de cette connexion porte-t-il {@code activity:write} ?
     *
     * <p>Question distincte du consentement : un athlète peut cocher la case dans Darilab et avoir
     * refusé — ou n'avoir jamais eu à accorder — l'écriture chez Strava, s'il s'est connecté avant
     * que nous ne demandions ce scope. Il lui faut alors se reconnecter, un scope ne s'ajoutant
     * pas à un jeton existant.</p>
     */
    private boolean canWriteToStrava(DeviceConnection conn) {
        String granted = conn.getScope();
        return granted != null && List.of(granted.split("[,\\s]+")).contains(WRITE_SCOPE);
    }

    /** Résumé d'une sortie tout juste importée, pour la notification. */
    public record ImportedActivity(String title, Integer durationS, Integer distanceM)
            implements NotificationService.ImportedActivitySummary {
    }

    /**
     * Recul appliqué au curseur, pour rattraper les sorties déposées en retard.
     *
     * <p>Deux jours : couvre la montre restée sans réseau le week-end et la trace téléversée le
     * lendemain. Le doublon est déjà écarté en amont, donc relire large ne coûte qu'une requête
     * de vérification par sortie — jamais un import en double.</p>
     */
    private static final long WATERMARK_OVERLAP_SEC = 2L * 24 * 3600;

    /**
     * Position du curseur pour la prochaine synchro.
     *
     * <p><strong>C'était le défaut le plus coûteux de l'intégration.</strong> Le curseur était
     * posé à <em>l'instant de la synchro</em>, alors que le paramètre {@code after} de Strava
     * filtre sur <em>l'heure de départ de la sortie</em>. Toute sortie commencée avant la synchro
     * précédente était donc exclue — définitivement. Concrètement : la synchro passe à 19 h 30,
     * on court de 18 h à 19 h 12, on téléverse à 19 h 14, et la sortie n'est jamais importée,
     * puisque la requête suivante demande « ce qui a commencé après 19 h 30 ».</p>
     *
     * <p>Cela explique aussi pourquoi le premier import fonctionne toujours : sans curseur, la
     * fenêtre de trente jours ramène tout, et le problème n'apparaît qu'à partir de la deuxième
     * synchro.</p>
     *
     * <p>Le curseur suit désormais le <b>départ de la dernière sortie vue</b>, reculé d'une marge.
     * Aucune sortie vue ne le fait avancer : sans quoi une période sans course ferait glisser le
     * curseur en avant et sauterait une trace déposée tardivement.</p>
     *
     * <p>Fonction pure, exposée pour être éprouvée directement : c'est une arithmétique de trois
     * lignes dont l'erreur a coûté toutes les sorties de plus d'une demi-heure.</p>
     */
    public static long nextWatermark(long previous, long latestStart) {
        return latestStart <= 0 ? previous
                : Math.max(previous, latestStart - WATERMARK_OVERLAP_SEC);
    }

    /**
     * Départ de la sortie en secondes epoch. {@code start_date} porte le fuseau (UTC) ;
     * {@code start_date_local} n'en a pas et ne peut donc pas être converti sans se tromper de
     * plusieurs heures — précisément l'erreur qu'un curseur ne pardonne pas.
     */
    public static long startEpoch(StravaActivity a) {
        if (a.startDate() == null) {
            return 0L;
        }
        try {
            return Instant.parse(a.startDate()).getEpochSecond();
        } catch (java.time.format.DateTimeParseException ex) {
            return 0L;
        }
    }

    @Transactional
    public void disconnect(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER)
                .ifPresent(connectionRepository::delete);
    }

    // --- Portail athlète : l'athlète connecte SA propre montre (CDC §12) -------
    // L'intégration est d'abord côté athlète ; le clubId est résolu depuis l'athlète.

    public StravaStatusResponse statusForAthlete(UUID athleteId) {
        return status(clubIdOf(athleteId), athleteId);
    }

    /**
     * L'athlète accepte — ou retire — le renommage de ses sorties sur son propre compte Strava.
     *
     * <p>Décocher n'annule rien de ce qui a déjà été écrit là-bas : nous ne gardons pas les noms
     * d'origine, et republier un nom que nous aurions deviné serait pire que de ne rien faire.
     * L'effet est uniquement sur les sorties à venir, et l'écran doit le dire.</p>
     */
    @Transactional
    public StravaStatusResponse setRenameOnStrava(UUID athleteId, boolean enabled) {
        UUID clubId = clubIdOf(athleteId);
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Compte Strava non connecté."));
        conn.setRenameOnProvider(enabled);
        connectionRepository.save(conn);
        log.info("Renommage sur Strava {} pour l'athlète {}", enabled ? "activé" : "désactivé", athleteId);
        return status(clubId, athleteId);
    }

    public String authorizeUrlForAthlete(UUID athleteId) {
        return authorizeUrl(clubIdOf(athleteId), athleteId);
    }

    @Transactional
    public StravaStatusResponse connectForAthlete(UUID athleteId, String code, String state) {
        return connect(clubIdOf(athleteId), athleteId, code, state);
    }

    @Transactional
    public int importForAthlete(UUID athleteId) {
        return importActivities(clubIdOf(athleteId), athleteId);
    }

    @Transactional
    public void disconnectForAthlete(UUID athleteId) {
        disconnect(clubIdOf(athleteId), athleteId);
    }

    private UUID clubIdOf(UUID athleteId) {
        return athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."))
                .getClub().getId();
    }

    /** Rafraîchit l'access token s'il expire dans moins de 60 s. */
    private String freshAccessToken(DeviceConnection conn) {
        if (conn.getExpiresAt() > Instant.now().getEpochSecond() + 60) {
            return conn.getAccessToken();
        }
        TokenResponse token = client.refresh(conn.getRefreshToken());
        if (token == null || token.accessToken() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Échec du rafraîchissement du jeton Strava.");
        }
        conn.setAccessToken(token.accessToken());
        conn.setRefreshToken(token.refreshToken());
        conn.setExpiresAt(token.expiresAt());
        connectionRepository.save(conn);
        return token.accessToken();
    }

    private ActivityImportRequest toImportRequest(StravaActivity a) {
        return new ActivityImportRequest(
                ActivitySource.STRAVA,
                String.valueOf(a.id()),
                LocalDate.parse(a.startDateLocal().substring(0, 10)),
                a.name(),
                a.distance() != null ? (int) Math.round(a.distance()) : null,
                a.movingTime(),
                a.averageHeartrate() != null ? (int) Math.round(a.averageHeartrate()) : null,
                a.totalElevationGain() != null ? (int) Math.round(a.totalElevationGain()) : null,
                // Jamais de confirmation automatique : si la sortie est déjà en base sous une
                // autre provenance (trace GPX importée à la main), la synchro doit l'écarter,
                // pas en créer une seconde copie.
                false);
    }

    /**
     * Capteurs, tracé et flux d'une activité Strava. Un athlète Strava n'avait ni carte ni temps
     * en zone alors qu'un import GPX lui donnait les deux : le tracé arrivait déjà (polyline
     * encodée) et les flux ne demandaient qu'un appel de plus.
     */
    private ActivityService.ImportExtras extras(StravaActivity a, String accessToken) {
        String polyline = a.map() != null ? a.map().summaryPolyline() : null;
        return new ActivityService.ImportExtras(
                round(a.maxHeartrate()),
                // Strava compte la cadence par jambe : on la double pour rester en pas/min.
                a.averageCadence() != null ? (int) Math.round(a.averageCadence() * 2) : null,
                round(a.averageWatts()),
                round(a.calories()),
                com.coachrun.util.PolylineDecoder.decode(polyline),
                toStream(client.activityStreams(accessToken, a.id())),
                toLaps(client.activityLaps(accessToken, a.id())));
    }

    /**
     * Tours Strava → tours applicatifs. Un tour unique n'en est pas un (Strava en renvoie toujours
     * au moins un, couvrant toute la sortie) : la liste est alors laissée vide et la lecture
     * retombera sur des splits kilométriques.
     */
    private java.util.List<com.coachrun.dto.response.ActivityLapsResponse.Lap> toLaps(
            java.util.List<StravaClient.StravaLap> laps) {
        if (laps == null || laps.size() < 2) {
            return java.util.List.of();
        }
        java.util.List<com.coachrun.dto.response.ActivityLapsResponse.Lap> out = new java.util.ArrayList<>();
        for (StravaClient.StravaLap l : laps) {
            out.add(com.coachrun.dto.response.ActivityLapsResponse.Lap.of(
                    l.lapIndex() != null ? l.lapIndex() : out.size() + 1,
                    l.distance() != null ? (int) Math.round(l.distance()) : null,
                    // Le temps en mouvement, comme partout ailleurs : un feu rouge n'est pas du travail.
                    l.movingTime() != null ? l.movingTime() : l.elapsedTime(),
                    round(l.averageHeartrate()),
                    round(l.maxHeartrate()),
                    // Cadence par jambe côté Strava, doublée pour rester en pas/min.
                    l.averageCadence() != null ? (int) Math.round(l.averageCadence() * 2) : null,
                    round(l.totalElevationGain())));
        }
        return out;
    }

    private Integer round(Double value) {
        return value != null ? (int) Math.round(value) : null;
    }

    /**
     * Convertit les flux Strava au format de {@code GpxParser.buildStream()} :
     * {@code [elapsedS, hr, paceSecPerKm]}, -1 pour une valeur absente. Le temps-en-zone et la
     * courbe d'allure n'ont alors rien à savoir de la source.
     */
    private java.util.List<int[]> toStream(StravaClient.ActivityStreams streams) {
        if (streams == null || streams.time().isEmpty()) {
            return java.util.List.of();
        }
        java.util.List<Double> time = streams.time();
        java.util.List<Double> hr = streams.heartrate();
        java.util.List<Double> velocity = streams.velocity();

        int step = Math.max(1, time.size() / STREAM_MAX_POINTS);
        java.util.List<int[]> stream = new java.util.ArrayList<>();
        for (int i = 0; i < time.size(); i += step) {
            int elapsed = (int) Math.round(time.get(i));
            int bpm = i < hr.size() && hr.get(i) != null ? (int) Math.round(hr.get(i)) : -1;
            int pace = -1;
            if (i < velocity.size() && velocity.get(i) != null && velocity.get(i) > 0.3) {
                // En dessous de 0,3 m/s l'athlète est à l'arrêt : l'allure n'a pas de sens.
                pace = (int) Math.round(1000.0 / velocity.get(i));
            }
            stream.add(new int[] {elapsed, bpm, pace});
        }
        return stream;
    }

    private void requireConfigured() {
        if (!client.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Intégration Strava non configurée sur ce serveur.");
        }
    }

    private Athlete requireAthlete(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }
}
