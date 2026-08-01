package com.coachrun.dto.response;

import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.entity.enums.Discipline;

import java.util.UUID;

/** Structure DARI Lab d'une séance de bibliothèque + métadonnées d'organisation. */
public record CourseStructureResponse(
        UUID templateId,
        String name,
        /** Titre affiché à l'athlète (le nom, lui, range la séance dans la bibliothèque). */
        String title,
        Discipline discipline,
        UUID categoryId,
        String categoryName,
        boolean favorite,
        boolean archived,
        int useCount,
        /** Encart d'écriture libre du coach. */
        String notes,
        SessionStructure structure
) {

    public static CourseStructureResponse of(WorkoutTemplate t, SessionStructure structure) {
        return new CourseStructureResponse(
                t.getId(), t.getName(), t.getTitle(), t.getDiscipline(),
                t.getCategory() == null ? null : t.getCategory().getId(),
                t.getCategory() == null ? null : t.getCategory().getName(),
                t.isFavorite(), t.isArchived(), t.getUseCount(), t.getNotes(), structure);
    }
}
