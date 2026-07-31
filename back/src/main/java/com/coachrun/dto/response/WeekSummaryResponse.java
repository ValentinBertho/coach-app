package com.coachrun.dto.response;

import java.time.LocalDate;

/**
 * Récapitulatif chiffré de la semaine en cours pour l'athlète : « 32/45 km, 3 séances sur 5 ».
 *
 * <p>Un athlète qui ouvre son espace veut savoir où il en est de sa semaine, pas parcourir un
 * graphe sur 8 semaines. Les deux chiffres tiennent en une ligne et se lisent d'un coup d'œil.</p>
 *
 * @param weekStart         lundi de la semaine en cours (fuseau métier)
 * @param plannedKm         kilomètres prescrits sur la semaine
 * @param realizedKm        kilomètres réalisés (activités importées ou saisies)
 * @param plannedSessions   séances prescrites
 * @param completedSessions séances réalisées (COMPLETED ou PARTIAL)
 */
public record WeekSummaryResponse(
        LocalDate weekStart,
        double plannedKm,
        double realizedKm,
        int plannedSessions,
        int completedSessions) {
}
