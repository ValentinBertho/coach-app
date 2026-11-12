package com.coachrun.dto.response;

import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.Sex;

import java.time.LocalDate;
import java.util.UUID;

/** Le compte d'un athlète, tel qu'il le voit lui-même. */
public record AthleteAccountResponse(
        UUID id,
        String firstName,
        String lastName,
        LocalDate birthDate,
        Sex sex,
        Discipline discipline,
        AthleteLevel level,
        String city,
        String country,
        String goal,
        boolean lookingForCoach) {

    public static AthleteAccountResponse from(AthleteAccount a) {
        return new AthleteAccountResponse(
                a.getId(), a.getFirstName(), a.getLastName(), a.getBirthDate(), a.getSex(),
                a.getDiscipline(), a.getLevel(), a.getCity(), a.getCountry(), a.getGoal(),
                a.isLookingForCoach());
    }
}
