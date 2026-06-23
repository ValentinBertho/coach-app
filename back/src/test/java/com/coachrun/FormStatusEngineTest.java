package com.coachrun;

import com.coachrun.engine.FormStatusEngine;
import com.coachrun.entity.enums.FormStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie l'état de forme : calculé sur fatigue + douleur uniquement (jamais le RPE).
 */
class FormStatusEngineTest {

    private final FormStatusEngine engine = new FormStatusEngine();

    @Test
    void greenWhenFreshOrNoData() {
        assertThat(engine.classify(3, 1)).isEqualTo(FormStatus.GREEN);
        assertThat(engine.classify(null, null)).isEqualTo(FormStatus.GREEN);
        assertThat(engine.classify(4, 2)).isEqualTo(FormStatus.GREEN);
    }

    @Test
    void orangeOnModerateFatigueOrPain() {
        assertThat(engine.classify(5, 0)).isEqualTo(FormStatus.ORANGE);   // fatigue ≥ 5
        assertThat(engine.classify(0, 3)).isEqualTo(FormStatus.ORANGE);   // douleur ≥ 3
        assertThat(engine.classify(7, 4)).isEqualTo(FormStatus.ORANGE);
    }

    @Test
    void redOnHighFatigueOrPain() {
        assertThat(engine.classify(8, 0)).isEqualTo(FormStatus.RED);      // fatigue ≥ 8
        assertThat(engine.classify(0, 5)).isEqualTo(FormStatus.RED);      // douleur ≥ 5
        assertThat(engine.classify(9, 6)).isEqualTo(FormStatus.RED);
    }
}
