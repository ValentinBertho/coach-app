package com.coachrun;

import com.coachrun.service.AthleteFeedbackService.LastFeedback;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Péremption du signal de forme.
 *
 * <p>Le dernier retour connu était classé sans aucune borne de fraîcheur. Un athlète ayant déclaré
 * fatigue 9 et douleur 6 après une compétition restait donc rouge indéfiniment s'il cessait de
 * saisir — et remontait en tête de la file « à surveiller », devant un athlète qui avait signalé
 * une gêne le matin même. La date, elle, n'était affichée nulle part : les deux pastilles étaient
 * indiscernables.</p>
 *
 * <p>Symétriquement, une valeur absente était lue comme un zéro : un athlète qui n'avait jamais
 * rien déclaré s'affichait en vert, c'est-à-dire comme une assurance que personne n'avait donnée.</p>
 */
class FormFreshnessTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 3);

    @Test
    void todaysFeedbackIsFresh() {
        assertThat(new LastFeedback(6, 2, TODAY).isFresh(TODAY)).isTrue();
    }

    @Test
    void feedbackWithinTheWindowIsFresh() {
        LocalDate justInside = TODAY.minusDays(AthleteFeedbackServiceWindow.DAYS);
        assertThat(new LastFeedback(8, 4, justInside).isFresh(TODAY)).isTrue();
    }

    @Test
    void feedbackOlderThanTheWindowIsStale() {
        LocalDate justOutside = TODAY.minusDays(AthleteFeedbackServiceWindow.DAYS + 1);
        assertThat(new LastFeedback(9, 6, justOutside).isFresh(TODAY)).isFalse();
    }

    /** Le cas qui motivait tout : un rouge de novembre, lu en janvier. */
    @Test
    void aRedFromSixWeeksAgoIsNoLongerASignal() {
        assertThat(new LastFeedback(9, 6, TODAY.minusWeeks(6)).isFresh(TODAY)).isFalse();
    }

    @Test
    void anAthleteWhoNeverDeclaredAnythingHasNoSignal() {
        assertThat(LastFeedback.NONE.isFresh(TODAY)).isFalse();
    }

    /** Une date récente mais sans aucune valeur ne dit rien non plus. */
    @Test
    void aDatedFeedbackWithoutAnyValueIsNotASignal() {
        assertThat(new LastFeedback(null, null, TODAY).isFresh(TODAY)).isFalse();
    }

    /** Accès lisible à la constante de fenêtre, pour que les bornes du test suivent le code. */
    private static final class AthleteFeedbackServiceWindow {
        static final int DAYS = com.coachrun.service.AthleteFeedbackService.FRESHNESS_DAYS;
    }
}
