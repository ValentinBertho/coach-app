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

import java.time.LocalDate;
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
        if (athleteRepository.findByIdAndClubMembership(athleteId, clubId).isEmpty()) {
            throw new NotFoundException("Athlète introuvable.");
        }
        return compute(requireScheduled(scheduledId, athleteId));
    }

    /**
     * Alertes de force des séances récentes d'un athlète, tous exercices confondus.
     *
     * <p>Les alertes du §6.8 — douleur, RPE proche de l'échec, RIR nul, charge sous la prescription
     * — n'existaient que séance par séance, derrière un appel que le coach devait déclencher à la
     * main. Pour vingt-cinq athlètes faisant deux séances de force par semaine, il aurait fallu
     * ouvrir une cinquantaine de séances chaque semaine pour les voir. Autant dire qu'elles
     * n'atteignaient personne.</p>
     */
    public List<AlertResponse> recentAlerts(UUID athleteId, LocalDate since) {
        List<AlertResponse> out = new ArrayList<>();
        for (ScheduledStrengthSession s : scheduledRepository
                .findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        athleteId, since, LocalDate.now())) {
            if (!s.isCompleted()) {
                continue;
            }
            out.addAll(compute(s).alerts());
        }
        return out;
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
            Double reference = prescribedCharge(scheduled, exerciseId);

            Suggestion s = engine.suggest(targetReps, targetRir, sets, currentCharge);
            progressions.add(new ExerciseProgression(exerciseId, name, s.recommended(), s.label(), s.deltaKg()));

            for (Alert a : engine.alerts(targetRir, isReath, sets, reference, currentCharge)) {
                alerts.add(new AlertResponse(a.level().name(), a.code(), a.message(), exerciseId, name));
            }
        }

        return new ProgressionResponse(scheduled.getId(), progressions, alerts);
    }

    /**
     * Charge <b>prescrite</b> pour cet exercice dans cette séance (borne haute de la fourchette).
     *
     * <p>L'alerte « chute de charge » comparait auparavant le maximum soulevé aujourd'hui à la
     * <em>dernière série enregistrée</em> de la séance précédente — deux agrégats différents. Un
     * drop-set en fin de séance précédente abaissait donc la référence, et surtout une semaine de
     * décharge programmée par l'application elle-même (−40 % appliqués à la prescription) levait
     * mécaniquement une alerte de niveau haut sur chaque exercice. Le coach recevait une alerte
     * provoquée par sa propre planification.</p>
     *
     * <p>La référence est désormais ce que la séance demandait. Un deload prescrit moins, l'athlète
     * soulève ce qui est prescrit, et le rapport reste à 1 : plus d'alerte. À l'inverse, un athlète
     * qui n'arrive pas à tenir la charge du jour est signalé, ce qui est bien le fait à remonter.</p>
     */
    private Double prescribedCharge(ScheduledStrengthSession scheduled, UUID exerciseId) {
        if (scheduled.getCalculatedCharges() == null || scheduled.getCalculatedCharges().isBlank()) {
            return null;
        }
        try {
            var calc = objectMapper.readValue(scheduled.getCalculatedCharges(),
                    com.coachrun.dto.response.CalculatedStrengthResponse.class);
            for (var block : calc.blocks()) {
                for (var exercise : block.exercises()) {
                    if (exerciseId.equals(exercise.item().exerciseId())
                            && exercise.charge() != null && exercise.charge().computable()
                            && exercise.charge().kgMax() != null && exercise.charge().kgMax() > 0) {
                        return exercise.charge().kgMax();
                    }
                }
            }
        } catch (Exception ignored) {
            // Charges calculées illisibles : pas de référence, l'alerte de charge ne se déclenche pas.
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
