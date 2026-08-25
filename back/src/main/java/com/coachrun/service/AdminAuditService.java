package com.coachrun.service;

import com.coachrun.dto.response.AdminAuditResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.AdminAuditLog;
import com.coachrun.entity.enums.AdminAuditAction;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Journal des actions d'administration : écriture et relecture.
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

    /** Consigne une action. Ne lève jamais : voir la note de classe. */
    @Transactional
    public void record(AdminAuditAction action, AdminAuditTarget targetType,
                       UUID targetId, String targetLabel, String summary) {
        try {
            AdminAuditLog entry = new AdminAuditLog();
            AuthPrincipal actor = currentActor();
            if (actor != null) {
                entry.setActorUserId(actor.userId());
                entry.setActorEmail(actor.email());
            }
            entry.setActorName(currentActorName());
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

    /** Variante sans cible identifiée (réglages de plateforme, RAZ démo…). */
    @Transactional
    public void recordPlatform(AdminAuditAction action, String summary) {
        record(action, AdminAuditTarget.PLATFORM, null, null, summary);
    }

    public PageResponse<AdminAuditResponse> search(AdminAuditAction action,
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
                repository.search(action, targetType, actorUserId, targetId, since, query, pageable),
                AdminAuditResponse::from);
    }

    /** Dernières actions, pour le bandeau du tableau de bord. */
    public List<AdminAuditResponse> latest() {
        return repository.findTop10ByOrderByOccurredAtDesc().stream()
                .map(AdminAuditResponse::from)
                .toList();
    }

    /** Historique attaché à une ressource, pour sa fiche. */
    public List<AdminAuditResponse> forTarget(UUID targetId) {
        return repository.findTop20ByTargetIdOrderByOccurredAtDesc(targetId).stream()
                .map(AdminAuditResponse::from)
                .toList();
    }

    public long countSince(Instant since) {
        return repository.countByOccurredAtAfter(since);
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
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
