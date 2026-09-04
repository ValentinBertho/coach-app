package com.coachrun.security;

import com.coachrun.util.FixedWindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Rate limiting des routes sensibles (auth, réinitialisation de mot de passe, vérification
 * d'email, acceptation d'invitation) <b>et</b> plafond global par utilisateur authentifié.
 * Désactivable via {@code app.rate-limit.enabled} (false en tests). Réponse 429 au dépassement.
 *
 * <p>La clé est {@code IP:bucket} où le bucket est un préfixe stable : les routes porteuses de
 * token ({@code /password-reset/{token}}…) partagent le même compteur, sinon chaque token essayé
 * ouvrirait une nouvelle fenêtre et le brute-force passerait sous le radar.</p>
 *
 * <p>Les routes authentifiées (envoi de messages, pièces jointes, import GPX) n'avaient jusqu'ici
 * aucune limite : un compte légitime — ou volé — pouvait saturer base et stockage sans jamais
 * être ralenti. Elles retombent désormais sur un plafond large, compté <b>par porteur de
 * jeton</b> : il n'entrave pas l'usage normal, il coupe l'emballement.</p>
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class RateLimitFilter extends OncePerRequestFilter {

    /** Bucket de la connexion : le seul où chaque requête est un essai de mot de passe. */
    public static final String LOGIN_BUCKET = "auth-login";
    /** Bucket fourre-tout des routes authentifiées, compté par porteur de jeton. */
    public static final String AUTHENTICATED_BUCKET = "api-authenticated";
    /**
     * Bucket des canaux de présence : flux temps réel et compteurs de non-lus.
     *
     * <p>Ils ont leur propre plafond, et c'est tout l'objet. Partagé avec les requêtes ordinaires,
     * il faisait de la navigation soutenue un motif de blocage : mesuré sur l'application réelle,
     * le calendrier coûte 19 appels et « Ma journée » 10, si bien qu'une vingtaine d'écrans dans
     * la minute suffisait à récolter des 429 et à perdre son flux de notifications. Dispensés de
     * tout plafond, ils rouvriraient en revanche la porte aux reconnexions {@code EventSource} en
     * boucle que provoque un proxy coupant mal les connexions longues. Un compteur à part freine
     * la boucle sans pénaliser le travail.</p>
     */
    public static final String LIVE_BUCKET = "api-live";
    /** Bucket des routes qui déclenchent un envoi d'e-mail, compté à l'heure. */
    public static final String EMAIL_BUCKET = "api-email";
    /** Bucket des routes <b>anonymes</b> qui déclenchent un envoi d'e-mail, compté à l'heure par IP. */
    public static final String ANONYMOUS_EMAIL_BUCKET = "public-email";
    /**
     * Bucket du rafraîchissement de jeton.
     *
     * <p>Il partageait le seuil général — vingt requêtes par minute et par IP, calibré contre le
     * devinage d'identifiants. Or un rafraîchissement n'est pas une tentative : c'est l'opération
     * la plus banale d'une application ouverte plusieurs fois par jour, et elle se déclenche à
     * chaque retour au premier plan. Deux contextes sur le même téléphone — l'application
     * installée et le même site dans le navigateur — la déclenchent chacun pour leur compte, et
     * derrière un NAT d'opérateur toute une cohorte partage l'adresse.</p>
     *
     * <p>Le dépassement ne se contentait pas de ralentir : le client interprétait le 429 comme un
     * refus de session et déconnectait. Un plafond destiné à protéger le mot de passe finissait
     * par éjecter des utilisateurs parfaitement authentifiés.</p>
     */
    public static final String REFRESH_BUCKET = "auth-refresh";

    /**
     * Bucket de l'annuaire public.
     *
     * <p>Séparé du seuil général parce que les deux usages n'ont rien à voir : vingt requêtes par
     * minute suffisent à décourager le devinage d'un mot de passe, elles ne suffisent pas à
     * feuilleter des résultats en changeant de filtre. Séparé aussi des routes authentifiées, parce
     * que ce visiteur n'a pas de jeton : le compte se fait par IP, et derrière un NAT d'opérateur
     * toute une cohorte partage l'adresse — d'où un plafond large, mais qui existe. Recopier
     * l'annuaire coach par coach est le premier geste de qui veut démarcher nos coachs.</p>
     */
    public static final String DIRECTORY_BUCKET = "public-directory";

    private final FixedWindowRateLimiter limiter;
    private final FixedWindowRateLimiter loginLimiter;
    private final FixedWindowRateLimiter refreshLimiter;
    private final FixedWindowRateLimiter directoryLimiter;
    private final FixedWindowRateLimiter authenticatedLimiter;
    private final FixedWindowRateLimiter liveLimiter;
    private final FixedWindowRateLimiter emailLimiter;
    private final FixedWindowRateLimiter anonymousEmailLimiter;
    private final int trustedProxyHops;

    @SuppressWarnings("checkstyle:ParameterNumber")
    public RateLimitFilter(@Value("${app.rate-limit.max-requests:20}") int maxRequests,
                           @Value("${app.rate-limit.window-seconds:60}") int windowSeconds,
                           @Value("${app.rate-limit.login-max-requests:5}") int loginMaxRequests,
                           @Value("${app.rate-limit.refresh-max-requests:60}") int refreshMaxRequests,
                           @Value("${app.rate-limit.directory-max-requests:90}") int directoryMax,
                           @Value("${app.rate-limit.authenticated-max-requests:300}") int authenticatedMax,
                           @Value("${app.rate-limit.live-max-requests:120}") int liveMax,
                           @Value("${app.rate-limit.email-max-requests:3}") int emailMax,
                           @Value("${app.rate-limit.email-window-seconds:3600}") int emailWindowSeconds,
                           @Value("${app.rate-limit.anonymous-email-max-requests:5}") int anonymousEmailMax,
                           @Value("${app.rate-limit.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.limiter = new FixedWindowRateLimiter(maxRequests, Duration.ofSeconds(windowSeconds));
        // La connexion a son propre seuil, bien plus strict : 20 essais par minute et par IP
        // laissent 28 800 mots de passe par jour à un attaquant, ce qui n'est pas une limite.
        this.loginLimiter = new FixedWindowRateLimiter(loginMaxRequests, Duration.ofSeconds(windowSeconds));
        // Rafraîchissement : large, parce qu'il est légitime et fréquent. Il reste borné — un
        // jeton rejoué est de toute façon refusé par la rotation et la liste noire.
        this.refreshLimiter = new FixedWindowRateLimiter(refreshMaxRequests, Duration.ofSeconds(windowSeconds));
        // Annuaire public : large — changer de filtre, feuilleter, ouvrir des fiches est l'usage
        // normal — mais borné, l'aspiration ne devant pas se faire au rythme du serveur.
        this.directoryLimiter =
                new FixedWindowRateLimiter(directoryMax, Duration.ofSeconds(windowSeconds));
        // Plafond global : large (une navigation soutenue reste très en dessous), mais il existe.
        this.authenticatedLimiter =
                new FixedWindowRateLimiter(authenticatedMax, Duration.ofSeconds(windowSeconds));
        // Canaux de présence : plafond propre, plus large que la navigation ordinaire — ils sont
        // rejoués à chaque écran — mais borné, pour que la reconnexion en boucle reste freinée.
        this.liveLimiter = new FixedWindowRateLimiter(liveMax, Duration.ofSeconds(windowSeconds));
        // Envois d'e-mail : quelques-uns par heure suffisent à tout usage légitime (on renvoie une
        // vérification parce qu'elle n'est pas arrivée, pas trois cents fois par minute).
        this.emailLimiter =
                new FixedWindowRateLimiter(emailMax, Duration.ofSeconds(emailWindowSeconds));
        // Envois d'e-mail déclenchés SANS être connecté : inscription et mot de passe oublié. Le
        // plafond ci-dessus ne les couvrait pas — il compte par porteur de jeton, et ces routes
        // n'en portent aucun. Elles retombaient donc sur le seuil général (20/min/IP), soit
        // 1 200 e-mails par heure et par adresse, sur un quota d'envoi partagé de 100 par jour.
        // Tant que l'inscription est sur invitation, le risque dort ; ouvrir la bêta le réveille.
        this.anonymousEmailLimiter =
                new FixedWindowRateLimiter(anonymousEmailMax, Duration.ofSeconds(emailWindowSeconds));
        this.trustedProxyHops = Math.max(1, trustedProxyHops);
    }

    /** Bucket de comptage de la route, ou {@code null} si elle n'est pas rate-limitée par IP. */
    public static String bucket(String uri) {
        if (uri.endsWith("/auth/login")) {
            return LOGIN_BUCKET;
        }
        if (uri.endsWith("/auth/register")) {
            return "auth-register";
        }
        // Dépôt d'une demande de création de club : écriture en base ET accusé de réception par
        // e-mail, sans aucun jeton. Sans bucket propre, la route retombait sur le plafond général
        // et une seule adresse pouvait remplir la file d'arbitrage.
        if (uri.endsWith("/public/club-requests")) {
            return "club-request";
        }
        if (uri.endsWith("/auth/refresh")) {
            return REFRESH_BUCKET;
        }
        if (uri.contains("/public/password-reset")) {
            return "password-reset";
        }
        if (uri.contains("/public/verify-email")) {
            return "verify-email";
        }
        if (uri.contains("/invitations/") && uri.endsWith("/accept")) {
            return "invitation-accept";
        }
        // Dépôt d'un retour de bêta : écriture de texte libre en base, donc un plafond, même
        // large. Le chemin complet est exigé : « /feedback » seul attraperait aussi
        // PATCH /me/workouts/{id}/feedback, le retour de séance de l'athlète — qui serait alors
        // plafonné par IP, donc partagé par tous les athlètes d'un même club derrière une box.
        if (uri.endsWith("/api/feedback")) {
            return "beta-feedback";
        }
        // Le signalement d'une fiche : écriture de texte libre en base par un visiteur anonyme.
        // Testé AVANT l'annuaire, qui l'attraperait sinon — et le plafond de l'annuaire est calibré
        // pour de la lecture (90/min), soit très au-dessus de ce qu'on veut concéder à un dépôt.
        // Il retombe donc sur le seuil général, comme la demande de création de club ; le reste de
        // la protection est applicative (CoachReportService, plafonds par fiche et par jour).
        if (uri.contains("/public/coaches") && uri.endsWith("/report")) {
            return "coach-report";
        }
        // L'annuaire public : la seule route de lecture ouverte à un visiteur anonyme, donc la
        // seule qu'on puisse aspirer.
        //
        // Les PHOTOS en sont exclues, et ce n'est pas un oubli : une page de douze résultats
        // déclenche douze requêtes d'image pour une seule requête de recherche. Les compter dans
        // le même seau ferait sauter le quota au bout de deux pages, et le symptôme serait des
        // vignettes cassées au hasard — le genre de défaut qu'on ne relie jamais à un plafond.
        // Elles sont immuables et servies avec un cache de trente jours : leur coût est marginal.
        if ((uri.contains("/public/coaches") || uri.contains("/public/coach-"))
                && !uri.contains("/public/coach-photos")) {
            return DIRECTORY_BUCKET;
        }
        return null;
    }

    /**
     * Routes authentifiées qui <b>déclenchent un envoi d'e-mail</b>, et sont donc comptées à part,
     * très bas, par porteur de jeton.
     *
     * <p>Elles retombaient sur le plafond général de 300 requêtes/minute — c'est-à-dire qu'un
     * compte légitime pouvait provoquer ~300 e-mails par minute. Or {@code /auth/resend-verification}
     * régénère et renvoie un lien à chaque appel, et {@code PATCH /auth/me} envoie une vérification
     * à toute nouvelle adresse : la seconde permet donc d'arroser une adresse <b>arbitraire</b>.
     * Le plan Resend de la bêta est à 100 e-mails/jour ; le quota tombait en vingt secondes, et il
     * emporte avec lui les réinitialisations de mot de passe et les invitations — précisément les
     * envois qu'on ne peut pas perdre.</p>
     */
    public static boolean isEmailTriggering(String uri, String method) {
        if (uri == null) {
            return false;
        }
        if (uri.endsWith("/auth/resend-verification")) {
            return true;
        }
        return uri.endsWith("/auth/me") && "PATCH".equalsIgnoreCase(method);
    }

    /**
     * Routes <b>anonymes</b> dont chaque appel provoque un envoi d'e-mail : inscription et demande
     * de réinitialisation de mot de passe.
     *
     * <p>Elles ne portent aucun jeton, donc {@link #isEmailTriggering} — qui compte par porteur —
     * ne les voyait pas. Elles retombaient sur le seuil général de 20 requêtes par minute et par
     * IP, soit jusqu'à 1 200 e-mails par heure depuis une seule adresse, sur un quota d'envoi de
     * 100 par jour partagé avec les invitations d'athlètes. Tant que l'inscription reste sur
     * invitation, {@code /auth/register} est fermée et le risque dort ; <b>ouvrir la bêta consiste
     * précisément à l'ouvrir</b>.</p>
     *
     * <p>Le dépôt d'une demande de création de club relève du même plafond : il déclenche un
     * accusé de réception, et c'est le formulaire le plus exposé de la plateforme — le seul
     * ouvert à un visiteur anonyme dans le régime « sur demande ».</p>
     *
     * <p>La correspondance est volontairement exacte sur la réinitialisation : les variantes
     * porteuses d'un jeton ({@code GET} de validation du lien, {@code POST} d'application du
     * nouveau mot de passe) n'envoient rien et ne doivent pas consommer ce quota.</p>
     */
    public static boolean isAnonymousEmailTriggering(String uri, String method) {
        if (uri == null || !"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return uri.endsWith("/auth/register")
                || uri.endsWith("/public/password-reset")
                || uri.endsWith("/public/club-requests")
                // L'inscription libre d'un athlète : ouverte à tout visiteur et suivie d'un
                // e-mail de vérification. Sans ce plafond, une seule adresse IP pourrait vider
                // le quota d'envoi quotidien — et il porte aussi les réinitialisations de mot de
                // passe et les invitations, précisément les envois qu'on ne peut pas perdre.
                || uri.endsWith("/public/athlete-registration");
    }

    /**
     * Flux temps réel et compteurs de présence, qui relèvent du plafond {@link #LIVE_BUCKET}.
     *
     * <p>Un flux SSE est une requête unique tenue ouverte une demi-heure ; le compteur de non-lus
     * est rejoué à chaque changement d'écran. Ni l'un ni l'autre ne décrit une intention de
     * l'utilisateur : les compter avec le reste revenait à plafonner la navigation elle-même.</p>
     */
    private boolean isLiveChannel(String uri) {
        return uri.endsWith("/stream") || uri.endsWith("/unread-count");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        // Plafond horaire des envois anonymes, en plus du seuil par minute ci-dessous : l'un borne
        // la rafale, l'autre borne la consommation du quota d'envoi sur la journée.
        if (isAnonymousEmailTriggering(request.getRequestURI(), request.getMethod())
                && !anonymousEmailLimiter.tryAcquire(
                        clientIp(request) + ":" + ANONYMOUS_EMAIL_BUCKET)) {
            reject(response);
            return;
        }

        String bucket = bucket(request.getRequestURI());
        String key;
        FixedWindowRateLimiter applicable;

        if (bucket != null) {
            key = clientIp(request) + ":" + bucket;
            if (LOGIN_BUCKET.equals(bucket)) {
                applicable = loginLimiter;
            } else if (REFRESH_BUCKET.equals(bucket)) {
                applicable = refreshLimiter;
            } else if (DIRECTORY_BUCKET.equals(bucket)) {
                applicable = directoryLimiter;
            } else {
                applicable = limiter;
            }
        } else if (isEmailTriggering(request.getRequestURI(), request.getMethod())) {
            // Envoi d'e-mail déclenché par un compte : seuil très bas, et par porteur de jeton
            // (c'est le compte qui déclenche, pas l'adresse réseau).
            String bearerKey = bearerKey(request);
            key = (bearerKey != null ? bearerKey : clientIp(request)) + ":" + EMAIL_BUCKET;
            applicable = emailLimiter;
        } else if (isLiveChannel(request.getRequestURI())) {
            // Flux temps réel et compteurs : leur propre plafond (cf. LIVE_BUCKET).
            String liveKey = bearerKey(request);
            key = (liveKey != null ? liveKey : clientIp(request)) + ":" + LIVE_BUCKET;
            applicable = liveLimiter;
        } else {
            // Route non listée : plafond global par porteur de jeton. Les requêtes anonymes hors
            // buckets (actuator, pages publiques) ne sont pas comptées ici.
            String bearerKey = bearerKey(request);
            if (bearerKey == null) {
                filterChain.doFilter(request, response);
                return;
            }
            key = bearerKey + ":" + AUTHENTICATED_BUCKET;
            applicable = authenticatedLimiter;
        }

        if (!applicable.tryAcquire(key)) {
            reject(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Réponse 429 commune à tous les plafonds. */
    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"status\":429,\"message\":\"Trop de requêtes, réessayez plus tard.\"}");
    }

    /**
     * Clé de comptage d'une requête authentifiée, dérivée du jeton porté.
     *
     * <p>Compter par porteur plutôt que par IP évite de pénaliser un club entier derrière une même
     * sortie réseau, et suit l'emballement là où il se produit. Le filtre s'exécute avant
     * l'authentification : on ne décode ni ne valide le jeton (c'est le rôle du filtre
     * d'authentification), on se contente d'une empreinte stable de sa charge utile. Un jeton
     * forgé n'ouvre aucun accès — au pire il choisit son propre compteur, ce qui ne dessert
     * que lui.</p>
     *
     * <p>Le jeton est aussi cherché dans le paramètre {@code access_token}. Les flux SSE
     * ({@code EventSource} ne sait pas poser d'en-tête) et l'ouverture d'une pièce jointe dans un
     * onglet l'y placent — et comme cette méthode ne lisait que l'en-tête, ces routes
     * échappaient <b>entièrement</b> au comptage : une reconnexion SSE en boucle, ou un
     * téléchargement répété de pièces jointes servies depuis la base, n'avaient aucune limite.</p>
     */
    private String bearerKey(HttpServletRequest request) {
        String token = null;
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            token = header.substring(7);
        } else {
            String param = request.getParameter("access_token");
            if (param != null && !param.isBlank()) {
                token = param;
            }
        }
        if (token == null) {
            return null;
        }
        int first = token.indexOf('.');
        int second = token.indexOf('.', first + 1);
        if (first < 0 || second < 0) {
            return null;
        }
        return "u:" + Integer.toHexString(token.substring(first + 1, second).hashCode());
    }

    /**
     * IP réellement attribuée par le proxy de confiance.
     *
     * <p>Prendre le <b>premier</b> élément de {@code X-Forwarded-For} était exploitable : cet
     * en-tête est fourni par le client, qui pouvait donc écrire ce qu'il voulait en tête de liste
     * et s'ouvrir une fenêtre neuve à chaque requête — le rate limiting ne limitait plus rien.
     * Chaque relais <b>ajoute</b> son observation en fin de chaîne : derrière
     * {@code trustedProxyHops} relais de confiance (Vercel puis Railway), l'adresse à retenir est
     * la n-ième en partant de la fin.</p>
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] hops = forwarded.split(",");
        int index = hops.length - trustedProxyHops;
        if (index < 0 || index >= hops.length) {
            // Moins de relais qu'annoncé : chaîne incomplète ou falsifiée. On retombe sur
            // l'adresse de la connexion TCP, la seule que le client ne peut pas choisir.
            //
            // Ce repli est sûr, mais il n'est bon que si l'adresse TCP est bien celle du client.
            // Derrière un relais, c'est celle *du relais* — la même pour tout le monde — et tous
            // les utilisateurs tombent alors dans un compteur unique : les plafonds destinés à
            // contenir un attaquant éjectent la cohorte entière. C'est ce que produit une
            // topologie mal déclarée, d'où l'alerte : le repli protège, il ne répare pas.
            warnOnce(hops.length);
            return request.getRemoteAddr();
        }
        String ip = hops[index].trim();
        return ip.isEmpty() ? request.getRemoteAddr() : ip;
    }

    /** Une seule alerte par démarrage : la topologie ne change pas d'une requête à l'autre. */
    private void warnOnce(int actualHops) {
        if (proxyHopsWarned.compareAndSet(false, true)) {
            logger.warn("X-Forwarded-For porte " + actualHops + " relais alors que "
                    + "RATE_LIMIT_TRUSTED_PROXY_HOPS=" + trustedProxyHops
                    + ". Le rate limiting retombe sur le premier élément de la chaîne. "
                    + "Ajuster la variable à la topologie réelle.");
        }
    }

    private final java.util.concurrent.atomic.AtomicBoolean proxyHopsWarned =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Purge des fenêtres closes. Les tables sont indexées sur des clés que l'appelant contrôle
     * (IP, porteur de jeton) : sans purge, elles croissaient sans borne — et le défaut du
     * {@code X-Forwarded-For} offrait précisément de quoi les faire enfler à volonté.
     */
    @Scheduled(fixedDelay = 600_000L)
    void purgeExpiredWindows() {
        limiter.purgeExpired();
        loginLimiter.purgeExpired();
        directoryLimiter.purgeExpired();
        refreshLimiter.purgeExpired();
        authenticatedLimiter.purgeExpired();
        // Les deux seaux horaires manquaient à l'appel : leurs tables, indexées sur des clés que
        // l'appelant contrôle, grossissaient sans jamais être nettoyées.
        emailLimiter.purgeExpired();
        anonymousEmailLimiter.purgeExpired();
    }
}
