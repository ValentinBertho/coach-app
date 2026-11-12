package com.coachrun.controller;

import com.coachrun.dto.response.AdminAuditResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditScope;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.service.AdminAuditService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Journal des actions d'administration.
 *
 * <p><b>Lecture seule, sans exception.</b> Aucune route n'écrit ni ne supprime : les lignes sont
 * posées par les services au moment de l'action. Un journal qu'on peut amender depuis l'interface
 * qu'il surveille ne prouve rien.</p>
 */
@Tag(name = "Admin — Journal d'audit")
@RestController
@RequestMapping("/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    @GetMapping
    public PageResponse<AdminAuditResponse> list(
            @RequestParam(required = false) AdminAuditAction action,
            @RequestParam(required = false) AdminAuditScope scope,
            @RequestParam(required = false) AdminAuditTarget targetType,
            @RequestParam(required = false) UUID actorUserId,
            @RequestParam(required = false) UUID targetId,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 50) Pageable pageable) {
        return adminAuditService.search(action, scope, targetType, actorUserId, targetId, days, q, pageable);
    }

    /**
     * Vocabulaire du journal, pour peupler les filtres sans le dupliquer côté front.
     *
     * <p>{@code scope} et {@code scopeLabel} <b>s'ajoutent</b> aux champs existants : un front en
     * cache qui les ignore continue d'afficher une liste plate d'actions, exactement comme avant
     * (Claude.md §4 bis — les réponses s'enrichissent, elles ne se réduisent pas).</p>
     */
    @GetMapping("/actions")
    public List<ActionOption> actions() {
        return java.util.Arrays.stream(AdminAuditAction.values())
                .map(a -> new ActionOption(a.name(), a.label(), a.sensitive(),
                        a.scope().name(), a.scope().label()))
                .toList();
    }

    /** Les familles du journal, pour le filtre de portée. */
    @GetMapping("/scopes")
    public List<ScopeOption> scopes() {
        return java.util.Arrays.stream(AdminAuditScope.values())
                .map(s -> new ScopeOption(s.name(), s.label()))
                .toList();
    }

    public record ActionOption(String value, String label, boolean sensitive,
                               String scope, String scopeLabel) {
    }

    public record ScopeOption(String value, String label) {
    }
}
