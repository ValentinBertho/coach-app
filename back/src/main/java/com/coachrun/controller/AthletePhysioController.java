package com.coachrun.controller;

import com.coachrun.dto.request.PerformanceRequest;
import com.coachrun.dto.request.PhysioProfileRequest;
import com.coachrun.dto.request.SessionCalcRequest;
import com.coachrun.dto.response.CalculatedBlockResponse;
import com.coachrun.dto.response.PerformanceResponse;
import com.coachrun.dto.response.PhysioProfileResponse;
import com.coachrun.dto.response.VdotResponse;
import com.coachrun.service.AthletePhysioService;
import com.coachrun.service.SessionCalculatorService;
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

/**
 * Profil physiologique d'un athlète : seuils, performances et VDOT (cf. DARI Lab).
 * Scopé au tenant via {@code @clubAccessValidator.hasAccess} (anti-IDOR).
 */
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
public class AthletePhysioController {

    private final AthletePhysioService physioService;
    private final SessionCalculatorService calculatorService;

    @GetMapping("/physio")
    public PhysioProfileResponse getProfile(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return physioService.getProfile(clubId, athleteId);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PutMapping("/physio")
    public PhysioProfileResponse updateProfile(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                               @Valid @RequestBody PhysioProfileRequest request) {
        return physioService.updateProfile(clubId, athleteId, request);
    }

    @GetMapping("/performances")
    public List<PerformanceResponse> listPerformances(@PathVariable UUID clubId,
                                                      @PathVariable UUID athleteId) {
        return physioService.listPerformances(clubId, athleteId);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping("/performances")
    @ResponseStatus(HttpStatus.CREATED)
    public PerformanceResponse addPerformance(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                              @Valid @RequestBody PerformanceRequest request) {
        return physioService.addPerformance(clubId, athleteId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @DeleteMapping("/performances/{performanceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePerformance(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                  @PathVariable UUID performanceId) {
        physioService.deletePerformance(clubId, athleteId, performanceId);
    }


    /** Test de Vitesse Critique : calcule la VC (+ D') depuis plusieurs efforts. */
    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @org.springframework.web.bind.annotation.PostMapping("/vc-test")
    public com.coachrun.dto.response.VcTestResponse vcTest(
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID clubId,
            @org.springframework.web.bind.annotation.PathVariable java.util.UUID athleteId,
            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody com.coachrun.dto.request.VcTestRequest request) {
        return physioService.computeVc(clubId, athleteId, request);
    }

    @GetMapping("/vdot")
    public VdotResponse getVdot(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return physioService.getVdot(clubId, athleteId);
    }

    /** Cibles calculées (allure/vitesse/FC/RPE/durée/distance) pour un bloc prescrit en fourchette. */
    @PostMapping("/session-calc")
    public CalculatedBlockResponse calculate(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                             @Valid @RequestBody SessionCalcRequest request) {
        return calculatorService.calculate(clubId, athleteId, request);
    }
}
