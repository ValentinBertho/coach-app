package com.coachrun.service;

import com.coachrun.dto.response.ProgressionResponse;
import com.coachrun.dto.response.ProgressionResponse.AlertResponse;
import com.coachrun.dto.response.ProgressionResponse.ExerciseProgression;
import com.coachrun.dto.strength.StrengthBlock;
import com.coachrun.dto.strength.StrengthExerciseItem;
import com.coachrun.dto.strength.StrengthPrescription;
import com.coachrun.dto.strength.StrengthStructure;
import com.coachrun.engine.ProgressionEngine;
import com.coachrun.engine.ProgressionEngine.Alert;
import com.coachrun.engine.ProgressionEngine.DoneSet;
import com.coachrun.engine.ProgressionEngine.Suggestion;
import com.coachrun.entity.PpExercise;
import com.coachrun.entity.ScheduledStrengthSession;
import com.coachrun.entity.StrengthResult;
import com.coachrun.entity.enums.ExerciseCategory;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.PpExerciseRepository;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.StrengthResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Suggestion de progression auto + alertes coach d'une séance de force réalisée (cf. DARI Lab
 * §6.7 / §6.8). Compare les séries réalisées à la prescription figée et à la séance précédente.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgressionService {

    private final ScheduledStrengthSessionRepository scheduledRepository;
    private final StrengthResultRepository resultRepository;
    private final PpExerciseRepository exerciseRepository;
    private final AthleteRepository athleteRepository;
    private final ProgressionEngine engine;
    private final ObjectMapper objectMapper;

    /** Côté coach : valide l'appartenance de l'athlète au club. */
    public ProgressionResponse forCoach(UUID clubId, UUID athleteId, UUID scheduledId) {
        if (athleteRepository.findByIdAndClubId(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        return compute(requireScheduled(scheduledId, athleteId));
    }

    /** Côté athlète : scopé à l'athleteId du principal. */
    public ProgressionResponse forAthlete(UUID athleteId, UUID scheduledId) {
        return compute(requireScheduled(scheduledId, athleteId));
    }

    private ScheduledStrengthSession requireScheduled(UUID scheduledId, UUID athleteId) {
        return scheduledRepository.findByIdAndAthleteId(scheduledId, athleteId)
                .orElseThrow(() -> new NotFoundException("Séance de force introuvable."));
    }

    private ProgressionResponse compute(ScheduledStrengthSession scheduled) {
        Map<UUID, StrengthPrescription> prescriptions = parsePrescriptions(scheduled.getSessionSnapshot());

        // Séries réalisées groupées par exercice.
        Map<UUID, List<DoneSet>> doneByExercise = new LinkedHashMap<>();
        Map<UUID, Double> currentChargeByExercise = new LinkedHashMap<>();
        for (StrengthResult r : resultRepository
                .findByScheduledSessionIdOrderByExerciseIdAscSetNumberAsc(scheduled.getId())) {
            doneByExercise.computeIfAbsent(r.getExerciseId(), k -> new ArrayList<>())
                    .add(new DoneSet(
                            r.getRepsDone(),
                            r.getRirDone(),
                            r.getRpeDone() == null ? null : r.getRpeDone().doubleValue(),
                            r.getPain(),
                            r.getChargeKg() == null ? null : r.getChargeKg().doubleValue()));
            if (r.getChargeKg() != null) {
                currentChargeByExercise.merge(r.getExerciseId(), r.getChargeKg().doubleValue(), Math::max);
            }
        }

        List<ExerciseProgression> progressions = new ArrayList<>();
        List<AlertResponse> alerts = new ArrayList<>();

        for (Map.Entry<UUID, List<DoneSet>> e : doneByExercise.entrySet()) {
            UUID exerciseId = e.getKey();
            List<DoneSet> sets = e.getValue();
            PpExercise exercise = exerciseRepository.findById(exerciseId).orElse(null);
            String name = exercise != null ? exercise.getName() : exerciseId.toString().substring(0, 8);
            boolean isReath = exercise != null && exercise.getCategory() == ExerciseCategory.REATHLETISATION;

            StrengthPrescription p = prescriptions.get(exerciseId);
            int targetReps = p != null && p.repsFixed() != null ? p.repsFixed() : Integer.MAX_VALUE;
            Integer targetRir = p != null ? p.rirMin() : null;
            double currentCharge = currentChargeByExercise.getOrDefault(exerciseId, 0.0);
            Double previousCharge = previousCharge(scheduled, exerciseId);

            Suggestion s = engine.suggest(targetReps, targetRir, sets, currentCharge);
            progressions.add(new ExerciseProgression(exerciseId, name, s.recommended(), s.label(), s.deltaKg()));

            for (Alert a : engine.alerts(targetRir, isReath, sets, previousCharge, currentCharge)) {
                alerts.add(new AlertResponse(a.level().name(), a.code(), a.message(), exerciseId, name));
            }
        }

        return new ProgressionResponse(scheduled.getId(), progressions, alerts);
    }

    /** Charge de travail de la séance précédente pour cet exercice (hors séance courante). */
    private Double previousCharge(ScheduledStrengthSession scheduled, UUID exerciseId) {
        for (StrengthResult r : resultRepository
                .findByAthleteIdAndExerciseIdOrderByCreatedAtDesc(scheduled.getAthlete().getId(), exerciseId)) {
            if (!r.getScheduledSession().getId().equals(scheduled.getId()) && r.getChargeKg() != null) {
                return r.getChargeKg().doubleValue();
            }
        }
        return null;
    }

    private Map<UUID, StrengthPrescription> parsePrescriptions(String snapshotJson) {
        Map<UUID, StrengthPrescription> map = new LinkedHashMap<>();
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return map;
        }
        try {
            StrengthStructure structure = objectMapper.readValue(snapshotJson, StrengthStructure.class);
            for (StrengthBlock block : structure.blocks()) {
                for (StrengthExerciseItem item : block.exercises()) {
                    if (item.exerciseId() != null) {
                        map.putIfAbsent(item.exerciseId(), item.prescription());
                    }
                }
            }
        } catch (Exception ignored) {
            // Snapshot illisible : pas de prescription cible, suggestion/alertes dégradées.
        }
        return map;
    }
}
