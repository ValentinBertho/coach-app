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
    private static final String SCOPE = "activity:read";
    /** Fenêtre d'import par défaut au premier import : 30 jours. */
    private static final long DEFAULT_LOOKBACK_SEC = 30L * 24 * 3600;

    private final StravaClient client;
    private final DeviceConnectionRepository connectionRepository;
    private final AthleteRepository athleteRepository;
    private final ActivityService activityService;

    public StravaStatusResponse status(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER).orElse(null);
        return new StravaStatusResponse(
                client.isConfigured(),
                conn != null,
                conn != null ? conn.getProviderAthleteId() : null,
                conn != null ? conn.getLastImportEpoch() : null);
    }

    /** URL d'autorisation Strava (l'athleteId transite par le paramètre state). */
    public String authorizeUrl(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        requireConfigured();
        return UriComponentsBuilder.fromHttpUrl("https://www.strava.com/oauth/authorize")
                .queryParam("client_id", client.clientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", client.redirectUri())
                .queryParam("approval_prompt", "auto")
                .queryParam("scope", SCOPE)
                .queryParam("state", athleteId)
                .build().toUriString();
    }

    @Transactional
    public StravaStatusResponse connect(UUID clubId, UUID athleteId, String code) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        requireConfigured();
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
            try {
                activityService.importActivity(clubId, athleteId, toImportRequest(a));
                imported++;
            } catch (ConflictException dup) {
                // Activité déjà importée : on ignore.
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
    public StravaStatusResponse connectForAthlete(UUID athleteId, String code) {
        return connect(clubIdOf(athleteId), athleteId, code);
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
                a.totalElevationGain() != null ? (int) Math.round(a.totalElevationGain()) : null);
    }

    private void requireConfigured() {
        if (!client.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Intégration Strava non configurée sur ce serveur.");
        }
    }

    private Athlete requireAthlete(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }
}
