package com.coachrun.integration;

import com.coachrun.config.HttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Client OAuth 2.0 PKCE + « partner gateway » du Garmin Connect Developer Program.
 *
 * <p>Deux hôtes distincts, comme l'impose la spécification Garmin :</p>
 * <ul>
 *   <li>{@code diauth.garmin.com} — l'échange de jetons. <b>Côté serveur uniquement</b> : Garmin
 *       n'offre pas de flux sans secret, et le secret ne doit jamais quitter ce back.</li>
 *   <li>{@code apis.garmin.com} — l'identifiant Garmin de l'utilisateur, ses permissions, la
 *       suppression de son enregistrement (et, aux étapes suivantes, la Training API).</li>
 * </ul>
 *
 * <p>Désactivé proprement si {@code app.garmin.client-id} n'est pas configuré.</p>
 */
@Slf4j
@Component
public class GarminClient {

    /** Page de consentement Garmin, ouverte dans le navigateur de l'athlète. */
    public static final String AUTHORIZE_URL = "https://connect.garmin.com/oauth2Confirm";
    private static final String TOKEN_BASE = "https://diauth.garmin.com";
    private static final String TOKEN_PATH = "/di-oauth2-service/oauth/token";
    private static final String API_BASE = "https://apis.garmin.com";

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient oauth;
    private final RestClient api;

    public GarminClient(
            @Value("${app.garmin.client-id:}") String clientId,
            @Value("${app.garmin.client-secret:}") String clientSecret,
            @Value("${app.garmin.redirect-uri:}") String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.oauth = RestClient.builder().baseUrl(TOKEN_BASE).requestFactory(HttpClients.timeouts()).build();
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

    /** Échange le code d'autorisation contre des jetons (étape 2 de la spécification PKCE). */
    public TokenResponse exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = baseForm("authorization_code");
        form.add("code", code);
        form.add("code_verifier", codeVerifier);
        if (StringUtils.hasText(redirectUri)) {
            // Doit être identique à celui de la demande d'autorisation, sinon Garmin refuse.
            form.add("redirect_uri", redirectUri);
        }
        return postToken(form);
    }

    /**
     * Rafraîchit l'access token. Garmin renvoie <b>un nouveau refresh token à chaque fois</b> :
     * l'ancien ne doit plus servir, il faut enregistrer celui-ci.
     */
    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseForm("refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(form);
    }

    /** Identifiant Garmin stable de l'utilisateur (le même d'une connexion à l'autre). */
    public String userId(String accessToken) {
        UserIdResponse r = api.get().uri("/partner-gateway/rest/user/id")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve().body(UserIdResponse.class);
        return r == null ? null : r.userId();
    }

    /**
     * Permissions accordées par l'athlète au moment du consentement ({@code WORKOUT_IMPORT},
     * {@code ACTIVITY_EXPORT}…). Il peut en avoir décoché : seule cette liste fait foi.
     */
    public List<String> permissions(String accessToken) {
        PermissionsResponse r = api.get().uri("/partner-gateway/rest/user/permissions")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve().body(PermissionsResponse.class);
        return r == null || r.permissions() == null ? List.of() : r.permissions();
    }

    /**
     * Supprime l'enregistrement de l'utilisateur chez Garmin. <b>Obligatoire</b> quand
     * l'application offre sa propre déconnexion : l'athlète qui clique « Déconnecter » ici doit
     * voir Darilab disparaître aussi de son compte Garmin Connect.
     *
     * @return vrai si Garmin a accepté la suppression
     */
    public boolean deleteRegistration(String accessToken) {
        try {
            api.delete().uri("/partner-gateway/rest/user/registration")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException e) {
            log.warn("Suppression de l'enregistrement Garmin refusée : {}", e.getMessage());
            return false;
        }
    }

    private MultiValueMap<String, String> baseForm(String grantType) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", grantType);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private TokenResponse postToken(MultiValueMap<String, String> form) {
        return oauth.post().uri(TOKEN_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(TokenResponse.class);
    }

    /**
     * Réponse du serveur de jetons. {@code expiresIn} et {@code refreshTokenExpiresIn} sont des
     * <em>durées</em> en secondes (24 h et ~90 jours), pas des instants.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("refresh_token_expires_in") Long refreshTokenExpiresIn,
            String scope) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserIdResponse(String userId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PermissionsResponse(List<String> permissions) {
    }
}
