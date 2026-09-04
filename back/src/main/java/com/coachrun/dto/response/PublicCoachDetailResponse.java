package com.coachrun.dto.response;

import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.enums.AthleteLevel;
import com.coachrun.entity.enums.CoachSpecialty;
import com.coachrun.entity.enums.Discipline;

import java.util.List;
import java.util.Set;

/**
 * La fiche publique d'un coach, telle qu'un visiteur la lit.
 *
 * <p>Mêmes retenues que pour la liste : aucune coordonnée, aucun identifiant technique. Le slug
 * suffit à composer l'adresse, et la demande de coaching se pose depuis cette page.</p>
 *
 * <p>Les diplômes sont rendus <b>tels que déclarés</b>, et l'objet le dit : {@code certificationsDeclared}
 * n'est pas un drapeau de configuration mais un rappel, transporté avec la donnée, que la
 * plateforme ne s'en porte pas garante. Un champ qu'on lit à côté de la valeur a plus de chances
 * d'être affiché qu'une règle écrite dans une documentation.</p>
 */
public record PublicCoachDetailResponse(
        String slug,
        String name,
        String headline,
        String bio,
        Set<Discipline> disciplines,
        Set<CoachSpecialty> specialties,
        List<String> specialtyLabels,
        Set<AthleteLevel> levels,
        Set<String> languages,
        String city,
        String country,
        boolean remote,
        boolean inPerson,
        Integer experienceYears,
        Integer capacityMax,
        String photoUrl,
        Integer medianResponseHours,
        boolean acceptingAthletes,
        List<CoachCertificationResponse> certifications,
        /** Toujours vrai : les diplômes sont déclaratifs, et l'écran doit le dire. */
        boolean certificationsDeclared,
        List<CoachOfferResponse> offers) {

    public static PublicCoachDetailResponse of(CoachProfile p,
                                               String photoUrl,
                                               List<CoachCertificationResponse> certifications,
                                               List<CoachOfferResponse> offers) {
        return new PublicCoachDetailResponse(
                p.getSlug(),
                p.getCoach() != null ? p.getCoach().getFullName() : null,
                p.getHeadline(),
                p.getBio(),
                p.getDisciplines(),
                p.getSpecialties(),
                p.getSpecialties().stream().map(CoachSpecialty::label).sorted().toList(),
                p.getLevels(),
                p.getLanguages(),
                p.getCity(),
                p.getCountry(),
                p.isRemote(),
                p.isInPerson(),
                p.getExperienceYears(),
                p.getCapacityMax(),
                photoUrl,
                p.getMedianResponseHours(),
                p.getStatus().acceptsRequests(),
                certifications,
                true,
                offers);
    }
}
