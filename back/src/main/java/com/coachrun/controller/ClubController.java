package com.coachrun.controller;

import com.coachrun.dto.request.GrantPermissionRequest;
import com.coachrun.dto.request.OwnershipRequest;
import com.coachrun.dto.response.AthleteAccessResponse;
import com.coachrun.dto.response.ClubMemberResponse;
import com.coachrun.service.ClubMembershipService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Multi-coach / club (cf. DARI Lab) : membres, statut privé/club, permissions. Scoping tenant. */
@Tag(name = "Club & permissions")
@RestController
@RequestMapping("/clubs/{clubId}")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class ClubController {

    private final ClubMembershipService clubService;

    @GetMapping("/members")
    public List<ClubMemberResponse> members(@PathVariable UUID clubId) {
        return clubService.members(clubId);
    }

    @GetMapping("/athletes/{athleteId}/access")
    public AthleteAccessResponse access(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return clubService.access(athleteId);
    }

    @PatchMapping("/athletes/{athleteId}/ownership")
    public AthleteAccessResponse ownership(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @Valid @RequestBody OwnershipRequest request) {
        return clubService.setOwnership(clubId, athleteId, request.ownership());
    }

    @PutMapping("/athletes/{athleteId}/permissions/{coachId}")
    public AthleteAccessResponse grant(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                       @PathVariable UUID coachId, @Valid @RequestBody GrantPermissionRequest request) {
        return clubService.grant(athleteId, coachId, request.permission(), request.expiresAt());
    }

    @DeleteMapping("/athletes/{athleteId}/permissions/{coachId}")
    public AthleteAccessResponse revoke(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                        @PathVariable UUID coachId) {
        return clubService.revoke(athleteId, coachId);
    }
}
