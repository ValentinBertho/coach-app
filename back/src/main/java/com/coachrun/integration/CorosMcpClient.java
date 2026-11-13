package com.coachrun.integration;

import com.coachrun.config.HttpClients;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client de COROS MCP : OAuth 2.0 (enregistrement dynamique + PKCE) et appels d'outils MCP.
 *
 * <p>Ce que fait COROS, d'après la documentation officielle (dépôt {@code coroslab/COROS-MCP},
 * script {@code coros_mcp_login.py}) :</p>
 * <ul>
 *   <li>une passerelle {@code https://mcp.coros.com} dont la découverte OpenID renvoie le serveur
 *       régional à utiliser ({@code mcpeu}, {@code mcpus}, {@code mcpcn}) ;</li>
 *   <li>aucun identifiant d'application délivré par COROS : on s'enregistre soi-même
 *       ({@code POST /connect/register}, client public, sans secret) ;</li>
 *   <li>autorisation PKCE S256, scope {@code openid offline_access mcp.tools}, paramètre
 *       {@code resource} = adresse MCP ;</li>
 *   <li>access token valable 30 jours, refresh token 180 jours, <b>renouvelé à chaque
 *       rafraîchissement</b> ;</li>
 *   <li>appels JSON-RPC 2.0 sans session sur {@code POST /mcp}, réponse en JSON ou en flux SSE.</li>
 * </ul>
 *
 * <p>Désactivé tant que {@code app.coros.enabled} est faux : l'intégration s'ouvre quand on le
 * décide, indépendamment du déploiement.</p>
 */
@Slf4j
@Component
public class CorosMcpClient {

    public static final String SCOPES = "openid offline_access mcp.tools";
    static final String CLIENT_NAME = "Darilab";
    private static final String PROTOCOL_VERSION = "2025-06-18";
    /** La passerelle peut changer d'avis (nouvelle région) : on la redemande au bout d'une heure. */
    private static final Duration DISCOVERY_TTL = Duration.ofHours(1);

    private final boolean enabled;
    private final String gatewayUrl;
    private final String redirectUri;
    private final RestClient http;
    private final ObjectMapper mapper;
    private final Map<String, Cached> discovery = new ConcurrentHashMap<>();

    public CorosMcpClient(
            @Value("${app.coros.enabled:false}") boolean enabled,
            @Value("${app.coros.gateway-url:https://mcp.coros.com}") String gatewayUrl,
            @Value("${app.coros.redirect-uri:}") String redirectUri,
            ObjectMapper mapper) {
        this.enabled = enabled;
        this.gatewayUrl = trimSlash(gatewayUrl);
        this.redirectUri = redirectUri;
        this.mapper = mapper;
        this.http = RestClient.builder().requestFactory(HttpClients.timeouts()).build();
    }

    public boolean isConfigured() {
        return enabled && StringUtils.hasText(gatewayUrl) && StringUtils.hasText(redirectUri);
    }

    public String redirectUri() {
        return redirectUri;
    }

    /** Adresses OAuth et MCP d'un serveur COROS. */
    public record Endpoints(String issuer, String authorization, String token, String registration,
                            String revocation, String mcp) {
    }

    private record Cached(Endpoints endpoints, Instant at) {
    }

    /** Le serveur régional désigné par la passerelle (ou la passerelle elle-même à défaut). */
    public Endpoints endpoints() {
        return endpointsFor(gatewayUrl);
    }

    /**
     * Adresses d'un serveur, lues dans sa découverte OpenID. Si la découverte échoue, on retombe
     * sur les chemins que documente COROS : mieux vaut tenter que refuser la connexion.
     */
    public Endpoints endpointsFor(String issuer) {
        String base = trimSlash(issuer);
        Cached c = discovery.get(base);
        if (c != null && c.at().plus(DISCOVERY_TTL).isAfter(Instant.now())) {
            return c.endpoints();
        }
        Endpoints e = discover(base);
        discovery.put(base, new Cached(e, Instant.now()));
        return e;
    }

    private Endpoints discover(String base) {
        JsonNode doc = null;
        try {
            doc = http.get().uri(base + "/.well-known/openid-configuration")
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("COROS : découverte OpenID impossible sur {} : {}", base, e.getMessage());
        }
        return endpointsFrom(base, doc);
    }

    /** Lit la découverte OpenID ; tout champ absent prend le chemin documenté par COROS. */
    static Endpoints endpointsFrom(String base, JsonNode doc) {
        String issuer = text(doc, "issuer");
        issuer = issuer != null && issuer.startsWith("https://") ? trimSlash(issuer) : base;
        return new Endpoints(
                issuer,
                orElse(text(doc, "authorization_endpoint"), issuer + "/oauth2/authorize"),
                orElse(text(doc, "token_endpoint"), issuer + "/oauth2/token"),
                orElse(text(doc, "registration_endpoint"), issuer + "/connect/register"),
                text(doc, "revocation_endpoint"),
                issuer + "/mcp");
    }

    /** Enregistre Darilab comme client public auprès de ce serveur (RFC 7591). */
    public String registerClient(Endpoints e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_name", CLIENT_NAME);
        body.put("redirect_uris", List.of(redirectUri));
        body.put("grant_types", List.of("authorization_code", "refresh_token"));
        body.put("response_types", List.of("code"));
        body.put("scope", SCOPES);
        body.put("token_endpoint_auth_method", "none");
        JsonNode r = http.post().uri(e.registration())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(JsonNode.class);
        String clientId = text(r, "client_id");
        if (clientId == null) {
            throw new IllegalStateException("COROS n'a pas renvoyé de client_id à l'enregistrement.");
        }
        return clientId;
    }

    public String authorizeUrl(Endpoints e, String clientId, String codeChallenge, String state) {
        return UriComponentsBuilder.fromHttpUrl(e.authorization())
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", SCOPES)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("resource", e.mcp())
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    public TokenResponse exchangeCode(Endpoints e, String clientId, String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        return postToken(e, form);
    }

    /** Le refresh token reçu en retour remplace l'ancien, qui ne sert plus. */
    public TokenResponse refresh(Endpoints e, String clientId, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("client_id", clientId);
        form.add("refresh_token", refreshToken);
        return postToken(e, form);
    }

    /**
     * Révoque un jeton (RFC 7009), si le serveur annonce une adresse de révocation. COROS n'en
     * documente pas : sans elle, l'athlète retire l'accès depuis son app COROS.
     *
     * @return vrai si COROS a accepté la révocation
     */
    public boolean revoke(Endpoints e, String clientId, String token) {
        if (!StringUtils.hasText(e.revocation()) || !StringUtils.hasText(token)) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("token", token);
        form.add("token_type_hint", "refresh_token");
        try {
            http.post().uri(e.revocation())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException ex) {
            log.warn("Révocation COROS refusée : {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Appelle un outil MCP et renvoie son {@code result} ({@code content}, {@code isError}…).
     * Le serveur est sans session : chaque appel est précédé de son {@code initialize}.
     *
     * @throws McpException si COROS répond par une erreur JSON-RPC
     */
    public JsonNode callTool(Endpoints e, String accessToken, String tool, Map<String, Object> arguments) {
        rpc(e, accessToken, 1, "initialize", Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", CLIENT_NAME, "version", "1.0.0")));
        return rpc(e, accessToken, 2, "tools/call", Map.of(
                "name", tool,
                "arguments", arguments == null ? Map.of() : arguments));
    }

    /**
     * Schéma d'entrée d'un outil, lu dans {@code tools/list} : c'est le seul endroit où COROS
     * décrit l'objet {@code course}. Null si l'outil n'est pas exposé.
     */
    public JsonNode toolInputSchema(Endpoints e, String accessToken, String tool) {
        rpc(e, accessToken, 1, "initialize", Map.of(
                "protocolVersion", PROTOCOL_VERSION,
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", CLIENT_NAME, "version", "1.0.0")));
        String cursor = null;
        for (int page = 0; page < 10; page++) {
            Map<String, Object> params = cursor == null ? Map.of() : Map.of("cursor", cursor);
            JsonNode result = rpc(e, accessToken, 2 + page, "tools/list", params);
            if (result == null) {
                return null;
            }
            for (JsonNode t : result.path("tools")) {
                if (tool.equals(t.path("name").asText())) {
                    return t.get("inputSchema");
                }
            }
            cursor = text(result, "nextCursor");
            if (cursor == null) {
                return null;
            }
        }
        return null;
    }

    private JsonNode rpc(Endpoints e, String accessToken, int id, String method, Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", id);
        body.put("method", method);
        body.put("params", params);
        ResponseEntity<String> r = http.post().uri(e.mcp())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json, text/event-stream")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().toEntity(String.class);
        JsonNode payload = readRpcPayload(mapper, r.getBody());
        if (payload == null) {
            throw new McpException("Réponse MCP vide (" + method + ").");
        }
        JsonNode error = payload.get("error");
        if (error != null && !error.isNull()) {
            throw new McpException("Erreur MCP (" + method + ") : " + text(error, "message"));
        }
        return payload.get("result");
    }

    /**
     * Le transport « streamable HTTP » répond soit en JSON, soit en flux SSE dont chaque
     * {@code data:} porte un message JSON-RPC. On garde la réponse (le message qui a un id).
     */
    static JsonNode readRpcPayload(ObjectMapper mapper, String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String trimmed = body.strip();
        try {
            if (trimmed.startsWith("{")) {
                return mapper.readTree(trimmed);
            }
            JsonNode last = null;
            StringBuilder data = new StringBuilder();
            for (String line : (trimmed + "\n\n").split("\\r?\\n", -1)) {
                if (line.startsWith("data:")) {
                    data.append(line.substring(5).strip());
                } else if (line.isEmpty() && !data.isEmpty()) {
                    JsonNode msg = mapper.readTree(data.toString());
                    data.setLength(0);
                    if (msg.has("result") || msg.has("error")) {
                        last = msg;
                    }
                }
            }
            return last;
        } catch (Exception ex) {
            throw new McpException("Réponse MCP illisible : " + ex.getMessage());
        }
    }

    /**
     * Identifiant COROS stable de l'utilisateur : le {@code sub} de l'id_token. Le jeton vient
     * directement du serveur de jetons, en TLS : on le lit sans en revérifier la signature, et
     * on ne s'en sert que pour l'affichage et l'audit.
     */
    public static String subjectOf(String idToken) {
        if (!StringUtils.hasText(idToken)) {
            return null;
        }
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            byte[] json = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode claims = new ObjectMapper().readTree(new String(json, StandardCharsets.UTF_8));
            String sub = text(claims, "sub");
            return sub != null && sub.length() <= 64 ? sub : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Région lisible d'un serveur COROS : « EU », « US », « CN », ou null si inconnue. */
    public static String regionOf(String issuer) {
        if (issuer == null) {
            return null;
        }
        if (issuer.contains("mcpeu.")) {
            return "EU";
        }
        if (issuer.contains("mcpus.")) {
            return "US";
        }
        if (issuer.contains("mcpcn.")) {
            return "CN";
        }
        return null;
    }

    private TokenResponse postToken(Endpoints e, MultiValueMap<String, String> form) {
        return http.post().uri(e.token())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().body(TokenResponse.class);
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode v = node.get(field);
        return v != null && v.isTextual() && !v.asText().isBlank() ? v.asText() : null;
    }

    private static String orElse(String value, String fallback) {
        return value != null ? value : fallback;
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return null;
        }
        String s = url.strip();
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Réponse du serveur de jetons ; {@code expiresIn} est une durée en secondes. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("id_token") String idToken,
            String scope) {
    }

    /** COROS a répondu, mais par une erreur MCP (outil inconnu, arguments refusés…). */
    public static class McpException extends RuntimeException {
        public McpException(String message) {
            super(message);
        }
    }
}
