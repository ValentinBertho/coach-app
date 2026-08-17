package com.coachrun.service;

import com.coachrun.dto.response.CalculatedSessionResponse;
import com.coachrun.dto.response.ReadinessResponse;
import com.coachrun.dto.session.CourseBlock;
import com.coachrun.dto.session.SessionStructure;
import com.coachrun.engine.SessionAdaptationEngine;
import com.coachrun.engine.SessionAdaptationEngine.Advice;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteProposal;
import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.ProposalStatus;
import com.coachrun.entity.enums.ProposalType;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;
import com.coachrun.exception.ConflictException;
import com.coachrun.exception.NotFoundException;
import com.coachrun.repository.AthleteProposalRepository;
import com.coachrun.repository.AthleteRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le feu vert du matin.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>L'athlète déclarait fatigue 8 et douleur 4 à sept heures ; à dix-huit heures, il lisait
 * « 6 × 1000 m à 3:45 ». Personne ne lui avait dit quoi faire de sa fatigue. Le check-in produisait
 * une pastille pour le coach, une alerte si la douleur était haute, et <b>rien pour celui qui
 * l'avait rempli</b>. C'est le meilleur moyen de faire cesser une saisie quotidienne.
 *
 * <h2>Ce qu'il ne fait pas</h2>
 *
 * <p>Il ne touche pas au calendrier. Il conseille, montre à quoi ressemblerait l'adaptation, et si
 * l'athlète la veut, il crée une <b>demande</b> que le coach accepte ou refuse
 * ({@link ProposalService}). Le produit n'écrit jamais une séance que personne n'a validée : c'est
 * la règle, et elle vaut aussi — surtout — quand l'application a raison.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadinessService {

    private final AthleteRepository athleteRepository;
    private final WorkoutRepository workoutRepository;
    private final AthleteProposalRepository proposalRepository;
    private final AthleteFeedbackService feedbackService;
    private final AthleteLoadService loadService;
    private final DailyCheckInService checkInService;
    private final WorkoutService workoutService;
    private final SessionAdaptationEngine engine;
    private final ProposalService proposalService;
    private final ClockService clock;

    /** Le conseil du jour pour un athlète — lecture seule, aucun effet de bord. */
    public ReadinessResponse forAthlete(UUID athleteId) {
        LocalDate today = clock.today();
        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        boolean checkInDone = checkInService.forDay(athleteId, today) != null;

        Workout workout = mainSessionOf(athleteId, today);
        if (workout == null) {
            return ReadinessResponse.nothingPlanned(checkInDone);
        }

        var last = feedbackService.lastFeedback(athleteId);
        // Un signal périmé ne décrit pas l'état d'aujourd'hui : mieux vaut ne rien conseiller que
        // de conseiller à partir d'une fatigue déclarée il y a trois semaines.
        Integer fatigue = last.isFresh(today) ? last.fatigue() : null;
        Integer pain = last.isFresh(today) ? last.pain() : null;

        Double acwr = null;
        try {
            acwr = loadService.load(athlete.getClub().getId(), athleteId).ratio();
        } catch (RuntimeException ignored) {
            // Une charge incalculable ne doit pas priver l'athlète de son conseil du matin.
        }

        var prescription = safePrescription(athlete.getClub().getId(), workout.getId());
        Integer hardestRpe = hardestRpe(prescription == null ? null : prescription.snapshot(), workout);

        var recommendation = engine.recommend(fatigue, pain, acwr, hardestRpe, true);

        String planned = summarize(prescription == null ? null : prescription.snapshot());
        String adapted = adaptedSummary(recommendation.advice(), prescription);

        return new ReadinessResponse(
                recommendation.advice(), recommendation.severity(),
                recommendation.title(), recommendation.reason(),
                workout.getId(), workout.getTitle(), planned, adapted,
                hasPendingRequest(athleteId, workout.getId()), checkInDone,
                actionsFor(recommendation.advice()));
    }

    /**
     * L'athlète demande l'adaptation conseillée. Cela ne modifie <b>rien</b> : cela crée une
     * demande, notifiée au coach référent, qu'il tranche en un tap.
     *
     * @param advice l'adaptation demandée, qui doit être celle que le moteur a conseillée — on
     *               n'ouvre pas une porte à « je décale ma séance dure tous les mardis »
     */
    @Transactional
    public ReadinessResponse request(UUID athleteId, Advice advice) {
        ReadinessResponse current = forAthlete(athleteId);
        if (current.workoutId() == null) {
            throw new ConflictException("Aucune séance à adapter aujourd'hui.");
        }
        if (current.advice() == Advice.KEEP) {
            throw new ConflictException("Rien ne justifie d'adapter cette séance aujourd'hui.");
        }
        if (advice != null && advice != current.advice()) {
            throw new ConflictException("Cette adaptation n'est pas celle qui est conseillée.");
        }
        if (current.pendingRequest()) {
            return current;
        }

        Athlete athlete = athleteRepository.findById(athleteId)
                .orElseThrow(() -> new NotFoundException("Athlète introuvable."));
        LocalDate today = clock.today();

        ProposalType type = switch (current.advice()) {
            case LIGHTEN -> ProposalType.SESSION_LIGHTEN;
            case EASY -> ProposalType.SESSION_EASY;
            case MOVE -> ProposalType.SESSION_MOVE;
            case KEEP -> throw new ConflictException("Rien à demander.");
        };
        Object payload = type == ProposalType.SESSION_MOVE
                ? Map.of("date", today.plusDays(1).toString())
                : Map.of();

        String title = switch (current.advice()) {
            case LIGHTEN -> current.sessionTitle() + " — alléger (" + current.adaptedSummary() + ")";
            case EASY -> current.sessionTitle() + " — transformer en footing";
            case MOVE -> current.sessionTitle() + " — décaler à demain";
            case KEEP -> current.sessionTitle();
        };

        AthleteProposal proposal = proposalService.propose(athlete, type, current.workoutId(),
                today, title, current.reason(), payload,
                dedupeKey(current.workoutId(), type), true);
        if (proposal != null) {
            proposalService.notifyCoachOfRequest(proposal);
        }
        return forAthlete(athleteId);
    }

    // ---------------------------------------------------------------------
    // Détail
    // ---------------------------------------------------------------------

    /**
     * La séance du jour qui porte le conseil : la première séance course encore à faire.
     *
     * <p>Une séance de repos n'a rien à adapter, et une séance déjà clôturée non plus — conseiller
     * d'alléger ce qui est fait est le genre de détail qui décrédibilise tout l'écran.</p>
     */
    private Workout mainSessionOf(UUID athleteId, LocalDate day) {
        return workoutRepository.findByAthleteIdAndScheduledDateOrderByCreatedAtAsc(athleteId, day)
                .stream()
                .filter(w -> w.getType() != WorkoutType.REST)
                .filter(w -> w.getStatus() == WorkoutStatus.PLANNED)
                .findFirst()
                .orElse(null);
    }

    private boolean hasPendingRequest(UUID athleteId, UUID workoutId) {
        return proposalRepository.findByTargetIdAndStatus(workoutId, ProposalStatus.PENDING).stream()
                .anyMatch(p -> p.getAthlete().getId().equals(athleteId));
    }

    private String dedupeKey(UUID workoutId, ProposalType type) {
        return "readiness:" + type + ":" + workoutId;
    }

    private com.coachrun.dto.response.WorkoutPrescriptionResponse safePrescription(UUID clubId, UUID workoutId) {
        try {
            return workoutService.prescription(clubId, workoutId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * RPE le plus élevé prescrit dans la séance — ce qui la rend « dure » ou non.
     *
     * <p>À défaut de structure, on retombe sur le type de séance : un fractionné reste un
     * fractionné même quand personne n'a saisi de RPE de bloc.</p>
     */
    private Integer hardestRpe(SessionStructure snapshot, Workout workout) {
        if (snapshot != null) {
            Integer max = snapshot.main().stream()
                    .map(CourseBlock::rpe)
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(null);
            if (max != null) {
                return max;
            }
        }
        return switch (workout.getType()) {
            case INTERVALS, RACE -> 9;
            case THRESHOLD, TEMPO -> 7;
            case LONG_RUN -> 6;
            default -> 4;
        };
    }

    /** « 6 × 1000 m » — le corps de séance en une ligne, tel qu'un coach l'écrirait. */
    private String summarize(SessionStructure structure) {
        if (structure == null || structure.main().isEmpty()) {
            return null;
        }
        List<String> parts = structure.main().stream().map(this::blockLabel)
                .filter(java.util.Objects::nonNull).toList();
        return parts.isEmpty() ? null : String.join(" + ", parts);
    }

    private String blockLabel(CourseBlock b) {
        String volume = b.distanceM() != null ? formatDistance(b.distanceM())
                : b.durationS() != null ? (b.durationS() / 60) + " min"
                : null;
        if (volume == null) {
            return null;
        }
        int reps = b.repCount();
        return reps > 1 ? reps + " × " + volume : volume;
    }

    private String formatDistance(int m) {
        return m >= 1000 ? (m % 1000 == 0 ? (m / 1000) + " km" : String.format("%.1f km", m / 1000.0).replace('.', ','))
                : m + " m";
    }

    /** À quoi ressemblerait la séance adaptée — calculée pour être montrée, jamais enregistrée. */
    private String adaptedSummary(Advice advice, com.coachrun.dto.response.WorkoutPrescriptionResponse rx) {
        if (rx == null || rx.snapshot() == null) {
            return null;
        }
        return switch (advice) {
            case LIGHTEN -> summarize(engine.lighten(rx.snapshot()));
            case EASY -> summarize(engine.easy(rx.snapshot(), mainDuration(rx.calculated())));
            case MOVE, KEEP -> null;
        };
    }

    private Integer mainDuration(CalculatedSessionResponse calc) {
        if (calc == null) {
            return null;
        }
        int total = 0;
        for (var e : calc.main()) {
            if (e.calc() != null && e.calc().estimatedDurationS() != null) {
                total += e.calc().estimatedDurationS();
            }
        }
        return total > 0 ? total : null;
    }

    private List<String> actionsFor(Advice advice) {
        return switch (advice) {
            case KEEP -> List.of();
            case LIGHTEN -> List.of("LIGHTEN");
            case EASY -> List.of("EASY");
            case MOVE -> List.of("MOVE");
        };
    }
}
