package com.coachrun.dto.request;

import com.coachrun.entity.enums.ActivitySource;
import com.coachrun.entity.enums.ActivitySport;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Import (manuel ou externe) d'une activité réalisée. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ActivityImportRequest(
        ActivitySource source,
        @Size(max = 128) String externalId,
        @NotNull LocalDate activityDate,
        @Size(max = 255) String title,
        @Min(0) Integer distanceM,
        @Min(0) Integer durationS,
        @Min(0) Integer avgHr,
        @Min(0) Integer elevationGainM,
        /**
         * Sport de la sortie. Facultatif : {@code null} signifie « non déclaré », et le
         * rapprochement automatique se comporte alors comme avant l'existence du champ. Une
         * saisie manuelle de séance de renforcement gagne à le renseigner — c'est ce qui
         * l'empêche d'aller se rapprocher du fractionné prescrit le même jour.
         */
        ActivitySport sport,
        /** Confirme l'enregistrement malgré une sortie très proche déjà présente le même jour. */
        Boolean confirmDuplicate) {

    public ActivitySource sourceOrDefault() {
        return source != null ? source : ActivitySource.MANUAL;
    }
}
