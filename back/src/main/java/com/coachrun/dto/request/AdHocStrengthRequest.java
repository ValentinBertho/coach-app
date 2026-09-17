package com.coachrun.dto.request;

import com.coachrun.entity.enums.FieldsPreset;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Séance de renforcement posée <b>directement</b> sur un jour du calendrier, sans passer par la
 * bibliothèque.
 *
 * <p>Une séance de prépa physique ne se construisait que dans la bibliothèque : créer le modèle,
 * l'ouvrir, le remplir, revenir au calendrier, le glisser. Cinq écrans pour une séance que le
 * coach improvise pour un athlète et qui ne resservira peut-être jamais. La course avait déjà son
 * chemin court (« Séance vierge (ad hoc) ») ; la force restait sans. C'est celui-ci.</p>
 *
 * <p>Le titre est facultatif : la séance naît « Séance de renforcement » et se renomme dans
 * l'éditeur, comme son équivalent course.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AdHocStrengthRequest(
        @NotNull LocalDate date,
        @Size(max = 255) String title,
        FieldsPreset fieldsPreset
) {
}
