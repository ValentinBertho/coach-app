package com.coachrun.entity.enums;

/**
 * Formule d'estimation du 1RM (cf. DARI Lab). NUZZO est la méthode recommandée par défaut
 * (polynôme degré 3, Pr. Lacourpaille, plus précise sur hautes répétitions).
 */
public enum RmFormula {
    EPLEY,
    BRZYCKI,
    RIR_BASED,
    NUZZO
}
