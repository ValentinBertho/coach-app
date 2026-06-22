package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.Sex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Détail complet d'un athlète (déchiffrement des données de santé à la lecture). */
public record AthleteResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        LocalDate birthDate,
        Sex sex,
        AthleteLevel level,
        AthleteStatus status,
        Integer hrMax,
        Integer hrRest,
        BigDecimal vma,
        BigDecimal weightKg,
        String medicalNotes,
        boolean invitationPending,
        java.util.UUID groupId,
        String groupName) {

    public static AthleteResponse from(Athlete a) {
        return new AthleteResponse(
                a.getId(), a.getFirstName(), a.getLastName(), a.getEmail(),
                a.getBirthDate(), a.getSex(), a.getLevel(), a.getStatus(),
                a.getHrMax(), a.getHrRest(), a.getVma(), a.getWeightKg(),
                a.getMedicalNotes(),
                a.getInviteToken() != null,
                a.getGroup() != null ? a.getGroup().getId() : null,
                a.getGroup() != null ? a.getGroup().getName() : null);
    }
}
