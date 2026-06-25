package com.coachrun.controller;

import com.coachrun.dto.request.LactateDetectRequest;
import com.coachrun.dto.request.LactateTestRequest;
import com.coachrun.dto.response.LTDetectionResponse;
import com.coachrun.dto.response.LactateTestResponse;
import com.coachrun.service.LactateTestService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Tests lactate d'un athlète + détection LT1/LT2 (cf. DARI Lab). Scoping tenant. */
@Tag(name = "Tests lactate")
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/lactate-tests")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
public class LactateTestController {

    private final LactateTestService testService;

    @GetMapping
    public List<LactateTestResponse> list(@PathVariable UUID clubId, @PathVariable UUID athleteId) {
        return testService.list(clubId, athleteId);
    }

    @GetMapping("/{testId}")
    public LactateTestResponse get(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                   @PathVariable UUID testId) {
        return testService.get(clubId, testId);
    }

    /** Détection temps réel des seuils sans persistance (UI de saisie). */
    @PostMapping("/detect")
    public LTDetectionResponse detect(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                      @Valid @RequestBody LactateDetectRequest request) {
        return testService.detect(clubId, athleteId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LactateTestResponse create(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                      @Valid @RequestBody LactateTestRequest request) {
        return testService.create(clubId, athleteId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @DeleteMapping("/{testId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                       @PathVariable UUID testId) {
        testService.delete(clubId, testId);
    }
}
