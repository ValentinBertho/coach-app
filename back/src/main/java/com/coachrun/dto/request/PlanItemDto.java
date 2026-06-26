package com.coachrun.dto.request;

import com.coachrun.entity.enums.PlanItemKind;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Item d'un plan : séance positionnée (semaine 0-based × jour 1=lundi..7=dimanche). {@code kind}
 * distingue course et renforcement ; {@code templateId} référence le modèle de séance course OU la
 * séance de force de bibliothèque. {@code kind} absent (anciens plans) ⇒ COURSE.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanItemDto(
        @Min(0) int weekIndex,
        @Min(1) @Max(7) int dayOfWeek,
        PlanItemKind kind,
        @NotNull UUID templateId) {

    public PlanItemKind kindOrDefault() {
        return kind != null ? kind : PlanItemKind.COURSE;
    }
}
