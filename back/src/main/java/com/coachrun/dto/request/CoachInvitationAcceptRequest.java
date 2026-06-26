package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Acceptation d'une invitation coach : mot de passe (et nom optionnel). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachInvitationAcceptRequest(
        @NotBlank @Size(min = 8, max = 100) String password,
        String fullName) {
}
