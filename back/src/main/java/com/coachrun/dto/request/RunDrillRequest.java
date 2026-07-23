package com.coachrun.dto.request;

import com.coachrun.entity.enums.RunDrillCategory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunDrillRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull RunDrillCategory category,
        /** Catégorie de l'arbre unifié (domaine DRILL), optionnelle (QA1). */
        UUID categoryId,
        @Size(max = 2000) String description,
        @Size(max = 512) String videoUrl
) {
}
