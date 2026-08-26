package com.coachrun.controller;

import com.coachrun.dto.response.AdminOverviewResponse;
import com.coachrun.dto.response.AdminPlatformResponse;
import com.coachrun.dto.response.AdminSearchResponse;
import com.coachrun.dto.response.AdminStatsResponse;
import com.coachrun.dto.response.MailLogResponse;
import com.coachrun.dto.response.MailStatsResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.service.AdminAuditService;
import com.coachrun.service.AdminOverviewService;
import com.coachrun.service.AdminPlatformService;
import com.coachrun.service.AdminSearchService;
import com.coachrun.service.AdminStatsService;
import com.coachrun.service.MailStatsService;
import com.coachrun.service.DemoResetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin — Pilotage, recherche & configuration")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminController {

    private final AdminStatsService adminStatsService;
    private final AdminOverviewService adminOverviewService;
    private final AdminSearchService adminSearchService;
    private final AdminPlatformService adminPlatformService;
    private final AdminAuditService adminAuditService;
    private final DemoResetService demoResetService;
    private final MailStatsService mailStatsService;

    /**
     * Compteurs bruts historiques.
     *
     * <p>Conservé tel quel bien que {@link #overview()} le remplace : des PWA en cache appellent
     * encore cette route, et une réponse d'API ne se retire pas (cf. Claude.md §4 bis).</p>
     */
    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminStatsService.stats();
    }

    /**
     * Tableau de bord de pilotage : anomalies actionnables d'abord, puis photographie,
     * dynamique, intégrations et dernières actions d'administration.
     */
    @GetMapping("/overview")
    public AdminOverviewResponse overview() {
        return adminOverviewService.overview();
    }

    /**
     * Recherche globale : un compte, un club ou un athlète, sans avoir à deviner d'avance dans
     * quel écran il se trouve.
     */
    @GetMapping("/search")
    public AdminSearchResponse search(@RequestParam(name = "q", required = false) String q) {
        return adminSearchService.search(q);
    }

    /** Configuration de l'instance, en lecture seule et sans aucune valeur de secret. */
    @GetMapping("/platform")
    public AdminPlatformResponse platform() {
        return adminPlatformService.platform();
    }

    /**
     * Consommation d'e-mails : plafonds du jour et du mois, histogramme quotidien, répartition par
     * nature d'envoi, échecs récents.
     *
     * <p>Le plan d'envoi est plafonné et rien ne le mesurait : la seule façon d'apprendre qu'on
     * l'avait dépassé était qu'un utilisateur signale n'avoir jamais reçu son lien de
     * réinitialisation — l'envoi qu'on ne peut précisément pas perdre.</p>
     *
     * @param days profondeur de l'histogramme (30 par défaut, borné entre 7 et 90)
     */
    @GetMapping("/mail/stats")
    public MailStatsResponse mailStats(@RequestParam(defaultValue = "30") int days) {
        return mailStatsService.stats(days);
    }

    /** Journal des envois, du plus récent au plus ancien : « untel a-t-il bien reçu son lien ? ». */
    @GetMapping("/mail/log")
    public PageResponse<MailLogResponse> mailLog(
            @org.springframework.data.web.PageableDefault(size = 50)
            org.springframework.data.domain.Pageable pageable) {
        return mailStatsService.log(pageable);
    }

    /** Indique si la RAZ démo est disponible (flag activé et hors prod). */
    @GetMapping("/demo/reset-available")
    public Map<String, Boolean> resetAvailable() {
        return Map.of("available", demoResetService.isAvailable());
    }

    /** RAZ démo : purge + recharge le jeu de démo. Garde-fous dans le service. */
    @PostMapping("/demo/reset")
    public ResponseEntity<Map<String, String>> reset() {
        demoResetService.reset();
        // Consigné APRÈS la purge, délibérément : la RAZ vide les tables, et une trace écrite
        // avant serait emportée avec le reste — c'est-à-dire la seule qu'on voudrait retrouver.
        adminAuditService.recordPlatform(AdminAuditAction.DEMO_RESET,
                "Toutes les données ont été effacées au profit du jeu de démonstration.");
        return ResponseEntity.ok(Map.of("status", "ok",
                "message", "Données réinitialisées avec le jeu de démo."));
    }
}
