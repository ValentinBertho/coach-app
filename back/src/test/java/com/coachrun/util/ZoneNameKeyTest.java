package com.coachrun.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneNameKeyTest {

    @Test
    void lowestZoneMatchesAcrossTheThreeReferenceModels() {
        String recup = ZoneNameKey.of("Récupération");
        assertThat(ZoneNameKey.of("Footing facile")).isEqualTo(recup);
        assertThat(ZoneNameKey.of("Régénération")).isEqualTo(recup);
        assertThat(ZoneNameKey.of("  RECUPERATION ")).isEqualTo(recup);
    }

    @Test
    void ignoresCaseAccentsAndExtraSpaces() {
        assertThat(ZoneNameKey.of(" Seuil  2   bas ")).isEqualTo(ZoneNameKey.of("seuil 2 bas"));
        assertThat(ZoneNameKey.of("Tempo")).isEqualTo("tempo");
    }

    @Test
    void keepsDistinctZonesDistinct() {
        assertThat(ZoneNameKey.of("EF")).isNotEqualTo(ZoneNameKey.of("Récupération"));
        assertThat(ZoneNameKey.of("Seuil 1")).isNotEqualTo(ZoneNameKey.of("Seuil 2 bas"));
        assertThat(ZoneNameKey.of(null)).isEmpty();
    }
}
