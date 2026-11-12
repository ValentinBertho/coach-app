package com.coachrun.dto.request;

import com.coachrun.entity.enums.CoachReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ce qu'un visiteur envoie pour signaler une fiche.
 *
 * <p>Le texte est <b>obligatoire</b>, contrairement au motif d'un refus de demande. Un signalement
 * réduit à une case cochée ne se traite pas : « diplôme inexact » sans dire lequel ni pourquoi
 * laisse l'administrateur devant une accusation qu'il ne peut ni vérifier ni écarter, et le coach
 * devant un soupçon sans contenu.</p>
 */
public record CoachReportSubmission(

        @NotNull(message = "Indiquez ce que vous signalez.")
        CoachReportReason reason,

        @NotBlank(message = "Décrivez ce que vous avez constaté : sans détail, le signalement ne peut pas être traité.")
        @Size(min = 20, max = 2000, message = "Décrivez en quelques phrases ce que vous avez constaté.")
        String details) {
}
