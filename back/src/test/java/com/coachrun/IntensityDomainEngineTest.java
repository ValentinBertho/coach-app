package com.coachrun;

import com.coachrun.engine.IntensityDomainEngine;
import com.coachrun.entity.enums.IntensityDomain;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie la classification en domaines d'intensité : priorité aux seuils physiologiques,
 * repli sur la FC, défaut conservatif.
 */
class IntensityDomainEngineTest {

    private final IntensityDomainEngine engine = new IntensityDomainEngine();

    @Test
    void physiologyTakesPriorityOverHeartRate() {
        // LT1 = 3.5 m/s, LT2 = 3.9 m/s
        assertThat(engine.classify(3.0, null, 3.5, 3.9, 180, 80, 90)).isEqualTo(IntensityDomain.DOMAIN_1);
        assertThat(engine.classify(3.7, null, 3.5, 3.9, 180, 80, 90)).isEqualTo(IntensityDomain.DOMAIN_2);
        assertThat(engine.classify(4.2, null, 3.5, 3.9, 180, 80, 90)).isEqualTo(IntensityDomain.DOMAIN_3);
    }

    @Test
    void lt2BoundaryStaysInDomain2() {
        assertThat(engine.classify(3.9, null, 3.5, 3.9, 180, 80, 90)).isEqualTo(IntensityDomain.DOMAIN_2);
    }

    @Test
    void fallsBackToHeartRateWhenNoThresholds() {
        assertThat(engine.classify(null, 130, null, null, 180, 80, 90)).isEqualTo(IntensityDomain.DOMAIN_1);
        assertThat(engine.classify(null, 160, null, null, 180, 80, 90)).isEqualTo(IntensityDomain.DOMAIN_2);
        assertThat(engine.classify(null, 175, null, null, 180, 80, 90)).isEqualTo(IntensityDomain.DOMAIN_3);
    }

    @Test
    void defaultsToDomain1WithoutData() {
        assertThat(engine.classify(null, null, null, null, null, 80, 90)).isEqualTo(IntensityDomain.DOMAIN_1);
    }
}
