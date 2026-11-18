package com.coachrun.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;

/**
 * Clé de rapprochement des zones <b>entre modèles de zones</b>.
 *
 * <p>Une séance de bibliothèque est écrite avec les zones d'un modèle (souvent le modèle par
 * défaut du club) ; chaque athlète lit ses cibles dans <i>son</i> modèle. Les zones homologues se
 * retrouvent par leur nom (« EF », « Tempo »…), mais la zone la plus basse ne porte pas le même
 * nom d'un modèle à l'autre : « Footing facile » (seuils lactiques), « Récupération » (vitesses
 * critiques), « Régénération » (VMA). Sans équivalence, l'échauffement et le retour au calme d'une
 * séance restaient sans cible chez un athlète d'un autre modèle.</p>
 *
 * <p>La clé ignore la casse, les accents et les espaces superflus, puis ramène les synonymes à un
 * nom commun. Même table côté front ({@code zone-name-key.ts}) : les deux doivent rester alignées.</p>
 */
public final class ZoneNameKey {

    /** Synonymes → clé commune (déjà normalisés : minuscules, sans accents). */
    private static final Map<String, String> ALIASES = Map.of(
            "footing facile", "recuperation",
            "footing", "recuperation",
            "recup", "recuperation",
            "regeneration", "recuperation");

    private ZoneNameKey() {
    }

    public static String of(String name) {
        if (name == null) {
            return "";
        }
        String flat = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
        return ALIASES.getOrDefault(flat, flat);
    }
}
