package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Versement d'une séance construite au calendrier dans la bibliothèque : une séance improvisée
 * pour un athlète devient un modèle réutilisable, sans la reconstruire bloc par bloc.
 *
 * <p>Le {@code name} range le modèle dans la bibliothèque ; absent, il reprend le titre.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveAsTemplateRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String name,
        UUID categoryId
) {

    /** Nom de rangement : celui saisi, à défaut le titre. */
    public String name() {
        return name == null || name.isBlank() ? title : name.trim();
    }
}
