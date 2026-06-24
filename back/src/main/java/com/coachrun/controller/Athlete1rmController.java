package com.coachrun.controller;

import com.coachrun.dto.request.Athlete1rmRequest;
import com.coachrun.dto.request.StrengthTestRequest;
import com.coachrun.dto.response.Athlete1rmResponse;
import com.coachrun.dto.response.CalculatedStrengthResponse;
import com.coachrun.dto.response.StrengthTestResponse;
import com.coachrun.service.Athlete1rmService;
import com.coachrun.service.StrengthSessionService;
import com.coachrun.service.StrengthTestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
    private final StrengthTestService testService;
    private final com.coachrun.service.ProgressionService progressionService;

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

    /** Aperçu live des charges d'une structure en cours d'édition (non enregistrée). */
    @PostMapping("/sessions/calculated-preview")
    public CalculatedStrengthResponse calculatedPreview(
            @PathVariable UUID clubId, @PathVariable UUID athleteId,
            @RequestBody com.coachrun.dto.request.StrengthStructureRequest request) {
        return sessionService.previewForAthlete(clubId, athleteId, request.structure());
    }

    /** Historique du e1RM d'un exercice (courbe d'évolution de la force). */
    @GetMapping("/1rm/{exerciseId}/history")
    public List<com.coachrun.dto.response.E1rmHistoryResponse> history(
            @PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID exerciseId) {
        return resultService.history(clubId, athleteId, exerciseId);
    }

    // --- Tests 1RM (4 protocoles, cf. DARI Lab §6.5) ---

    @GetMapping("/tests")
    public List<StrengthTestResponse> listTests(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                @RequestParam(required = false) UUID exerciseId) {
        return testService.list(clubId, athleteId, exerciseId);
    }

    /** Enregistre un test direct ; met à jour le profil 1RM (source {@code tested}). */
    @PostMapping("/tests")
    @ResponseStatus(HttpStatus.CREATED)
    public StrengthTestResponse recordTest(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @Valid @RequestBody StrengthTestRequest request) {
        return testService.record(clubId, athleteId, request);
    }

    /** Progression auto + alertes coach d'une séance de force réalisée (§6.7 / §6.8). */
    @GetMapping("/scheduled/{scheduledId}/progression")
    public com.coachrun.dto.response.ProgressionResponse progression(
            @PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID scheduledId) {
        return progressionService.forCoach(clubId, athleteId, scheduledId);
    }

    /** Suivi de charge interne force (UA méca/métab) sur une période optionnelle. */
    @GetMapping("/load")
    public List<com.coachrun.dto.response.StrengthLoadResponse> loadTracking(
            @PathVariable UUID clubId, @PathVariable UUID athleteId,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return resultService.loadTracking(clubId, athleteId, from, to);
    }
}
