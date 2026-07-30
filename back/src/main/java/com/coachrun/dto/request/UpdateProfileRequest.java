package com.coachrun.dto.request;

import com.coachrun.entity.enums.PaceUnit;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Édition du profil de l'utilisateur courant. Changer d'e-mail repasse le compte en
 * « non vérifié » et déclenche un nouvel envoi de confirmation.
 */
public record UpdateProfileRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        PaceUnit paceUnit) {
}
