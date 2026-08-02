package com.coachrun.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import com.coachrun.config.HttpClients;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Client OAuth + API Strava (HTTP). Encapsule l'échange de jetons et l'import d'activités.
 * Désactivé proprement si {@code app.strava.client-id} n'est pas configuré.
 */
@Slf4j
@Component
public class StravaClient {

    private static final String OAUTH_BASE = "https://www.strava.com";
    private static final String API_BASE = "https://www.strava.com/api/v3";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient oauth;
    private final RestClient api;

    public StravaClient(
            @Value("${app.strava.client-id:}") String clientId,
            @Value("${app.strava.client-secret:}") String clientSecret,
            @Value("${app.strava.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        // Timeouts explicites : l'import Strava tourne dans un job planifié, une connexion qui
        // pend y retiendrait un thread du pool pour toujours.
        this.oauth = RestClient.builder().baseUrl(OAUTH_BASE).requestFactory(HttpClients.timeouts()).build();
        this.api = RestClient.builder().baseUrl(API_BASE).requestFactory(HttpClients.timeouts()).build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    public String clientId() {
        return clientId;
    }

    public String redirectUri() {
        return redirectUri;
    }

    /** Échange le code d'autorisation contre des jetons. */
    public TokenResponse exchangeCode(String code) {
        return oauth.post().uri("/oauth/token")
                .body(java.util.Map.of(
                        "client_id", clientId,
                        "client_secret", clientSecret,
                        "code", code,
                        "grant_type", "authorization_code"))
                .retrieve().body(TokenResponse.class);
    }

    /** Rafraîchit l'access token. */
    public TokenResponse refresh(String refreshToken) {
        return oauth.post().uri("/oauth/token")
                .body(java.util.Map.of(
                        "client_id", clientId,
                        "client_secret", clientSecret,
                        "refresh_token", refreshToken,
                        "grant_type", "refresh_token"))
                .retrieve().body(TokenResponse.class);
    }

    /** Liste les activités postérieures à {@code afterEpoch} (secondes). */
    public List<StravaActivity> listActivities(String accessToken, long afterEpoch) {
        StravaActivity[] body = api.get()
                .uri(uri -> uri.path("/athlete/activities")
                        .queryParam("after", afterEpoch)
                        .queryParam("per_page", 50).build())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve().body(StravaActivity[].class);
        return body == null ? List.of() : List.of(body);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_at") long expiresAt,
            String scope,
            StravaAthlete athlete) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StravaAthlete(Long id) {
    }

    /**
     * Flux détaillés d'une activité (séries alignées sur le même index) : temps écoulé, fréquence
     * cardiaque et vitesse instantanée. Alimente le temps-en-zone, que seul l'import GPX
     * remplissait jusqu'ici.
     *
     * <p>Un appel par activité : à réserver aux activités <strong>nouvellement</strong> importées,
     * le quota Strava étant de 100 requêtes / 15 min.</p>
     *
     * @return les flux, ou {@code null} si l'activité n'en a pas (tapis, saisie manuelle) ou si le
     *         quota est atteint — dans tous les cas la synchro continue sans eux
     */
    public ActivityStreams activityStreams(String accessToken, long activityId) {
        try {
            StreamSet body = api.get()
                    .uri(uri -> uri.path("/activities/{id}/streams")
                            .queryParam("keys", "time,heartrate,velocity_smooth")
                            .queryParam("key_by_type", true).build(activityId))
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(StreamSet.class);
            return body == null ? null : new ActivityStreams(
                    values(body.time()), values(body.heartrate()), values(body.velocitySmooth()));
        } catch (HttpClientErrorException.TooManyRequests ex) {
            // Quota Strava (100 req / 15 min) : on journalise et on continue sans les flux.
            log.warn("Quota Strava atteint sur les flux de l'activité {} — import poursuivi sans "
                    + "temps-en-zone", activityId);
            return null;
        } catch (RestClientException ex) {
            log.warn("Flux Strava indisponibles pour l'activité {} : {}", activityId, ex.getMessage());
            return null;
        }
    }

    private static List<Double> values(Stream stream) {
        return stream == null || stream.data() == null ? List.of() : stream.data();
    }

    /** Flux d'une activité : secondes écoulées, FC (bpm) et vitesse (m/s), même longueur. */
    public record ActivityStreams(List<Double> time, List<Double> heartrate, List<Double> velocity) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StreamSet(
            Stream time,
            Stream heartrate,
            @JsonProperty("velocity_smooth") Stream velocitySmooth) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Stream(List<Double> data) {
    }

    /**
     * Activité Strava. Le client ne désérialisait que 8 champs et jetait le reste de la même
     * réponse : la FC max, la cadence, la puissance et surtout le tracé (« summary_polyline »)
     * étaient perdus alors qu'ils arrivaient déjà.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StravaActivity(
            Long id,
            String name,
            String type,
            Double distance,
            @JsonProperty("moving_time") Integer movingTime,
            @JsonProperty("total_elevation_gain") Double totalElevationGain,
            @JsonProperty("average_heartrate") Double averageHeartrate,
            @JsonProperty("max_heartrate") Double maxHeartrate,
            @JsonProperty("average_speed") Double averageSpeed,
            @JsonProperty("average_cadence") Double averageCadence,
            @JsonProperty("average_watts") Double averageWatts,
            Double calories,
            @JsonProperty("suffer_score") Double sufferScore,
            @JsonProperty("gear_id") String gearId,
            @JsonProperty("workout_type") Integer workoutType,
            @JsonProperty("pr_count") Integer prCount,
            ActivityMap map,
            @JsonProperty("start_date_local") String startDateLocal) {
    }

    /** Carte de l'activité : le tracé encodé (Google Encoded Polyline). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivityMap(String id, @JsonProperty("summary_polyline") String summaryPolyline) {
    }
}
