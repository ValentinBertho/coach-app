package com.coachrun.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Correction d'une sortie par l'athlète, et ressenti associé.
 *
 * <p>Tous les champs sont optionnels : l'écran envoie ce qu'il a modifié. {@code null} signifie
 * « inchangé » et non « efface » — sans quoi noter un RPE effacerait le titre.</p>
 *
 * <p>Les <b>chiffres</b> (date, distance, durée, dénivelé) ne sont modifiables que sur une sortie
 * saisie à la main. Sur une sortie venue de la montre, ils sont la mesure : les réécrire ferait
 * mentir la charge d'entraînement que le coach lit, sans que rien ne l'indique. Le titre, le RPE
 * et le commentaire restent modifiables partout — ils appartiennent à l'athlète, pas au capteur.</p>
 *
 * @param clearComment vide explicitement le commentaire (le {@code null} de {@code comment}
 *                     voulant dire « inchangé »)
 */
public record ActivityUpdateRequest(
        @Size(max = 200) String title,
        LocalDate activityDate,
        @PositiveOrZero Integer distanceM,
        @PositiveOrZero Integer durationS,
        @PositiveOrZero Integer elevationGainM,
        @Min(1) @Max(10) Integer rpe,
        @Size(max = 2000) String comment,
        Boolean clearRpe,
        Boolean clearComment) {
}
