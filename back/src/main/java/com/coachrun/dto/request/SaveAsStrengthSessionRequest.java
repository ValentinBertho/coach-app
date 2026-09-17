package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Versement d'une séance de renforcement du calendrier dans la bibliothèque, sous forme de
 * nouveau modèle — le pendant force de {@link SaveAsTemplateRequest}.
 *
 * <p>Sans lui, une séance improvisée pour un athlète puis affinée séance après séance restait
 * enfermée sur son jour : la garder supposait de la reconstruire bloc par bloc dans la
 * bibliothèque.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveAsStrengthSessionRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2048) String notes
) {
}
