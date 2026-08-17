package com.coachrun.service;

import com.coachrun.dto.response.ProgressionResponse;
import com.coachrun.dto.response.ProposalResponse;
import com.coachrun.dto.strength.StrengthExerciseItem;
import com.coachrun.dto.strength.StrengthStructure;
import com.coachrun.entity.Athlete;
import com.coachrun.entity.AthleteProposal;
import com.coachrun.entity.ScheduledStrengthSession;
import com.coachrun.entity.enums.ProposalType;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La surcharge progressive, enfin appliquée.
 *
 * <h2>Le manque qu'il comble</h2>
 *
 * <p>{@link com.coachrun.engine.ProgressionEngine} savait déjà dire « toutes les séries réussies,
 * RIR au-dessus de la cible, aucune douleur : +2,5 kg ». Cette suggestion s'affichait dans l'écran
 * d'une séance <b>passée</b>, que personne ne rouvre, et n'avait aucun effet : pour l'appliquer, le
 * coach retournait dans la bibliothèque et retapait la charge. Elle était donc calculée, puis
 * perdue.</p>
 *
 * <h2>Comment elle atteint enfin la séance suivante</h2>
 *
 * <p>À la clôture d'une séance de force, on regarde la <b>prochaine séance déjà planifiée</b> qui
 * contient le même exercice. S'il y en a une, la suggestion devient une proposition qui la vise
 * nommément : « Développé couché, jeudi : 42,5 → 45 kg ». S'il n'y en a pas, on ne propose rien —
 * une suggestion qui ne s'applique à rien n'est pas une suggestion.</p>
 *
 * <p>Et rien n'est appliqué avant qu'un humain n'ait tapé dessus : c'est {@link ProposalService} qui
 * détient la seule porte d'écriture.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StrengthProgressionService {

    private final ScheduledStrengthSessionRepository scheduledRepository;
    private final ProgressionService progressionService;
    private final ProposalService proposalService;
    private final ObjectMapper objectMapper;
    private final ClockService clock;

    /** Au-delà, la « prochaine séance » n'en est plus une : le coach l'aura réécrite avant. */
    private static final int LOOKAHEAD_DAYS = 21;

    /**
     * Convertit les suggestions d'une séance de force terminée en propositions sur la séance
     * suivante.
     *
     * @return les propositions créées (vide si rien ne justifiait d'en créer)
     */
    @Transactional
    public List<ProposalResponse> proposeAfter(ScheduledStrengthSession completed) {
        if (!completed.isCompleted()) {
            return List.of();
        }
        Athlete athlete = completed.getAthlete();
        ProgressionResponse progression;
        try {
            progression = progressionService.forAthlete(athlete.getId(), completed.getId());
        } catch (RuntimeException e) {
            log.debug("Progression illisible pour la séance de force {}", completed.getId());
            return List.of();
        }

        List<ScheduledStrengthSession> upcoming = scheduledRepository
                .findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(
                        athlete.getId(), clock.today(), clock.today().plusDays(LOOKAHEAD_DAYS))
                .stream()
                .filter(s -> !s.isCompleted())
                .filter(s -> !s.getId().equals(completed.getId()))
                .toList();
        if (upcoming.isEmpty()) {
            return List.of();
        }

        List<ProposalResponse> created = new ArrayList<>();
        for (var exercise : progression.exercises()) {
            if (!exercise.recommended() || exercise.deltaKg() == null) {
                continue;
            }
            ScheduledStrengthSession target = firstSessionWith(upcoming, exercise.exerciseId());
            if (target == null) {
                continue;
            }
            double delta = exercise.deltaKg();
            Double currentKg = currentCharge(target, exercise.exerciseId());
            String title = exercise.exerciseName() + " — "
                    + (currentKg != null
                        ? fmt(currentKg) + " → " + fmt(currentKg + delta) + " kg"
                        : "+" + fmt(delta) + " kg")
                    + " le " + dayLabel(target.getScheduledDate());

            AthleteProposal p = proposalService.propose(athlete, ProposalType.STRENGTH_LOAD,
                    target.getId(), target.getScheduledDate(), title,
                    reasonFor(exercise),
                    Map.of("exerciseId", exercise.exerciseId().toString(), "deltaKg", delta),
                    "strength:" + completed.getId() + ":" + exercise.exerciseId(), false);
            if (p != null) {
                created.add(ProposalResponse.from(p, null));
            }
        }
        if (!created.isEmpty()) {
            log.info("Progression de force : {} proposition(s) après la séance {}",
                    created.size(), completed.getId());
        }
        return created;
    }

    /**
     * Pourquoi la charge monte, en une ligne.
     *
     * <p>Sans elle, accepter une progression revient à faire confiance à une boîte noire — et un
     * coach ne le fait pas deux fois. Le moteur de progression connaît la règle qu'il a appliquée ;
     * il faut juste la dire.</p>
     */
    private String reasonFor(ProgressionResponse.ExerciseProgression e) {
        return e.suggestionLabel() + " — série cible atteinte à la séance précédente, sans douleur.";
    }

    private ScheduledStrengthSession firstSessionWith(List<ScheduledStrengthSession> sessions, UUID exerciseId) {
        for (ScheduledStrengthSession s : sessions) {
            if (findItem(s, exerciseId) != null) {
                return s;
            }
        }
        return null;
    }

    /** Charge actuellement prescrite pour cet exercice dans cette séance, si elle est en kilos. */
    private Double currentCharge(ScheduledStrengthSession session, UUID exerciseId) {
        StrengthExerciseItem item = findItem(session, exerciseId);
        return item == null || item.prescription() == null ? null : item.prescription().chargeKgMin();
    }

    private StrengthExerciseItem findItem(ScheduledStrengthSession session, UUID exerciseId) {
        StrengthStructure structure = readStructure(session);
        if (structure == null) {
            return null;
        }
        return structure.blocks().stream()
                .flatMap(b -> b.exercises().stream())
                .filter(ex -> exerciseId.equals(ex.exerciseId()))
                .findFirst()
                .orElse(null);
    }

    private StrengthStructure readStructure(ScheduledStrengthSession session) {
        if (session.getSessionSnapshot() == null || session.getSessionSnapshot().isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(session.getSessionSnapshot(), StrengthStructure.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v)
                : String.valueOf(Math.round(v * 10) / 10.0).replace('.', ',');
    }

    private String dayLabel(LocalDate d) {
        return d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.FRENCH)
                + " " + d.getDayOfMonth();
    }
}
