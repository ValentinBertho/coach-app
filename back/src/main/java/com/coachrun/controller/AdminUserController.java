package com.coachrun.controller;

import com.coachrun.dto.request.AdminSuspendRequest;
import com.coachrun.dto.request.AdminUserCreateRequest;
import com.coachrun.dto.request.AdminUserUpdateRequest;
import com.coachrun.dto.response.AdminUserDetailResponse;
import com.coachrun.dto.response.AdminUserResponse;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.entity.enums.AdminAuditAction;
import com.coachrun.entity.enums.AdminAuditTarget;
import com.coachrun.entity.enums.UserRole;
import com.coachrun.entity.enums.UserStatus;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.AdminAuditService;
import com.coachrun.service.AdminUserService;
import com.coachrun.service.ImpersonationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@Tag(name = "Admin — Utilisateurs")
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final ImpersonationService impersonationService;
    private final AdminAuditService adminAuditService;

    /**
     * Ouvre une session au nom de cet utilisateur, pour voir l'application exactement comme lui.
     *
     * <p>La quasi-totalité des défauts remontés en bêta se décrivent par un écran, et se
     * reproduisent seulement depuis le compte concerné — ses séances, ses allures, son historique.
     * La seule alternative était de demander son mot de passe à l'utilisateur.</p>
     *
     * <p>Le jeton rendu n'a pas de rafraîchissement : la session dure le temps d'un jeton d'accès.
     * Un compte d'administration ne peut pas être emprunté (cf. {@code ImpersonationService}), et
     * chaque ouverture est consignée au journal d'audit.</p>
     */
    @PostMapping("/{id}/impersonate")
    public com.coachrun.dto.response.ImpersonationResponse impersonate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id) {
        var response = impersonationService.impersonate(principal.userId(), id);
        adminAuditService.record(AdminAuditAction.USER_IMPERSONATED, AdminAuditTarget.USER,
                id, response.user().email(),
                "Session ouverte au nom de ce compte (rôle " + response.user().role()
                        + "). Les écritures faites pendant ce temps lui sont attribuées.");
        return response;
    }

    @GetMapping
    public PageResponse<AdminUserResponse> list(@RequestParam(required = false) UserRole role,
                                                @RequestParam(required = false) UserStatus status,
                                                @RequestParam(required = false) UUID clubId,
                                                @RequestParam(required = false) Boolean verified,
                                                @RequestParam(required = false) String q,
                                                @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return adminUserService.list(role, status, clubId, verified, q, pageable);
    }

    @GetMapping("/{id}")
    public AdminUserResponse get(@PathVariable UUID id) {
        return adminUserService.get(id);
    }

    /** Fiche complète : vérification, activité, clubs, appareils, historique d'administration. */
    @GetMapping("/{id}/detail")
    public AdminUserDetailResponse detail(@PathVariable UUID id) {
        return adminUserService.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse create(@Valid @RequestBody AdminUserCreateRequest request) {
        return adminUserService.create(request);
    }

    @PutMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody AdminUserUpdateRequest request) {
        return adminUserService.update(id, request);
    }

    /** Suspend le compte et ferme ses sessions en cours (la suspension ne les fermait pas). */
    @PostMapping("/{id}/suspend")
    public AdminUserResponse suspend(@PathVariable UUID id,
                                     @Valid @RequestBody(required = false) AdminSuspendRequest request) {
        return adminUserService.suspend(id, request != null ? request.reason() : null);
    }

    @PostMapping("/{id}/reactivate")
    public AdminUserResponse reactivate(@PathVariable UUID id) {
        return adminUserService.reactivate(id);
    }

    /** Ferme toutes les sessions sans suspendre : « j'ai perdu mon téléphone ». */
    @PostMapping("/{id}/revoke-sessions")
    public AdminUserResponse revokeSessions(@PathVariable UUID id) {
        return adminUserService.revokeSessions(id);
    }

    /**
     * Envoie un lien de réinitialisation à l'adresse du compte. L'administrateur ne choisit ni ne
     * voit le nouveau mot de passe : il n'a aucune raison de le connaître.
     */
    @PostMapping("/{id}/password-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendPasswordReset(@PathVariable UUID id) {
        adminUserService.sendPasswordReset(id);
    }

    /** Renvoie l'e-mail de confirmation d'adresse — le blocage n° 1 des nouveaux coachs. */
    @PostMapping("/{id}/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@PathVariable UUID id) {
        adminUserService.resendVerification(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        adminUserService.delete(id);
    }

    /** Rattache un club additionnel à un coach (modèle multi-club). */
    @PutMapping("/{id}/clubs/{clubId}")
    public AdminUserResponse addClub(@PathVariable UUID id, @PathVariable UUID clubId) {
        return adminUserService.addClub(id, clubId);
    }

    @DeleteMapping("/{id}/clubs/{clubId}")
    public AdminUserResponse removeClub(@PathVariable UUID id, @PathVariable UUID clubId) {
        return adminUserService.removeClub(id, clubId);
    }
}
