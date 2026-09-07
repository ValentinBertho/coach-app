package com.coachrun.security;

import com.coachrun.entity.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le jeton de flux, unité par unité : sa durée, son usage unique, sa portée.
 *
 * <p><b>Ce qu'il remplace.</b> Les flux SSE et l'ouverture d'une pièce jointe portaient le
 * <i>jeton de session</i> en paramètre d'URL. Un JWT d'accès vaut une heure et ouvre toute l'API ;
 * une URL, elle, se retrouve dans les journaux d'accès du relais, dans l'historique du navigateur
 * et dans le {@code Referer}. Un journal partagé avec l'hébergeur suffisait à rejouer une session
 * complète — et la fenêtre de rejeu se comptait en dizaines de minutes.</p>
 *
 * <p>Les propriétés vérifiées ici sont exactement ce qui referme cette fenêtre. Elles se testent
 * sans contexte Spring : c'est de l'arithmétique de jeton, pas du HTTP.</p>
 */
class StreamTokenServiceTest {

    private static final AuthPrincipal ALICE = principal();
    private static final AuthPrincipal BOB = principal();

    private static AuthPrincipal principal() {
        return new AuthPrincipal(UUID.randomUUID(), UUID.randomUUID(), null,
                "x@test.fr", UserRole.COACH);
    }

    /** Horloge que le test avance à la main : pas de {@code Thread.sleep} pour tester une minute. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-03-01T10:00:00Z");

        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }

        void advance(Duration by) { now = now.plus(by); }
    }

    private MovableClock clock;

    private StreamTokenService service(Duration ttl) {
        clock = new MovableClock();
        return new StreamTokenService(ttl, clock);
    }

    @Test
    void aFreshTokenOpensTheStreamItWasIssuedFor() {
        StreamTokenService tokens = service(Duration.ofSeconds(60));
        String token = tokens.issue(ALICE, StreamTokenService.Scope.STREAM);

        assertThat(tokens.consume(token, StreamTokenService.Scope.STREAM))
                .contains(ALICE);
    }

    /**
     * Le cœur du correctif : une URL glanée dans un journal d'accès ne se rejoue pas, même
     * ramassée dans la seconde.
     */
    @Test
    void aTokenIsBurntOnFirstUse() {
        StreamTokenService tokens = service(Duration.ofSeconds(60));
        String token = tokens.issue(ALICE, StreamTokenService.Scope.STREAM);

        assertThat(tokens.consume(token, StreamTokenService.Scope.STREAM)).isPresent();
        assertThat(tokens.consume(token, StreamTokenService.Scope.STREAM))
                .as("le second usage ne vaut rien")
                .isEmpty();
    }

    @Test
    void aTokenExpires() {
        StreamTokenService tokens = service(Duration.ofSeconds(60));
        String token = tokens.issue(ALICE, StreamTokenService.Scope.STREAM);

        clock.advance(Duration.ofSeconds(61));

        assertThat(tokens.consume(token, StreamTokenService.Scope.STREAM)).isEmpty();
    }

    /** Un jeton de flux n'ouvre pas une pièce jointe, et réciproquement. */
    @Test
    void aTokenOnlyServesItsOwnScope() {
        StreamTokenService tokens = service(Duration.ofSeconds(60));
        String forStream = tokens.issue(ALICE, StreamTokenService.Scope.STREAM);

        assertThat(tokens.consume(forStream, StreamTokenService.Scope.ATTACHMENT)).isEmpty();
    }

    /**
     * Présenter un jeton hors de sa portée le brûle quand même. Sinon, l'essayer sur la mauvaise
     * portée puis sur la bonne serait un moyen gratuit de tester des valeurs.
     */
    @Test
    void aTokenPresentedOnTheWrongScopeIsBurntToo() {
        StreamTokenService tokens = service(Duration.ofSeconds(60));
        String forStream = tokens.issue(ALICE, StreamTokenService.Scope.STREAM);

        tokens.consume(forStream, StreamTokenService.Scope.ATTACHMENT);

        assertThat(tokens.consume(forStream, StreamTokenService.Scope.STREAM)).isEmpty();
    }

    @Test
    void anUnknownTokenOpensNothing() {
        StreamTokenService tokens = service(Duration.ofSeconds(60));

        assertThat(tokens.consume("aaaaaaaaaaaa.inventé", StreamTokenService.Scope.STREAM)).isEmpty();
        assertThat(tokens.consume("", StreamTokenService.Scope.STREAM)).isEmpty();
        assertThat(tokens.consume(null, StreamTokenService.Scope.STREAM)).isEmpty();
    }

    @Test
    void twoTokensNeverCollide() {
        StreamTokenService tokens = service(Duration.ofSeconds(60));

        assertThat(tokens.issue(ALICE, StreamTokenService.Scope.STREAM))
                .isNotEqualTo(tokens.issue(ALICE, StreamTokenService.Scope.STREAM));
    }

    /**
     * L'étiquette de tête sert au comptage des requêtes, et à rien d'autre : elle est stable pour
     * un compte, différente d'un compte à l'autre, et ne laisse pas deviner l'identifiant.
     *
     * <p>Sans elle, les flux — dont le jeton est opaque — retomberaient sur un comptage par
     * adresse IP, c'est-à-dire sur un compteur partagé par tous les athlètes d'un club derrière
     * la même box.</p>
     */
    @Test
    void theCountingTagIsStablePerAccountAndRevealsNothing() {
        StreamTokenService tokens = service(Duration.ofSeconds(60));
        String first = tokens.issue(ALICE, StreamTokenService.Scope.STREAM);
        String second = tokens.issue(ALICE, StreamTokenService.Scope.ATTACHMENT);
        String other = tokens.issue(BOB, StreamTokenService.Scope.STREAM);

        assertThat(StreamTokenService.tagOf(first)).isEqualTo(StreamTokenService.tagOf(second));
        assertThat(StreamTokenService.tagOf(other)).isNotEqualTo(StreamTokenService.tagOf(first));
        assertThat(first).doesNotContain(ALICE.userId().toString());
    }

    /** Les jetons que personne ne vient consommer ne s'accumulent pas indéfiniment. */
    @Test
    void expiredTokensAreSweptAway() {
        StreamTokenService tokens = service(Duration.ofSeconds(60));
        for (int i = 0; i < 10; i++) {
            tokens.issue(ALICE, StreamTokenService.Scope.STREAM);
        }
        assertThat(tokens.outstanding()).isEqualTo(10);

        clock.advance(Duration.ofMinutes(2));
        tokens.purgeExpired();

        assertThat(tokens.outstanding()).isZero();
    }
}
