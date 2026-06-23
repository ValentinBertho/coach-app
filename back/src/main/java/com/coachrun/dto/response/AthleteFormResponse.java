package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.Discipline;
import com.coachrun.entity.enums.FormStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ligne du tableau de bord coach : un athlète avec son état de forme (fatigue + douleur)
 * issu de son dernier retour de séance.
 */
public record AthleteFormResponse(
        UUID id,
        String firstName,
        String lastName,
        Discipline discipline,
        FormStatus formStatus,
        Integer fatigue,
        Integer pain,
        LocalDate lastFeedbackDate
) {

    public static AthleteFormResponse of(Athlete a, FormStatus status,
                                         Integer fatigue, Integer pain, LocalDate lastFeedbackDate) {
        Discipline discipline = a.getDiscipline() == null ? Discipline.ROUTE : a.getDiscipline();
        return new AthleteFormResponse(a.getId(), a.getFirstName(), a.getLastName(),
                discipline, status, fatigue, pain, lastFeedbackDate);
    }
}
