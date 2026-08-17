package com.coachrun.dto.response;

import com.coachrun.engine.PlanBuilderEngine.Phase;
import com.coachrun.engine.PlanBuilderEngine.Plan;
import com.coachrun.entity.RaceObjective;

import java.time.LocalDate;
import java.util.List;

/**
 * La trame d'un plan, telle que le coach la lit avant de décider de la poser.
 *
 * <p>Le même objet sert d'aperçu et de compte rendu : {@code applied} dit lequel des deux. Deux
 * formes distinctes obligeraient l'interface à traiter deux fois la même liste de semaines.</p>
 *
 * @param warning ce qui empêcherait la pose, ou {@code null} — affiché avant le bouton, jamais après
 */
public record PlanPreviewResponse(
        boolean applied,
        String raceName,
        LocalDate raceDate,
        LocalDate sourceWeekStart,
        int sourceSessions,
        String summary,
        String warning,
        int createdSessions,
        int skippedWeeks,
        List<Week> weeks
) {

    /**
     * @param volumeFactor multiplicateur appliqué au volume de la semaine de référence
     * @param skipped      la semaine tombe dans une indisponibilité déclarée : elle reste visible
     *                     dans la trame, mais ne recevra aucune séance
     */
    public record Week(int index, LocalDate monday, Phase phase, String label,
                       double volumeFactor, boolean skipped) {
    }

    public static PlanPreviewResponse from(Plan plan, RaceObjective race, LocalDate sourceWeek,
                                           int sourceSessions, String warning) {
        return new PlanPreviewResponse(false, race.getName(), race.getRaceDate(),
                sourceWeek, sourceSessions, plan.summary(), warning, 0, 0, weeks(plan));
    }

    public static PlanPreviewResponse applied(Plan plan, RaceObjective race, LocalDate sourceWeek,
                                              int created, int skipped) {
        return new PlanPreviewResponse(true, race.getName(), race.getRaceDate(),
                sourceWeek, 0, plan.summary(), null, created, skipped, weeks(plan));
    }

    private static List<Week> weeks(Plan plan) {
        return plan.weeks().stream()
                .map(w -> new Week(w.index(), w.monday(), w.phase(), w.label(),
                        w.volumeFactor(), w.skipped()))
                .toList();
    }
}
