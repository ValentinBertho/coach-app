package com.coachrun.dto.response;

import com.coachrun.engine.OneRmEngine.WorkZone;
import com.coachrun.entity.enums.RmFormula;

import java.util.List;

/** Résultat d'un calcul de 1RM : e1RM, formule utilisée et zones de travail Lacourpaille. */
public record E1rmResponse(
        double e1rm,
        RmFormula formula,
        List<WorkZone> zones
) {
}
