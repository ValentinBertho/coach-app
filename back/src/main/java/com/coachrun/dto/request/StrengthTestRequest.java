package com.coachrun.dto.request;

import com.coachrun.entity.enums.StrengthTestProtocol;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre un test de force (cf. DARI Lab §6.5). Selon le protocole :
 * {@code weightKg} requis (sauf saisie partielle), {@code reps} pour rep-test/AMRAP,
 * {@code durationSec} pour l'isométrie.
 *
 * <p>{@code confirmLargeGap} lève le garde-fou d'écart : un test qui s'écarte de plus de 10 % du
 * profil courant est refusé tant que le coach ne l'a pas confirmé (cf. {@code StrengthTestService}).</p>
 */
public record StrengthTestRequest(
        @NotNull UUID exerciseId,
        @NotNull StrengthTestProtocol protocol,
        LocalDate testDate,
        Double weightKg,
        Integer reps,
        Integer durationSec,
        Integer rir,
        String notes,
        /** Confirme un écart de plus de 10 % avec le 1RM courant (sinon le test est refusé). */
        Boolean confirmLargeGap
) {
}
