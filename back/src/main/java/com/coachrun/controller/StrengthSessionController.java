package com.coachrun.controller;

import com.coachrun.dto.request.StrengthSessionRequest;
import com.coachrun.dto.request.StrengthStructureRequest;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.StrengthSessionResponse;
import com.coachrun.service.StrengthSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import java.util.UUID;

/** Bibliothèque de séances de préparation physique (cf. DARI Lab). Scoping tenant. */
@Tag(name = "Préparation physique — Séances")
@RestController
@RequestMapping("/clubs/{clubId}/pp/sessions")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class StrengthSessionController {

    private final StrengthSessionService sessionService;

    @GetMapping
    public PageResponse<StrengthSessionResponse> list(@PathVariable UUID clubId,
                                                      @RequestParam(required = false) String q,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return sessionService.search(clubId, q, pageable);
    }

    @GetMapping("/{id}")
    public StrengthSessionResponse get(@PathVariable UUID clubId, @PathVariable UUID id) {
        return sessionService.get(clubId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StrengthSessionResponse create(@PathVariable UUID clubId,
                                          @Valid @RequestBody StrengthSessionRequest request) {
        return sessionService.create(clubId, request);
    }

    @PutMapping("/{id}")
    public StrengthSessionResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                          @Valid @RequestBody StrengthSessionRequest request) {
        return sessionService.update(clubId, id, request);
    }

    @PutMapping("/{id}/structure")
    public StrengthSessionResponse putStructure(@PathVariable UUID clubId, @PathVariable UUID id,
                                                @Valid @RequestBody StrengthStructureRequest request) {
        return sessionService.putStructure(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID clubId, @PathVariable UUID id) {
        sessionService.archive(clubId, id);
    }
}
