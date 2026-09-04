package com.coachrun.dto.response;

import com.coachrun.entity.AthleteAccount;
import com.coachrun.entity.CoachingRequest;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.CoachingRequestStatus;
import com.coachrun.entity.enums.Discipline;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * Une demande de coaching, telle que la voient ses deux parties.
 *
 * <h2>Ce que le coach voit de l'athlète, et ce qu'il ne voit pas</h2>
 *
 * <p>Objectif, discipline, niveau, ville, âge : de quoi décider. <b>Ni e-mail, ni téléphone</b>,
 * tant que la demande n'est pas acceptée. Une demande n'est pas une prise de contact : si elle
 * livrait les coordonnées, il suffirait d'en recevoir pour se constituer un fichier, et un refus
 * n'aurait plus aucun effet.</p>
 *
 * <p>L'âge est rendu en années plutôt qu'en date de naissance : c'est ce dont un coach a besoin
 * pour juger, et la date exacte est une donnée personnelle de plus qu'il n'a pas à détenir avant
 * d'avoir accepté.</p>
 */
public record CoachingRequestResponse(
        UUID id,
        CoachingRequestStatus status,
        String statusLabel,
        // --- L'athlète, vu du coach ---
        String athleteName,
        Integer athleteAge,
        Discipline athleteDiscipline,
        AthleteLevel athleteLevel,
        String athleteCity,
        String athleteGoal,
        // --- Le coach, vu de l'athlète ---
        String coachName,
        String coachSlug,
        // --- L'échange ---
        String message,
        String coachQuestion,
        String athleteAnswer,
        String offerLabel,
        Integer offerAmountCents,
        String declineReason,
        Instant createdAt,
        Instant decidedAt,
        Instant expiresAt,
        /**
         * La fiche créée à l'acceptation, {@code null} avant.
         *
         * <p>C'est par elle que le coach agit sur la relation — y mettre fin, notamment. Sans ce
         * champ, l'écran des demandes connaîtrait l'athlète mais pas le dossier, et devrait aller
         * le chercher ailleurs pour un geste qui part pourtant d'ici.</p>
         */
        UUID createdAthleteId) {

    public static CoachingRequestResponse of(CoachingRequest r, String coachSlug) {
        AthleteAccount a = r.getAthleteAccount();
        return new CoachingRequestResponse(
                r.getId(),
                r.getStatus(),
                r.getStatus().label(),
                a.getFirstName() + " " + a.getLastName(),
                age(a.getBirthDate()),
                a.getDiscipline(),
                a.getLevel(),
                a.getCity(),
                a.getGoal(),
                r.getCoach() != null ? r.getCoach().getFullName() : null,
                coachSlug,
                r.getMessage(),
                r.getCoachQuestion(),
                r.getAthleteAnswer(),
                r.getOfferLabel(),
                r.getOfferAmountCents(),
                r.getDeclineReason(),
                r.getCreatedAt(),
                r.getDecidedAt(),
                r.getExpiresAt(),
                r.getCreatedAthleteId());
    }

    private static Integer age(LocalDate birthDate) {
        return birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears();
    }
}
