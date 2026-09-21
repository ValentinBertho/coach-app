package com.coachrun.service;

import com.coachrun.dto.response.AdminAuditResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.AdminAuditLog;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditScope;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.repository.AdminAuditLogRepository;
import com.coachrun.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Journal des actions : écriture et relecture.
 *
 * <p><b>Il ne regarde plus seulement le back-office.</b> Le journal ne consignait que les gestes
 * d'administration. Il consigne aussi, désormais, ce que font les utilisateurs ordinaires dès que
 * c'est important : ouvrir un accès, changer un mot de passe, retirer un consentement santé,
 * exporter ou effacer un dossier, archiver un athlète. La famille de chaque action est donnée par
 * {@link AdminAuditScope}, qui sert à garder intactes les lectures conçues pour l'administration
 * seule — le bandeau « dernières actions » et le compteur hebdomadaire, qu'une journée de
 * connexions aurait sinon noyés.</p>
 *
 * <p><b>L'acteur n'est pas un paramètre.</b> Il est lu dans le {@code SecurityContext}, et
 * l'adresse d'appel dans la requête courante. Le faire passer par la signature de chaque méthode
 * de service aurait obligé à modifier une dizaine de contrats pour un besoin transverse — et la
 * première signature qu'on oublie de propager est celle dont la trace manquera le jour où on la
 * cherche. Le contexte est déjà la source d'autorité de {@code @PreAuthorize} ; c'est la même
 * source qu'on interroge ici.</p>
 *
 * <p><b>La trace et le geste vont ensemble.</b> L'écriture rejoint la transaction de l'appelant
 * ({@code REQUIRED}) : si la mutation échoue, la trace disparaît avec elle — un journal qui
 * annoncerait des suppressions qui n'ont pas eu lieu serait pire qu'aucun journal. À l'inverse,
 * une erreur propre à l'écriture du journal est avalée et signalée en {@code ERROR} plutôt que
 * remontée : perdre une trace vaut mieux qu'empêcher un administrateur de suspendre un compte
 * compromis. La réserve à connaître : si l'échec vient de la base elle-même, la transaction est
 * déjà marquée pour annulation et le geste échouera de toute façon au commit.</p>
 *
 * <p><b>Ce que la trace porte, au-delà du geste.</b> Le rôle de l'acteur <b>au moment du geste</b>
 * (il change, et le relire plus tard raconterait autre chose), l'administrateur derrière une
 * session empruntée quand il y en a un, et l'appel HTTP qui a servi. Les trois répondent à des
 * questions qu'on ne se pose qu'après coup, c'est-à-dire trop tard pour les instrumenter.</p>
 *
 * <p><b>Rien de sensible dans le résumé.</b> Les appelants composent des phrases à partir de
 * noms, rôles et statuts. Aucune note médicale, aucune valeur physiologique, aucun jeton, aucun
 * mot de passe ne doit y transiter — c'est une règle d'appel, rappelée sur chaque site d'écriture.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditService {

    /** Le journal accepte 1000 caractères ; on tronque plutôt que de faire échouer l'insertion. */
    private static final int SUMMARY_MAX = 1000;
    private static final int USER_AGENT_MAX = 255;

    private final AdminAuditLogRepository repository;
    private final com.coachrun.repository.UserRepository userRepository;

    /** Consigne une action. Ne lève jamais : voir la note de classe. */
    @Transactional
    public void record(AdminAuditAction action, AdminAuditTarget targetType,
                       UUID targetId, String targetLabel, String summary) {
        write(action, targetType, targetId, targetLabel, summary, null);
    }

    /**
     * Consigne une action dans une transaction <b>à elle</b>, qui survit au sort de l'appelant.
     *
     * <h2>Pourquoi cette seconde porte existe</h2>
     *
     * <p>{@link #record} rejoint la transaction du geste, et c'est voulu : un journal ne doit pas
     * annoncer une suppression qui n'a pas eu lieu. Mais trois familles d'événements ne sont pas
     * des mutations de l'appelant, et cette règle les faisait disparaître en silence.</p>
     *
     * <ol>
     *   <li><b>Ce qui est consigné à côté d'un refus.</b> Un échec de connexion se termine par une
     *       exception, donc par un rollback : la trace partait avec — et l'échec de connexion est
     *       précisément la ligne qu'on vient chercher dans un journal de sécurité.</li>
     *   <li><b>Ce qui est consigné depuis une lecture.</b> Un export RGPD ne modifie rien : son
     *       service est en {@code readOnly = true}. Une transaction jointe hérite de cet attribut,
     *       Hibernate n'y vide jamais sa session, et l'insertion ne serait <b>jamais écrite</b> —
     *       sans la moindre erreur pour le signaler. Le pire des cas : un journal qui se croit
     *       complet.</li>
     *   <li><b>Ce qui n'a pas d'acteur dans le contexte de sécurité.</b> Une connexion, une
     *       réinitialisation de mot de passe ou une inscription se produisent avant qu'un
     *       principal n'existe : l'acteur est alors fourni explicitement.</li>
     * </ol>
     *
     * <p>Le prix assumé : la trace subsiste même si la transaction de l'appelant échoue ensuite.
     * Pour ces trois familles, c'est le bon arbitrage — l'événement <b>a</b> eu lieu (la tentative
     * de connexion, la lecture du dossier), indépendamment de ce que l'appelant en fait après.</p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDetached(Actor actor, AdminAuditAction action, AdminAuditTarget targetType,
                               UUID targetId, String targetLabel, String summary) {
        write(action, targetType, targetId, targetLabel, summary, actor);
    }

    /** Variante de {@link #recordDetached} quand l'acteur est celui du contexte de sécurité. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDetached(AdminAuditAction action, AdminAuditTarget targetType,
                               UUID targetId, String targetLabel, String summary) {
        write(action, targetType, targetId, targetLabel, summary, null);
    }

    /**
     * Identité de l'acteur quand le contexte de sécurité ne peut pas la donner : connexion,
     * inscription, réinitialisation de mot de passe, acceptation d'invitation.
     *
     * <p>{@link #anonymous} couvre la tentative de connexion sur un compte qui n'existe pas. La
     * ligne n'a alors pas d'acteur identifié — seulement l'adresse saisie en cible et l'adresse IP
     * d'appel, qui sont justement ce qu'on regarde pour reconnaître un bourrage d'identifiants.</p>
     */
    public record Actor(UUID userId, String email, String role) {

        public static Actor of(com.coachrun.entity.User user) {
            return user == null ? anonymous() : new Actor(
                    user.getId(), user.getEmail(),
                    user.getRole() != null ? user.getRole().name() : null);
        }

        public static Actor anonymous() {
            return new Actor(null, null, null);
        }
    }

    /**
     * Écriture commune. {@code explicitActor} nul = l'acteur est lu dans le contexte de sécurité,
     * qui reste le cas normal (cf. note de classe : le faire passer par chaque signature de
     * service, c'est garantir d'en oublier une).
     */
    private void write(AdminAuditAction action, AdminAuditTarget targetType,
                       UUID targetId, String targetLabel, String summary, Actor explicitActor) {
        try {
            AdminAuditLog entry = new AdminAuditLog();
            if (explicitActor != null) {
                entry.setActorUserId(explicitActor.userId());
                entry.setActorEmail(truncate(explicitActor.email(), 255));
                entry.setActorName(truncate(explicitActor.email(), 255));
                entry.setActorRole(truncate(explicitActor.role(), 32));
            } else {
                AuthPrincipal actor = currentActor();
                if (actor != null) {
                    entry.setActorUserId(actor.userId());
                    entry.setActorEmail(actor.email());
                }
                entry.setActorName(currentActorName());
                applyActorContext(entry, actor);
            }
            entry.setAction(action);
            entry.setTargetType(targetType);
            entry.setTargetId(targetId);
            entry.setTargetLabel(truncate(targetLabel, 255));
            entry.setSummary(truncate(summary, SUMMARY_MAX));
            entry.setOccurredAt(Instant.now());
            applyRequestContext(entry);
            repository.save(entry);
        } catch (RuntimeException ex) {
            // Volontairement non propagé : perdre une trace est moins grave que refuser l'action.
            log.error("Journal d'audit indisponible pour {} sur {} — action effectuée quand même",
                    action, targetType, ex);
        }
    }

    /**
     * De quel droit, et qui vraiment.
     *
     * <p><b>Le rôle</b> est recopié plutôt que relu au moment de la relecture : il change (un head
     * coach redevient coach, un administrateur est rétrogradé), et un journal qui affiche le rôle
     * d'aujourd'hui en face d'un geste d'il y a six mois raconte quelque chose de faux.</p>
     *
     * <p><b>L'emprunteur</b> est le renseignement qui manquait le plus. Une action faite par un
     * administrateur depuis une session empruntée était consignée comme celle du compte emprunté,
     * sans marque d'aucune sorte. L'e-mail est résolu en base — une lecture de plus, mais
     * uniquement dans ce cas rare, et c'est le seul moyen que la ligne reste lisible si le compte
     * administrateur disparaît ensuite.</p>
     */
    private void applyActorContext(AdminAuditLog entry, AuthPrincipal actor) {
        if (actor == null) {
            return;
        }
        if (actor.role() != null) {
            entry.setActorRole(actor.role().name());
        }
        UUID impersonator = actor.impersonatorUserId();
        if (impersonator == null) {
            return;
        }
        entry.setImpersonatorUserId(impersonator);
        entry.setImpersonatorEmail(truncate(
                userRepository.findById(impersonator)
                        .map(com.coachrun.entity.User::getEmail)
                        .orElse(null), 255));
    }

    /** Variante sans cible identifiée (réglages de plateforme, RAZ démo…). */
    @Transactional
    public void recordPlatform(AdminAuditAction action, String summary) {
        record(action, AdminAuditTarget.PLATFORM, null, null, summary);
    }

    /**
     * Recherche filtrée. {@code scope} nul = tout le journal ; sinon, la famille demandée — c'est
     * le filtre qui rend l'écran lisible maintenant qu'une connexion y figure au même titre qu'une
     * suppression de club.
     */
    public PageResponse<AdminAuditResponse> search(AdminAuditAction action,
                                                   AdminAuditScope scope,
                                                   AdminAuditTarget targetType,
                                                   UUID actorUserId,
                                                   UUID targetId,
                                                   Integer days,
                                                   String q,
                                                   Pageable pageable) {
        Instant since = (days == null || days <= 0)
                ? null
                : Instant.now().minus(java.time.Duration.ofDays(Math.min(days, 365)));
        String query = (q == null || q.isBlank()) ? "" : q.trim();
        return PageResponse.from(
                repository.search(action, AdminAuditAction.inScope(scope), targetType, actorUserId,
                        targetId, since, query, pageable),
                AdminAuditResponse::from);
    }

    /**
     * Dernières actions <b>d'administration</b>, pour le bandeau du tableau de bord.
     *
     * <p>La restriction est le prix de l'ouverture du journal : dix lignes sans filtre ne
     * montreraient plus que les dix dernières connexions, et le bandeau — fait pour repérer d'un
     * coup d'œil un geste inhabituel sur la plateforme — ne servirait plus à rien. Le reste du
     * journal se lit sur son écran, où il se filtre.</p>
     */
    public List<AdminAuditResponse> latest() {
        return repository.findTop10ByActionInOrderByOccurredAtDesc(
                        AdminAuditAction.inScope(AdminAuditScope.ADMINISTRATION)).stream()
                .map(AdminAuditResponse::from)
                .toList();
    }

    /**
     * Historique attaché à une ressource, pour sa fiche.
     *
     * <p>Volontairement <b>sans restriction de famille</b>, contrairement au bandeau et au
     * compteur : sur la fiche d'un compte, ses connexions et ses changements de mot de passe sont
     * précisément ce qu'on vient y lire. Vingt lignes au maximum — la fiche porte un lien vers le
     * journal complet filtré sur cette ressource, où les familles se filtrent.</p>
     */
    public List<AdminAuditResponse> forTarget(UUID targetId) {
        return repository.findTop20ByTargetIdOrderByOccurredAtDesc(targetId).stream()
                .map(AdminAuditResponse::from)
                .toList();
    }

    /**
     * Nombre de gestes d'administration depuis une date, pour le compteur du tableau de bord.
     *
     * <p>Restreint à l'administration pour la même raison que {@link #latest()} : l'écran annonce
     * « actions d'administration (7 j) », et un compteur qui dirait soudain quelques milliers
     * parce qu'il additionne les connexions mentirait à son propre libellé.</p>
     */
    public long countSince(Instant since) {
        return repository.countByActionInAndOccurredAtAfter(
                AdminAuditAction.inScope(AdminAuditScope.ADMINISTRATION), since);
    }

    private AuthPrincipal currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal)
                ? principal
                : null;
    }

    /**
     * Le principal ne porte pas le nom complet ; l'e-mail suffit à identifier l'acteur et évite
     * une lecture supplémentaire en base à chaque écriture du journal.
     */
    private String currentActorName() {
        AuthPrincipal actor = currentActor();
        return actor != null ? actor.email() : null;
    }

    /**
     * Adresse d'appel et navigateur, quand l'action vient d'une requête HTTP. L'en-tête
     * {@code X-Forwarded-For} est privilégié : derrière le proxy de production, l'adresse directe
     * est toujours celle du proxy, donc sans valeur pour un journal de sécurité.
     */
    private void applyRequestContext(AdminAuditLog entry) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        entry.setIpAddress(truncate(ip, 64));
        entry.setUserAgent(truncate(request.getHeader("User-Agent"), USER_AGENT_MAX));
        entry.setRequestMethod(truncate(request.getMethod(), 8));
        // `getRequestURI()` et non l'URL complète : la chaîne de requête peut transporter des
        // filtres saisis à la main, donc de la donnée qui n'a rien à faire dans un journal.
        entry.setRequestPath(truncate(request.getRequestURI(), 255));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
