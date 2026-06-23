package com.coachrun.dto.request;

import com.coachrun.entity.enums.FieldsPreset;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Assignation d'une séance de force au calendrier d'un athlète, à une date, avec le préréglage
 * des champs demandés (défaut : DEBUTANT).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduleStrengthRequest(
        @NotNull LocalDate date,
        FieldsPreset fieldsPreset
) {
}
