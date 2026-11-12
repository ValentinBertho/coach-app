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
        boolean lookingForCoach,
        /** La demande préparée à l'inscription, tant que l'e-mail n'est pas confirmé ; sinon nul. */
        PendingRequest pendingRequest) {

    /** Ce que l'athlète doit pouvoir relire : à qui, et quoi. */
    public record PendingRequest(String coachSlug, String coachName, String message) {
    }

    public static AthleteAccountResponse from(AthleteAccount a, PendingRequest pendingRequest) {
        return new AthleteAccountResponse(
                a.getId(), a.getFirstName(), a.getLastName(), a.getBirthDate(), a.getSex(),
                a.getDiscipline(), a.getLevel(), a.getCity(), a.getCountry(), a.getGoal(),
                a.isLookingForCoach(), pendingRequest);
    }
}
