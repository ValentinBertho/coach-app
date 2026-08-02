package com.coachrun.dto.response;

import com.coachrun.entity.StrengthResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Une série de force réalisée, telle qu'exportée dans le dossier RGPD de l'athlète. Porte le
 * ressenti (RPE, RIR, douleur) et le commentaire libre : ce sont ses données, il doit les
 * retrouver dans son export.
 */
public record StrengthResultExportResponse(
        UUID id,
        UUID scheduledSessionId,
        UUID exerciseId,
        int setNumber,
        BigDecimal chargeKg,
        Integer repsDone,
        Integer durationSecDone,
        BigDecimal rpeDone,
        Integer rirDone,
        Integer pain,
        String comment,
        Instant recordedAt) {

    public static StrengthResultExportResponse from(StrengthResult r) {
        return new StrengthResultExportResponse(
                r.getId(),
                r.getScheduledSession() == null ? null : r.getScheduledSession().getId(),
                r.getExerciseId(), r.getSetNumber(), r.getChargeKg(),
                r.getRepsDone(), r.getDurationSecDone(), r.getRpeDone(), r.getRirDone(),
                r.getPain(), r.getComment(), r.getCreatedAt());
    }
}
