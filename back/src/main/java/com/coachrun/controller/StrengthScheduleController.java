package com.coachrun.controller;

import com.coachrun.dto.request.ScheduleStrengthRequest;
import com.coachrun.dto.response.ScheduledStrengthResponse;
import com.coachrun.dto.response.StrengthPrescriptionResponse;
import com.coachrun.service.StrengthScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Calendrier de force d'un athlète, côté coach (cf. DARI Lab). Scoping tenant. */
@Tag(name = "Préparation physique — Calendrier")
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/pp")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class StrengthScheduleController {

    private final StrengthScheduleService scheduleService;

    @PostMapping("/sessions/{sessionId}/schedule")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduledStrengthResponse schedule(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                              @PathVariable UUID sessionId,
                                              @Valid @RequestBody ScheduleStrengthRequest request) {
        return scheduleService.schedule(clubId, athleteId, sessionId, request.date(), request.fieldsPreset());
    }

    @GetMapping("/scheduled")
    public List<ScheduledStrengthResponse> calendar(
            @PathVariable UUID clubId, @PathVariable UUID athleteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return scheduleService.coachCalendar(clubId, athleteId, from, to);
    }

    @GetMapping("/scheduled/{scheduledId}/prescription")
    public StrengthPrescriptionResponse prescription(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                                     @PathVariable UUID scheduledId) {
        return scheduleService.prescription(clubId, scheduledId);
    }
}
