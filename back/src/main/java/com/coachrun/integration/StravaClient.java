package com.coachrun.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

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
        this.oauth = RestClient.builder().baseUrl(OAUTH_BASE).build();
        this.api = RestClient.builder().baseUrl(API_BASE).build();
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StravaActivity(
            Long id,
            String name,
            String type,
            Double distance,
            @JsonProperty("moving_time") Integer movingTime,
            @JsonProperty("total_elevation_gain") Double totalElevationGain,
            @JsonProperty("average_heartrate") Double averageHeartrate,
            @JsonProperty("start_date_local") String startDateLocal) {
    }
}
