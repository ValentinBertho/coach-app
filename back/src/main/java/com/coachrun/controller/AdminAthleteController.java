package com.coachrun.controller;

import com.coachrun.dto.request.AthleteRequest;
import com.coachrun.dto.response.AdminAthleteResponse;
import com.coachrun.dto.response.AdminInvitationLinkResponse;
import com.coachrun.dto.response.AthleteResponse;
import com.coachrun.dto.response.InvitationAdminResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.service.AdminAthleteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin — Athlètes & invitations")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminAthleteController {

    private final AdminAthleteService adminAthleteService;

    @GetMapping("/athletes")
    public PageResponse<AdminAthleteResponse> list(@RequestParam(required = false) UUID clubId,
                                                   @RequestParam(required = false) AthleteStatus status,
                                                   @RequestParam(required = false) String q,
                                                   @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
        return adminAthleteService.list(clubId, status, q, pageable);
    }

    @GetMapping("/athletes/{id}")
    public AthleteResponse get(@PathVariable UUID id) {
        return adminAthleteService.get(id);
    }

    @PutMapping("/athletes/{id}")
    public AthleteResponse update(@PathVariable UUID id, @Valid @RequestBody AthleteRequest request) {
        return adminAthleteService.update(id, request);
    }

    @DeleteMapping("/athletes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        adminAthleteService.delete(id);
    }

    @GetMapping("/invitations")
    public PageResponse<InvitationAdminResponse> invitations(
            @PageableDefault(size = 20) Pageable pageable) {
        return adminAthleteService.pendingInvitations(pageable);
    }

    @DeleteMapping("/invitations/{athleteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID athleteId) {
        adminAthleteService.revokeInvitation(athleteId);
    }

    /**
     * Régénère et renvoie l'invitation. Seule la révocation existait : une invitation expirée
     * n'avait d'autre issue que de supprimer l'athlète et de le recréer — donc d'effacer son
     * historique — ou de passer par le compte d'un coach du club.
     *
     * <p>Le lien est rendu dans la réponse : l'e-mail peut ne jamais arriver, et un athlète peut
     * n'avoir aucune adresse connue.</p>
     */
    @org.springframework.web.bind.annotation.PostMapping("/invitations/{athleteId}/resend")
    public AdminInvitationLinkResponse resend(@PathVariable UUID athleteId) {
        return adminAthleteService.resendInvitation(athleteId);
    }
}
