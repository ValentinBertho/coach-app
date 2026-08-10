package com.coachrun.util;

import com.coachrun.dto.InjuryReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Lecture / écriture de la liste de blessures stockée en JSON ({@code injuries_json}).
 *
 * <p>Deux entités portent exactement la même donnée — la séance prescrite et la sortie hors
 * programme — et les deux la rendent depuis une fabrique statique de DTO ({@code from(entity)}),
 * appelée de partout : d'où un utilitaire statique plutôt qu'un composant injecté. Le mapper
 * dédié est sans état et sans configuration héritée, donc sûr à partager.</p>
 *
 * <p>Un contenu abîmé ne doit jamais faire échouer la lecture d'une séance : on rend alors une
 * liste vide et on trace, comme le fait déjà la lecture des tours. Rien de la donnée elle-même
 * n'est journalisé — ce sont des données de santé (RGPD art. 9).</p>
 */
@Slf4j
public final class InjuryCodec {

    /** Au-delà, ce n'est plus un débrief de séance — et la colonne a une taille bornée. */
    private static final int MAX_INJURIES = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private InjuryCodec() {
    }

    /** Blessures stockées, ou liste vide si la colonne est nulle ou illisible. */
    public static List<InjuryReport> read(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<InjuryReport>>() { });
        } catch (Exception e) {
            log.warn("Blessures illisibles en base : déclaration ignorée à la lecture");
            return List.of();
        }
    }

    /**
     * Sérialise les blessures déclarées, ou rend {@code null} pour « aucune » — l'athlète qui
     * retire sa déclaration doit pouvoir vider la colonne, pas y laisser un tableau vide.
     */
    public static String write(List<InjuryReport> injuries) {
        if (injuries == null || injuries.isEmpty()) {
            return null;
        }
        List<InjuryReport> capped = injuries.stream()
                .filter(i -> i != null && i.kind() != null && i.area() != null)
                .limit(MAX_INJURIES)
                .toList();
        if (capped.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(capped);
        } catch (Exception e) {
            log.warn("Blessures non sérialisables : déclaration ignorée à l'écriture");
            return null;
        }
    }
}
