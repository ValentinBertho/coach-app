package com.coachrun.service;

import com.coachrun.entity.ScheduledStrengthSession;
import com.coachrun.entity.WellnessCheckIn;
import com.coachrun.entity.Workout;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.WellnessCheckInRepository;
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
 * récent des trois pour que la pastille de forme et les alertes douleur voient toutes les
 * sources.</p>
 *
 * <p>Le check-in matinal est la troisième : sans lui, la forme ne se rafraîchissait qu'après une
 * séance, donc jamais pendant une coupure, une blessure ou une semaine de repos — précisément
 * les moments où le coach a le plus besoin de savoir.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteFeedbackService {

    private final WorkoutRepository workoutRepository;
    private final ScheduledStrengthSessionRepository strengthRepository;
    private final WellnessCheckInRepository checkInRepository;

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
        WellnessCheckIn checkIn = checkInRepository
                .findFirstByAthleteIdOrderByCheckDateDesc(athleteId)
                .filter(c -> c.getFatigue() != null || c.getPain() != null)
                .orElse(null);

        LastFeedback best = LastFeedback.NONE;
        if (course != null) {
            best = mostRecent(best,
                    new LastFeedback(course.getFatigue(), course.getPain(), course.getScheduledDate()));
        }
        if (strength != null) {
            best = mostRecent(best, new LastFeedback(strength.getSessionFatigue(),
                    strength.getSessionPain(), strength.getScheduledDate()));
        }
        if (checkIn != null) {
            best = mostRecent(best,
                    new LastFeedback(checkIn.getFatigue(), checkIn.getPain(), checkIn.getCheckDate()));
        }
        return best;
    }

    /**
     * Le plus récent des deux. À égalité de date, on garde celui déjà retenu : le check-in est
     * évalué en dernier, donc un check-in du matin ne remplace pas le retour de la séance du même
     * jour — le post-séance est le signal le plus informé des deux.
     */
    private static LastFeedback mostRecent(LastFeedback current, LastFeedback candidate) {
        if (candidate.date() == null) {
            return current;
        }
        if (current.date() == null || candidate.date().isAfter(current.date())) {
            return candidate;
        }
        return current;
    }
}
