package com.coachrun.security;

import com.coachrun.util.FixedWindowRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Deux protections des formulaires publics que le rate limiting par IP ne donne pas.
 *
 * <h2>Le piège à robots</h2>
 *
 * <p>Les formulaires ouverts à un visiteur anonyme — inscription, demande de création de club,
 * mot de passe oublié, signalement d'une fiche — portent un champ invisible pour un humain, et
 * mesurent le temps passé avant l'envoi. Le front les transmet en en-têtes
 * ({@value #TRAP_HEADER}, {@value #ELAPSED_HEADER}) : un robot qui remplit tous les champs, ou
 * qui envoie le formulaire à l'instant où il l'a chargé, est refusé. Rien n'est demandé à
 * l'humain, aucun service tiers n'est appelé, aucun cookie n'est posé.</p>
 *
 * <p>Les en-têtes <b>absents</b> laissent passer : un onglet ouvert avant le déploiement charge
 * encore l'ancien front, et le refuser ferait échouer l'inscription d'une vraie personne. Le
 * piège arrête les robots génériques qui remplissent des formulaires ; un script écrit contre
 * l'API elle-même relève des plafonds ci-dessous.</p>
 *
 * <h2>Le plafond quotidien des envois anonymes</h2>
 *
 * <p>Le plafond de {@link RateLimitFilter} compte par adresse IP : un robot réparti sur des
 * centaines d'adresses passe dessous, et chaque inscription envoie un e-mail. Le quota d'envoi
 * (100 par jour sur le plan de la bêta) est partagé avec les invitations d'athlètes et les
 * réinitialisations de mot de passe — précisément les envois qu'on ne peut pas perdre. Deux
 * plafonds <b>globaux</b>, toutes adresses confondues, en gardent donc une part en réserve :
 * l'un pour les inscriptions et demandes de club, l'autre pour les mots de passe oubliés, pour
 * qu'une vague sur les inscriptions n'empêche pas un utilisateur de récupérer son compte.</p>
 *
 * <p>Suit {@code app.rate-limit.enabled} : les tests de bout en bout remplissent les formulaires
 * en quelques millisecondes et s'inscrivent en rafale.</p>
 */
@Slf4j
@Component
@Order(2)
@ConditionalOnProperty(name = "app.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class PublicFormGuardFilter extends OncePerRequestFilter {

    /** Valeur du champ invisible : vide pour un humain. */
    public static final String TRAP_HEADER = "X-Form-Trap";
    /** Millisecondes écoulées entre l'affichage du formulaire et son envoi. */
    public static final String ELAPSED_HEADER = "X-Form-Elapsed";

    private static final String SIGNUP_KEY = "signup";
    private static final String PASSWORD_RESET_KEY = "password-reset";

    private final long minFillMs;
    private final FixedWindowRateLimiter signupDaily;
    private final FixedWindowRateLimiter passwordResetDaily;
    private final AtomicBoolean signupAlerted = new AtomicBoolean();
    private final AtomicBoolean resetAlerted = new AtomicBoolean();

    public PublicFormGuardFilter(
            @Value("${app.form-guard.min-fill-ms:2000}") long minFillMs,
            @Value("${app.form-guard.signup-daily-max:40}") int signupDailyMax,
            @Value("${app.form-guard.password-reset-daily-max:20}") int passwordResetDailyMax) {
        this.minFillMs = minFillMs;
        this.signupDaily = new FixedWindowRateLimiter(signupDailyMax, Duration.ofDays(1));
        this.passwordResetDaily = new FixedWindowRateLimiter(passwordResetDailyMax, Duration.ofDays(1));
    }

    /** Formulaires publics qui portent le piège. */
    static boolean isGuardedForm(String uri, String method) {
        if (uri == null || !"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return RateLimitFilter.isAnonymousEmailTriggering(uri, method)
                || (uri.contains("/public/coaches/") && uri.endsWith("/report"));
    }

    /** Inscriptions et demandes de club : chacune envoie un e-mail à une adresse inconnue. */
    static boolean isSignup(String uri) {
        return uri.endsWith("/auth/register")
                || uri.endsWith("/public/athlete-registration")
                || uri.endsWith("/public/club-requests");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!isGuardedForm(uri, request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        if (looksAutomated(request)) {
            // Réponse neutre : elle ne dit pas au robot lequel des deux indices l'a trahi. Un
            // humain trop rapide (remplissage automatique) n'a qu'à renvoyer : le chronomètre
            // court depuis l'affichage du formulaire, le second envoi passe.
            write(response, 400, "Envoi trop rapide : patientez un instant puis réessayez.");
            return;
        }
        // Le plafond global ne se consomme qu'après le piège : un robot pris au piège n'entame
        // pas le quota des vraies personnes.
        if (isSignup(uri)) {
            if (!signupDaily.tryAcquire(SIGNUP_KEY)) {
                alertOnce(signupAlerted, "inscriptions et demandes de club");
                write(response, 429, "Les inscriptions sont momentanément saturées. "
                        + "Réessayez demain, ou écrivez-nous à contact@darilab.app.");
                return;
            }
        } else if (uri.endsWith("/public/password-reset")) {
            if (!passwordResetDaily.tryAcquire(PASSWORD_RESET_KEY)) {
                alertOnce(resetAlerted, "demandes de réinitialisation de mot de passe");
                write(response, 429, "Trop de demandes aujourd'hui. "
                        + "Réessayez demain, ou écrivez-nous à contact@darilab.app.");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Champ invisible rempli, ou formulaire envoyé plus vite qu'un humain ne le lit. */
    boolean looksAutomated(HttpServletRequest request) {
        String trap = request.getHeader(TRAP_HEADER);
        if (trap != null && !trap.isBlank()) {
            return true;
        }
        String elapsed = request.getHeader(ELAPSED_HEADER);
        if (elapsed == null || elapsed.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(elapsed.trim()) < minFillMs;
        } catch (NumberFormatException e) {
            // Le front envoie toujours un entier : autre chose est forgé.
            return true;
        }
    }

    /** Une alerte par fenêtre : c'est un signal d'attaque, pas une ligne de journal par requête. */
    private void alertOnce(AtomicBoolean flag, String what) {
        if (flag.compareAndSet(false, true)) {
            log.error("Plafond quotidien atteint pour les {} : envois refusés jusqu'à la fenêtre "
                    + "suivante. Vague d'inscriptions automatisées probable.", what);
        }
    }

    private static void write(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }

    /** Réarme les alertes quand la fenêtre du jour se referme. */
    @Scheduled(fixedDelay = 3_600_000L)
    void purgeExpiredWindows() {
        if (signupDaily.purgeExpired() > 0) {
            signupAlerted.set(false);
        }
        if (passwordResetDaily.purgeExpired() > 0) {
            resetAlerted.set(false);
        }
    }
}
