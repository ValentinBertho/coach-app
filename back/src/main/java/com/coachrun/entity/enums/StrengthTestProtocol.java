package com.coachrun.entity.enums;

/**
 * Protocole de test de force (cf. DARI Lab §6.5). Détermine la dérivation du 1RM.
 *
 * <ul>
 *   <li>{@code TRUE_1RM} — charge maximale soulevée 1 fois ; e1RM = charge.</li>
 *   <li>{@code REP_TEST_3_5} — série à l'échec sur 3 à 5 reps ; e1RM via Nuzzo.</li>
 *   <li>{@code AMRAP_TEST} — répétitions maximales à charge fixe ; e1RM via Nuzzo.</li>
 *   <li>{@code ISO_MVC} — contraction isométrique maximale (dynamomètre) ; valeur conservée telle quelle.</li>
 * </ul>
 */
public enum StrengthTestProtocol {
    TRUE_1RM,
    REP_TEST_3_5,
    AMRAP_TEST,
    ISO_MVC
}
