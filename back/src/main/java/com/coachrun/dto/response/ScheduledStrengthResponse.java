package com.coachrun.dto.response;

import com.coachrun.entity.ScheduledStrengthSession;

import java.time.LocalDate;
import java.util.UUID;

/** Séance de force planifiée (résumé pour le calendrier). */
public record ScheduledStrengthResponse(
        UUID id,
        UUID athleteId,
        UUID sourceSessionId,
        String title,
        LocalDate scheduledDate,
        LocalDate originalDate,
        boolean movedByAthlete,
        boolean completed,
        Integer sessionFatigue,
        Integer sessionPain,
        /** RPE séance donné par l'athlète à la validation ; null tant qu'il n'a rien dit. */
        java.math.BigDecimal sessionRpe,
        /** Le mot laissé au coach en fin de séance, comme sur une séance de course. */
        String sessionComment,
        /**
         * Résumé des charges calculées pour cet athlète au moment de la planification (ex.
         * « 4 exercices · Squat 72–78 kg »). Renseigné à la planification seulement : le CdC §8
         * demande que le coach voie les charges obtenues, pas juste « séance planifiée ».
         */
        String chargeSummary,
        /** Date du « vu 👏 » du coach sur le débrief ; null tant qu'il n'a pas eu lieu. */
        java.time.Instant coachAcknowledgedAt,
        /**
         * Nombre d'exercices du snapshot. La carte du calendrier ne disait que « Renfo haut du
         * corps » : impossible de distinguer une séance de 3 exercices d'une de 10 sans l'ouvrir.
         */
        int exerciseCount
) {

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** Compte les exercices des blocs du snapshot ; 0 si absent ou illisible. */
    static int countExercises(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return 0;
        try {
            int n = 0;
            for (var block : JSON.readTree(snapshot).path("blocks")) {
                n += block.path("exercises").size();
            }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    public static ScheduledStrengthResponse from(ScheduledStrengthSession s) {
        return from(s, null);
    }

    public static ScheduledStrengthResponse from(ScheduledStrengthSession s, String chargeSummary) {
        return new ScheduledStrengthResponse(
                s.getId(), s.getAthlete().getId(), s.getSourceSessionId(), s.getTitle(),
                s.getScheduledDate(), s.getOriginalDate(), s.isMovedByAthlete(), s.isCompleted(),
                s.getSessionFatigue(), s.getSessionPain(), s.getSessionRpe(), s.getSessionComment(),
                chargeSummary,
                s.getCoachAcknowledgedAt(),
                countExercises(s.getSessionSnapshot()));
    }
}
