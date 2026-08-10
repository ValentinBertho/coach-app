package com.coachrun.dto;

import com.coachrun.entity.enums.InjuryArea;
import com.coachrun.entity.enums.InjuryKind;
import com.coachrun.entity.enums.InjurySide;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Une blessure déclarée par l'athlète sur une séance ou une sortie.
 *
 * <p>Sert aussi bien en entrée qu'en sortie, et de format de stockage (sérialisée en JSON dans
 * {@code injuries_json}) : trois formes identiques pour la même donnée éviteraient une conversion
 * à chaque bord sans rien apporter — la structure est un fait déclaré, pas un agrégat calculé.</p>
 *
 * <p><b>Donnée de santé (RGPD art. 9)</b> : sa collecte est gardée par le consentement de
 * l'athlète, au même titre que la fatigue et la douleur.</p>
 *
 * @param kind nature de la blessure
 * @param area zone du corps
 * @param side côté, ou {@code null} (sans objet / non précisé)
 * @param note précision libre de l'athlète
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InjuryReport(
        @NotNull InjuryKind kind,
        @NotNull InjuryArea area,
        InjurySide side,
        @Size(max = 200) String note) {
}
