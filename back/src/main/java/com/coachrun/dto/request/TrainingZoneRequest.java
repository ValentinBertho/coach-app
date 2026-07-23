package com.coachrun.dto.request;

import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.ZoneScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création / mise à jour d'une zone de travail du club (nom, couleur, portée, discipline). */
public record TrainingZoneRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 16) String color,
        @Size(max = 1024) String description,
        ZoneScope scope,
        Discipline discipline,
        Integer sortOrder
) {
}
