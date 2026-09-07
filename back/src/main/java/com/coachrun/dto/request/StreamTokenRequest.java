package com.coachrun.dto.request;

import com.coachrun.security.StreamTokenService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

/**
 * Demande d'un jeton de flux. La portée est obligatoire : un jeton sans portée ouvrirait les
 * deux usages, ce qui reviendrait à recréer un petit jeton de session.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StreamTokenRequest(@NotNull StreamTokenService.Scope scope) {
}
