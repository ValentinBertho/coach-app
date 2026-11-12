package com.coachrun.dto.request;

import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.CoachSpecialty;
import com.coachrun.entity.enums.Discipline;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Ce que le coach écrit sur sa fiche publique.
 *
 * <p>Presque tout est facultatif, et c'est délibéré : une fiche s'écrit en plusieurs fois, et un
 * formulaire qui refuse d'enregistrer un brouillon incomplet pousse à le remplir n'importe comment
 * pour passer. Les exigences de complétude s'appliquent à la <b>soumission</b>, pas à
 * l'enregistrement — le service les porte, et il les nomme une par une.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachProfileRequest(
        @Size(max = 140) String headline,
        @Size(max = 4000) String bio,
        Set<Discipline> disciplines,
        Set<CoachSpecialty> specialties,
        /** Vide = tous les niveaux, ce qui est le cas le plus fréquent. */
        Set<AthleteLevel> levels,
        /** Codes ISO 639-1 sur deux lettres. */
        Set<@Size(min = 2, max = 2) String> languages,
        @Size(max = 120) String city,
        @Size(max = 2) String country,
        boolean remote,
        boolean inPerson,
        @Min(0) @Max(70) Integer experienceYears,
        @Min(1) @Max(500) Integer capacityMax) {
}
