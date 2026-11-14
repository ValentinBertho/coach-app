package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Consignes d'une séance planifiée, pour CET athlète : l'encart d'écriture libre et l'effort
 * attendu sur la séance entière.
 *
 * <p>Mêmes conventions que l'éditeur de modèle, qui partage l'écran : un texte vide efface les
 * notes, un RPE à zéro efface l'annonce. La borne des notes suit la colonne
 * {@code workouts.notes} (VARCHAR 2048).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutInstructionsRequest(
        @Size(max = 2048) String notes,
        @Min(0) @Max(10) Integer targetRpe) {
}
