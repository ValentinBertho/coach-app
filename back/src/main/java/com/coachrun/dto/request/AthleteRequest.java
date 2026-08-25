package com.coachrun.dto.request;

import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.Sex;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Création / mise à jour d'un athlète (profil + données physiologiques).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AthleteRequest(
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @Email @Size(max = 255) String email,
        LocalDate birthDate,
        Sex sex,
        AthleteLevel level,
        @Min(100) @Max(230) Integer hrMax,
        @Min(25) @Max(120) Integer hrRest,
        @DecimalMin("5.0") BigDecimal vma,
        @DecimalMin("20.0") BigDecimal weightKg,
        @Size(max = 2048) String medicalNotes,
        java.util.UUID groupId,
        /** À la création : athlète privé (coaching hors club, invisible des autres coachs). */
        Boolean privateAthlete,
        /**
         * Statut du profil. <b>Champ ajouté</b>, facultatif : {@code null} laisse le statut
         * inchangé, ce qui est le comportement de tous les appelants antérieurs.
         *
         * <p>Seule l'administration l'applique ({@code AdminAthleteService}). Côté coach, le
         * passage en archive garde sa route dédiée ({@code AthleteService#archive}) — un statut
         * modifiable par un enregistrement de formulaire y serait un archivage accidentel.</p>
         */
        com.coachrun.entity.enums.AthleteStatus status) {
}
