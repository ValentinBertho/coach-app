package com.coachrun;

import com.coachrun.util.PlannedVolume;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce qui compte comme volume prévu d'une séance — et ce qui n'en est que le résidu.
 *
 * <p>Une séance écrite « footing 55' », prescrite à un athlète dont le profil ne fournit aucune
 * allure, ne totalise que ses éducatifs : une centaine de mètres. Ce chiffre s'affichait comme un
 * volume, faisait perdre la séance au rapprochement, et comptait pour zéro kilomètre dans le
 * volume hebdomadaire. Ces tests fixent le seuil qui le disqualifie.</p>
 */
class PlannedVolumeTest {

    /** Allure d'endurance typique : 5:30/km. */
    private static final int REF = 330;

    @Test
    void keepsAVolumeThatDescribesASession() {
        assertThat(PlannedVolume.usableDistanceM(12000)).isEqualTo(12000);
        assertThat(PlannedVolume.usableDurationS(3300)).isEqualTo(3300);
    }

    /**
     * Le résidu ne se reconnaît pas à son incohérence — 100 m en 20 s donne 3'20/km, une allure
     * parfaitement plausible — mais à sa taille. Aucune séance de course à pied ne tient sous
     * 500 mètres ou trois minutes, échauffement compris.
     */
    @Test
    void discardsWhatIsTooSmallToBeASession() {
        assertThat(PlannedVolume.usableDistanceM(100)).isNull();
        assertThat(PlannedVolume.usableDistanceM(499)).isNull();
        assertThat(PlannedVolume.usableDurationS(20)).isNull();
        assertThat(PlannedVolume.usableDurationS(179)).isNull();
    }

    @Test
    void estimatesTheDistanceFromTheDurationWhenTheTargetIsUnusable() {
        // 55 min à 5:30/km ≈ 10 km, arrondi à la centaine de mètres.
        assertThat(PlannedVolume.distanceOrEstimate(100, 3300, REF)).isEqualTo(10000);
        assertThat(PlannedVolume.distanceOrEstimate(null, 1800, REF)).isEqualTo(5500);
    }

    @Test
    void prefersTheTargetWhenItDescribesASession() {
        assertThat(PlannedVolume.distanceOrEstimate(12000, 3300, REF)).isEqualTo(12000);
    }

    /**
     * Sans référence propre à l'athlète, on ne dit rien : un repère tiré d'une valeur moyenne du
     * monde fausserait son volume dans un sens qu'il ne pourrait pas soupçonner.
     */
    @Test
    void inventsNothingWithoutAReferencePace() {
        assertThat(PlannedVolume.distanceOrEstimate(100, 3300, null)).isNull();
        assertThat(PlannedVolume.distanceOrEstimate(null, 3300, 0)).isNull();
        assertThat(PlannedVolume.distanceOrEstimate(null, null, REF)).isNull();
    }
}
