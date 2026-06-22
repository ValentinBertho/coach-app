package com.coachrun.controller;

import com.coachrun.dto.request.AthleteRequest;
import com.coachrun.dto.response.AthleteInvitationResponse;
import com.coachrun.dto.response.AthleteResponse;
import com.coachrun.dto.response.AthleteSummaryResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.service.AthleteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Gestion des athlètes d'un club. Toutes les routes sont scopées au tenant
 * via {@code @clubAccessValidator.hasAccess} (anti-IDOR).
 */
@RestController
@RequestMapping("/clubs/{clubId}/athletes")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class AthleteController {

    private final AthleteService athleteService;

    @GetMapping
    public PageResponse<AthleteSummaryResponse> list(
            @PathVariable UUID clubId,
            @RequestParam(required = false) AthleteStatus status,
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return athleteService.list(clubId, status, groupId, q, pageable);
    }

    @GetMapping("/{athleteId}")
    public AthleteResponse get(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return athleteService.get(clubId, athleteId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AthleteResponse create(@PathVariable UUID clubId,
                                  @Valid @RequestBody AthleteRequest request) {
        return athleteService.create(clubId, request);
    }

    @PutMapping("/{athleteId}")
    public AthleteResponse update(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @Valid @RequestBody AthleteRequest request) {
        return athleteService.update(clubId, athleteId, request);
    }

    @DeleteMapping("/{athleteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        athleteService.archive(clubId, athleteId);
    }

    @PostMapping("/{athleteId}/invitation")
    public AthleteInvitationResponse invite(@PathVariable UUID clubId,
                                            @PathVariable UUID athleteId) {
        return athleteService.invite(clubId, athleteId);
    }
}
