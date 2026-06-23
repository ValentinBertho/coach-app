package com.coachrun.dto.request;

import com.coachrun.entity.enums.TestType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Création d'un test lactate : valeurs au repos + paliers. {@code applyToProfile} pousse les
 * seuils détectés vers le profil physio de l'athlète (défaut : vrai).
 */
public record LactateTestRequest(
        TestType testType,
        @NotNull LocalDate testDate,
        @Size(max = 2048) String notes,
        BigDecimal lactateRest,
        Integer hrRest,
        Integer hrMax,
        Boolean applyToProfile,
        @Valid @NotNull List<LactateTestStepRequest> steps
) {
}
