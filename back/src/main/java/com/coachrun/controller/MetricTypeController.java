package com.coachrun.controller;

import com.coachrun.dto.request.MetricTypeRequest;
import com.coachrun.dto.response.MetricTypeResponse;
import com.coachrun.service.MetricTypeService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Catalogue de métriques de zone (global + custom club). */
@Tag(name = "Métriques de zone")
@RestController
@RequestMapping("/clubs/{clubId}/metric-types")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class MetricTypeController {

    private final MetricTypeService metricTypeService;

    @GetMapping
    public List<MetricTypeResponse> list(@PathVariable UUID clubId) {
        return metricTypeService.list(clubId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MetricTypeResponse create(@PathVariable UUID clubId,
                                     @Valid @RequestBody MetricTypeRequest request) {
        return metricTypeService.create(clubId, request);
    }
}
