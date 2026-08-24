package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Ouvrir un fil : vers un coach, un athlète, un groupe ou le club.
 *
 * @param kind     COACH | ATHLETE | GROUP | CLUB
 * @param targetId le coach, l'athlète, le groupe ou le club visé
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenConversationRequest(
        @NotBlank @Pattern(regexp = "COACH|ATHLETE|GROUP|CLUB") String kind,
        @NotNull UUID targetId) {
}
