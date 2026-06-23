package com.coachrun.dto.request;

import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.enums.Discipline;

import java.util.UUID;

/**
 * Mise à jour de la structure DARI Lab d'une séance de la bibliothèque : discipline, catégorie,
 * favori et blocs (échauffement/corps/retour) prescrits en fourchettes.
 */
public record CourseStructureRequest(
        Discipline discipline,
        UUID categoryId,
        Boolean favorite,
        SessionStructure structure
) {
}
