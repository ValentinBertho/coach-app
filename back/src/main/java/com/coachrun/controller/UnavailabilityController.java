package com.coachrun.controller;

import com.coachrun.dto.request.UnavailabilityRequest;
import com.coachrun.dto.response.UnavailabilityResponse;
import com.coachrun.service.UnavailabilityService;
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
import java.util.UUID;

/** Indisponibilités d'un athlète (blessure, maladie, vacances…). Scoping tenant. */
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/unavailabilities")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class UnavailabilityController {

    private final UnavailabilityService service;

    @GetMapping
    public List<UnavailabilityResponse> list(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return service.list(clubId, athleteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UnavailabilityResponse create(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                         @Valid @RequestBody UnavailabilityRequest request) {
        return service.create(clubId, athleteId, request);
    }

    @PutMapping("/{id}")
    public UnavailabilityResponse update(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                         @PathVariable UUID id, @Valid @RequestBody UnavailabilityRequest request) {
        return service.update(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID id) {
        service.delete(clubId, id);
    }
}
