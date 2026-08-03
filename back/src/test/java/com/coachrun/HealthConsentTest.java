package com.coachrun;

import com.coachrun.entity.Athlete;
import com.coachrun.exception.ForbiddenException;
import com.coachrun.security.HealthDataConsentValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consentement au traitement des données de santé : état courant et garde de collecte.
 *
 * <p>Le consentement était écrit une fois à l'acceptation de l'invitation puis jamais relu avant
 * d'écrire quoi que ce soit. Deux trous en découlaient, tous deux sur le parcours normal : le
 * coach remplit la fiche d'un athlète <em>avant</em> de l'inviter (donc sans base légale), et
 * rien n'aurait empêché de continuer à collecter après un retrait.</p>
 */
class HealthConsentTest {

    private final HealthDataConsentValidator validator = new HealthDataConsentValidator();

    private Athlete athlete(Instant consentedAt, Instant withdrawnAt) {
        Athlete a = new Athlete();
        a.setFirstName("Chloé");
        a.setHealthDataConsentAt(consentedAt);
        a.setHealthDataConsentWithdrawnAt(withdrawnAt);
        return a;
    }

    @Test
    void athleteWhoNeverAcceptedHasNoActiveConsent() {
        assertThat(athlete(null, null).isHealthDataConsentActive()).isFalse();
    }

    @Test
    void consentGivenAndNeverWithdrawnIsActive() {
        assertThat(athlete(Instant.now(), null).isHealthDataConsentActive()).isTrue();
    }

    @Test
    void withdrawalAfterConsentDeactivatesIt() {
        Instant consented = Instant.now().minus(30, ChronoUnit.DAYS);
        Instant withdrawn = Instant.now().minus(1, ChronoUnit.DAYS);
        assertThat(athlete(consented, withdrawn).isHealthDataConsentActive()).isFalse();
    }

    /**
     * Reconsentement : la date de retrait est conservée (preuve du mouvement passé) mais un
     * consentement postérieur reprend le dessus. Sans cette comparaison, réactiver le partage
     * après un retrait serait impossible.
     */
    @Test
    void consentGivenAgainAfterWithdrawalIsActive() {
        Instant withdrawn = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant reconsented = Instant.now().minus(1, ChronoUnit.DAYS);
        assertThat(athlete(reconsented, withdrawn).isHealthDataConsentActive()).isTrue();
    }

    @Test
    void validatorRefusesCollectionWhenInvitationNeverAccepted() {
        assertThatThrownBy(() -> validator.requireConsent(athlete(null, null), "un test de lactate"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("n'a pas encore accepté son invitation")
                .hasMessageContaining("un test de lactate");
    }

    @Test
    void validatorRefusesCollectionAfterWithdrawalWithADistinctMessage() {
        Athlete withdrawn = athlete(
                Instant.now().minus(30, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
        assertThatThrownBy(() -> validator.requireConsent(withdrawn, "la douleur déclarée"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("a retiré son consentement");
    }

    @Test
    void validatorLetsCollectionThroughWhenConsentIsActive() {
        assertThatCode(() -> validator.requireConsent(athlete(Instant.now(), null), "un test"))
                .doesNotThrowAnyException();
    }

    /** Un athlète absent ne doit pas faire échouer une garde : c'est un autre problème. */
    @Test
    void validatorIgnoresNullAthlete() {
        assertThatCode(() -> validator.requireConsent(null, "un test")).doesNotThrowAnyException();
    }

    // --- Minimisation des valeurs déclarées par l'athlète (V0-05) --------------

    /**
     * Deuxième régime, distinct du refus : quand c'est l'athlète qui déclare une valeur sur
     * lui-même, on écarte la donnée plutôt que de bloquer le geste. Refuser son retour de séance
     * casserait la boucle quotidienne pour un RPE et un commentaire qui, eux, relèvent de
     * l'exécution du contrat.
     */
    @Test
    void keepsSelfDeclaredHealthValueWhenConsentIsActive() {
        assertThat(validator.keepIfAllowed(athlete(Instant.now(), null), 7)).isEqualTo(7);
    }

    @Test
    void dropsSelfDeclaredHealthValueWithoutConsent() {
        assertThat(validator.keepIfAllowed(athlete(null, null), 7)).isNull();
    }

    @Test
    void dropsSelfDeclaredHealthValueAfterWithdrawal() {
        Athlete withdrawn = athlete(
                Instant.now().minus(30, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS));
        assertThat(validator.keepIfAllowed(withdrawn, 4)).isNull();
    }
}
