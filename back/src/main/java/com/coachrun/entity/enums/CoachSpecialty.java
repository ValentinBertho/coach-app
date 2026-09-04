package com.coachrun.entity.enums;

import java.util.Arrays;
import java.util.List;

/**
 * Ce sur quoi un coach se déclare compétent — et ce sur quoi un athlète filtrera.
 *
 * <p>Une énumération, et non du texte libre : ce sont les <b>facettes de l'annuaire</b>. En texte
 * libre, « semi » et « semi-marathon » deviennent deux filtres différents et aucun ne rend de
 * résultat. La contrepartie assumée est qu'un coach ne peut pas déclarer une spécialité absente de
 * cette liste ; l'ajouter demande une ligne ici, ce qui est le bon prix pour un vocabulaire qui
 * doit rester commun.</p>
 *
 * <p>La liste couvre la course sur route, le trail, la piste et la préparation physique — le
 * périmètre réel du produit. Elle s'allongera quand des coachs réclameront ce qui leur manque,
 * pas avant : une facette que personne ne coche est une facette qui rend l'annuaire vide.</p>
 */
public enum CoachSpecialty {

    DEBUTANT,
    CINQ_DIX_KM,
    SEMI_MARATHON,
    MARATHON,
    TRAIL,
    ULTRA,
    PISTE,
    CROSS,
    PREPARATION_PHYSIQUE,
    RETOUR_DE_BLESSURE,
    REPRISE_DU_SPORT,
    TRIATHLON;

    /** Libellé français, affiché tel quel dans les filtres et sur la fiche. */
    public String label() {
        return switch (this) {
            case DEBUTANT -> "Débutant";
            case CINQ_DIX_KM -> "5 – 10 km";
            case SEMI_MARATHON -> "Semi-marathon";
            case MARATHON -> "Marathon";
            case TRAIL -> "Trail";
            case ULTRA -> "Ultra";
            case PISTE -> "Piste";
            case CROSS -> "Cross";
            case PREPARATION_PHYSIQUE -> "Préparation physique";
            case RETOUR_DE_BLESSURE -> "Retour de blessure";
            case REPRISE_DU_SPORT -> "Reprise du sport";
            case TRIATHLON -> "Triathlon";
        };
    }

    public static List<CoachSpecialty> all() {
        return Arrays.asList(values());
    }
}
