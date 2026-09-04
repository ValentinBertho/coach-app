package com.coachrun.dto.response;

import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.CoachProfileStatus;
import com.coachrun.entity.enums.CoachSpecialty;
import com.coachrun.entity.enums.Discipline;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * La fiche telle que son propriétaire la voit dans l'éditeur.
 *
 * <p>Elle porte ce que le public ne verra jamais : le statut, la note d'arbitrage, et la liste des
 * manques qui empêchent de la soumettre. C'est cette dernière qui évite l'aller-retour « Publier »
 * → « il manque quelque chose » → « quoi ? ».</p>
 */
public record CoachProfileResponse(
        UUID id,
        String slug,
        CoachProfileStatus status,
        String statusLabel,
        String coachName,
        String headline,
        String bio,
        Set<Discipline> disciplines,
        Set<CoachSpecialty> specialties,
        Set<AthleteLevel> levels,
        Set<String> languages,
        String city,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean remote,
        boolean inPerson,
        Integer experienceYears,
        Integer capacityMax,
        Instant submittedAt,
        Instant publishedAt,
        Instant reviewedAt,
        /** Motif d'un refus, écrit par un administrateur pour être lu par le coach. */
        String reviewNote,
        Integer medianResponseHours,
        /**
         * Adresse de la photo, ou {@code null}. Une <b>URL</b>, jamais des octets : la fiche se lit
         * vingt fois par page d'annuaire, et l'image ne s'affiche qu'une.
         *
         * <p>Elle change à chaque remplacement — l'identifiant de la photo en fait partie — si bien
         * qu'aucun cache n'a à être invalidé.</p>
         */
        String photoUrl,
        List<CoachCertificationResponse> certifications,
        List<CoachOfferResponse> offers,
        /** Ce qui manque pour soumettre. Vide = la fiche est prête. */
        List<String> missing) {

    public static CoachProfileResponse of(CoachProfile p,
                                          String photoUrl,
                                          List<CoachCertificationResponse> certifications,
                                          List<CoachOfferResponse> offers,
                                          List<String> missing) {
        return new CoachProfileResponse(
                p.getId(),
                p.getSlug(),
                p.getStatus(),
                p.getStatus().label(),
                p.getCoach() != null ? p.getCoach().getFullName() : null,
                p.getHeadline(),
                p.getBio(),
                p.getDisciplines(),
                p.getSpecialties(),
                p.getLevels(),
                p.getLanguages(),
                p.getCity(),
                p.getCountry(),
                p.getLatitude(),
                p.getLongitude(),
                p.isRemote(),
                p.isInPerson(),
                p.getExperienceYears(),
                p.getCapacityMax(),
                p.getSubmittedAt(),
                p.getPublishedAt(),
                p.getReviewedAt(),
                p.getReviewNote(),
                p.getMedianResponseHours(),
                photoUrl,
                certifications,
                offers,
                missing);
    }
}
