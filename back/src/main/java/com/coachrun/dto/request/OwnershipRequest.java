package com.coachrun.dto.request;

import com.coachrun.entity.enums.AthleteOwnershipType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

/** Bascule du statut d'un athlète entre privé et club. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OwnershipRequest(@NotNull AthleteOwnershipType ownership) {
}
