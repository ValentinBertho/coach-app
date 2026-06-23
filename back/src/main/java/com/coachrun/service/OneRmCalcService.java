package com.coachrun.service;

import com.coachrun.dto.request.E1rmRequest;
import com.coachrun.dto.response.E1rmResponse;
import com.coachrun.engine.OneRmEngine;
import com.coachrun.entity.enums.RmFormula;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Calcul du e1RM (formules + zones de travail) — délègue au moteur, sans persistance. */
@Service
@RequiredArgsConstructor
public class OneRmCalcService {

    private final OneRmEngine engine;

    public E1rmResponse compute(E1rmRequest req) {
        RmFormula formula = req.formula() != null ? req.formula() : RmFormula.NUZZO;
        Integer rir = req.rir();
        if (rir == null && req.rpe() != null) {
            rir = engine.rirFromRpe(req.rpe());
        }
        double e1rm = engine.estimate(req.weight(), req.reps(), rir, formula);
        double rounded = Math.round(e1rm * 100.0) / 100.0;
        return new E1rmResponse(rounded, formula, engine.zones(rounded));
    }
}
