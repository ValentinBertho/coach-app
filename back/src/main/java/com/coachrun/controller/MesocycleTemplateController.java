package com.coachrun.controller;

import com.coachrun.dto.request.MesocycleTemplateRequest;
import com.coachrun.dto.response.MesocycleTemplateResponse;
import com.coachrun.service.MesocycleTemplateService;
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

@Tag(name = "Modèles de mésocycle")
@RestController
@RequestMapping("/clubs/{clubId}/mesocycle-templates")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class MesocycleTemplateController {

    private final MesocycleTemplateService service;

    @GetMapping
    public List<MesocycleTemplateResponse> list(@PathVariable UUID clubId) {
        return service.list(clubId);
    }

    @GetMapping("/{id}")
    public MesocycleTemplateResponse get(@PathVariable UUID clubId, @PathVariable UUID id) {
        return service.get(clubId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MesocycleTemplateResponse create(@PathVariable UUID clubId,
                                            @Valid @RequestBody MesocycleTemplateRequest request) {
        return service.create(clubId, request);
    }

    @PutMapping("/{id}")
    public MesocycleTemplateResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                            @Valid @RequestBody MesocycleTemplateRequest request) {
        return service.update(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id) {
        service.delete(clubId, id);
    }
}
