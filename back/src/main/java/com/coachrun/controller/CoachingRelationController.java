package com.coachrun.controller;

import com.coachrun.dto.request.ClubRequestDecision;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.CoachingRelationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * La sortie : mettre fin à un coaching, des deux côtés.
 *
 * <p>Une place de marché où l'on entre sans pouvoir sortir est un piège. Le socle de la révocation
 * existait ; il lui manquait une porte.</p>
 *
 * <p>Les deux gestes vivent dans le même contrôleur parce qu'ils décrivent le même événement vu de
 * deux côtés. Leurs adresses diffèrent parce que leurs périmètres diffèrent : l'athlète n'a qu'un
 * coach et n'a rien à désigner ; le coach en a plusieurs et doit nommer lequel.</p>
 */
@Tag(name = "Relation de coaching")
@RestController
@RequiredArgsConstructor
public class CoachingRelationController {

    private final CoachingRelationService service;

    /**
     * L'athlète met fin à son coaching.
     *
     * <p>Sans préavis et sans motif obligatoire : exiger une justification pour partir la
     * demanderait à celui-là même dont on veut se détacher. Rien n'est détruit — la fiche reste
     * chez son coach, qui la garde en lecture ; l'athlète redevient un compte libre de repartir
     * dans l'annuaire.</p>
     */
    @PostMapping("/me/coach/end")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ATHLETE')")
    public void endByAthlete(@AuthenticationPrincipal AuthPrincipal principal,
                             @Valid @RequestBody(required = false) ClubRequestDecision body) {
        service.endByAthlete(principal.userId(), body == null ? null : body.note());
    }

    /**
     * Le coach met fin au coaching d'un de ses athlètes.
     *
     * <p>Scopé par club comme le reste de la surface coach, et réservé au <b>référent</b> : un
     * coach qui n'est pas celui de l'athlète n'a pas à décider de son suivi.</p>
     */
    @PostMapping("/clubs/{clubId}/athletes/{athleteId}/end-relation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) "
            + "and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    public void endByCoach(@AuthenticationPrincipal AuthPrincipal principal,
                           @PathVariable UUID clubId,
                           @PathVariable UUID athleteId,
                           @Valid @RequestBody(required = false) ClubRequestDecision body) {
        service.endByCoach(principal.userId(), athleteId, body == null ? null : body.note());
    }
}
