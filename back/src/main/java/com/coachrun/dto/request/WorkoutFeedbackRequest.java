package com.coachrun.dto.request;

import com.coachrun.entity.enums.WorkoutStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Feedback de l'athlète sur sa séance : statut réalisé, sensation (1–5), RPE (1–10),
 * fatigue (1–10), douleur (0–10), blessures déclarées et commentaire.
 *
 * <p>Fatigue + douleur alimentent l'état de forme. La <b>sensation</b> ne s'y ajoute pas : elle
 * dit comment la séance a été vécue, ce que ni l'effort perçu ni la fatigue ne portent — une
 * séance de seuil peut être très dure et très bien vécue, et c'est justement l'information que le
 * coach cherchait dans le commentaire libre.</p>
 *
 * <p>{@code actualDurationS} porte la durée réellement effectuée sur une séance écourtée : c'est
 * elle qui pèse dans la charge, pas la durée prescrite. {@code missedReason} accompagne un statut
 * {@code MISSED} — l'athlète pouvait jusqu'ici seulement se taire, et son silence devenait une
 * alerte « séance manquée » sans qu'il ait pu en dire la raison.</p>
 *
 * @param injuries blessures déclarées ({@code null} = inchangé, liste vide = plus aucune)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkoutFeedbackRequest(
        WorkoutStatus status,
        /** Sensation générale : 1 = excellente … 5 = très mauvaise. Jamais l'effort. */
        @Min(1) @Max(5) Integer feel,
        @Min(1) @Max(10) Integer rpe,
        @Min(1) @Max(10) Integer fatigue,
        @Min(0) @Max(10) Integer pain,
        @Size(max = 1024) String comment,
        @Valid @Size(max = 5) java.util.List<com.coachrun.dto.InjuryReport> injuries,
        /** Durée réellement effectuée (secondes) ; plafonnée à 12 h, une saisie au-delà est une faute de frappe. */
        @Min(1) @Max(43200) Integer actualDurationS,
        com.coachrun.entity.enums.MissedReason missedReason) {
}
