package com.coachrun.dto.response;

import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.FormStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Vue agrégée d'un groupe pour le coach : répartition de l'état de forme, charge moyenne,
 * volume prévu/réalisé et adhérence, plus le détail par athlète.
 */
public record GroupAnalyticsResponse(
        UUID groupId,
        String name,
        int athleteCount,
        FormDistribution form,
        Aggregate totals,
        List<Row> athletes) {

    /** Répartition de l'état de forme (fatigue + douleur) sur le groupe. */
    public record FormDistribution(int green, int orange, int red) {
    }

    /** Agrégats du groupe sur la fenêtre demandée. */
    public record Aggregate(Double avgAcwr, double plannedKm, double realizedKm, Integer compliancePct) {
    }

    /** Ligne par athlète. */
    public record Row(
            UUID id,
            String firstName,
            String lastName,
            Discipline discipline,
            FormStatus formStatus,
            Integer fatigue,
            Integer pain,
            Double acwr,
            double plannedKm,
            double realizedKm,
            Integer compliancePct,
            LocalDate lastFeedbackDate) {
    }
}
