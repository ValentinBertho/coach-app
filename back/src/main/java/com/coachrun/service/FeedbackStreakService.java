package com.coachrun.service;

import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Série de retours consécutifs d'un athlète : combien de séances d'affilée, en remontant de la
 * plus récente, ont reçu un ressenti.
 *
 * <p>Sert la célébration à la validation (« 12e retour d'affilée 🔥 »). C'est la seule
 * contrepartie visible que l'athlète reçoit pour un geste qui, sinon, ne profite qu'au coach —
 * et c'est ce qui fait la différence entre un formulaire et une habitude.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackStreakService {

    /** Fenêtre d'observation : au-delà, la série n'a plus de sens motivationnel. */
    private static final int WINDOW_DAYS = 180;

    private final WorkoutRepository workoutRepository;
    private final ScheduledStrengthSessionRepository strengthRepository;
    private final ClockService clock;

    /** Une séance passée, avec ou sans ressenti. */
    private record Session(LocalDate date, boolean rated) {
    }

    /**
     * Nombre de séances consécutives notées, la plus récente d'abord. Zéro si la dernière
     * séance échue n'a pas reçu de retour.
     */
    public int currentStreak(UUID athleteId) {
        LocalDate today = clock.today();
        LocalDate from = today.minusDays(WINDOW_DAYS);
        List<Session> sessions = new ArrayList<>();

        for (Workout w : workoutRepository
                .findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(athleteId, from, today)) {
            // Une séance encore planifiée aujourd'hui n'est pas « manquée » : elle n'a pas
            // encore eu lieu et ne doit donc pas casser la série.
            if (w.getScheduledDate().isEqual(today) && w.getStatus() == WorkoutStatus.PLANNED) {
                continue;
            }
            sessions.add(new Session(w.getScheduledDate(), w.getRpe() != null || w.getFatigue() != null));
        }
        strengthRepository.findByAthleteIdAndScheduledDateBetweenOrderByScheduledDateAsc(athleteId, from, today)
                .stream()
                .filter(s -> !(s.getScheduledDate().isEqual(today) && !s.isCompleted()))
                .forEach(s -> sessions.add(new Session(s.getScheduledDate(),
                        s.getSessionRpe() != null || s.getSessionFatigue() != null)));

        sessions.sort(Comparator.comparing(Session::date).reversed());

        int streak = 0;
        for (Session s : sessions) {
            if (!s.rated()) {
                break;
            }
            streak++;
        }
        return streak;
    }
}
