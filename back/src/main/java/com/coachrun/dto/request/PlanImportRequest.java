package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Import d'un plan rédigé hors de l'application — le PDF ou le tableur d'un coach extérieur,
 * converti en fichier JSON — dans le calendrier d'un athlète.
 *
 * <p>Chaque séance est une {@link WorkoutRequest} ordinaire : le fichier ne parle pas une langue
 * à part, il ne fait que regrouper ce qu'on aurait saisi une par une. {@code title} nomme le plan
 * dans la notification unique envoyée à l'athlète.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanImportRequest(
        @Size(max = 255) String title,
        @NotEmpty @Size(max = 500) @Valid List<WorkoutRequest> workouts) {
}
