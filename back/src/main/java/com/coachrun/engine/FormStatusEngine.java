package com.coachrun.engine;

import com.coachrun.entity.enums.FormStatus;
import org.springframework.stereotype.Component;

/**
 * État de forme à partir de la fatigue et de la douleur (cf. DARI Lab — règle non négociable :
 * jamais le RPE).
 * <ul>
 *   <li>🔴 RED : fatigue ≥ 8 OU douleur ≥ 5 ;</li>
 *   <li>🟡 ORANGE : fatigue ≥ 5 OU douleur ≥ 3 ;</li>
 *   <li>🟢 GREEN : sinon.</li>
 * </ul>
 * Une valeur absente est traitée comme 0 (pas de signal négatif).
 */
@Component
public class FormStatusEngine {

    public FormStatus classify(Integer fatigue, Integer pain) {
        int f = fatigue == null ? 0 : fatigue;
        int p = pain == null ? 0 : pain;
        if (f >= 8 || p >= 5) {
            return FormStatus.RED;
        }
        if (f >= 5 || p >= 3) {
            return FormStatus.ORANGE;
        }
        return FormStatus.GREEN;
    }
}
