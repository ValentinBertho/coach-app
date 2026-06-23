package com.coachrun.dto.request;

import com.coachrun.entity.enums.Discipline;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Création / mise à jour d'une catégorie de la bibliothèque de séances course. */
public record SessionCategoryRequest(
        @NotBlank @Size(max = 255) String name,
        UUID parentId,
        Discipline discipline,
        Integer sortOrder
) {
}
