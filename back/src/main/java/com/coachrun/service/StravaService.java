package com.coachrun.service;

import com.coachrun.dto.request.ActivityImportRequest;
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
     * {@code activity:read_all} et non {@code activity:read} : ce dernier ne remonte que les
     * activités <em>publiques</em>. Or les athlètes suivis par un coach gardent très souvent
     * leurs sorties en « privé » ou « abonnés uniquement » — avec le scope restreint, elles ne
     * se synchronisent jamais et l'intégration paraît cassée.
     */
    private static final String SCOPE = "activity:read_all";
    /** Échantillonnage des flux, aligné sur {@code GpxParser} : au-delà, la courbe n'y gagne rien. */
    private static final int STREAM_MAX_POINTS = 400;
    /** Fenêtre d'import par défaut au premier import : 30 jours. */
    private static final long DEFAULT_LOOKBACK_SEC = 30L * 24 * 3600;

    private final StravaClient client;
    private final DeviceConnectionRepository connectionRepository;
    private final AthleteRepository athleteRepository;
    private final ActivityService activityService;
    private final com.coachrun.security.OAuthStateCodec stateCodec;

    public StravaStatusResponse status(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER).orElse(null);
        return new StravaStatusResponse(
                client.isConfigured(),
                conn != null,
                conn != null ? conn.getProviderAthleteId() : null,
                conn != null ? conn.getLastImportEpoch() : null);
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
        long after = conn.getLastImportEpoch() != null
                ? conn.getLastImportEpoch()
                : Instant.now().getEpochSecond() - DEFAULT_LOOKBACK_SEC;

        int imported = 0;
        for (StravaActivity a : client.listActivities(accessToken, after)) {
            if (a.id() == null || a.startDateLocal() == null) {
                continue;
            }
            // Les flux détaillés coûtent un appel par activité (quota Strava : 100 req / 15 min).
            // On écarte donc les activités déjà connues AVANT d'aller les chercher : sur une
            // synchro horaire, la quasi-totalité de la page renvoyée est déjà en base.
            if (activityService.alreadyImported(athleteId, ActivitySource.STRAVA, String.valueOf(a.id()))) {
                continue;
            }
            try {
                activityService.importActivity(clubId, athleteId, toImportRequest(a),
                        extras(a, accessToken));
                imported++;
            } catch (ConflictException dup) {
                // Deux cas, même traitement : course entre deux synchros, ou sortie déjà présente
                // sous une autre provenance. Dans les deux cas on passe — c'est le doublon qu'on
                // ne veut pas, pas l'import.
            }
        }
        conn.setLastImportEpoch(Instant.now().getEpochSecond());
        connectionRepository.save(conn);
        log.info("Import Strava athlète {} : {} activité(s)", athleteId, imported);
        return imported;
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
