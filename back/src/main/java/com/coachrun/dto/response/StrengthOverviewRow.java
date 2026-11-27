package com.coachrun.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Une ligne de la vue d'ensemble « Suivi &amp; Analyse » : où en est chaque athlète en force, sans
 * ouvrir sa fiche. Les deux fenêtres de 4 semaines donnent la tendance (charge mécanique).
 */
public record StrengthOverviewRow(
        UUID athleteId,
        int oneRmCount,
        Instant lastOneRmUpdate,
        LocalDate lastTestDate,
        int sessions4w,
        int sessionsPrev4w,
        BigDecimal mechanicalLoad4w,
        BigDecimal mechanicalLoadPrev4w) {
}
