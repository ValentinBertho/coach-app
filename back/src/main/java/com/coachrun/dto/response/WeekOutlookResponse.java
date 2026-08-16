package com.coachrun.dto.response;

import com.coachrun.engine.WeekOutlookEngine.Level;
import com.coachrun.engine.WeekOutlookEngine.WeekOutlook;

import java.time.LocalDate;

/**
 * La semaine prévue, jugée avant d'être courue.
 *
 * <p>{@code sentence} est le seul champ que l'interface a vraiment besoin d'afficher ; les autres
 * existent pour que la pastille et l'aide contextuelle puissent s'appuyer sur les chiffres plutôt
 * que de les réextraire du texte.</p>
 */
public record WeekOutlookResponse(
        LocalDate weekStart,
        int plannedLoadUa,
        double chronicWeeklyUa,
        Integer deltaPct,
        Double projectedAcwr,
        int hardSessions,
        Level level,
        String sentence
) {

    public static WeekOutlookResponse from(LocalDate weekStart, WeekOutlook o) {
        return new WeekOutlookResponse(weekStart, o.plannedLoadUa(), o.chronicWeeklyUa(),
                o.deltaPct(), o.projectedAcwr(), o.hardSessions(), o.level(), o.sentence());
    }
}
