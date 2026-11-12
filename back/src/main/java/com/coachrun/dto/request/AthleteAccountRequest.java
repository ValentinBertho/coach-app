package com.coachrun.dto.request;

import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.Sex;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;

/** Ce qu'un athlète peut modifier de son propre compte. Ni e-mail ni mot de passe : ils ont leurs routes. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AthleteAccountRequest(
        @Size(max = 120) String firstName,
        @Size(max = 120) String lastName,
        Sex sex,
        Discipline discipline,
        AthleteLevel level,
        @Size(max = 120) String city,
        @Size(max = 2) String country,
        @Size(max = 1000) String goal,
        boolean lookingForCoach) {
}
