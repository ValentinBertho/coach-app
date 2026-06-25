package com.coachrun.controller;

import com.coachrun.dto.request.RunDrillRequest;
import com.coachrun.dto.response.RunDrillResponse;
import com.coachrun.service.RunDrillService;
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

@Tag(name = "Éducatifs course")
@RestController
@RequestMapping("/clubs/{clubId}/run-drills")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class RunDrillController {

    private final RunDrillService drillService;

    @GetMapping
    public List<RunDrillResponse> list(@PathVariable UUID clubId) {
        return drillService.list(clubId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunDrillResponse create(@PathVariable UUID clubId, @Valid @RequestBody RunDrillRequest request) {
        return drillService.create(clubId, request);
    }

    @PutMapping("/{id}")
    public RunDrillResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                   @Valid @RequestBody RunDrillRequest request) {
        return drillService.update(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id) {
        drillService.delete(clubId, id);
    }
}
