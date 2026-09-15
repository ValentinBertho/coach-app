package com.coachrun.entity.enums;

/**
 * Latéralité d'un exercice de force : comment le volume prescrit se répartit entre les côtés.
 *
 * <p>« 3 × 8 » sur une fente ne veut pas dire la même chose selon qu'il s'agit de 8 répétitions
 * en tout, de 8 par jambe, ou de 8 en alternant — et l'athlète n'avait aucun moyen de le
 * deviner.</p>
 */
public enum SideMode {
    /** Les deux côtés travaillent ensemble (défaut). */
    BILATERAL,
    /** On alterne gauche / droite à l'intérieur de la série. */
    ALTERNE,
    /** Le volume prescrit vaut pour chaque côté (série faite deux fois). */
    PAR_COTE
}
