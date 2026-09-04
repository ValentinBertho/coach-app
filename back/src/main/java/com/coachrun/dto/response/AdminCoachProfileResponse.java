package com.coachrun.dto.response;

import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.enums.CoachProfileStatus;
import com.coachrun.entity.enums.CoachSpecialty;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Une fiche dans la file d'arbitrage.
 *
 * <p>Elle porte de quoi <b>décider sans ouvrir autre chose</b> : qui, depuis quand, ce qu'il
 * annonce, ce qu'il déclare comme diplômes et ce qu'il facture. L'écran de validation des demandes
 * de club avait montré l'inverse — une file qui n'affiche qu'un nom oblige à ouvrir chaque ligne,
 * et une file qu'on n'arbitre pas en trois minutes ne s'arbitre pas du tout.</p>
 */
public record AdminCoachProfileResponse(
        UUID id,
        UUID coachId,
        String coachName,
        String coachEmail,
        String slug,
        CoachProfileStatus status,
        String statusLabel,
        String headline,
        String bio,
        Set<CoachSpecialty> specialties,
        String city,
        Integer experienceYears,
        Instant submittedAt,
        Instant reviewedAt,
        String reviewedByEmail,
        String reviewNote,
        List<CoachCertificationResponse> certifications,
        List<CoachOfferResponse> offers) {

    public static AdminCoachProfileResponse of(CoachProfile p,
                                               String reviewedByEmail,
                                               List<CoachCertificationResponse> certifications,
                                               List<CoachOfferResponse> offers) {
        return new AdminCoachProfileResponse(
                p.getId(),
                p.getCoach() != null ? p.getCoach().getId() : null,
                p.getCoach() != null ? p.getCoach().getFullName() : null,
                p.getCoach() != null ? p.getCoach().getEmail() : null,
                p.getSlug(),
                p.getStatus(),
                p.getStatus().label(),
                p.getHeadline(),
                p.getBio(),
                p.getSpecialties(),
                p.getCity(),
                p.getExperienceYears(),
                p.getSubmittedAt(),
                p.getReviewedAt(),
                reviewedByEmail,
                p.getReviewNote(),
                certifications,
                offers);
    }
}
