package com.coachrun.controller;

import com.coachrun.dto.response.AdminStatsResponse;
import com.coachrun.dto.response.MailLogResponse;
import com.coachrun.dto.response.MailStatsResponse;
import com.coachrun.dto.response.PageResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Admin — Dashboard & RAZ")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminController {

    private final AdminStatsService adminStatsService;
    private final DemoResetService demoResetService;
    private final MailStatsService mailStatsService;

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminStatsService.stats();
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
    public MailStatsResponse mailStats(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int days) {
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
        return ResponseEntity.ok(Map.of("status", "ok",
                "message", "Données réinitialisées avec le jeu de démo."));
    }
}
