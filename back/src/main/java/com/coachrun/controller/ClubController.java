package com.coachrun.controller;

import com.coachrun.dto.request.AddCoachRequest;
import com.coachrun.dto.request.GrantPermissionRequest;
import com.coachrun.dto.request.OwnershipRequest;
import com.coachrun.dto.response.AthleteAccessResponse;
import com.coachrun.dto.response.ClubMemberResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.ClubMembershipService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    /** Ajoute un coach existant au club (par e-mail) avec un rôle. */
    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ClubMemberResponse addCoach(@PathVariable UUID clubId,
                                       @Valid @RequestBody AddCoachRequest request,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return clubService.addCoach(clubId, request.email(), request.role(), principal.userId());
    }

    /** Retire un coach du club (le propriétaire ne peut pas être retiré). */
    @DeleteMapping("/members/{coachId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCoach(@PathVariable UUID clubId, @PathVariable UUID coachId) {
        clubService.removeCoach(clubId, coachId);
    }

    @GetMapping("/athletes/{athleteId}/access")
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
    public AthleteAccessResponse access(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return clubService.access(athleteId);
    }

    @PatchMapping("/athletes/{athleteId}/ownership")
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    public AthleteAccessResponse ownership(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @Valid @RequestBody OwnershipRequest request) {
        return clubService.setOwnership(clubId, athleteId, request.ownership());
    }

    @PutMapping("/athletes/{athleteId}/permissions/{coachId}")
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    public AthleteAccessResponse grant(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                       @PathVariable UUID coachId, @Valid @RequestBody GrantPermissionRequest request) {
        return clubService.grant(athleteId, coachId, request.permission(), request.expiresAt());
    }

    @DeleteMapping("/athletes/{athleteId}/permissions/{coachId}")
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    public AthleteAccessResponse revoke(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                        @PathVariable UUID coachId) {
        return clubService.revoke(athleteId, coachId);
    }
}
