package com.coachrun.controller;

import com.coachrun.dto.response.AdminOverviewResponse;
import com.coachrun.dto.response.AdminPlatformResponse;
import com.coachrun.dto.response.AdminSearchResponse;
import com.coachrun.dto.response.AdminStatsResponse;
import com.coachrun.dto.response.MailLogResponse;
import com.coachrun.dto.response.MailStatsResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.service.AdminOverviewService;
import com.coachrun.service.AdminPlatformService;
import com.coachrun.service.AdminSearchService;
import com.coachrun.service.AdminStatsService;
import com.coachrun.service.MailStatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


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
}
