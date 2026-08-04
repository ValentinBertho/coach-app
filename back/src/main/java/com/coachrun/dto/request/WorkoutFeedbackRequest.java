package com.coachrun.dto.request;

import com.coachrun.entity.enums.WorkoutStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Feedback de l'athlète sur sa séance : statut réalisé, RPE (1–10), fatigue (1–10),
 * douleur (0–10) et commentaire. Fatigue + douleur alimentent l'état de forme.
 *
 * <p>{@code actualDurationS} porte la durée réellement effectuée sur une séance écourtée : c'est
 * elle qui pèse dans la charge, pas la durée prescrite. {@code missedReason} accompagne un statut
 * {@code MISSED} — l'athlète pouvait jusqu'ici seulement se taire, et son silence devenait une
 * alerte « séance manquée » sans qu'il ait pu en dire la raison.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutFeedbackRequest(
        WorkoutStatus status,
        @Min(1) @Max(10) Integer rpe,
        @Min(1) @Max(10) Integer fatigue,
        @Min(0) @Max(10) Integer pain,
        @Size(max = 1024) String comment,
        /** Durée réellement effectuée (secondes) ; plafonnée à 12 h, une saisie au-delà est une faute de frappe. */
        @Min(1) @Max(43200) Integer actualDurationS,
        com.coachrun.entity.enums.MissedReason missedReason) {
}
