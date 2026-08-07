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

import java.util.ArrayList;
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

    // --- Abonnement aux événements (Webhook Events API) -------------------------
    // Un abonnement par APPLICATION, pas par athlète : Strava pousse ensuite les créations
    // d'activité de tous les athlètes connectés vers une unique URL de rappel. C'est ce qui
    // remplace l'attente de la prochaine synchro planifiée par une remontée en quelques secondes.

    /** Abonnement en place, ou vide s'il n'y en a aucun. Strava n'en accepte qu'un par application. */
    public List<WebhookSubscription> viewWebhookSubscriptions() {
        try {
            WebhookSubscription[] subs = api.get()
                    .uri(uri -> uri.path("/push_subscriptions")
                            .queryParam("client_id", clientId)
                            .queryParam("client_secret", clientSecret)
                            .build())
                    .retrieve()
                    .body(WebhookSubscription[].class);
            return subs == null ? List.of() : List.of(subs);
        } catch (RestClientException e) {
            throw new IllegalStateException("Lecture de l'abonnement Strava impossible : " + e.getMessage(), e);
        }
    }

    /**
     * Crée l'abonnement. Strava appelle immédiatement {@code callbackUrl} en GET pour valider
     * l'adresse : elle doit donc être publiquement joignable <b>avant</b> cet appel, et répondre
     * au défi en moins de deux secondes.
     */
    public WebhookSubscription createWebhookSubscription(String callbackUrl, String verifyToken) {
        try {
            return api.post()
                    .uri("/push_subscriptions")
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body("client_id=" + clientId + "&client_secret=" + clientSecret
                            + "&callback_url=" + callbackUrl + "&verify_token=" + verifyToken)
                    .retrieve()
                    .body(WebhookSubscription.class);
        } catch (HttpClientErrorException e) {
            // Strava répond ici en clair (« callback url not verifiable », « already exists ») :
            // remonter son message tel quel évite de faire deviner ce qui a été refusé.
            throw new IllegalStateException("Strava a refusé l'abonnement : " + e.getResponseBodyAsString(), e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Création de l'abonnement Strava impossible : " + e.getMessage(), e);
        }
    }

    public void deleteWebhookSubscription(long subscriptionId) {
        try {
            api.delete()
                    .uri(uri -> uri.path("/push_subscriptions/{id}")
                            .queryParam("client_id", clientId)
                            .queryParam("client_secret", clientSecret)
                            .build(subscriptionId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new IllegalStateException("Suppression de l'abonnement Strava impossible : " + e.getMessage(), e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WebhookSubscription(Long id, @JsonProperty("callback_url") String callbackUrl) {
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

    /** Taille de page demandée à Strava (maximum autorisé : 200). */
    private static final int PER_PAGE = 100;

    /**
     * Nombre de pages parcourues au plus. Dix pages, soit mille sorties, couvrent très largement
     * un premier import de trente jours ; le plafond existe pour qu'un compte hors norme ne
     * consomme pas le quota Strava (100 requêtes par tranche de 15 minutes) à lui seul.
     */
    private static final int MAX_PAGES = 10;

    /**
     * Sorties postérieures à {@code afterEpoch}, <b>toutes pages confondues</b>.
     *
     * <p>Une seule page était demandée. Au-delà de la cinquantième sortie de la fenêtre — ce qui
     * arrive dès le premier import d'un athlète assidu — le reste n'était jamais lu, et le
     * curseur avançait quand même : les sorties sautées l'étaient définitivement.</p>
     */
    public List<StravaActivity> listActivities(String accessToken, long afterEpoch) {
        List<StravaActivity> all = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            final int current = page;
            StravaActivity[] body = api.get()
                    .uri(uri -> uri.path("/athlete/activities")
                            .queryParam("after", afterEpoch)
                            .queryParam("page", current)
                            .queryParam("per_page", PER_PAGE).build())
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(StravaActivity[].class);
            if (body == null || body.length == 0) {
                break;
            }
            all.addAll(List.of(body));
            if (body.length < PER_PAGE) {
                break;
            }
        }
        return all;
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

    /**
     * Tours d'une activité ({@code /activities/{id}/laps}) : les répétitions telles que la montre
     * les a découpées. C'est la seule façon de relire un fractionné — le résumé d'activité ne
     * porte que des moyennes, où « 10 × 400 à 3:20 » et un footing de 8 km se ressemblent.
     *
     * <p>Un appel de plus par activité <strong>nouvellement</strong> importée, comme les flux.
     * Dégrade en liste vide si le quota est atteint : l'import continue sans les tours.</p>
     */
    public List<StravaLap> activityLaps(String accessToken, long activityId) {
        try {
            StravaLap[] body = api.get()
                    .uri(uri -> uri.path("/activities/{id}/laps").build(activityId))
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().body(StravaLap[].class);
            return body == null ? List.of() : List.of(body);
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("Quota Strava atteint sur les tours de l'activité {} — import poursuivi sans "
                    + "détail par tour", activityId);
            return List.of();
        } catch (RestClientException ex) {
            log.warn("Tours Strava indisponibles pour l'activité {} : {}", activityId, ex.getMessage());
            return List.of();
        }
    }

    /** Un tour Strava. {@code moving_time} et non {@code elapsed_time} : les arrêts ne sont pas du travail. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StravaLap(
            @JsonProperty("lap_index") Integer lapIndex,
            String name,
            Double distance,
            @JsonProperty("moving_time") Integer movingTime,
            @JsonProperty("elapsed_time") Integer elapsedTime,
            @JsonProperty("average_heartrate") Double averageHeartrate,
            @JsonProperty("max_heartrate") Double maxHeartrate,
            @JsonProperty("average_cadence") Double averageCadence,
            @JsonProperty("total_elevation_gain") Double totalElevationGain) {
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
            @JsonProperty("start_date_local") String startDateLocal,
            /**
             * Départ en UTC ({@code 2026-08-05T16:00:00Z}). Distinct de {@code start_date_local},
             * qui n'a pas de décalage et ne peut donc pas se convertir en instant : c'est celui-ci
             * qui sert à positionner le curseur d'import.
             */
            @JsonProperty("start_date") String startDate) {
    }

    /** Carte de l'activité : le tracé encodé (Google Encoded Polyline). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ActivityMap(String id, @JsonProperty("summary_polyline") String summaryPolyline) {
    }
}
