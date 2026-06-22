package com.coachrun.controller;

import com.coachrun.dto.request.PlanApplyRequest;
import com.coachrun.dto.request.TrainingPlanRequest;
import com.coachrun.dto.response.TrainingPlanResponse;
import com.coachrun.service.TrainingPlanService;
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
import java.util.Map;
import java.util.UUID;

@Tag(name = "Plans périodisés")
@RestController
@RequestMapping("/clubs/{clubId}/training-plans")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class TrainingPlanController {

    private final TrainingPlanService planService;

    @GetMapping
    public List<TrainingPlanResponse> list(@PathVariable UUID clubId) {
        return planService.list(clubId);
    }

    @GetMapping("/{id}")
    public TrainingPlanResponse get(@PathVariable UUID clubId, @PathVariable UUID id) {
        return planService.get(clubId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingPlanResponse create(@PathVariable UUID clubId, @Valid @RequestBody TrainingPlanRequest request) {
        return planService.create(clubId, request);
    }

    @PutMapping("/{id}")
    public TrainingPlanResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                       @Valid @RequestBody TrainingPlanRequest request) {
        return planService.update(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id) {
        planService.delete(clubId, id);
    }

    @PostMapping("/{id}/apply")
    public Map<String, Integer> apply(@PathVariable UUID clubId, @PathVariable UUID id,
                                      @Valid @RequestBody PlanApplyRequest request) {
        int created = planService.applyToAthlete(clubId, id, request.athleteId(), request.startDate());
        return Map.of("created", created);
    }
}
