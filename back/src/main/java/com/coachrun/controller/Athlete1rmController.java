package com.coachrun.controller;

import com.coachrun.dto.request.Athlete1rmRequest;
import com.coachrun.dto.response.Athlete1rmResponse;
import com.coachrun.dto.response.CalculatedStrengthResponse;
import com.coachrun.service.Athlete1rmService;
import com.coachrun.service.StrengthSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Profil 1RM d'un athlète + calcul des charges d'une séance de force pour cet athlète. */
@Tag(name = "Préparation physique — 1RM athlète")
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/pp")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class Athlete1rmController {

    private final Athlete1rmService oneRmService;
    private final StrengthSessionService sessionService;
    private final com.coachrun.service.StrengthResultService resultService;

    @GetMapping("/1rm")
    public List<Athlete1rmResponse> list(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return oneRmService.list(clubId, athleteId);
    }

    @PutMapping("/1rm")
    public Athlete1rmResponse set(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @Valid @RequestBody Athlete1rmRequest request) {
        return oneRmService.set(clubId, athleteId, request);
    }

    /** Séance de force calculée (charges en kg) pour cet athlète. */
    @GetMapping("/sessions/{sessionId}/calculated")
    public CalculatedStrengthResponse calculated(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                 @PathVariable UUID sessionId) {
        return sessionService.calculateForAthlete(clubId, athleteId, sessionId);
    }

    /** Historique du e1RM d'un exercice (courbe d'évolution de la force). */
    @GetMapping("/1rm/{exerciseId}/history")
    public List<com.coachrun.dto.response.E1rmHistoryResponse> history(
            @PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID exerciseId) {
        return resultService.history(clubId, athleteId, exerciseId);
    }
}
