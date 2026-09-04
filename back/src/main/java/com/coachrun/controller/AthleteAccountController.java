package com.coachrun.controller;

import com.coachrun.dto.request.AthleteAccountRequest;
import com.coachrun.dto.response.AthleteAccountResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.AthleteAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le compte d'un athlète, vu par lui-même.
 *
 * <p>Distinct de {@code /me}, qui sert son <b>entraînement</b> et suppose une fiche donc un coach.
 * Cette route-ci répond même à un athlète que personne ne suit encore : c'est précisément l'état
 * dans lequel il arrive.</p>
 */
@Tag(name = "Athlète — Mon compte")
@RestController
@RequestMapping("/me/account")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ATHLETE')")
public class AthleteAccountController {

    private final AthleteAccountService service;

    @GetMapping
    public AthleteAccountResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return service.myAccount(principal.userId());
    }

    @PatchMapping
    public AthleteAccountResponse update(@AuthenticationPrincipal AuthPrincipal principal,
                                         @Valid @RequestBody AthleteAccountRequest request) {
        return service.update(principal.userId(), request);
    }
}
