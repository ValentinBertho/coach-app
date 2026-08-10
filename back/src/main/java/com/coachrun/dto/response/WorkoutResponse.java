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
        java.time.Instant coachCommentAt,
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
                w.getRpe(),
                w.getFatigue(),
                w.getPain(),
                w.getFeel(),
                com.coachrun.util.InjuryCodec.read(w.getInjuriesJson()),
                w.getAthleteComment(),
                w.getCoachComment(),
                w.getCoachCommentAt(),
                w.isMovedByAthlete(),
                w.getOriginalDate(),
                w.getSourceTemplateId(),
                w.getPlannedLoadUa(),
                w.getOrderIndex(),
                w.getSteps().stream().map(WorkoutStepResponse::from).toList());
    }
}
