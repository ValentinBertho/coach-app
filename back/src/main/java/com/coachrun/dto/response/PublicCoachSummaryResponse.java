package com.coachrun.dto.response;

import com.coachrun.entity.CoachProfile;
import com.coachrun.entity.enums.CoachSpecialty;
import com.coachrun.entity.enums.Discipline;

import java.util.List;
import java.util.Set;

/**
 * Un coach dans une liste de résultats.
 *
 * <p><b>Ce que cette réponse ne porte pas, et pourquoi.</b> Ni e-mail, ni téléphone, ni identifiant
 * d'utilisateur ou de club. L'annuaire est servi sans authentification : tout champ posé ici est
 * publié, et un annuaire est précisément ce qu'on aspire pour constituer un fichier de démarchage.
 * Le coach se contacte par une demande de coaching, jamais par une adresse recopiée depuis une
 * liste.</p>
 *
 * <p>Le tarif rendu est le <b>moins cher</b> des formules comparables, ramené au mois. Afficher
 * « à partir de » demande de choisir un seul nombre, et une formule trimestrielle ne se compare
 * pas à une mensuelle sans être ramenée à la même unité.</p>
 */
public record PublicCoachSummaryResponse(
        String slug,
        String name,
        String headline,
        Set<Discipline> disciplines,
        Set<CoachSpecialty> specialties,
        List<String> specialtyLabels,
        Set<String> languages,
        String city,
        boolean remote,
        boolean inPerson,
        Integer experienceYears,
        /** {@code null} quand aucune formule n'a d'équivalent mensuel (à la séance, forfait). */
        Integer fromMonthlyCents,
        String photoUrl,
        Integer medianResponseHours,
        /** Faux quand le coach a fermé sa fiche : elle se consulte, elle ne se sollicite plus. */
        boolean acceptingAthletes) {

    public static PublicCoachSummaryResponse of(CoachProfile p, Integer fromMonthlyCents,
                                                String photoUrl) {
        return new PublicCoachSummaryResponse(
                p.getSlug(),
                p.getCoach() != null ? p.getCoach().getFullName() : null,
                p.getHeadline(),
                p.getDisciplines(),
                p.getSpecialties(),
                p.getSpecialties().stream().map(CoachSpecialty::label).sorted().toList(),
                p.getLanguages(),
                p.getCity(),
                p.isRemote(),
                p.isInPerson(),
                p.getExperienceYears(),
                fromMonthlyCents,
                photoUrl,
                p.getMedianResponseHours(),
                p.getStatus().acceptsRequests());
    }
}
