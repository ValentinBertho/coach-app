package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Création / renommage d'un modèle de zones.
 *
 * <p>À la création, le modèle recopie par défaut l'échelle d'un jeu existant
 * ({@code copyFromSetId}, sinon le jeu par défaut du club) : douze zones et leurs règles ne se
 * ressaisissent pas à la main. {@code copyZones=false} donne un modèle vide, à construire.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZoneSetRequest(
        @NotBlank @Size(max = 255) String name,
        UUID copyFromSetId,
        Boolean copyZones
) {
}
