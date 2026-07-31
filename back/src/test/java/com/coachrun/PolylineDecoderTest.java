package com.coachrun;

import com.coachrun.util.PolylineDecoder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Décodage du tracé Strava (« Google Encoded Polyline »). Sans lui, un athlète Strava n'avait
 * pas de carte alors que le tracé arrivait déjà dans la réponse de l'API.
 */
class PolylineDecoderTest {

    /** Quatre points sur les quais de Saône, encodés au format Google. */
    private static final String LYON = "opfvGogr\\gEgEgEkHkHkH";

    @Test
    void decodesToLatLonPairs() {
        List<double[]> points = PolylineDecoder.decode(LYON);

        assertThat(points).hasSize(4);
        assertThat(points.get(0)[0]).isCloseTo(45.75, within(1e-5));
        assertThat(points.get(0)[1]).isCloseTo(4.85, within(1e-5));
        assertThat(points.get(3)[0]).isCloseTo(45.7535, within(1e-5));
        assertThat(points.get(3)[1]).isCloseTo(4.854, within(1e-5));
    }

    @Test
    void handlesMissingOrEmptyPolyline() {
        // Une activité sur tapis n'a pas de tracé : pas de carte, mais pas d'échec d'import.
        assertThat(PolylineDecoder.decode(null)).isEmpty();
        assertThat(PolylineDecoder.decode("")).isEmpty();
        assertThat(PolylineDecoder.decode("   ")).isEmpty();
    }

    @Test
    void stopsCleanlyOnATruncatedPolyline() {
        // Chaîne coupée en plein milieu d'une valeur : on garde ce qui est lisible.
        // 6 caractères : la latitude est lisible, pas la longitude → aucun point complet.
        assertThat(PolylineDecoder.decode(LYON.substring(0, 6))).isEmpty();
        // 14 caractères : deux points complets, le troisième est coupé.
        assertThat(PolylineDecoder.decode(LYON.substring(0, 14))).hasSize(2);
    }

    @Test
    void downsamplesVeryLongRoutes() {
        // Un marathon enregistré à la seconde dépasse les 10 000 points : la carte n'en a pas besoin.
        StringBuilder encoded = new StringBuilder("opfvGogr\\");
        for (int i = 0; i < 3000; i++) {
            encoded.append("gEgE");
        }

        List<double[]> points = PolylineDecoder.decode(encoded.toString());

        assertThat(points).hasSizeLessThanOrEqualTo(401);
        assertThat(points).isNotEmpty();
    }
}
