package com.coachrun.service;

import com.coachrun.dto.request.LactateDetectRequest;
import com.coachrun.dto.request.LactateTestRequest;
import com.coachrun.dto.request.LactateTestStepRequest;
import com.coachrun.dto.response.LTDetectionResponse;
import com.coachrun.dto.response.LactateTestResponse;
import com.coachrun.engine.LactateThresholdEngine;
import com.coachrun.engine.LactateThresholdEngine.LTDetectionResult;
import com.coachrun.engine.LactateThresholdEngine.StepPoint;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.LactateTest;
import com.coachrun.entity.LactateTestStep;
import com.coachrun.entity.enums.TestType;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.LactateTestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Tests lactate : saisie des paliers, détection LT1/LT2 (Dmax modifié) et mise à jour optionnelle
 * du profil physio de l'athlète. Scoping tenant systématique (anti-IDOR).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LactateTestService {

    private final LactateTestRepository testRepository;
    private final AthleteRepository athleteRepository;
    private final LactateThresholdEngine engine;

    /** Détection temps réel (sans persistance) pour l'UI de saisie. */
    public LTDetectionResponse detect(UUID clubId, UUID athleteId, LactateDetectRequest req) {
        requireAthlete(clubId, athleteId);
        LTDetectionResult r = engine.detect(toPoints(req.steps()), toDouble(req.lactateRest()));
        return LTDetectionResponse.from(r);
    }

    public List<LactateTestResponse> list(UUID clubId, UUID athleteId) {
        requireAthlete(clubId, athleteId);
        return testRepository.findByClubIdAndAthleteIdOrderByTestDateDesc(clubId, athleteId).stream()
                .map(LactateTestResponse::summary)
                .toList();
    }

    public LactateTestResponse get(UUID clubId, UUID testId) {
        return LactateTestResponse.from(requireTest(clubId, testId));
    }

    /** Liste — variante athlète-scopée (portail /me, lecture seule). */
    public List<LactateTestResponse> listForAthlete(UUID athleteId) {
        return testRepository.findByAthleteIdOrderByTestDateDesc(athleteId).stream()
                .map(LactateTestResponse::summary)
                .toList();
    }

    /** Détail (avec paliers pour la courbe) — variante athlète-scopée. */
    public LactateTestResponse getForAthlete(UUID athleteId, UUID testId) {
        return LactateTestResponse.from(testRepository.findByIdAndAthleteId(testId, athleteId)
                .orElseThrow(() -> new NotFoundException("Test introuvable.")));
    }

    @Transactional
    public LactateTestResponse create(UUID clubId, UUID athleteId, LactateTestRequest req) {
        Athlete a = requireAthlete(clubId, athleteId);

        LactateTest test = new LactateTest();
        test.setClub(a.getClub());
        test.setAthlete(a);
        test.setTestType(req.testType() != null ? req.testType() : TestType.LACTATE);
        test.setTestDate(req.testDate());
        test.setNotes(req.notes());
        test.setLactateRest(req.lactateRest());
        test.setHrRest(req.hrRest());
        test.setHrMax(req.hrMax());
        test.replaceSteps(req.steps().stream().map(this::toStep).toList());

        LTDetectionResult r = engine.detect(toPoints(req.steps()), toDouble(req.lactateRest()));
        test.setLt1Ms(bd(r.lt1Ms()));
        test.setLt2Ms(bd(r.lt2Ms()));
        test.setFcLt1(r.fcLt1());
        test.setFcLt2(r.fcLt2());

        test = testRepository.save(test);

        // Mise à jour du profil physio (défaut : vrai).
        if (req.applyToProfile() == null || req.applyToProfile()) {
            if (r.lt1Ms() != null) {
                a.setLt1Ms(bd(r.lt1Ms()));
            }
            if (r.lt2Ms() != null) {
                a.setLt2Ms(bd(r.lt2Ms()));
            }
            if (r.fcLt1() != null) {
                a.setFcLt1(r.fcLt1());
            }
            if (r.fcLt2() != null) {
                a.setFcLt2(r.fcLt2());
            }
        }
        log.info("Test lactate {} enregistré (athlète={}, LT2={})", test.getId(), athleteId, r.lt2Ms());
        return LactateTestResponse.from(test);
    }

    @Transactional
    public void delete(UUID clubId, UUID testId) {
        testRepository.delete(requireTest(clubId, testId));
    }

    // --- Helpers --------------------------------------------------------------

    private LactateTestStep toStep(LactateTestStepRequest req) {
        LactateTestStep step = new LactateTestStep();
        step.setSpeedMs(req.speedMs());
        step.setHr(req.hr());
        step.setLactate(req.lactate());
        step.setRpe(req.rpe());
        step.setDurationS(req.durationS());
        return step;
    }

    private List<StepPoint> toPoints(List<LactateTestStepRequest> steps) {
        return steps.stream()
                .map(s -> new StepPoint(
                        s.speedMs() == null ? 0 : s.speedMs().doubleValue(),
                        toDouble(s.lactate()), s.hr()))
                .toList();
    }

    private BigDecimal bd(Double v) {
        return v == null ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private Athlete requireAthlete(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }

    private LactateTest requireTest(UUID clubId, UUID testId) {
        return testRepository.findByIdAndClubId(testId, clubId)
                .orElseThrow(() -> new NotFoundException("Test introuvable."));
    }
}
