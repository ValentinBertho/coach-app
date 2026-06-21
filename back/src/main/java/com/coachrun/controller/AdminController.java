package com.coachrun.controller;

import com.coachrun.dto.response.AdminStatsResponse;
import com.coachrun.service.AdminStatsService;
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

    @GetMapping("/stats")
    public AdminStatsResponse stats() {
        return adminStatsService.stats();
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
