package com.coachrun.config;

import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.AdminAuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Consigne toute <b>écriture</b> faite depuis une session empruntée.
 *
 * <h2>Le trou que ce filtre bouche</h2>
 *
 * <p>{@code ImpersonationService} le disait en toutes lettres : « les actions effectuées pendant
 * l'impersonation ne sont pas attribuées à l'administrateur : elles ressemblent en tout point à
 * celles de l'utilisateur […] écrire depuis un compte emprunté laisse une trace indiscernable de
 * celle de son titulaire ». L'ouverture de la session était bien journalisée ; ce qui se passait
 * ensuite, non. Un athlète pouvait donc voir son ressenti modifié, sa séance déplacée ou son
 * message envoyé sans que rien, nulle part, ne dise qu'un administrateur était derrière.</p>
 *
 * <p>Enrichir les colonnes du journal ne suffisait pas à le boucher : chaque contrôleur
 * d'administration exige {@code PLATFORM_ADMIN}, et un jeton d'impersonation ne porte jamais ce
 * rôle — un administrateur ne peut pas emprunter un autre administrateur. La colonne
 * « emprunteur » serait donc restée vide pour toujours. Les écritures à tracer sont celles du
 * <b>produit</b>, et c'est ici qu'elles passent toutes.</p>
 *
 * <h2>Ce qu'il consigne, et ce qu'il ne consigne pas</h2>
 *
 * <ul>
 *   <li><b>Les écritures seulement.</b> {@code GET} et {@code HEAD} sont le cas d'usage normal et
 *       revendiqué de l'impersonation — voir l'application comme l'utilisateur. Les journaliser
 *       noierait les écritures, qui sont le sujet, sous des centaines de lectures.</li>
 *   <li><b>Celles qui ont abouti.</b> Un {@code 4xx} n'a rien changé ; une tentative refusée est
 *       déjà un refus. On consigne ce qui a laissé une trace dans les données.</li>
 *   <li><b>La route, jamais le corps.</b> La charge utile d'une requête porte du ressenti, des
 *       douleurs, des messages : rien de tout cela n'entre dans un journal d'administration.
 *       La méthode et le chemin disent ce qu'il faut savoir, et la fiche concernée dit le reste.</li>
 * </ul>
 *
 * <p>Placé <b>après</b> la chaîne de sécurité (le principal doit exister) et après le traitement
 * (le statut de la réponse doit être connu). Un échec d'écriture du journal n'a aucune
 * conséquence sur la requête : elle est déjà servie.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(ImpersonationAuditFilter.ORDER)
public class ImpersonationAuditFilter extends OncePerRequestFilter {

    /**
     * Après la chaîne de sécurité de Spring, dont l'ordre par défaut est bien plus bas
     * ({@code SecurityProperties.DEFAULT_FILTER_ORDER}, -100).
     *
     * <p>Un ordre <b>plus grand</b> place ce filtre à l'<b>intérieur</b> de cette chaîne, ce qui
     * est la condition pour que le principal existe encore au retour de {@code chain.doFilter()}.
     * Rendu public pour que ce soit vérifié par un test : à l'extérieur, la trace disparaîtrait
     * en silence, sans qu'aucune requête n'échoue.</p>
     */
    public static final int ORDER = 100;

    private final AdminAuditService audit;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        chain.doFilter(request, response);

        if (isRead(request.getMethod()) || response.getStatus() >= 400) {
            return;
        }
        AuthPrincipal principal = currentPrincipal();
        if (principal == null || !principal.impersonated()) {
            return;
        }
        try {
            audit.record(AdminAuditAction.IMPERSONATED_WRITE, AdminAuditTarget.USER,
                    principal.userId(), principal.email(),
                    request.getMethod() + " " + request.getRequestURI());
        } catch (RuntimeException ex) {
            // La requête est déjà servie : on ne la fait pas échouer pour une trace manquée.
            log.error("Journal d'audit indisponible pour une écriture en session empruntée", ex);
        }
    }

    private static boolean isRead(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method);
    }

    private AuthPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) ? p : null;
    }
}
