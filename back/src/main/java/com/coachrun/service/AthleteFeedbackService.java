package com.coachrun.service;

import com.coachrun.entity.DailyCheckIn;
import com.coachrun.entity.ScheduledStrengthSession;
import com.coachrun.entity.Workout;
import com.coachrun.repository.DailyCheckInRepository;
import com.coachrun.repository.ScheduledStrengthSessionRepository;
import com.coachrun.repository.WorkoutRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Dernier signal de forme déclaré par un athlète, <strong>course, force et check-in matinal
 * confondus</strong>.
 *
 * <p>L'état de forme DARI Lab est fatigue + douleur (jamais le RPE) ; il n'y a aucune raison
 * qu'un retour de renforcement compte moins qu'un retour de course, ni qu'un athlète qui a
 * déclaré au réveil « fatigue 8, douleur 4 » soit affiché « aucun retour récent » parce que sa
 * séance du soir n'est pas encore faite. Ce service prend le plus récent des trois pour que la
 * pastille de forme et les alertes douleur voient toutes les sources.</p>
 *
 * <p>Le sommeil du check-in n'entre pas ici : il est du contexte pour le coach, pas un terme du
 * calcul de forme — même statut que le RPE.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AthleteFeedbackService {

    private final WorkoutRepository workoutRepository;
    private final ScheduledStrengthSessionRepository strengthRepository;
    private final DailyCheckInRepository checkInRepository;

    /**
     * Au-delà de ce délai, le dernier retour ne décrit plus l'état du jour.
     *
     * <p>Dix jours : au-dessus d'une semaine — un athlète qui s'entraîne trois fois par semaine
     * peut sauter une semaine sans que sa fiche bascule — et bien en dessous du délai à partir
     * duquel l'alerte « athlète silencieux » se déclenche déjà.</p>
     */
    public static final int FRESHNESS_DAYS = 10;

    /** Dernier retour connu ; les champs sont nuls si l'athlète n'a jamais rien déclaré. */
    public record LastFeedback(Integer fatigue, Integer pain, LocalDate date) {

        public static final LastFeedback NONE = new LastFeedback(null, null, null);

        /**
         * Le retour est-il assez récent pour décrire l'état d'aujourd'hui ?
         *
         * <p>Aucun retour du tout n'est pas frais non plus : un athlète qui n'a jamais rien déclaré
         * n'est pas « en forme », il est sans signal. L'afficher en vert donnait au coach une
         * assurance que personne ne lui avait fournie.</p>
         */
        public boolean isFresh(LocalDate today) {
            return date != null
                    && !date.isBefore(today.minusDays(FRESHNESS_DAYS))
                    && (fatigue != null || pain != null);
        }
    }

    public LastFeedback lastFeedback(UUID athleteId) {
        List<LastFeedback> candidates = new ArrayList<>(3);

        workoutRepository
                .findFirstByAthleteIdAndFatigueIsNotNullOrderByScheduledDateDescCreatedAtDesc(athleteId)
                .map(w -> new LastFeedback(w.getFatigue(), w.getPain(), w.getScheduledDate()))
                .ifPresent(candidates::add);

        List<ScheduledStrengthSession> strengthPage =
                strengthRepository.findLatestFeedback(athleteId, PageRequest.of(0, 1));
        if (!strengthPage.isEmpty()) {
            ScheduledStrengthSession s = strengthPage.get(0);
            candidates.add(new LastFeedback(s.getSessionFatigue(), s.getSessionPain(), s.getScheduledDate()));
        }

        List<DailyCheckIn> checkInPage =
                checkInRepository.findLatestWithSignal(athleteId, PageRequest.of(0, 1));
        if (!checkInPage.isEmpty()) {
            DailyCheckIn c = checkInPage.get(0);
            candidates.add(new LastFeedback(c.getFatigue(), c.getPain(), c.getCheckDate()));
        }

        // À égalité de date, l'ordre d'insertion départage (course, puis force, puis check-in) :
        // un retour de séance décrit l'effet d'une charge réelle, le check-in décrit un état.
        return candidates.stream()
                .max(Comparator.comparing(LastFeedback::date))
                .orElse(LastFeedback.NONE);
    }
}
