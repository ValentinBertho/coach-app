package com.coachrun.service;

import com.coachrun.dto.response.GarminStatusResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.integration.GarminClient;
import com.coachrun.integration.GarminClient.TokenResponse;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.security.OAuthStateCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Connexion Garmin Connect (OAuth 2.0 PKCE) d'un athlète.
 *
 * <p>Côté athlète uniquement : c'est lui qui relie <b>son</b> compte Garmin, jamais son coach à
 * sa place (cf. la politique de confidentialité, « Montres et applications connectées »).</p>
 *
 * <p>Cette classe ne fait que la liaison — consentement, jetons, permissions, déconnexion.
 * L'envoi des séances (Training API) et la réception des activités (Activity API) s'appuient
 * dessus via {@link #freshAccessToken(DeviceConnection)}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GarminService {

    private static final DeviceProvider PROVIDER = DeviceProvider.GARMIN;
    public static final String WORKOUT_IMPORT = "WORKOUT_IMPORT";
    public static final String ACTIVITY_EXPORT = "ACTIVITY_EXPORT";
    /**
     * Marge de rafraîchissement : Garmin recommande de retrancher au moins 600 s à la durée de
     * vie annoncée, pour ne jamais présenter un jeton qui expire pendant l'appel.
     */
    private static final long REFRESH_MARGIN_SEC = 600;

    private final GarminClient client;
    private final DeviceConnectionRepository connectionRepository;
    private final AthleteRepository athleteRepository;
    private final OAuthStateCodec stateCodec;
    private final AdminAuditService audit;
    /**
     * Fournisseur paresseux : le service d'envoi des séances dépend de celui-ci (jetons), et
     * celui-ci a besoin de lui à la déconnexion. Résolu à l'usage, le cycle n'existe pas.
     */
    private final ObjectProvider<GarminWorkoutSyncService> workoutSync;

    public GarminStatusResponse statusForAthlete(UUID athleteId) {
        requireAthlete(athleteId);
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER).orElse(null);
        List<String> permissions = conn != null ? permissionsOf(conn) : List.of();
        GarminWorkoutSyncService.Summary summary = conn != null
                ? workoutSync.getObject().summary(athleteId)
                : new GarminWorkoutSyncService.Summary(0, 0);
        return new GarminStatusResponse(
                client.isConfigured(),
                conn != null,
                conn != null ? conn.getProviderAthleteId() : null,
                permissions,
                permissions.contains(WORKOUT_IMPORT),
                permissions.contains(ACTIVITY_EXPORT),
                summary.workoutsOnGarmin(),
                summary.workoutsInError());
    }

    /**
     * URL de consentement Garmin. Le {@code state} signé porte l'athlète ; le
     * {@code code_challenge} est dérivé de ce même state (voir {@link OAuthStateCodec#pkceVerifier}),
     * ce qui permet de retrouver le verifier au retour sans rien stocker.
     */
    public String authorizeUrlForAthlete(UUID athleteId) {
        requireAthlete(athleteId);
        requireConfigured();
        String state = stateCodec.issue(athleteId);
        String challenge = OAuthStateCodec.pkceChallenge(stateCodec.pkceVerifier(state));
        UriComponentsBuilder url = UriComponentsBuilder.fromHttpUrl(GarminClient.AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", client.clientId())
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("state", state);
        if (StringUtils.hasText(client.redirectUri())) {
            url.queryParam("redirect_uri", client.redirectUri());
        }
        return url.encode().build().toUriString();
    }

    @Transactional
    public GarminStatusResponse connectForAthlete(UUID athleteId, String code, String state) {
        Athlete athlete = requireAthlete(athleteId);
        requireConfigured();
        stateCodec.verify(state, athleteId);
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code d'autorisation manquant.");
        }
        TokenResponse token;
        try {
            token = client.exchangeCode(code, stateCodec.pkceVerifier(state));
        } catch (RestClientException e) {
            log.warn("Échange de jetons Garmin refusé : {}", e.getMessage());
            token = null;
        }
        if (token == null || token.accessToken() == null || token.refreshToken() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Échec de l'échange de jetons Garmin.");
        }

        String userId;
        List<String> permissions;
        try {
            userId = client.userId(token.accessToken());
            permissions = client.permissions(token.accessToken());
        } catch (RestClientException e) {
            log.warn("Lecture du profil Garmin impossible : {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Garmin n'a pas répondu, réessaie dans un instant.");
        }

        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER)
                .orElseGet(() -> {
                    DeviceConnection c = new DeviceConnection();
                    c.setAthlete(athlete);
                    c.setProvider(PROVIDER);
                    return c;
                });
        applyToken(conn, token);
        conn.setProviderAthleteId(userId);
        conn.setScope(joinPermissions(permissions));
        connectionRepository.save(conn);
        log.info("Garmin connecté pour l'athlète {} (permissions : {})", athleteId, permissions);
        // Aucun jeton au journal : seulement l'identifiant Garmin, opaque.
        audit.record(AdminAuditAction.DEVICE_CONNECTED, AdminAuditTarget.ATHLETE,
                athleteId, athleteLabel(athlete),
                "Garmin" + (userId != null ? " (compte " + userId + ")" : ""));
        return statusForAthlete(athleteId);
    }

    /**
     * L'athlète se déconnecte depuis Darilab. Garmin exige alors qu'on supprime aussi
     * l'enregistrement de son côté : sans cela, Darilab resterait listé dans son compte Garmin
     * Connect alors qu'il croit l'avoir retiré. L'échec de cet appel n'empêche pas la
     * déconnexion locale — les jetons sont effacés quoi qu'il arrive.
     */
    /** J'envoie maintenant mes séances à venir sur ma montre (sans attendre la passe planifiée). */
    @Transactional
    public GarminStatusResponse syncWorkoutsForAthlete(UUID athleteId) {
        requireAthlete(athleteId);
        requireConfigured();
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Compte Garmin non connecté."));
        if (!permissionsOf(conn).contains(WORKOUT_IMPORT)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Garmin n'autorise pas l'import d'entraînements sur ce compte.");
        }
        workoutSync.getObject().syncAthlete(athleteId);
        return statusForAthlete(athleteId);
    }

    @Transactional
    public void disconnectForAthlete(UUID athleteId) {
        Athlete athlete = requireAthlete(athleteId);
        connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER).ifPresent(conn -> {
            boolean remote = false;
            String token = null;
            if (client.isConfigured()) {
                try {
                    token = freshAccessToken(conn);
                } catch (RuntimeException e) {
                    log.warn("Jeton Garmin indisponible pour l'athlète {} : {}", athleteId, e.getMessage());
                }
            }
            // D'abord retirer de sa montre les séances à venir que nous y avions mises : après la
            // désinscription, nous n'en aurions plus le droit et elles y resteraient.
            workoutSync.getObject().removeAllForAthlete(athleteId, token);
            if (token != null) {
                try {
                    remote = client.deleteRegistration(token);
                } catch (RuntimeException e) {
                    log.warn("Désinscription Garmin impossible pour l'athlète {} : {}", athleteId, e.getMessage());
                }
            }
            connectionRepository.delete(conn);
            audit.record(AdminAuditAction.DEVICE_DISCONNECTED, AdminAuditTarget.ATHLETE,
                    athleteId, athleteLabel(athlete),
                    "Garmin — jetons supprimés" + (remote ? ", enregistrement Garmin retiré" : ""));
        });
    }

    /**
     * Garmin signale que l'utilisateur a retiré Darilab depuis son compte (ou que nous l'avons
     * fait). La connexion locale n'a plus de raison d'être ; on ne rappelle surtout pas Garmin.
     *
     * @return nombre de connexions retirées
     */
    @Transactional
    public int handleDeregistration(String garminUserId) {
        List<UUID> athleteIds = connectionRepository
                .findAthleteIdsByProviderAndProviderAthleteId(PROVIDER, garminUserId);
        for (UUID athleteId : athleteIds) {
            connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER).ifPresent(conn -> {
                // Plus aucun droit sur son compte : on oublie seulement ce que nous y avions envoyé.
                workoutSync.getObject().removeAllForAthlete(athleteId, null);
                connectionRepository.delete(conn);
                Athlete athlete = conn.getAthlete();
                audit.record(AdminAuditAction.DEVICE_DISCONNECTED, AdminAuditTarget.ATHLETE,
                        athleteId, athleteLabel(athlete), "Garmin — accès retiré depuis Garmin Connect");
            });
        }
        if (!athleteIds.isEmpty()) {
            log.info("Garmin : désinscription reçue, {} connexion(s) retirée(s)", athleteIds.size());
        }
        return athleteIds.size();
    }

    /** L'utilisateur a modifié ses permissions depuis Garmin Connect : on suit sa décision. */
    @Transactional
    public int handlePermissionsChange(String garminUserId, List<String> permissions) {
        List<UUID> athleteIds = connectionRepository
                .findAthleteIdsByProviderAndProviderAthleteId(PROVIDER, garminUserId);
        for (UUID athleteId : athleteIds) {
            connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER).ifPresent(conn -> {
                conn.setScope(joinPermissions(permissions));
                connectionRepository.save(conn);
            });
        }
        if (!athleteIds.isEmpty()) {
            log.info("Garmin : permissions mises à jour ({}) pour {} connexion(s)", permissions, athleteIds.size());
        }
        return athleteIds.size();
    }

    /**
     * Access token valide pour cette connexion, rafraîchi au besoin. Garmin renvoie un nouveau
     * refresh token à chaque rafraîchissement : il est enregistré dans la foulée.
     */
    @Transactional
    public String freshAccessToken(DeviceConnection conn) {
        if (conn.getExpiresAt() > Instant.now().getEpochSecond() + REFRESH_MARGIN_SEC) {
            return conn.getAccessToken();
        }
        TokenResponse token = client.refresh(conn.getRefreshToken());
        if (token == null || token.accessToken() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Échec du rafraîchissement du jeton Garmin.");
        }
        applyToken(conn, token);
        connectionRepository.save(conn);
        return token.accessToken();
    }

    /** Les permissions sont rangées dans la colonne {@code scope}, séparées par des virgules. */
    public static List<String> permissionsOf(DeviceConnection conn) {
        String raw = conn.getScope();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String joinPermissions(List<String> permissions) {
        String joined = permissions == null ? "" : String.join(",", permissions);
        // La colonne fait 255 caractères ; les six permissions connues en prennent moins de cent.
        return joined.length() > 255 ? joined.substring(0, 255) : joined;
    }

    private static void applyToken(DeviceConnection conn, TokenResponse token) {
        conn.setAccessToken(token.accessToken());
        if (token.refreshToken() != null) {
            conn.setRefreshToken(token.refreshToken());
        }
        long ttl = token.expiresIn() != null ? token.expiresIn() : 0L;
        conn.setExpiresAt(Instant.now().getEpochSecond() + ttl);
    }

    private void requireConfigured() {
        if (!client.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Intégration Garmin non configurée sur ce serveur.");
        }
    }

    private Athlete requireAthlete(UUID athleteId) {
        return athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }

    private static String athleteLabel(Athlete athlete) {
        return athlete == null ? null : (athlete.getFirstName() + " " + athlete.getLastName()).trim();
    }
}
