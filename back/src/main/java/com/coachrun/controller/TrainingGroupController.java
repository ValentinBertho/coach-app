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
    private final com.coachrun.service.GroupAnalyticsService groupAnalyticsService;

    @GetMapping
    public List<TrainingGroupResponse> list(@PathVariable UUID clubId,
                                            @AuthenticationPrincipal AuthPrincipal principal) {
        return groupService.list(clubId, principal.userId());
    }

    /**
     * Semaine du groupe (vue calendrier multi-athlètes) : une ligne par athlète accessible,
     * séances course et force. Endpoint agrégé, pour éviter N appels côté calendrier.
     */
    @GetMapping("/{id}/calendar")
    public com.coachrun.dto.response.GroupCalendarResponse calendar(
            @PathVariable UUID clubId, @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
            @org.springframework.web.bind.annotation.RequestParam
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
            @AuthenticationPrincipal AuthPrincipal principal) {
        return groupService.calendar(clubId, id, principal.userId(), from, to);
    }

    /** Analytics agrégées d'un groupe (état de forme, ACWR moyen, volume, adhérence). */
    @GetMapping("/{id}/analytics")
    public com.coachrun.dto.response.GroupAnalyticsResponse analytics(
            @PathVariable UUID clubId, @PathVariable UUID id,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "8") int weeks,
            @AuthenticationPrincipal AuthPrincipal principal) {
        // Un groupe privé n'existe pas pour les autres coachs, ici comme dans la liste.
        groupService.requireVisible(clubId, id, principal.userId());
        return groupAnalyticsService.compute(clubId, id, weeks);
    }

    /**
     * Génère un mésocycle pour tout le groupe à partir de la semaine source de chaque athlète
     * (modèle de mésocycle ou paramètres directs). L'accès en écriture est vérifié athlète par
     * athlète : les athlètes non accessibles sont ignorés.
     */
    /**
     * Planifie une même séance pour tout le groupe à une date : la prescription collective, qui
     * n'existait que répétée athlète par athlète.
     */
    @PostMapping("/{id}/schedule")
    public GroupApplyResponse schedule(@PathVariable UUID clubId, @PathVariable UUID id,
                                       @Valid @RequestBody com.coachrun.dto.request.GroupScheduleRequest request,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        return groupService.schedule(clubId, id, principal.userId(), request);
    }

    @PostMapping("/{id}/generate-mesocycle")
    public GroupApplyResponse generateMesocycle(@PathVariable UUID clubId, @PathVariable UUID id,
                                                @Valid @RequestBody GenerateMesocycleRequest request,
                                                @AuthenticationPrincipal AuthPrincipal principal) {
        groupService.requireVisible(clubId, id, principal.userId());
        return workoutService.generateMesocycleForGroup(clubId, id, request, principal.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainingGroupResponse create(@PathVariable UUID clubId,
                                        @Valid @RequestBody TrainingGroupRequest request,
                                        @AuthenticationPrincipal AuthPrincipal principal) {
        return groupService.create(clubId, principal.userId(), request);
    }

    @PutMapping("/{id}")
    public TrainingGroupResponse update(@PathVariable UUID clubId, @PathVariable UUID id,
                                        @Valid @RequestBody TrainingGroupRequest request,
                                        @AuthenticationPrincipal AuthPrincipal principal) {
        return groupService.update(clubId, id, principal.userId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID clubId, @PathVariable UUID id,
                       @AuthenticationPrincipal AuthPrincipal principal) {
        groupService.delete(clubId, id, principal.userId());
    }
}
