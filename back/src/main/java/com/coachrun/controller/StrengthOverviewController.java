package com.coachrun.controller;

import com.coachrun.dto.response.StrengthOverviewRow;
import com.coachrun.security.AthleteAccessValidator;
import com.coachrun.service.StrengthOverviewService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** Vue d'ensemble « Suivi &amp; Analyse » : tous les athlètes du coach sur une seule table. */
@Tag(name = "Préparation physique — Vue d'ensemble")
@RestController
@RequestMapping("/clubs/{clubId}/pp/overview")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class StrengthOverviewController {

    /** Borne la liste : l'écran envoie au plus une page d'athlètes. */
    static final int MAX_ATHLETES = 200;

    private final StrengthOverviewService overviewService;
    private final AthleteAccessValidator athleteAccessValidator;

    @GetMapping
    public List<StrengthOverviewRow> overview(@PathVariable UUID clubId,
                                              @RequestParam(name = "athleteIds") List<UUID> athleteIds,
                                              Authentication authentication) {
        // Les identifiants viennent du navigateur : chacun repasse par le même contrôle que la
        // fiche de l'athlète. Un identifiant non lisible est simplement ignoré (pas d'oracle).
        List<UUID> readable = new LinkedHashSet<>(athleteIds).stream()
                .limit(MAX_ATHLETES)
                .filter(id -> athleteAccessValidator.canRead(authentication, id))
                .toList();
        return overviewService.overview(readable);
    }
}
