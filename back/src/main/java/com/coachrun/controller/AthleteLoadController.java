package com.coachrun.controller;

import com.coachrun.dto.response.LoadResponse;
import com.coachrun.service.AthleteLoadService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Charge d'entraînement d'un athlète (ACWR/monotonie/domaines) — cf. DARI Lab Data. */
@Tag(name = "Charge d'entraînement")
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/load")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class AthleteLoadController {

    private final AthleteLoadService loadService;

    @GetMapping
    public LoadResponse load(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return loadService.load(clubId, athleteId);
    }
}
