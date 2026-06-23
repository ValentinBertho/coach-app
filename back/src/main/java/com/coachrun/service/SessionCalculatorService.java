package com.coachrun.service;

import com.coachrun.dto.request.SessionCalcRequest;
import com.coachrun.dto.response.CalculatedBlockResponse;
import com.coachrun.engine.SessionCalculatorEngine;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteVdotPace;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.AthleteVdotPaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Calcule les cibles d'un bloc de séance pour un athlète donné, en assemblant son profil
 * physiologique (seuils) et ses allures d'équivalence VDOT, puis en déléguant au moteur.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionCalculatorService {

    private final AthleteRepository athleteRepository;
    private final AthleteVdotPaceRepository vdotPaceRepository;
    private final SessionCalculatorEngine engine;

    public CalculatedBlockResponse calculate(UUID clubId, UUID athleteId, SessionCalcRequest req) {
        Athlete a = athleteRepository.findByIdAndClubId(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        AthleteVdotPace paces = vdotPaceRepository.findByAthleteId(athleteId).orElse(null);

        SessionCalculatorEngine.AthletePaceContext ctx = new SessionCalculatorEngine.AthletePaceContext(
                toDouble(a.getLt1Ms()), toDouble(a.getLt2Ms()), toDouble(a.getVcMs()),
                a.getFcLt1(), a.getFcLt2(), a.getHrMax(),
                pct(a.getFcDomain1Pct(), 80), pct(a.getFcDomain2Pct(), 90),
                paces == null ? null : paces.getPace800mS(),
                paces == null ? null : paces.getPace1500mS(),
                paces == null ? null : paces.getPace3000mS(),
                paces == null ? null : paces.getPace5kmS(),
                paces == null ? null : paces.getPace10kmS(),
                paces == null ? null : paces.getPace15kmS(),
                paces == null ? null : paces.getPaceSemiS(),
                paces == null ? null : paces.getPaceMarathonS());

        SessionCalculatorEngine.PrescriptionInput input = new SessionCalculatorEngine.PrescriptionInput(
                req.ref(), req.minPct(), req.maxPct(), req.reps(), req.distanceM(), req.durationS());

        return CalculatedBlockResponse.from(engine.calculate(input, ctx));
    }

    private Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private double pct(BigDecimal v, double fallback) {
        return v == null ? fallback : v.doubleValue();
    }
}
