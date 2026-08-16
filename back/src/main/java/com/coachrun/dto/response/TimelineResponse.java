package com.coachrun.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Le fil de l'athlète : ce qui lui est arrivé, dans l'ordre.
 *
 * <p>Rien de neuf n'est collecté ici. Tests, records, courses, blessures déclarées au débrief,
 * coupures et pics de charge existaient tous — éparpillés sur six écrans, si bien qu'aucun ne
 * répondait à « où on en est avec lui depuis six mois ».</p>
 */
public record TimelineResponse(LocalDate from, LocalDate to, List<Event> events) {

    /**
     * @param kind   TEST | RECORD | RACE | INJURY | UNAVAILABILITY | LOAD_PEAK | STRENGTH_TEST
     * @param tone   neutral | good | watch | alert — la couleur de la pastille, pas une note
     * @param link   route de l'application vers le détail, ou {@code null}
     */
    public record Event(
            LocalDate date,
            String kind,
            String tone,
            String title,
            String detail,
            UUID targetId,
            String link) {
    }
}
