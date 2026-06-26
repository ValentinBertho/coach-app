package com.coachrun.controller;

import com.coachrun.dto.request.RaceObjectiveRequest;
import com.coachrun.dto.response.RaceObjectiveResponse;
import com.coachrun.service.RaceObjectiveService;
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

/** Courses cibles d'un athlète. Scoping tenant. */
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/races")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
public class RaceObjectiveController {

    private final RaceObjectiveService raceService;

    @GetMapping
    public List<RaceObjectiveResponse> list(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return raceService.list(clubId, athleteId);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RaceObjectiveResponse create(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                        @Valid @RequestBody RaceObjectiveRequest request) {
        return raceService.create(clubId, athleteId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PutMapping("/{raceId}")
    public RaceObjectiveResponse update(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                        @PathVariable UUID raceId, @Valid @RequestBody RaceObjectiveRequest request) {
        return raceService.update(clubId, raceId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @DeleteMapping("/{raceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID raceId) {
        raceService.delete(clubId, raceId);
    }
}
