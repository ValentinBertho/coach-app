package com.coachrun.dto.response;

import com.coachrun.entity.LactateTest;
import com.coachrun.entity.enums.TestType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Détail d'un test lactate : valeurs au repos, seuils détectés et paliers (pour la courbe). */
public record LactateTestResponse(
        UUID id,
        TestType testType,
        LocalDate testDate,
        String notes,
        BigDecimal lactateRest,
        Integer hrRest,
        Integer hrMax,
        BigDecimal lt1Ms, BigDecimal lt2Ms,
        Integer fcLt1, Integer fcLt2,
        List<LactateTestStepResponse> steps
) {

    public static LactateTestResponse from(LactateTest t) {
        return new LactateTestResponse(
                t.getId(), t.getTestType(), t.getTestDate(), t.getNotes(),
                t.getLactateRest(), t.getHrRest(), t.getHrMax(),
                t.getLt1Ms(), t.getLt2Ms(), t.getFcLt1(), t.getFcLt2(),
                t.getSteps().stream().map(LactateTestStepResponse::from).toList());
    }

    /** Variante résumé (sans paliers) pour les listes. */
    public static LactateTestResponse summary(LactateTest t) {
        return new LactateTestResponse(
                t.getId(), t.getTestType(), t.getTestDate(), t.getNotes(),
                t.getLactateRest(), t.getHrRest(), t.getHrMax(),
                t.getLt1Ms(), t.getLt2Ms(), t.getFcLt1(), t.getFcLt2(), List.of());
    }
}
