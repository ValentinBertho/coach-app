package com.coachrun.entity.enums;

/**
 * Origine d'une valeur de zone par athlète : pré-remplie automatiquement depuis le moteur physio
 * (socle VDOT/seuils) ou saisie/ajustée manuellement par le coach. Cf. AUDIT-COACH-ZONES-METRIQUES §3.1.
 */
public enum ZoneValueSource {
    /** Valeur générée par le moteur (resync) — écrasable au prochain resync si non verrouillée. */
    AUTO,
    /** Valeur imposée par le coach — jamais écrasée par le resync. */
    MANUAL
}
