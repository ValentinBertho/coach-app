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
        PrescriptionRef ref,
        Double minPct,
        Double maxPct
) {

    /** Prescription par zone (authoring Z3). */
    public static CoursePrescription ofZone(UUID zoneId) {
        return new CoursePrescription(zoneId, null, null, null);
    }

    /** Prescription legacy par référentiel + fourchette % (lecture des snapshots figés). */
    public static CoursePrescription ofRange(PrescriptionRef ref, Double minPct, Double maxPct) {
        return new CoursePrescription(null, ref, minPct, maxPct);
    }

    /** Vrai si la prescription cible une zone (chemin Z3) plutôt qu'un référentiel legacy. */
    public boolean hasZone() {
        return zoneId != null;
    }
}
