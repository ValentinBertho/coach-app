package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/**
 * Motif facultatif d'une suspension de compte.
 *
 * <p>Il n'est pas obligatoire — l'exiger transformerait un geste d'urgence en formulaire — mais il
 * est consigné au journal quand il est fourni : six mois plus tard, « compte suspendu » sans motif
 * ne se relit pas.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdminSuspendRequest(@Size(max = 300) String reason) {
}
