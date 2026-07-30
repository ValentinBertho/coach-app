package com.coachrun.service;

import com.coachrun.entity.ScheduledStrengthSession;
import com.coachrun.entity.Workout;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Dernier signal de forme déclaré par un athlète, <strong>course et force confondues</strong>.
 *
 * <p>L'état de forme DARI Lab est fatigue + douleur (jamais le RPE) ; il n'y a aucune raison
 * qu'un retour de renforcement compte moins qu'un retour de course. Ce service prend le plus
 * récent des deux pour que la pastille de forme et les alertes douleur voient les deux
 * sources.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteFeedbackService {

    private final WorkoutRepository workoutRepository;
    private final ScheduledStrengthSessionRepository strengthRepository;

    /** Dernier retour connu ; les champs sont nuls si l'athlète n'a jamais rien déclaré. */
    public record LastFeedback(Integer fatigue, Integer pain, LocalDate date) {

        public static final LastFeedback NONE = new LastFeedback(null, null, null);
    }

    public LastFeedback lastFeedback(UUID athleteId) {
        Workout course = workoutRepository
                .findFirstByAthleteIdAndFatigueIsNotNullOrderByScheduledDateDescCreatedAtDesc(athleteId)
                .orElse(null);
        List<ScheduledStrengthSession> strengthPage =
                strengthRepository.findLatestFeedback(athleteId, PageRequest.of(0, 1));
        ScheduledStrengthSession strength = strengthPage.isEmpty() ? null : strengthPage.get(0);

        if (course == null && strength == null) {
            return LastFeedback.NONE;
        }
        if (strength == null) {
            return new LastFeedback(course.getFatigue(), course.getPain(), course.getScheduledDate());
        }
        if (course == null || strength.getScheduledDate().isAfter(course.getScheduledDate())) {
            return new LastFeedback(strength.getSessionFatigue(), strength.getSessionPain(),
                    strength.getScheduledDate());
        }
        return new LastFeedback(course.getFatigue(), course.getPain(), course.getScheduledDate());
    }
}
