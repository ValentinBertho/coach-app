package com.coachrun.dto.response;

import com.coachrun.entity.Workout;
import com.coachrun.entity.enums.WorkoutStatus;
import com.coachrun.entity.enums.WorkoutType;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkoutResponse(
        UUID id,
        UUID athleteId,
        LocalDate scheduledDate,
        WorkoutType type,
        WorkoutStatus status,
        String title,
        String notes,
        Integer targetDistanceM,
        Integer targetDurationS,
        /** Durée réellement effectuée sur une séance écourtée ; null si menée à son terme. */
        Integer actualDurationS,
        /** Motif renseigné quand l'athlète a déclaré la séance non faite. */
        com.coachrun.entity.enums.MissedReason missedReason,
        /**
         * Effort perçu <b>attendu</b> pour la séance entière (1–10), annoncé par le coach.
         * Nul quand rien n'a été annoncé : l'interface se tait plutôt que d'afficher un zéro.
         */
        Integer targetRpe,
        /** Effort perçu <b>ressenti</b>, saisi par l'athlète. C'est l'écart avec le précédent qui informe. */
        Integer rpe,
        Integer fatigue,
        Integer pain,
        /** Sensation générale déclarée (1 = excellente … 5 = très mauvaise) ; distincte du RPE. */
        Integer feel,
        /** Blessures déclarées au débrief ; liste vide si aucune. */
        List<com.coachrun.dto.InjuryReport> injuries,
        String athleteComment,
        /** Retour du coach sur la séance réalisée (visible par l'athlète). */
        String coachComment,
        /** Quand l'athlète a ouvert le mot du coach. Nul = non lu, donc encore à remonter. */
        java.time.Instant coachCommentReadAt,
        java.time.Instant coachCommentAt,
        /**
         * Date du « vu 👏 » du coach ; null tant qu'il n'a pas eu lieu. Exposée à l'athlète —
         * c'est tout l'objet du geste : une reconnaissance qui reste sur la séance, là où une
         * notification est passée et oubliée.
         */
        java.time.Instant coachAcknowledgedAt,
        boolean movedByAthlete,
        LocalDate originalDate,
        UUID sourceTemplateId,
        /** Charge prévue en UA (sRPE appliqué à la prescription) — total hebdo du calendrier. */
        Integer plannedLoadUa,
        int orderIndex,
        List<WorkoutStepResponse> steps) {

    public static WorkoutResponse from(Workout w) {
        return new WorkoutResponse(
                w.getId(),
                w.getAthlete().getId(),
                w.getScheduledDate(),
                w.getType(),
                w.getStatus(),
                w.getTitle(),
                w.getNotes(),
                w.getTargetDistanceM(),
                w.getTargetDurationS(),
                w.getActualDurationS(),
                w.getMissedReason(),
                w.getTargetRpe(),
                w.getRpe(),
                w.getFatigue(),
                w.getPain(),
                w.getFeel(),
                com.coachrun.util.InjuryCodec.read(w.getInjuriesJson()),
                w.getAthleteComment(),
                w.getCoachComment(),
                w.getCoachCommentReadAt(),
                w.getCoachCommentAt(),
                w.getCoachAcknowledgedAt(),
                w.isMovedByAthlete(),
                w.getOriginalDate(),
                w.getSourceTemplateId(),
                w.getPlannedLoadUa(),
                w.getOrderIndex(),
                w.getSteps().stream().map(WorkoutStepResponse::from).toList());
    }
}
