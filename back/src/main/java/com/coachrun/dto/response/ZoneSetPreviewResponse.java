package com.coachrun.dto.response;

import java.util.List;
import java.util.UUID;

/**
 * Ce que donnent les règles d'un modèle de zones pour un athlète donné — sans rien écrire.
 *
 * <p>Sert l'écran « Mes zones » : le coach règle ses pourcentages en voyant tout de suite les
 * allures et fréquences cardiaques qu'ils produiront chez un de ses athlètes.</p>
 */
public record ZoneSetPreviewResponse(
        /** L'athlète travaille déjà sur ce modèle (choisi, ou celui par défaut du club). */
        boolean athleteUsesThisSet,
        References references,
        List<Value> values
) {
    /** Valeurs de référence de l'athlète, celles auxquelles les pourcentages s'appliquent. */
    public record References(
            /** Allure à la vitesse critique, en s/km. */
            Integer vcPaceS,
            /** Allure à la VMA, en s/km. */
            Integer vmaPaceS,
            Integer lt1PaceS,
            Integer lt2PaceS,
            Integer fcMax,
            Integer fcLt2,
            Integer hrRest
    ) {
    }

    /**
     * Fourchette d'un couple (zone, métrique). Allure en s/km (min = la plus rapide), vitesse en
     * km/h, FC en bpm, %FCmax en %.
     *
     * <p>{@code manual} : l'athlète a une valeur saisie à la main ou verrouillée sur sa fiche, qui
     * prime sur la règle. C'est alors elle qui est renvoyée, puisque c'est elle qu'il recevra.</p>
     */
    public record Value(UUID zoneId, UUID metricTypeId, String metricCode,
                        double min, double max, boolean manual) {
    }
}
