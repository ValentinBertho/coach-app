package com.coachrun.controller;

import com.coachrun.dto.request.AthleteZoneValueRequest;
import com.coachrun.dto.response.AthleteZoneValueResponse;
import com.coachrun.service.AthleteZoneValueService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Valeurs de zones par athlète (fiche athlète) : lecture, saisie/ajustement, resync. */
@Tag(name = "Zones & métriques de l'athlète")
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/zone-values")
@RequiredArgsConstructor
public class AthleteZoneValueController {

    private final AthleteZoneValueService service;

    @GetMapping
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
    public List<AthleteZoneValueResponse> list(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return service.list(clubId, athleteId);
    }

    @PostMapping("/resync")
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    public List<AthleteZoneValueResponse> resync(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return service.resync(clubId, athleteId);
    }

    @PutMapping("/{zoneId}/{metricId}")
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    public AthleteZoneValueResponse upsert(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @PathVariable UUID zoneId, @PathVariable UUID metricId,
                                           @Valid @RequestBody AthleteZoneValueRequest request) {
        return service.upsert(clubId, athleteId, zoneId, metricId, request);
    }
}
