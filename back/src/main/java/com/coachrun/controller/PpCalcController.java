package com.coachrun.controller;

import com.coachrun.dto.request.E1rmRequest;
import com.coachrun.dto.response.E1rmResponse;
import com.coachrun.service.OneRmCalcService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Calculs de préparation physique (e1RM + zones de travail). */
@Tag(name = "Préparation physique — Calculs")
@RestController
@RequestMapping("/clubs/{clubId}/pp/calc")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class PpCalcController {

    private final OneRmCalcService calcService;

    @PostMapping("/e1rm")
    public E1rmResponse e1rm(@PathVariable UUID clubId, @Valid @RequestBody E1rmRequest request) {
        return calcService.compute(request);
    }
}
