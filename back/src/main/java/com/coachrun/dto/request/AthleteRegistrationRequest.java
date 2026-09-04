package com.coachrun.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Inscription libre d'un athlète — sans invitation, sans coach, sans club.
 *
 * <p>Les <b>deux</b> consentements sont exigés, et ils ne recouvrent pas la même chose : les CGU
 * régissent l'usage du service, le consentement santé autorise le traitement de données de
 * l'article 9. Les fondre en une case unique priverait le second de la clarté qu'il exige.</p>
 *
 * <p>{@code birthDate} est obligatoire : c'est ce qui permet d'appliquer l'âge minimum de 16 ans à
 * l'inscription libre. En dessous, le chemin reste celui d'aujourd'hui — le coach ou le club crée
 * la fiche et invite, la relation étant nouée hors plateforme avec les responsables légaux.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AthleteRegistrationRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @NotNull @Past LocalDate birthDate,
        @Size(max = 1000) String goal,
        @AssertTrue(message = "L'acceptation des conditions d'utilisation est requise.")
        boolean termsAccepted,
        @AssertTrue(message = "Le consentement au traitement des données de santé est requis.")
        boolean healthDataConsent) {
}
