package com.coachrun.service;

import com.coachrun.dto.request.StrengthTestRequest;
import com.coachrun.dto.response.StrengthTestResponse;
import com.coachrun.engine.OneRmEngine;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Athlete1rmProfile;
import com.coachrun.entity.EstimatedOneRm;
import com.coachrun.entity.StrengthTest;
import com.coachrun.entity.enums.RmFormula;
import com.coachrun.exception.ApiException;
import com.coachrun.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import com.coachrun.repository.Athlete1rmProfileRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.EstimatedOneRmRepository;
import com.coachrun.repository.PpExerciseRepository;
import com.coachrun.repository.StrengthTestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tests de force (4 protocoles, cf. DARI Lab §6.5). Un test direct dérive le e1RM selon son
 * protocole, l'historise et met à jour le profil 1RM avec la source {@code tested}, qui prévaut
 * toujours sur une estimation issue des séances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrengthTestService {

    private final StrengthTestRepository testRepository;
    private final AthleteRepository athleteRepository;
    private final PpExerciseRepository exerciseRepository;
    private final Athlete1rmProfileRepository profileRepository;
    private final EstimatedOneRmRepository estimatedRepository;
    private final OneRmEngine oneRmEngine;

    public List<StrengthTestResponse> list(UUID clubId, UUID athleteId, UUID exerciseId) {
        requireAthlete(clubId, athleteId);
        List<StrengthTest> tests = exerciseId != null
                ? testRepository.findByAthleteIdAndExerciseIdOrderByTestDateDesc(athleteId, exerciseId)
                : testRepository.findByAthleteIdOrderByTestDateDesc(athleteId);
        return tests.stream().map(StrengthTestResponse::from).toList();
    }

    @Transactional
    public StrengthTestResponse record(UUID clubId, UUID athleteId, StrengthTestRequest req) {
        Athlete athlete = requireAthlete(clubId, athleteId);
        if (exerciseRepository.findByIdAndClubId(req.exerciseId(), clubId).isEmpty()) {
            throw new NotFoundException("Exercice introuvable.");
        }
        if (req.weightKg() == null || req.weightKg() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La charge (ou la valeur mesurée) est requise.");
        }

        double e1rm = oneRmEngine.e1rmForTest(req.protocol(), req.weightKg(), req.reps());
        BigDecimal e1rmKg = BigDecimal.valueOf(e1rm).setScale(2, RoundingMode.HALF_UP);

        StrengthTest test = new StrengthTest();
        test.setAthlete(athlete);
        test.setExerciseId(req.exerciseId());
        test.setProtocol(req.protocol());
        test.setTestDate(Optional.ofNullable(req.testDate()).orElse(LocalDate.now()));
        test.setWeightKg(BigDecimal.valueOf(req.weightKg()));
        test.setReps(req.reps());
        test.setDurationSec(req.durationSec());
        test.setRir(req.rir());
        test.setComputedE1rmKg(e1rmKg);
        test.setNotes(req.notes());
        test = testRepository.save(test);

        historise(athlete, req, e1rmKg);
        applyToProfile(athlete, req.exerciseId(), e1rmKg);

        log.info("Test 1RM {} enregistré (athlète={}, exercice={}, e1RM={} kg)",
                req.protocol(), athleteId, req.exerciseId(), e1rmKg);
        return StrengthTestResponse.from(test);
    }

    private void historise(Athlete athlete, StrengthTestRequest req, BigDecimal e1rmKg) {
        EstimatedOneRm hist = new EstimatedOneRm();
        hist.setAthlete(athlete);
        hist.setExerciseId(req.exerciseId());
        hist.setChargeKg(BigDecimal.valueOf(req.weightKg()));
        hist.setReps(req.reps() == null ? 1 : req.reps());
        hist.setRpeOrRir("TEST");
        hist.setFormulaUsed(RmFormula.NUZZO);
        hist.setE1rmKg(e1rmKg);
        estimatedRepository.save(hist);
    }

    /** Un test direct écrase toujours le profil (source {@code tested}). */
    private void applyToProfile(Athlete athlete, UUID exerciseId, BigDecimal e1rmKg) {
        Athlete1rmProfile profile = profileRepository
                .findByAthleteIdAndExerciseId(athlete.getId(), exerciseId)
                .orElseGet(() -> {
                    Athlete1rmProfile p = new Athlete1rmProfile();
                    p.setAthlete(athlete);
                    p.setExerciseId(exerciseId);
                    return p;
                });
        profile.setRmKg(e1rmKg);
        profile.setSource("tested");
        profileRepository.save(profile);
    }

    private Athlete requireAthlete(UUID clubId, UUID athleteId) {
        return athleteRepository.findByIdAndClubMembership(athleteId, clubId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
    }
}
