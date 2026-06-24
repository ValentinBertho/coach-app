package com.coachrun.controller;

import com.coachrun.dto.request.AssignCycleRequest;
import com.coachrun.dto.request.StrengthCycleRequest;
import com.coachrun.dto.response.StrengthCycleResponse;
import com.coachrun.service.StrengthCycleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cycles de préparation physique (cf. DARI Lab). Scoping tenant. */
@Tag(name = "Préparation physique — Cycles")
@RestController
@RequestMapping("/clubs/{clubId}/pp")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class StrengthCycleController {

    private final StrengthCycleService cycleService;

    @GetMapping("/cycles")
    public List<StrengthCycleResponse> list(@PathVariable UUID clubId) {
        return cycleService.list(clubId);
    }

    @GetMapping("/cycles/{id}")
    public StrengthCycleResponse get(@PathVariable UUID clubId, @PathVariable UUID id) {
        return cycleService.get(clubId, id);
    }

    @PostMapping("/cycles")
    @ResponseStatus(HttpStatus.CREATED)
    public StrengthCycleResponse create(@PathVariable UUID clubId,
                                        @Valid @RequestBody StrengthCycleRequest request) {
        return cycleService.create(clubId, request);
    }

    @PutMapping("/cycles/{id}")
    public StrengthCycleResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                        @Valid @RequestBody StrengthCycleRequest request) {
        return cycleService.update(clubId, id, request);
    }

    @DeleteMapping("/cycles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id) {
        cycleService.delete(clubId, id);
    }

    /** Assigne un cycle au calendrier d'un athlète. */
    @PostMapping("/cycles/{id}/assign/{athleteId}")
    public Map<String, Integer> assign(@PathVariable UUID clubId, @PathVariable UUID id,
                                       @PathVariable UUID athleteId, @Valid @RequestBody AssignCycleRequest request) {
        return Map.of("scheduled", cycleService.assign(clubId, athleteId, id, request.startDate()));
    }
}
