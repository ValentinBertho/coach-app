package com.coachrun.dto.response;

import com.coachrun.entity.Athlete;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.AthleteStatus;
import com.coachrun.entity.enums.Sex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
        String groupName,
        List<RefResponse> coaches,
        List<RefResponse> clubs) {

    public static AthleteResponse from(Athlete a) {
        // Coachs explicitement rattachés (modèle many-to-many).
        List<RefResponse> coaches = a.getCoaches().stream()
                .map(u -> new RefResponse(u.getId(), u.getFullName()))
                .sorted(Comparator.comparing(RefResponse::name, Comparator.nullsLast(String::compareTo)))
                .toList();
        // Clubs = club principal + clubs additionnels (modèle many-to-many).
        List<RefResponse> clubs = new ArrayList<>();
        clubs.add(new RefResponse(a.getClub().getId(), a.getClub().getName()));
        a.getAdditionalClubs().stream()
                .map(c -> new RefResponse(c.getId(), c.getName()))
                .sorted(Comparator.comparing(RefResponse::name, Comparator.nullsLast(String::compareTo)))
                .forEach(clubs::add);

        return new AthleteResponse(
                a.getId(), a.getFirstName(), a.getLastName(), a.getEmail(),
                a.getBirthDate(), a.getSex(), a.getLevel(), a.getStatus(),
                a.getHrMax(), a.getHrRest(), a.getVma(), a.getWeightKg(),
                a.getMedicalNotes(),
                a.getInviteToken() != null,
                a.getGroup() != null ? a.getGroup().getId() : null,
                a.getGroup() != null ? a.getGroup().getName() : null,
                coaches, clubs);
    }
}
