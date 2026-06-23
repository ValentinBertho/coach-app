package com.coachrun.dto.request;

import com.coachrun.entity.enums.StrengthTestProtocol;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enregistre un test de force (cf. DARI Lab §6.5). Selon le protocole :
 * {@code weightKg} requis (sauf saisie partielle), {@code reps} pour rep-test/AMRAP,
 * {@code durationSec} pour l'isométrie.
 */
public record StrengthTestRequest(
        @NotNull UUID exerciseId,
        @NotNull StrengthTestProtocol protocol,
        LocalDate testDate,
        Double weightKg,
        Integer reps,
        Integer durationSec,
        Integer rir,
        String notes
) {
}
