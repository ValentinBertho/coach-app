package com.coachrun.controller;

import com.coachrun.dto.request.GenerateMesocycleRequest;
import com.coachrun.dto.request.TrainingGroupRequest;
import com.coachrun.dto.response.GroupApplyResponse;
import com.coachrun.dto.response.TrainingGroupResponse;
import com.coachrun.security.AuthPrincipal;
import com.coachrun.service.TrainingGroupService;
import com.coachrun.service.WorkoutService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@Tag(name = "Groupes d'entraînement")
@RestController
@RequestMapping("/clubs/{clubId}/groups")
@RequiredArgsConstructor
@PreAuthorize("@clubAccessValidator.hasAccess(authentication, #clubId)")
public class TrainingGroupController {

    private final TrainingGroupService groupService;
    private final WorkoutService workoutService;

    @GetMapping
    public List<TrainingGroupResponse> list(@PathVariable UUID clubId) {
        return groupService.list(clubId);
    }

    /**
     * Génère un mésocycle pour tout le groupe à partir de la semaine source de chaque athlète
     * (modèle de mésocycle ou paramètres directs). L'accès en écriture est vérifié athlète par
     * athlète : les athlètes non accessibles sont ignorés.
     */
    @PostMapping("/{id}/generate-mesocycle")
    public GroupApplyResponse generateMesocycle(@PathVariable UUID clubId, @PathVariable UUID id,
                                                @Valid @RequestBody GenerateMesocycleRequest request,
                                                @AuthenticationPrincipal AuthPrincipal principal) {
        return workoutService.generateMesocycleForGroup(clubId, id, request, principal.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingGroupResponse create(@PathVariable UUID clubId, @Valid @RequestBody TrainingGroupRequest request) {
        return groupService.create(clubId, request);
    }

    @PutMapping("/{id}")
    public TrainingGroupResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                        @Valid @RequestBody TrainingGroupRequest request) {
        return groupService.update(clubId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id) {
        groupService.delete(clubId, id);
    }
}
