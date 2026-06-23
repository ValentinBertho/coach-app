package com.coachrun.dto.response;

import com.coachrun.entity.SessionCategory;
import com.coachrun.entity.enums.Discipline;

import java.util.UUID;

/** Catégorie de bibliothèque (liste plate ordonnée ; l'arbre est reconstruit via {@code parentId}). */
public record SessionCategoryResponse(
        UUID id,
        String name,
        UUID parentId,
        Discipline discipline,
        int sortOrder
) {

    public static SessionCategoryResponse from(SessionCategory c) {
        return new SessionCategoryResponse(
                c.getId(), c.getName(),
                c.getParent() == null ? null : c.getParent().getId(),
                c.getDiscipline(), c.getSortOrder());
    }
}
