package com.coachrun.controller;

import com.coachrun.dto.request.CalendarNoteRequest;
import com.coachrun.dto.response.CalendarNoteResponse;
import com.coachrun.service.CalendarNoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Notes libres du coach sur le calendrier d'un athlète (chip note, CDC §8). Scoping tenant. */
@RestController
@RequestMapping("/clubs/{clubId}/athletes/{athleteId}/notes")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canRead(authentication, #athleteId)")
public class CalendarNoteController {

    private final CalendarNoteService noteService;

    @GetMapping
    public List<CalendarNoteResponse> list(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return noteService.list(clubId, athleteId, from, to);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarNoteResponse create(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                       @Valid @RequestBody CalendarNoteRequest request) {
        return noteService.create(clubId, athleteId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @PutMapping("/{noteId}")
    public CalendarNoteResponse update(@PathVariable UUID clubId, @PathVariable UUID athleteId,
                                       @PathVariable UUID noteId, @Valid @RequestBody CalendarNoteRequest request) {
        return noteService.update(clubId, noteId, request);
    }

    @PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId) and @athleteAccessValidator.canWrite(authentication, #athleteId)")
    @DeleteMapping("/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID athleteId, @PathVariable UUID noteId) {
        noteService.delete(clubId, noteId);
    }
}
