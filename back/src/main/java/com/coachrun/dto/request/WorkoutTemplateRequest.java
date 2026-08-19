package com.coachrun.dto.request;

import com.coachrun.entity.enums.WorkoutType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Création / mise à jour d'un modèle de séance (bibliothèque).
 *
 * <p>Seul le <b>titre</b> est exigé : demander en plus un « nom du modèle » faisait saisir deux
 * fois la même chose. Le nom, qui range la séance dans la bibliothèque, reprend le titre quand
 * il n'est pas renseigné (cf. {@link #name()}).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutTemplateRequest(
        @Size(max = 255) String name,
        @NotNull WorkoutType type,
        @NotBlank @Size(max = 255) String title,
        @Size(max = 2048) String notes,
        @Min(0) Integer targetDistanceM,
        @Min(0) Integer targetDurationS,
        /**
         * Effort perçu attendu pour la séance entière. Borné 1–10 comme le ressenti de
         * l'athlète : les deux se lisent côte à côte, sur la même échelle, ou ils ne se
         * comparent pas.
         */
        @Min(1) @jakarta.validation.constraints.Max(10) Integer targetRpe,
        UUID categoryId,
        @Valid List<WorkoutStepRequest> steps) {

    public List<WorkoutStepRequest> steps() {
        return steps == null ? List.of() : steps;
    }

    /** Nom de rangement : celui saisi, à défaut le titre de la séance. */
    public String name() {
        return name == null || name.isBlank() ? title : name.trim();
    }
}
