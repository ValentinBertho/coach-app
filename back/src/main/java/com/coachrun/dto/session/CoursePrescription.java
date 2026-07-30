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
        Double maxPct
) {

    /** Prescription par zone (authoring Z3). */
    public static CoursePrescription ofZone(UUID zoneId) {
        return new CoursePrescription(zoneId, null, null, null, null);
    }

    /** Prescription par zone d'allure doublée d'une zone cardio (double échelle). */
    public static CoursePrescription ofZones(UUID zoneId, UUID hrZoneId) {
        return new CoursePrescription(zoneId, hrZoneId, null, null, null);
    }

    /** Prescription legacy par référentiel + fourchette % (lecture des snapshots figés). */
    public static CoursePrescription ofRange(PrescriptionRef ref, Double minPct, Double maxPct) {
        return new CoursePrescription(null, null, ref, minPct, maxPct);
    }

    /** Vrai si la prescription cible une zone (chemin Z3) plutôt qu'un référentiel legacy. */
    public boolean hasZone() {
        return zoneId != null;
    }
}
