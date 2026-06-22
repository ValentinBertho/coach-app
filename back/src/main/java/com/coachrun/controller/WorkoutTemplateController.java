package com.coachrun.controller;

import com.coachrun.dto.request.TemplateApplyRequest;
import com.coachrun.dto.request.WorkoutTemplateRequest;
import com.coachrun.dto.response.PageResponse;
import com.coachrun.dto.response.WorkoutResponse;
import com.coachrun.dto.response.WorkoutTemplateResponse;
import com.coachrun.service.WorkoutTemplateService;
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

@Tag(name = "Bibliothèque de séances")
@RestController
@RequestMapping("/clubs/{clubId}/workout-templates")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class WorkoutTemplateController {

    private final WorkoutTemplateService templateService;

    @GetMapping
    public PageResponse<WorkoutTemplateResponse> list(@PathVariable UUID clubId,
                                                      @RequestParam(required = false) String q,
                                                      @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return templateService.list(clubId, q, pageable);
    }

    @GetMapping("/{id}")
    public WorkoutTemplateResponse get(@PathVariable UUID clubId, @PathVariable UUID id) {
        return templateService.get(clubId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutTemplateResponse create(@PathVariable UUID clubId,
                                          @Valid @RequestBody WorkoutTemplateRequest request) {
        return templateService.create(clubId, request);
    }

    @PutMapping("/{id}")
    public WorkoutTemplateResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                          @Valid @RequestBody WorkoutTemplateRequest request) {
        return templateService.update(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id) {
        templateService.delete(clubId, id);
    }

    @PostMapping("/{id}/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutResponse apply(@PathVariable UUID clubId, @PathVariable UUID id,
                                 @Valid @RequestBody TemplateApplyRequest request) {
        return templateService.apply(clubId, id, request.athleteId(), request.date());
    }
}
