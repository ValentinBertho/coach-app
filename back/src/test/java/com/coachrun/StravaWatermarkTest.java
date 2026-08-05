package com.coachrun;

import com.coachrun.integration.StravaClient.StravaActivity;
import com.coachrun.service.StravaService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Curseur d'import Strava.
 *
 * <p>Le défaut que ces tests verrouillent a coûté, en production, <b>toutes</b> les sorties de
 * plus d'une demi-heure. Le curseur était posé à l'instant de la synchro, alors que le paramètre
 * {@code after} de Strava filtre sur l'heure de <em>départ</em> de la sortie : une course
 * commencée à 18 h et déposée à 19 h 14 était exclue par la synchro de 19 h 30, qui demandait
 * « ce qui a commencé après 18 h 30 ». Définitivement, sans trace ni erreur.</p>
 *
 * <p>C'est aussi pourquoi le premier import paraissait toujours fonctionner : sans curseur, la
 * fenêtre de trente jours ramène tout, et le défaut n'apparaît qu'à la deuxième synchro.</p>
 */
class StravaWatermarkTest {

    private static final long HOUR = 3600L;
    private static final long DAY = 24 * HOUR;

    /**
     * Le scénario exact du terrain. La synchro de 18 h 30 ne voit que la sortie du matin ; la
     * course de 17 h 58 n'est déposée qu'à 19 h 14. Le curseur laissé par la synchro de 18 h 30
     * doit rester <b>en deçà</b> de 17 h 58, sinon celle de 19 h 30 ne verra jamais la course.
     *
     * <p>C'est tout le défaut : le curseur était posé sur l'heure de la synchro, donc à 18 h 30,
     * et la requête suivante demandait « ce qui a commencé après 18 h 30 ».</p>
     */
    @Test
    void theCursorStaysBehindAnActivityUploadedAfterTheSync() {
        long morningRun = Instant.parse("2026-08-05T06:10:00Z").getEpochSecond();
        long previous = morningRun - 2 * DAY;
        long lateRun = Instant.parse("2026-08-05T17:58:00Z").getEpochSecond();

        // Synchro de 18 h 30 : elle ne connaît que la sortie du matin.
        long afterSync = StravaService.nextWatermark(previous, morningRun);

        assertThat(afterSync).isLessThan(lateRun);
    }

    /** Le curseur suit le départ de la dernière sortie vue, reculé de la marge de rattrapage. */
    @Test
    void theCursorTrailsTheLatestActivityStart() {
        long previous = Instant.parse("2026-07-01T00:00:00Z").getEpochSecond();
        long latest = Instant.parse("2026-08-05T10:00:00Z").getEpochSecond();

        long next = StravaService.nextWatermark(previous, latest);

        assertThat(next).isEqualTo(latest - 2 * DAY);
    }

    /**
     * Aucune sortie vue : le curseur ne bouge pas. S'il avançait, une semaine sans courir le
     * ferait glisser au-delà d'une trace déposée en retard, qui serait perdue à son tour.
     */
    @Test
    void anEmptySyncLeavesTheCursorWhereItWas() {
        long previous = Instant.parse("2026-08-01T06:00:00Z").getEpochSecond();

        assertThat(StravaService.nextWatermark(previous, 0L)).isEqualTo(previous);
    }

    /** Le curseur ne recule jamais : une vieille sortie retrouvée ne rouvre pas la fenêtre. */
    @Test
    void anOldActivityDoesNotDragTheCursorBackwards() {
        long previous = Instant.parse("2026-08-05T00:00:00Z").getEpochSecond();
        long ancient = Instant.parse("2026-01-01T00:00:00Z").getEpochSecond();

        assertThat(StravaService.nextWatermark(previous, ancient)).isEqualTo(previous);
    }

    /**
     * Le départ se lit sur {@code start_date}, qui porte le fuseau. {@code start_date_local} n'en
     * a pas : le lire comme un instant décalerait le curseur de plusieurs heures — précisément
     * l'erreur qu'un curseur ne pardonne pas.
     */
    @Test
    void theStartInstantComesFromTheZonedField() {
        StravaActivity a = activity("2026-08-05T19:58:00Z", "2026-08-05T21:58:00");

        assertThat(StravaService.startEpoch(a))
                .isEqualTo(Instant.parse("2026-08-05T19:58:00Z").getEpochSecond());
    }

    /** Une date absente ou illisible ne fait pas avancer le curseur — elle ne le casse pas non plus. */
    @Test
    void anUnreadableStartIsIgnoredRatherThanFatal() {
        assertThat(StravaService.startEpoch(activity(null, "2026-08-05T21:58:00"))).isZero();
        assertThat(StravaService.startEpoch(activity("pas une date", null))).isZero();
    }

    private StravaActivity activity(String startDate, String startDateLocal) {
        return new StravaActivity(1L, "Afternoon Run", "Run", 14418.0, 4351, null, null, null,
                null, null, null, null, null, null, null, null, null, startDateLocal, startDate);
    }
}
