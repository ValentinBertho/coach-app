package com.coachrun.dto.request;

import com.coachrun.entity.enums.GroupVisibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Création ou renommage d'un groupe.
 *
 * @param visibility      absente = CLUB, la valeur historique
 * @param invitedCoachIds coachs conviés quand le groupe est privé
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TrainingGroupRequest(
        @NotBlank @Size(max = 255) String name,
        GroupVisibility visibility,
        List<UUID> invitedCoachIds) {

    public GroupVisibility visibilityOrDefault() {
        return visibility == null ? GroupVisibility.CLUB : visibility;
    }
}
