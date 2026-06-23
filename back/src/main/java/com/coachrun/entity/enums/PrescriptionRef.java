package com.coachrun.entity.enums;

/**
 * Référentiel d'une prescription d'allure course (cf. DARI Lab — prescription en fourchettes).
 * Le pourcentage prescrit s'applique soit à un seuil physiologique (LT1/LT2/VC), soit à une
 * allure d'équivalence VDOT sur une distance donnée.
 */
public enum PrescriptionRef {
    PCT_LT1,
    PCT_LT2,
    PCT_VC,
    PCT_PACE_800M,
    PCT_PACE_1500M,
    PCT_PACE_3000M,
    PCT_PACE_5KM,
    PCT_PACE_10KM,
    PCT_PACE_15KM,
    PCT_PACE_SEMI,
    PCT_PACE_MARATHON
}
