package com.coachrun.controller;

import com.coachrun.dto.request.TrainingZoneReorderRequest;
import com.coachrun.dto.request.TrainingZoneRequest;
import com.coachrun.dto.request.ZoneMetricsRequest;
import com.coachrun.dto.response.TrainingZoneResponse;
import com.coachrun.service.TrainingZoneService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Zones de travail paramétrables par le coach (Récupération, Endurance, Seuil, VO2…). */
@Tag(name = "Zones d'entraînement")
@RestController
@RequestMapping("/clubs/{clubId}/training-zones")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class TrainingZoneController {

    private final TrainingZoneService zoneService;

    @GetMapping
    public List<TrainingZoneResponse> list(@PathVariable UUID clubId) {
        return zoneService.list(clubId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingZoneResponse create(@PathVariable UUID clubId,
                                       @Valid @RequestBody TrainingZoneRequest request) {
        return zoneService.create(clubId, request);
    }

    @PutMapping("/{id}")
    public TrainingZoneResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                       @Valid @RequestBody TrainingZoneRequest request) {
        return zoneService.update(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id) {
        zoneService.delete(clubId, id);
    }

    @PatchMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@PathVariable UUID clubId,
                        @Valid @RequestBody TrainingZoneReorderRequest request) {
        zoneService.reorder(clubId, request.orderedIds());
    }

    @PutMapping("/{id}/metrics")
    public TrainingZoneResponse setMetrics(@PathVariable UUID clubId, @PathVariable UUID id,
                                           @Valid @RequestBody ZoneMetricsRequest request) {
        return zoneService.setMetrics(clubId, id, request);
    }

    /** Édite la règle de calcul (ancre + %) d'une métrique de la zone. */
    @PutMapping("/{id}/metrics/{metricId}/rule")
    public TrainingZoneResponse setRule(@PathVariable UUID clubId, @PathVariable UUID id,
                                        @PathVariable UUID metricId,
                                        @Valid @RequestBody com.coachrun.dto.request.ZoneRuleRequest request) {
        return zoneService.setRule(clubId, id, metricId, request);
    }
}
