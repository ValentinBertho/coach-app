package com.coachrun.dto.request;

import com.coachrun.dto.session.SessionStructure;
import com.coachrun.entity.enums.Discipline;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Mise à jour de la structure DARI Lab d'une séance de la bibliothèque : identité (nom, titre),
 * discipline, catégorie, favori et blocs (échauffement/corps/retour) prescrits en fourchettes.
 *
 * <p>Toutes les métadonnées sont <b>optionnelles</b> : un champ absent laisse la valeur en place.
 * L'éditeur auto-sauvegarde en boucle la seule structure — un {@code null} interprété comme
 * « efface » lui faisait perdre la catégorie choisie à la création. Le détachement explicite
 * passe donc par {@code clearCategory}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseStructureRequest(
        @Size(max = 255) String name,
        @Size(max = 255) String title,
        Discipline discipline,
        UUID categoryId,
        /** {@code true} = ranger la séance « sans catégorie » ({@code categoryId} ignoré). */
        Boolean clearCategory,
        Boolean favorite,
        /** Encart d'écriture libre du coach (intention, consignes, ressenti attendu…). */
        String notes,
        /**
         * Effort perçu attendu pour la séance <b>entière</b> (1–10). Distinct du RPE porté par
         * chaque bloc : un 10 × 400 a des blocs à 9 et un échauffement à 3, sans que « la séance »
         * ait un chiffre.
         *
         * <p>{@code 0} efface l'annonce — l'éditeur auto-sauvegarde en boucle, et {@code null}
         * signifie ici « champ absent, laisse en place », comme le reste des métadonnées.</p>
         */
        @jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(10)
        Integer targetRpe,
        SessionStructure structure
) {
}
