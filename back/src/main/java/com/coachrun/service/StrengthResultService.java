package com.coachrun.service;

import com.coachrun.dto.request.StrengthResultRequest;
import com.coachrun.dto.response.E1rmHistoryResponse;
import com.coachrun.engine.OneRmEngine;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.Athlete1rmProfile;
import com.coachrun.entity.EstimatedOneRm;
import com.coachrun.entity.ScheduledStrengthSession;
import com.coachrun.entity.StrengthResult;
import com.coachrun.entity.enums.RmFormula;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.Athlete1rmProfileRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.EstimatedOneRmRepository;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.StrengthResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Retour par exercice (séries réalisées) avec <strong>recalcul automatique du e1RM</strong>
 * (cf. DARI Lab §6.5). Le meilleur e1RM d'une séance met à jour le profil 1RM (source
 * {@code estimated}) s'il dépasse le courant ; un test direct ({@code tested}) prévaut toujours.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrengthResultService {

    private final ScheduledStrengthSessionRepository scheduledRepository;
    private final StrengthResultRepository resultRepository;
    private final EstimatedOneRmRepository estimatedRepository;
    private final Athlete1rmProfileRepository profileRepository;
    private final AthleteRepository athleteRepository;
    private final OneRmEngine oneRmEngine;

    /** Enregistre les séries réalisées d'une séance et met à jour le e1RM par exercice. */
    @Transactional
    public List<E1rmHistoryResponse> submit(UUID athleteId, UUID scheduledId,
                                            List<StrengthResultRequest> entries) {
        ScheduledStrengthSession scheduled = scheduledRepository.findByIdAndAthleteId(scheduledId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance de force introuvable."));
        Athlete athlete = scheduled.getAthlete();

        // Meilleur e1RM par exercice (et la série source associée).
        Map<UUID, BestSet> bestByExercise = new LinkedHashMap<>();

        for (StrengthResultRequest e : entries) {
            StrengthResult r = new StrengthResult();
            r.setScheduledSession(scheduled);
            r.setAthlete(athlete);
            r.setExerciseId(e.exerciseId());
            r.setSetNumber(e.setNumber());
            r.setChargeKg(e.chargeKg());
            r.setRepsDone(e.repsDone());
            r.setDurationSecDone(e.durationSecDone());
            r.setRpeDone(e.rpeDone());
            r.setRirDone(e.rirDone());
            r.setPain(e.pain());
            r.setComment(e.comment());
            r = resultRepository.save(r);

            Double e1rm = estimate(e);
            if (e1rm != null) {
                BestSet current = bestByExercise.get(e.exerciseId());
                if (current == null || e1rm > current.e1rm) {
                    bestByExercise.put(e.exerciseId(), new BestSet(e1rm, e, r.getId()));
                }
            }
        }

        List<E1rmHistoryResponse> updates = new ArrayList<>();
        for (Map.Entry<UUID, BestSet> ex : bestByExercise.entrySet()) {
            updates.add(applyBest(athlete, ex.getKey(), ex.getValue()));
        }
        return updates;
    }

    public List<E1rmHistoryResponse> history(UUID clubId, UUID athleteId, UUID exerciseId) {
        if (athleteRepository.findByIdAndClubId(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        return estimatedRepository.findByAthleteIdAndExerciseIdOrderByCreatedAtAsc(athleteId, exerciseId)
                .stream().map(E1rmHistoryResponse::from).toList();
    }

    // --- Recalcul -------------------------------------------------------------

    private Double estimate(StrengthResultRequest e) {
        if (e.chargeKg() == null || e.repsDone() == null || e.repsDone() <= 0) {
            return null;
        }
        double charge = e.chargeKg().doubleValue();
        if (e.rirDone() != null) {
            return oneRmEngine.rirBased1RM(charge, e.repsDone(), e.rirDone());
        }
        return oneRmEngine.nuzzo1RM(charge, e.repsDone());
    }

    private E1rmHistoryResponse applyBest(Athlete athlete, UUID exerciseId, BestSet best) {
        BigDecimal e1rm = BigDecimal.valueOf(best.e1rm).setScale(2, RoundingMode.HALF_UP);
        RmFormula formula = best.request.rirDone() != null ? RmFormula.RIR_BASED : RmFormula.NUZZO;
        String rpeOrRir = best.request.rirDone() != null ? "RIR" + best.request.rirDone()
                : (best.request.rpeDone() != null ? "RPE" + best.request.rpeDone() : null);

        // Historique
        EstimatedOneRm hist = new EstimatedOneRm();
        hist.setAthlete(athlete);
        hist.setExerciseId(exerciseId);
        hist.setSourceResultId(best.resultId);
        hist.setChargeKg(best.request.chargeKg());
        hist.setReps(best.request.repsDone());
        hist.setRpeOrRir(rpeOrRir);
        hist.setFormulaUsed(formula);
        hist.setE1rmKg(e1rm);
        estimatedRepository.save(hist);

        // Mise à jour du profil (sauf si un test direct prévaut)
        Athlete1rmProfile profile = profileRepository
                .findByAthleteIdAndExerciseId(athlete.getId(), exerciseId).orElse(null);
        if (profile == null) {
            profile = new Athlete1rmProfile();
            profile.setAthlete(athlete);
            profile.setExerciseId(exerciseId);
            profile.setRmKg(e1rm);
            profile.setSource("estimated");
            profileRepository.save(profile);
        } else if (!"tested".equals(profile.getSource())
                && e1rm.compareTo(profile.getRmKg()) > 0) {
            profile.setRmKg(e1rm);
            profile.setSource("estimated");
        }
        log.info("e1RM recalculé athlète={} exercice={} → {} kg", athlete.getId(), exerciseId, e1rm);
        return E1rmHistoryResponse.from(hist);
    }

    private record BestSet(double e1rm, StrengthResultRequest request, UUID resultId) {
    }
}
