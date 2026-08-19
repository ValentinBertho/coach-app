package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Renommage d'une séance planifiée.
 *
 * <p>La borne suit la colonne {@code workouts.title} (VARCHAR 255) : sans elle, un titre trop
 * long ne serait refusé qu'au moment de l'écriture, sous la forme d'un 409 de contrainte — un
 * message qui ne dit pas à l'utilisateur ce qu'il doit corriger.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutTitleRequest(@NotBlank @Size(max = 255) String title) {
}
