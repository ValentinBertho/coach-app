package com.coachrun.dto.response;

import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.WorkoutTemplate;
import com.coachrun.entity.enums.Discipline;

import java.util.UUID;

/** Structure DARI Lab d'une séance de bibliothèque + métadonnées d'organisation. */
public record CourseStructureResponse(
        UUID templateId,
        String name,
        Discipline discipline,
        UUID categoryId,
        String categoryName,
        boolean favorite,
        boolean archived,
        int useCount,
        SessionStructure structure
) {

    public static CourseStructureResponse of(WorkoutTemplate t, SessionStructure structure) {
        return new CourseStructureResponse(
                t.getId(), t.getName(), t.getDiscipline(),
                t.getCategory() == null ? null : t.getCategory().getId(),
                t.getCategory() == null ? null : t.getCategory().getName(),
                t.isFavorite(), t.isArchived(), t.getUseCount(), structure);
    }
}
