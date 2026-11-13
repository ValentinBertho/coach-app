package com.coachrun.service;

import com.coachrun.dto.response.CorosStatusResponse;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.DeviceConnection;
import com.coachrun.entity.OAuthClientRegistration;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.entity.enums.DeviceProvider;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.integration.CorosMcpClient;
import com.coachrun.integration.CorosMcpClient.Endpoints;
import com.coachrun.integration.CorosMcpClient.TokenResponse;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.DeviceConnectionRepository;
import com.coachrun.repository.OAuthClientRegistrationRepository;
import com.coachrun.security.OAuthStateCodec;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;

/**
 * Connexion COROS (via COROS MCP) d'un athlète.
 *
 * <p>Côté athlète uniquement, comme Garmin : c'est lui qui relie <b>son</b> compte COROS. COROS
 * MCP n'ouvre l'accès qu'aux données de l'utilisateur qui autorise ; c'est exactement notre
 * modèle (un jeton par athlète, le coach voit ensuite ce que Darilab en a tiré).</p>
 *
 * <p>Cette classe fait la liaison — enregistrement du client, consentement, jetons,
 * déconnexion. L'envoi des séances ({@link CorosWorkoutSyncService}) s'appuie sur
 * {@link #freshAccessToken(DeviceConnection)} et {@link #endpointsOf(DeviceConnection)}.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CorosService {

    private static final DeviceProvider PROVIDER = DeviceProvider.COROS;
    /** L'access token vit 30 jours : on le renouvelle la veille, jamais pendant un appel. */
    private static final long REFRESH_MARGIN_SEC = 24 * 3600;

    private final CorosMcpClient client;
    private final DeviceConnectionRepository connectionRepository;
    private final OAuthClientRegistrationRepository registrationRepository;
    private final AthleteRepository athleteRepository;
    private final OAuthStateCodec stateCodec;
    private final AdminAuditService audit;
    /**
     * Fournisseur paresseux, comme pour Garmin : le service d'envoi dépend de celui-ci (jetons),
     * et celui-ci a besoin de lui pour le statut et la déconnexion.
     */
    private final ObjectProvider<CorosWorkoutSyncService> workoutSync;

    public CorosStatusResponse statusForAthlete(UUID athleteId) {
        requireAthlete(athleteId);
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER).orElse(null);
        CorosWorkoutSyncService.Summary summary = conn != null
                ? workoutSync.getObject().summary(athleteId)
                : new CorosWorkoutSyncService.Summary(0, 0);
        return new CorosStatusResponse(
                client.isConfigured(),
                conn != null,
                conn != null ? conn.getProviderAthleteId() : null,
                conn != null ? CorosMcpClient.regionOf(conn.getApiBaseUrl()) : null,
                summary.workoutsOnCoros(),
                summary.workoutsInError());
    }

    /** J'envoie maintenant mes séances à venir sur ma montre (sans attendre la passe planifiée). */
    @Transactional
    public CorosStatusResponse syncWorkoutsForAthlete(UUID athleteId) {
        requireAthlete(athleteId);
        requireConfigured();
        connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "Compte COROS non connecté."));
        workoutSync.getObject().syncAthlete(athleteId);
        return statusForAthlete(athleteId);
    }

    /**
     * URL de consentement COROS. Comme pour Garmin, le {@code code_challenge} PKCE est dérivé du
     * {@code state} signé : rien à stocker entre l'aller et le retour.
     */
    @Transactional
    public String authorizeUrlForAthlete(UUID athleteId) {
        requireAthlete(athleteId);
        requireConfigured();
        Endpoints e = client.endpoints();
        String clientId = clientIdFor(e);
        String state = stateCodec.issue(athleteId);
        String challenge = OAuthStateCodec.pkceChallenge(stateCodec.pkceVerifier(state));
        return client.authorizeUrl(e, clientId, challenge, state);
    }

    @Transactional
    public CorosStatusResponse connectForAthlete(UUID athleteId, String code, String state) {
        Athlete athlete = requireAthlete(athleteId);
        requireConfigured();
        stateCodec.verify(state, athleteId);
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Code d'autorisation manquant.");
        }
        Endpoints e = client.endpoints();
        String clientId = clientIdFor(e);
        TokenResponse token;
        try {
            token = client.exchangeCode(e, clientId, code, stateCodec.pkceVerifier(state));
        } catch (RestClientException ex) {
            log.warn("Échange de jetons COROS refusé : {}", ex.getMessage());
            token = null;
        }
        if (token == null || token.accessToken() == null || token.refreshToken() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Échec de l'échange de jetons COROS.");
        }

        String userId = CorosMcpClient.subjectOf(token.idToken());
        DeviceConnection conn = connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER)
                .orElseGet(() -> {
                    DeviceConnection c = new DeviceConnection();
                    c.setAthlete(athlete);
                    c.setProvider(PROVIDER);
                    return c;
                });
        applyToken(conn, token);
        conn.setProviderAthleteId(userId);
        conn.setApiBaseUrl(e.issuer());
        conn.setClientId(clientId);
        conn.setScope(truncate(StringUtils.hasText(token.scope()) ? token.scope() : CorosMcpClient.SCOPES));
        connectionRepository.save(conn);
        log.info("COROS connecté pour l'athlète {} (serveur {})", athleteId, e.issuer());
        // Aucun jeton au journal : seulement l'identifiant COROS, opaque.
        audit.record(AdminAuditAction.DEVICE_CONNECTED, AdminAuditTarget.ATHLETE,
                athleteId, athleteLabel(athlete),
                "COROS" + (userId != null ? " (compte " + userId + ")" : ""));
        return statusForAthlete(athleteId);
    }

    /**
     * L'athlète se déconnecte depuis Darilab. Les jetons sont effacés quoi qu'il arrive ; si
     * COROS annonce une adresse de révocation, on lui demande aussi d'invalider le jeton.
     */
    @Transactional
    public void disconnectForAthlete(UUID athleteId) {
        Athlete athlete = requireAthlete(athleteId);
        connectionRepository.findByAthleteIdAndProvider(athleteId, PROVIDER).ifPresent(conn -> {
            boolean remote = false;
            if (client.isConfigured()) {
                try {
                    remote = client.revoke(endpointsOf(conn), conn.getClientId(), conn.getRefreshToken());
                } catch (RuntimeException ex) {
                    log.warn("Révocation COROS impossible pour l'athlète {} : {}", athleteId, ex.getMessage());
                }
            }
            // COROS ne permet pas de retirer les séances déjà posées : on oublie seulement leur suivi.
            workoutSync.getObject().forgetAthlete(athleteId);
            connectionRepository.delete(conn);
            audit.record(AdminAuditAction.DEVICE_DISCONNECTED, AdminAuditTarget.ATHLETE,
                    athleteId, athleteLabel(athlete),
                    "COROS — jetons supprimés" + (remote ? ", accès révoqué chez COROS" : ""));
        });
    }

    /** Le serveur COROS qui a émis les jetons de cette connexion. */
    public Endpoints endpointsOf(DeviceConnection conn) {
        return StringUtils.hasText(conn.getApiBaseUrl())
                ? client.endpointsFor(conn.getApiBaseUrl())
                : client.endpoints();
    }

    /**
     * Access token valide pour cette connexion, rafraîchi au besoin. COROS renvoie un nouveau
     * refresh token à chaque rafraîchissement : il est enregistré dans la foulée, sinon la
     * connexion serait perdue au rafraîchissement suivant.
     */
    @Transactional
    public String freshAccessToken(DeviceConnection conn) {
        if (conn.getExpiresAt() > Instant.now().getEpochSecond() + REFRESH_MARGIN_SEC) {
            return conn.getAccessToken();
        }
        TokenResponse token = client.refresh(endpointsOf(conn), conn.getClientId(), conn.getRefreshToken());
        if (token == null || token.accessToken() == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Échec du rafraîchissement du jeton COROS.");
        }
        applyToken(conn, token);
        connectionRepository.save(conn);
        return token.accessToken();
    }

    /**
     * Notre client OAuth auprès de ce serveur COROS : enregistré une fois, puis réutilisé.
     * Synchronisé pour qu'une rafale de connexions n'enregistre pas plusieurs clients.
     */
    synchronized String clientIdFor(Endpoints e) {
        return registrationRepository
                .findByProviderAndIssuerAndRedirectUri(PROVIDER, e.issuer(), client.redirectUri())
                .map(OAuthClientRegistration::getClientId)
                .orElseGet(() -> {
                    String clientId;
                    try {
                        clientId = client.registerClient(e);
                    } catch (RuntimeException ex) {
                        log.warn("Enregistrement du client COROS refusé ({}) : {}", e.issuer(), ex.getMessage());
                        throw new ApiException(HttpStatus.BAD_GATEWAY,
                                "COROS n'a pas répondu, réessaie dans un instant.");
                    }
                    OAuthClientRegistration r = new OAuthClientRegistration();
                    r.setProvider(PROVIDER);
                    r.setIssuer(e.issuer());
                    r.setRedirectUri(client.redirectUri());
                    r.setClientId(clientId);
                    registrationRepository.saveAndFlush(r);
                    log.info("COROS : client OAuth enregistré auprès de {}", e.issuer());
                    return clientId;
                });
    }

    private static void applyToken(DeviceConnection conn, TokenResponse token) {
        conn.setAccessToken(token.accessToken());
        if (token.refreshToken() != null) {
            conn.setRefreshToken(token.refreshToken());
        }
        long ttl = token.expiresIn() != null ? token.expiresIn() : 0L;
        conn.setExpiresAt(Instant.now().getEpochSecond() + ttl);
    }

    private static String truncate(String s) {
        return s.length() > 255 ? s.substring(0, 255) : s;
    }

    private void requireConfigured() {
        if (!client.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Intégration COROS non ouverte sur ce serveur.");
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
