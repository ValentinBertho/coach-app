package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Génération d'un mésocycle progressif à partir d'une semaine type. {@code increasePct},
 * {@code deloadEvery} et {@code deloadPct} sont optionnels (défauts : +10 %/sem, décharge toutes
 * les 4 semaines à 60 %).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerateMesocycleRequest(
        @NotNull LocalDate sourceWeekStart,
        @NotNull LocalDate firstWeekStart,
        @NotNull @Min(1) Integer weeks,
        Double increasePct,
        Integer deloadEvery,
        Double deloadPct) {

    public double increasePctOrDefault() {
        return increasePct != null ? increasePct : 10.0;
    }

    public int deloadEveryOrDefault() {
        return deloadEvery != null ? deloadEvery : 4;
    }

    public double deloadPctOrDefault() {
        return deloadPct != null ? deloadPct : 60.0;
    }
}
