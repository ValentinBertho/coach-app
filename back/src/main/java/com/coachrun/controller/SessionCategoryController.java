package com.coachrun.controller;

import com.coachrun.dto.request.SessionCategoryRequest;
import com.coachrun.dto.response.SessionCategoryResponse;
import com.coachrun.service.SessionCategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

/** Arbre de catégories de la bibliothèque de séances course (cf. DARI Lab). */
@Tag(name = "Catégories de séances")
@RestController
@RequestMapping("/clubs/{clubId}/session-categories")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class SessionCategoryController {

    private final SessionCategoryService categoryService;

    @GetMapping
    public List<SessionCategoryResponse> list(@PathVariable UUID clubId) {
        return categoryService.list(clubId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionCategoryResponse create(@PathVariable UUID clubId,
                                          @Valid @RequestBody SessionCategoryRequest request) {
        return categoryService.create(clubId, request);
    }

    @PutMapping("/{id}")
    public SessionCategoryResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                          @Valid @RequestBody SessionCategoryRequest request) {
        return categoryService.update(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id) {
        categoryService.delete(clubId, id);
    }
}
