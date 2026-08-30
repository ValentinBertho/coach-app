package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Réglage des deux pourcentages d'une zone, en un seul geste.
 *
 * <p>Une zone est <b>une</b> définition physiologique exprimée en plusieurs unités : l'allure et la
 * vitesse d'un même seuil valent le même pourcentage de la même ancre. Les régler métrique par
 * métrique — ce que faisait le client — lançait autant de requêtes que d'unités, donc autant de
 * resynchronisations concurrentes sur les mêmes lignes : verrou optimiste, et 500.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ZonePercentagesRequest(
        @NotNull @PositiveOrZero @DecimalMax("500") Double lowPct,
        @NotNull @PositiveOrZero @DecimalMax("500") Double highPct
) {
}
