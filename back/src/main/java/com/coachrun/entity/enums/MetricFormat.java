package com.coachrun.entity.enums;

/** Format d'affichage d'une valeur de métrique (cf. AUDIT-COACH-ZONES-METRIQUES §3.1). */
public enum MetricFormat {
    /** minutes:secondes, ex. allure « 4:05 ». */
    MMSS,
    /** entier, ex. FC « 168 ». */
    INT,
    /** décimal à une décimale, ex. vitesse « 12,4 ». */
    DEC1
}
