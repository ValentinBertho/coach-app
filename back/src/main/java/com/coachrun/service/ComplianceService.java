package com.coachrun.service;

import com.coachrun.dto.response.ActivityLapsResponse;
import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.CalculatedSessionResponse.CalculatedBlockEntry;
import com.coachrun.dto.response.ComplianceResponse;
import com.coachrun.engine.ComplianceEngine;
import com.coachrun.engine.ComplianceEngine.PrescribedEffort;
import com.coachrun.engine.ComplianceEngine.RealizedLap;
import com.coachrun.entity.Activity;
import com.coachrun.entity.Workout;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.ActivityRepository;
import com.coachrun.repository.WorkoutRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * « Séance tenue ? » — le rapprochement des fourchettes prescrites et des tours réellement courus.
 *
 * <h2>Deux colonnes qui ne s'étaient jamais rencontrées</h2>
 *
 * <p>{@code Workout.calculatedPaces} porte, figées depuis l'assignation, les fourchettes d'allure de
 * chaque bloc calculées <b>pour cet athlète</b>. {@code Activity.lapsJson} porte les tours relevés
 * par sa montre. Les deux étaient en base, stockées côte à côte, et rien ne les confrontait : une
 * séance était validée sur son volume total, si bien qu'un 10 × 400 couru à l'allure du dimanche
 * comptait pour 100 %.</p>
 *
 * <p>Le calcul se fait <b>à la lecture</b>, comme le temps-en-zone : les fourchettes appartiennent
 * à la séance et ne bougent plus, mais la sortie rattachée, elle, peut changer (rapprochement
 * corrigé, tours réimportés). Stocker le score le figerait sur un appariement qui a pu être défait.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ComplianceService {

    private final WorkoutRepository workoutRepository;
    private final ActivityRepository activityRepository;
    private final ActivityService activityService;
    private final ComplianceEngine engine;
    private final ObjectMapper objectMapper;

    /** Conformité d'une séance vue par le coach (scopée club). */
    public ComplianceResponse forCoach(UUID clubId, UUID workoutId) {
        Workout w = workoutRepository.findByIdAndClubId(workoutId, clubId)
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        return compute(w);
    }

    /** Conformité d'une de mes séances (portail athlète). */
    public ComplianceResponse forAthlete(UUID athleteId, UUID workoutId) {
        Workout w = workoutRepository.findById(workoutId)
                .filter(x -> x.getAthlete().getId().equals(athleteId))
                .orElseThrow(() -> new NotFoundException("Séance introuvable."));
        return compute(w);
    }

    /**
     * Conformité d'une séance, ou une réponse vide quand l'un des deux côtés manque.
     *
     * <p>Vide, et non « 0 % » : une séance sans sortie rattachée n'est pas une séance ratée, et une
     * séance sans fourchette prescrite n'est pas jugeable. Un score qui punit l'absence de données
     * est un score que personne ne croit deux fois.</p>
     */
    public ComplianceResponse compute(Workout w) {
        CalculatedSessionResponse calc = readCalculated(w);
        if (calc == null) {
            return ComplianceResponse.empty();
        }
        Activity activity = activityRepository
                .findByAthleteIdAndMatchedWorkoutId(w.getAthlete().getId(), w.getId())
                .orElse(null);
        if (activity == null) {
            return ComplianceResponse.empty();
        }

        List<PrescribedEffort> prescribed = flatten(calc);
        if (prescribed.isEmpty()) {
            return ComplianceResponse.empty();
        }

        List<RealizedLap> laps = lapsOf(w, activity);
        if (laps.isEmpty()) {
            return ComplianceResponse.empty();
        }

        var result = engine.evaluate(prescribed, laps);
        return ComplianceResponse.from(result, activity.getId());
    }

    /**
     * Les efforts prescrits, à plat et dans l'ordre — <b>corps de séance seulement</b>.
     *
     * <p>Échauffement et retour au calme sont exclus volontairement : personne ne juge une séance
     * sur son échauffement, et les inclure ferait chuter le score de toute séance dont on a démarré
     * l'échauffement plus vite que prescrit, ce que tout le monde fait.</p>
     *
     * <p>Un bloc à répétitions produit autant d'efforts que de répétitions : c'est ce qui permet de
     * dire « les deux dernières ont dérivé » plutôt que « le bloc n'est pas tenu ».</p>
     */
    private List<PrescribedEffort> flatten(CalculatedSessionResponse calc) {
        List<PrescribedEffort> out = new ArrayList<>();
        for (CalculatedBlockEntry entry : calc.main()) {
            if (entry.block() == null || entry.calc() == null || !entry.calc().computable()) {
                continue;
            }
            var b = entry.block();
            var c = entry.calc();
            int reps = b.repCount() * b.setCount();
            // La durée/distance calculée porte déjà les répétitions : on redescend à l'unité, sans
            // quoi chaque effort serait comparé au volume du bloc entier.
            Integer unitDistance = b.distanceM();
            Integer unitDuration = b.durationS() != null ? b.durationS()
                    : (c.estimatedDurationS() != null && reps > 0 ? c.estimatedDurationS() / reps : null);

            for (int i = 1; i <= reps; i++) {
                String label = reps > 1 ? label(b) + " (" + i + "/" + reps + ")" : label(b);
                out.add(new PrescribedEffort(label,
                        c.paceMinSecPerKm(), c.paceMaxSecPerKm(), unitDistance, unitDuration));
            }
        }
        return out;
    }

    private String label(com.coachrun.dto.session.CourseBlock b) {
        if (b.distanceM() != null) {
            return b.distanceM() >= 1000
                    ? (b.distanceM() / 1000.0 == Math.floor(b.distanceM() / 1000.0)
                        ? (b.distanceM() / 1000) + " km" : b.distanceM() + " m")
                    : b.distanceM() + " m";
        }
        if (b.durationS() != null) {
            return (b.durationS() / 60) + " min";
        }
        return b.type() == null ? "Bloc" : b.type();
    }

    /**
     * Les tours de la sortie : ceux de la montre s'ils existent, les splits kilométriques sinon.
     *
     * <p>Les tours de montre valent toujours mieux : ce sont les répétitions telles qu'elles ont
     * été courues. Les splits kilométriques ne les remplacent pas — sur un 10 × 400, ils décrivent
     * une découpe qui n'a rien à voir avec la séance — mais ils permettent au moins de juger un
     * seuil ou une sortie longue, où le kilomètre est l'unité naturelle.</p>
     */
    private List<RealizedLap> lapsOf(Workout w, Activity activity) {
        ActivityLapsResponse laps = activityService.laps(
                w.getClub().getId(), activity.getId(), ActivityLapsResponse.Kind.DEVICE);
        if (laps == null || laps.laps().isEmpty()) {
            laps = activityService.laps(
                    w.getClub().getId(), activity.getId(), ActivityLapsResponse.Kind.SPLIT);
        }
        if (laps == null || laps.laps().isEmpty()) {
            return List.of();
        }
        return laps.laps().stream()
                .map(l -> new RealizedLap(l.index(), l.distanceM(), l.durationS(), l.paceSPerKm(),
                        l.avgHr()))
                .toList();
    }

    private CalculatedSessionResponse readCalculated(Workout w) {
        if (w.getCalculatedPaces() == null || w.getCalculatedPaces().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(w.getCalculatedPaces(), CalculatedSessionResponse.class);
        } catch (Exception e) {
            log.debug("Cibles calculées illisibles pour la séance {}", w.getId());
            return null;
        }
    }
}
