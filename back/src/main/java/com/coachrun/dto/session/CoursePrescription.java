package com.coachrun.dto.session;

import com.coachrun.entity.enums.PrescriptionRef;

import java.util.UUID;

/**
 * Prescription d'intensité d'un bloc course.
 *
 * <p><b>Authoring cible (Z3)</b> : une <b>zone à 100 %</b> — {@code zoneId} seul. La cible concrète
 * (allure/FC) est <b>lue</b> sur la fiche athlète ({@code AthleteZoneValue}), jamais saisie ici.</p>
 *
 * <p><b>Legacy (lecture)</b> : les snapshots figés et anciens modèles portent le couple
 * {@code ref + minPct/maxPct} (fourchette en % d'un référentiel). Ces champs restent lus tels quels
 * pour l'affichage historique — voir §3.6 (compatibilité).</p>
 *
 * <p><b>Fourchette choisie par le coach</b> ({@code custom}) : mêmes champs {@code ref + %}, mais
 * <b>voulus</b>, pas hérités. Un « 10×400 à 102–106 % de VC » ne rentre dans aucune zone nommée, et
 * les zones du club ne se règlent pas séance par séance. Le drapeau distingue les deux : sans lui,
 * la migration douce ({@link com.coachrun.service.PrescriptionZoneMapper}) recollerait une zone sur
 * le bloc à la relecture et la cible du coach serait remplacée en silence par celle de la zone.</p>
 */
public record CoursePrescription(
        UUID zoneId,
        /**
         * Zone <b>cardio</b> facultative, portée en plus de la zone d'allure. Les deux échelles
         * étant indépendantes (12 bandes d'allure, 4 bandes cardio), c'est elle qui fournit la
         * cible FC affichée à côté de l'allure. Absente, la FC est lue sur {@code zoneId} — cas des
         * zones qui portent allure <i>et</i> FC.
         */
        UUID hrZoneId,
        PrescriptionRef ref,
        Double minPct,
        Double maxPct,
        /** La fourchette {@code ref + %} est un choix du coach, pas un reliquat legacy. */
        Boolean custom
) {

    /** Prescription par zone (authoring Z3). */
    public static CoursePrescription ofZone(UUID zoneId) {
        return new CoursePrescription(zoneId, null, null, null, null, null);
    }

    /** Prescription par zone d'allure doublée d'une zone cardio (double échelle). */
    public static CoursePrescription ofZones(UUID zoneId, UUID hrZoneId) {
        return new CoursePrescription(zoneId, hrZoneId, null, null, null, null);
    }

    /** Prescription legacy par référentiel + fourchette % (lecture des snapshots figés). */
    public static CoursePrescription ofRange(PrescriptionRef ref, Double minPct, Double maxPct) {
        return new CoursePrescription(null, null, ref, minPct, maxPct, null);
    }

    /** Fourchette en % assumée par le coach : elle prime sur toute zone déduite. */
    public static CoursePrescription ofCustomRange(PrescriptionRef ref, Double minPct, Double maxPct) {
        return new CoursePrescription(null, null, ref, minPct, maxPct, true);
    }

    /** Vrai si la prescription cible une zone (chemin Z3) plutôt qu'un référentiel legacy. */
    public boolean hasZone() {
        return zoneId != null;
    }

    /**
     * Vrai si le coach a écrit lui-même la fourchette (référentiel + % complets). Ce cas court-circuite
     * la zone : c'est la prescription la plus précise dont on dispose pour ce bloc.
     */
    public boolean isCustomRange() {
        return Boolean.TRUE.equals(custom) && ref != null && minPct != null && maxPct != null;
    }
}
